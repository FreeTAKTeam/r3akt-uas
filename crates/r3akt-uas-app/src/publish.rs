//! Aircraft position publication as the shared R3S origin-fix event (UAS-PRF-010).
//!
//! Every wire type here is shared: `r3akt_map_core::sensor_wire` owns the event
//! and its validation, `r3akt_profile_rch::encode_events` owns field 13. This
//! module only maps a [`TelemetrySample`] onto the shared `OriginPosition`.

use std::path::Path;
use std::sync::mpsc::{RecvTimeoutError, TryRecvError};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use crate::camera::origin_position;
use base64::Engine as _;
use base64::engine::general_purpose::STANDARD as BASE64_STANDARD;
use lxmf_propagation::{MessageKind, StoreLimits};
use lxmf_runtime::{
    EXT_FIELDS_BASE64, EXT_LINK_CONNECT_TIMEOUT_MS, EXT_RAW_BYTES_BASE64, InProcessBackend,
    InProcessBackendConfig, LocalPropagation, PropagationRouteLimits,
};
use lxmf_sdk::{SdkBackend, SendRequest};
use r3akt_map_core::sensor_wire::{OriginFixEvent, SensorEvent, encode_object_fields};
use r3akt_profile_rch::{EventEnvelope, RchSource, encode_events};
use rns_transport::destination::{DestinationName, SingleInputDestination};
use rns_transport::hash::AddressHash;
use rns_transport::identity::PrivateIdentity;
use rns_transport::iface::tcp_client::{TcpClient, TcpRuntimeStatusHandle};
use rns_transport::iface::{IfaceRole, InterfaceMode};
use rns_transport::transport::{Transport, TransportConfig};

use crate::aircraft::create_command;
use crate::identity::AppIdentity;
use crate::markers::{self, Change};
use crate::telemetry::TelemetrySample;
use r3akt_map_core::MarkerRecord;

#[derive(Debug, thiserror::Error)]
pub enum FixError {
    /// The shared R3S validation rejected the fix.
    #[error("origin fix rejected: {0}")]
    Invalid(&'static str),
    #[error("shared event encoder failed: {0}")]
    Encode(String),
}

/// Encodes `sample` as the aircraft object's fix message. Without a team it is
/// one origin-fix event (field 13 only, step 2a); with a team it is the shared
/// object message of `sensor_wire::encode_object_fields` (fields 3, 11 and 13),
/// which RCH relays to REM members of that team (step 2b).
///
/// `fix_time_s` must be wall-clock time; the hub ignores older fixes and
/// refuses fixes more than 30 s ahead of its clock.
///
/// # Errors
/// [`FixError::Invalid`] when shared validation rejects the values,
/// [`FixError::Encode`] when the shared encoder fails.
pub fn fix_message(
    identity: &AppIdentity,
    sample: &TelemetrySample,
    team: Option<&str>,
) -> Result<Vec<u8>, FixError> {
    let position = origin_position(sample);
    if let Some(team) = team {
        return encode_object_fields(identity.object_id(), Some(&position), &[], Some(team))
            .map_err(FixError::Invalid);
    }
    let event = SensorEvent::OriginFix(OriginFixEvent {
        origin_id: identity.object_id().to_owned(),
        position,
    });
    event.validate().map_err(FixError::Invalid)?;
    // The shared event serializes as `{event_type, payload}`, as the hub's own producer does.
    let mut envelope: EventEnvelope = serde_json::to_value(&event)
        .and_then(serde_json::from_value)
        .map_err(|error| FixError::Encode(error.to_string()))?;
    envelope.source = Some(RchSource::new(identity.destination_hash()));
    encode_events(&[envelope]).map_err(|error| FixError::Encode(error.to_string()))
}

// ---- Publisher worker (task 5, R-2: construction only) ----------------------

/// What the publisher last did. "Sent" means handed to LXMF, never accepted by the hub.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum PublishState {
    Stopped,
    Connecting,
    /// Waiting for a first fix or for the create command to be sent.
    Creating,
    Sent,
    Failed,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PublishStatus {
    pub state: PublishState,
    /// The app's `lxmf.delivery` destination hash: the hash the hub grants.
    pub destination_hash: Option<String>,
    pub object_id: Option<String>,
    pub hub_connected: bool,
    pub create_sent: bool,
    pub fixes_sent: u64,
    pub last_sent_fix: Option<TelemetrySample>,
    pub last_error: Option<String>,
    /// RCH team UID carried in field 11, or `None` for the field-13-only message.
    pub team_uid: Option<String>,
    /// Marker changes held by the custody outbox and not yet delivered to the hub.
    pub markers_pending: u32,
}

#[derive(Debug, thiserror::Error, PartialEq, Eq, uniffi::Error)]
pub enum PublishError {
    #[error("publisher is already running")]
    AlreadyRunning,
    #[error("invalid publisher configuration: {reason}")]
    Config { reason: String },
    #[error("marker not placed: {reason}")]
    Marker { reason: String },
}

enum Cmd {
    Fix(TelemetrySample),
    Marker {
        change: Change,
        record: Box<MarkerRecord>,
        reply: std::sync::mpsc::Sender<Result<(), String>>,
    },
    ResendLast,
    Stop,
}

struct Worker {
    tx: std::sync::mpsc::Sender<Cmd>,
    thread: std::thread::JoinHandle<()>,
}

/// Longest wait for the hub's path per send, bounding how long a stop can take.
pub const SEND_PATH_TIMEOUT_MS: u64 = 5_000;

static WORKER: Mutex<Option<Worker>> = Mutex::new(None);
/// The latest offered fix: the aircraft position a marker is placed at (D-10 (a)).
static LAST_FIX: Mutex<Option<TelemetrySample>> = Mutex::new(None);
static STORE_DIR: Mutex<Option<std::path::PathBuf>> = Mutex::new(None);
/// Longest wait for one marker deposit; the worker may be finishing a send.
const MARKER_REPLY_TIMEOUT: Duration = Duration::from_secs(30);
static STATUS: Mutex<PublishStatus> = Mutex::new(STATUS_STOPPED);

fn update(f: impl FnOnce(&mut PublishStatus)) {
    f(&mut STATUS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner));
}

/// Starts publishing to the hub at `hub_address` (`host:port`, Reticulum TCP)
/// whose `lxmf.delivery` destination is `hub_destination`. Identity and create
/// command live in `store_dir`. Nothing is sent before the first offered fix.
/// With `team_uid` (an RCH canonical team UID) fixes go out as the shared object
/// message that the hub relays to that team's REM members.
///
/// # Errors
/// [`PublishError::AlreadyRunning`], or [`PublishError::Config`] for a
/// malformed hub destination or team UID.
#[uniffi::export]
pub fn publish_start(
    store_dir: String,
    hub_address: String,
    hub_destination: &str,
    team_uid: Option<String>,
) -> Result<(), PublishError> {
    let hub = hex32(hub_destination, "hub destination")?;
    let team = team_uid
        .map(|t| hex32(&t, "team UID"))
        .transpose()?
        .map(|t| {
            if r3akt_profile_rch::teams::is_canonical_team_uid(&t) {
                Ok(t)
            } else {
                Err(PublishError::Config {
                    reason: format!("team UID {t} is not an RCH canonical team"),
                })
            }
        })
        .transpose()?;
    let mut worker = WORKER
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    if worker.as_ref().is_some_and(|w| !w.thread.is_finished()) {
        return Err(PublishError::AlreadyRunning);
    }
    update(|s| {
        *s = PublishStatus {
            state: PublishState::Connecting,
            team_uid: team.clone(),
            ..STATUS_STOPPED
        };
    });
    *STORE_DIR
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner) =
        Some(std::path::PathBuf::from(&store_dir));
    let (tx, rx) = std::sync::mpsc::channel();
    let thread = std::thread::spawn(move || {
        // A panic in shared code must not leave the status claiming "Sent".
        let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            run(
                Path::new(&store_dir),
                &hub_address,
                &hub,
                team.as_deref(),
                &rx,
            )
        }))
        .unwrap_or_else(|_| Err("publisher worker panicked".to_owned()));
        update(|s| {
            s.hub_connected = false;
            match result {
                Ok(()) => s.state = PublishState::Stopped,
                Err(error) => {
                    s.state = PublishState::Failed;
                    s.last_error = Some(error);
                }
            }
        });
    });
    *worker = Some(Worker { tx, thread });
    Ok(())
}

const STATUS_STOPPED: PublishStatus = PublishStatus {
    state: PublishState::Stopped,
    destination_hash: None,
    object_id: None,
    hub_connected: false,
    create_sent: false,
    fixes_sent: 0,
    last_sent_fix: None,
    last_error: None,
    team_uid: None,
    markers_pending: 0,
};

/// Lower-cases a 32-hex hash; RCH matches hashes and team UIDs in lower case.
fn hex32(value: &str, what: &str) -> Result<String, PublishError> {
    let value = value.trim().to_ascii_lowercase();
    if value.len() == 32 && value.bytes().all(|b| b.is_ascii_hexdigit()) {
        Ok(value)
    } else {
        Err(PublishError::Config {
            reason: format!("{what} must be 32 hex characters"),
        })
    }
}

/// Fix time of the last fix handed to the worker, for the publication rate.
static LAST_OFFERED_S: Mutex<Option<u64>> = Mutex::new(None);

/// UAS-VID-005: 1 Hz while moving, 0.2 Hz while stationary.
fn due(last_offered_s: Option<u64>, sample: &TelemetrySample) -> bool {
    let interval_s = if sample.ground_speed_mps >= crate::dji::MIN_MOVING_SPEED_MPS {
        1
    } else {
        5
    };
    last_offered_s.is_none_or(|last| sample.fix_time_s >= last.saturating_add(interval_s))
}

/// Offers the latest fix, however often the source polls. It always becomes the
/// marker placement position; it goes to the hub only at the publication rate,
/// replacing any fix not yet sent. Nothing is queued.
#[uniffi::export]
pub fn publish_offer_fix(sample: TelemetrySample) {
    *LAST_FIX
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner) = Some(sample.clone());
    let mut last = LAST_OFFERED_S
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    if due(*last, &sample) {
        *last = Some(sample.fix_time_s);
        send_cmd(Cmd::Fix(sample));
    }
}

pub(crate) fn last_fix() -> Option<TelemetrySample> {
    LAST_FIX
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .clone()
}

pub(crate) fn store_dir() -> Option<std::path::PathBuf> {
    STORE_DIR
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .clone()
}

/// Hands one marker change to the worker and waits until the custody outbox holds it.
pub(crate) fn submit_marker(change: Change, record: MarkerRecord) -> Result<(), String> {
    let (reply, result) = std::sync::mpsc::channel();
    let sent = WORKER
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .as_ref()
        .is_some_and(|w| {
            w.tx.send(Cmd::Marker {
                change,
                record: Box::new(record),
                reply,
            })
            .is_ok()
        });
    if !sent {
        return Err("start publishing first".to_owned());
    }
    result
        .recv_timeout(MARKER_REPLY_TIMEOUT)
        .map_err(|_| "publisher did not answer".to_owned())?
}

/// Re-sends the exact bytes of the last sent fix (replay check).
#[uniffi::export]
pub fn publish_resend_last_fix() {
    send_cmd(Cmd::ResendLast);
}

#[uniffi::export]
#[must_use]
pub fn publish_status() -> PublishStatus {
    STATUS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .clone()
}

/// Stops the publisher and waits for its thread to end. It can block for up to
/// one send's path wait ([`SEND_PATH_TIMEOUT_MS`]); call it off the UI thread.
#[uniffi::export]
pub fn publish_stop() {
    let worker = WORKER
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .take();
    if let Some(worker) = worker {
        let _ = worker.tx.send(Cmd::Stop);
        let _ = worker.thread.join();
    }
}

fn send_cmd(cmd: Cmd) {
    if let Some(worker) = WORKER
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .as_ref()
    {
        let _ = worker.tx.send(cmd);
    }
}

type Net = (
    Arc<Transport>,
    Arc<tokio::sync::Mutex<SingleInputDestination>>,
    TcpRuntimeStatusHandle,
);

/// REM's announce capabilities (owner ruling D-19): RCH lists a peer as a REM client, which an
/// operator can add to a team, only with `r3akt` and `emergencymessages`. The app handles no
/// Emergency Action Messages and reads nothing the hub sends it (G-17).
const ANNOUNCE_CAPABILITIES: [&str; 3] = ["r3akt", "emergencymessages", "telemetry"];

/// The LXMF delivery announce app data RCH lists the app by: a display name and
/// [`ANNOUNCE_CAPABILITIES`], encoded by LXMF-rs.
fn announce_app_data(destination_hash: &str) -> Option<Vec<u8>> {
    let name = format!(
        "R3AKT UAS {}",
        destination_hash.get(..8).unwrap_or(destination_hash)
    );
    let capabilities = ANNOUNCE_CAPABILITIES.map(str::to_owned);
    lxmf_core::announce::encode_delivery_announce_app_data_with_capabilities(
        &name,
        None,
        &capabilities,
    )
    .ok()
}

/// Constructs the transport, the announced delivery destination and one TCP client.
/// Path responses carry the same `app_data` as the announce, or a hub asking for a path
/// to the app would overwrite the app's name and capabilities with an empty announce.
fn connect(
    runtime: &tokio::runtime::Runtime,
    private: &PrivateIdentity,
    hub_address: &str,
    app_data: Option<Vec<u8>>,
) -> Result<Net, String> {
    runtime.block_on(async {
        let transport = Arc::new(Transport::new(TransportConfig::new(
            "r3akt-uas",
            private,
            true,
        )));
        let destination = transport
            .add_destination(private.clone(), DestinationName::new("lxmf", "delivery"))
            .await;
        transport
            .set_destination_announce_app_data(&destination, app_data)
            .await;
        let manager = transport.iface_manager();
        let client = manager
            .lock()
            .await
            .spawn_as_with_mode_and_handle(
                TcpClient::new(hub_address),
                TcpClient::spawn,
                IfaceRole::default(),
                InterfaceMode::default(),
            )
            .1;
        let tcp = client
            .lock()
            .map_err(|_| "TCP client state poisoned".to_owned())?
            .runtime_status_handle();
        Ok::<_, String>((transport, destination, tcp))
    })
}

/// The shared in-process backend and the shared custody outbox over one transport.
fn backend_and_outbox(
    runtime: &tokio::runtime::Runtime,
    transport: Arc<Transport>,
    private: PrivateIdentity,
    source: AddressHash,
    dir: &Path,
) -> Result<(InProcessBackend, LocalPropagation), String> {
    let mut config = InProcessBackendConfig::new(
        "r3akt-uas",
        runtime.handle().clone(),
        transport,
        private,
        source,
    );
    // Bounds one outbox recovery, and with it how long a stop or a marker call waits.
    config.link_connect_timeout = Duration::from_millis(SEND_PATH_TIMEOUT_MS);
    config.resource_transfer_timeout = Duration::from_secs(20);
    let outbox = LocalPropagation::open(
        config.clone(),
        dir.join("outbox.sqlite"),
        StoreLimits::default(),
    )
    .map_err(|e| format!("custody outbox: {e}"))?;
    Ok((InProcessBackend::new(config), outbox))
}

/// The worker thread: owns the runtime, transport and backend; `send` blocks here only.
fn run(
    dir: &Path,
    hub_address: &str,
    hub: &str,
    team: Option<&str>,
    rx: &std::sync::mpsc::Receiver<Cmd>,
) -> Result<(), String> {
    let identity = AppIdentity::load_or_create(dir).map_err(|e| e.to_string())?;
    let source_hex = identity.destination_hash();
    let announce_data = announce_app_data(&source_hex);
    update(|s| {
        s.destination_hash = Some(source_hex.clone());
        s.object_id = Some(identity.object_id().to_owned());
    });
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .build()
        .map_err(|e| format!("tokio runtime: {e}"))?;
    let private = identity.private_identity().clone();
    let (transport, destination, tcp) =
        connect(&runtime, &private, hub_address, announce_data.clone())?;
    let source = runtime.block_on(async { destination.lock().await.desc.address_hash });
    let (backend, outbox) = backend_and_outbox(&runtime, transport.clone(), private, source, dir)?;
    let hub_hash =
        AddressHash::new_from_hex_string(hub).map_err(|_| "hub destination".to_owned())?;
    let send = |fields: &[u8]| send_fields(&backend, &source_hex, hub, fields);
    let mut hub_remembered = false;

    let mut slot: Option<TelemetrySample> = None;
    let mut resend = false;
    let mut announced = false;
    let mut create: Option<Vec<u8>> = None;
    let mut created = false;
    let mut last_sent: Option<Vec<u8>> = None;
    loop {
        // Drain every pending command; only the newest fix survives.
        let mut next = rx.recv_timeout(Duration::from_secs(1));
        loop {
            match next {
                Ok(Cmd::Fix(sample)) => slot = Some(sample),
                Ok(Cmd::ResendLast) => resend = true,
                Ok(Cmd::Marker {
                    change,
                    record,
                    reply,
                }) => {
                    let result =
                        deposit_marker(&runtime, &outbox, &source_hex, hub, team, change, &record);
                    update_pending(&outbox);
                    let _ = reply.send(result);
                }
                Ok(Cmd::Stop) | Err(RecvTimeoutError::Disconnected) => return Ok(()),
                Err(RecvTimeoutError::Timeout) => break,
            }
            next = rx.try_recv().map_err(|e| match e {
                TryRecvError::Empty => RecvTimeoutError::Timeout,
                TryRecvError::Disconnected => RecvTimeoutError::Disconnected,
            });
        }

        // The hub verifies the sender only after an announce on this connection.
        let connected = tcp.to_json()["stream_state"].as_str() == Some("connected");
        update(|s| s.hub_connected = connected);
        if !connected {
            // The create is fire-and-forget; re-send its stored bytes on the next
            // connection (the hub de-duplicates them by command_id).
            announced = false;
            created = false;
            continue;
        }
        if !announced {
            runtime.block_on(transport.send_announce(&destination, announce_data.as_deref()));
            announced = true;
        }

        if !created {
            if create.is_none() {
                let Some(first) = &slot else { continue };
                create = Some(create_command(dir, &identity, first).map_err(|e| e.to_string())?);
                update(|s| s.state = PublishState::Creating);
            }
            match send(create.as_deref().unwrap_or_default()) {
                Ok(()) => {
                    created = true;
                    update(|s| s.create_sent = true);
                    // Packets can arrive reordered and the hub skips a fix that precedes
                    // its create, so the first fix waits for the next pass (1 s).
                    continue;
                }
                Err(error) => {
                    update(|s| s.last_error = Some(format!("create not sent: {error}")));
                    continue;
                }
            }
        }

        // Marker changes: keep the hub's identity for offline deposits, then deliver the
        // oldest pending change only, so the hub sees create, moves and delete in order.
        recover_oldest_marker(&runtime, &outbox, hub_hash, &mut hub_remembered);

        if std::mem::take(&mut resend)
            && let Some(bytes) = &last_sent
            && let Err(error) = send(bytes)
        {
            update(|s| s.last_error = Some(format!("replay not sent: {error}")));
        }
        if let Some(sample) = slot.take() {
            match fix_message(&identity, &sample, team) {
                Err(error) => update(|s| s.last_error = Some(error.to_string())),
                Ok(bytes) => {
                    if record_send(send(&bytes), &sample) {
                        last_sent = Some(bytes);
                    } else {
                        slot = Some(sample); // retried unless a newer fix replaces it
                    }
                }
            }
        }
    }
}

fn now_s() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |d| i64::try_from(d.as_secs()).unwrap_or(i64::MAX))
}

/// Encodes one marker change with the shared encoder and deposits it in the custody outbox.
/// A create is never superseded; moves and the delete of one marker share a replacement key.
fn deposit_marker(
    runtime: &tokio::runtime::Runtime,
    outbox: &LocalPropagation,
    source: &str,
    hub: &str,
    team: Option<&str>,
    change: Change,
    record: &MarkerRecord,
) -> Result<(), String> {
    let fields = markers::message(change, record, source, team, now_s().saturating_mul(1000))?;
    let prepared = runtime
        .block_on(outbox.prepare(&fields_request(source, hub, &fields)))
        .map_err(|e| format!("{e} (the hub must have been reached once)"))?;
    let kind = match change {
        Change::Create => MessageKind::Application,
        Change::Move | Change::Delete => MessageKind::Telemetry {
            key: markers::replacement_key(record),
        },
    };
    outbox
        .deposit_own(prepared, kind, now_s(), None)
        .map(|_| ())
        .map_err(|e| e.to_string())
}

/// Recovers the oldest undelivered change, as a Resource so its completion records delivery
/// (the app has no receive path for recipient proofs, G-17).
fn recover_oldest_marker(
    runtime: &tokio::runtime::Runtime,
    outbox: &LocalPropagation,
    hub: AddressHash,
    hub_remembered: &mut bool,
) {
    // Keeping the hub's identity lets marker changes be deposited later while offline.
    if !*hub_remembered {
        *hub_remembered = runtime
            .block_on(outbox.remember_recipient(hub, now_s()))
            .is_ok();
    }
    let now = now_s();
    let oldest = outbox
        .status(now)
        .ok()
        .and_then(|all| all.into_iter().find(|s| !s.state.terminal()));
    if let Some(status) = oldest.filter(|s| s.next_attempt_at <= now) {
        let limits = PropagationRouteLimits {
            max_packet_bytes: Some(0),
            link_connect_timeout: Some(Duration::from_millis(SEND_PATH_TIMEOUT_MS)),
        };
        let result = runtime.block_on(outbox.recover_direct(
            &status.transient_id,
            now,
            now + 30,
            limits,
            |_, _| true,
        ));
        if let Err(error) = result {
            update(|s| s.last_error = Some(format!("marker not sent yet: {error}")));
        }
    }
    update_pending(outbox);
}

fn update_pending(outbox: &LocalPropagation) {
    let pending = outbox.status(now_s()).map_or(0, |all| {
        u32::try_from(all.iter().filter(|s| !s.state.terminal()).count()).unwrap_or(u32::MAX)
    });
    update(|s| s.markers_pending = pending);
}

fn fields_request(source: &str, hub: &str, fields: &[u8]) -> SendRequest {
    let mut request = SendRequest::new(source, hub, serde_json::json!({}));
    let ext = &mut request.extensions;
    ext.insert(EXT_RAW_BYTES_BASE64.to_owned(), serde_json::json!(""));
    ext.insert(
        EXT_LINK_CONNECT_TIMEOUT_MS.to_owned(),
        serde_json::json!(SEND_PATH_TIMEOUT_MS),
    );
    ext.insert(
        EXT_FIELDS_BASE64.to_owned(),
        serde_json::json!(BASE64_STANDARD.encode(fields)),
    );
    request
}

/// One fields-only LXMF message to the hub through the shared in-process backend.
fn send_fields(
    backend: &InProcessBackend,
    source: &str,
    hub: &str,
    fields: &[u8],
) -> Result<(), String> {
    backend
        .send(fields_request(source, hub, fields))
        .map(|_| ())
        .map_err(|e| e.to_string())
}

fn record_send(result: Result<(), String>, sample: &TelemetrySample) -> bool {
    let sent = result.is_ok();
    update(|s| match result {
        Ok(()) => {
            s.state = PublishState::Sent;
            s.fixes_sent += 1;
            s.last_sent_fix = Some(sample.clone());
            s.last_error = None;
        }
        Err(error) => s.last_error = Some(format!("fix not sent: {error}")),
    });
    sent
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::telemetry::AltitudeReference;
    use r3akt_map_core::sensor::{AltitudeDatum, OriginPosition};
    use r3akt_map_core::sensor_frame;

    fn sample(reference: AltitudeReference) -> TelemetrySample {
        TelemetrySample {
            sample_time_s: 1_800_000_000,
            fix_time_s: 1_800_000_000,
            latitude_deg: 52.5,
            longitude_deg: 13.4,
            altitude_m: 30.0,
            altitude_reference: reference,
            ground_speed_mps: 5.0,
            course_deg: Some(90.0),
            horizontal_accuracy_m: Some(2.0),
            battery_percent: None,
            simulated: true,
        }
    }

    const BLUE: &str = "43341e5c822d99857fa6e8641f2ca9c0";

    fn decode_one(id: &AppIdentity, s: &TelemetrySample, team: Option<&str>) -> OriginPosition {
        let bytes = fix_message(id, s, team).unwrap();
        let frame = sensor_frame::decode(&bytes, &id.destination_hash()).unwrap();
        assert!(frame.diagnostics.is_empty());
        let [SensorEvent::OriginFix(fix)] = frame.events.as_slice() else {
            panic!("expected exactly one origin fix, got {:?}", frame.events);
        };
        assert_eq!(fix.origin_id, id.object_id());
        let fields = r3akt_map_core::msgpack::to_json(&bytes).unwrap();
        assert_eq!(fields.get("11").and_then(|t| t.as_str()), team);
        fix.position.clone()
    }

    #[test]
    fn fix_decodes_to_one_origin_fix_for_the_object() {
        let dir = std::env::temp_dir().join(format!("r3akt-uas-publish-{}", std::process::id()));
        let id = AppIdentity::load_or_create(&dir).unwrap();

        for team in [None, Some(BLUE)] {
            let msl = decode_one(&id, &sample(AltitudeReference::Msl), team);
            assert_eq!(
                (msl.lat, msl.lon, msl.fix_at_ms),
                (52.5, 13.4, 1_800_000_000_000)
            );
            assert_eq!(
                (msl.alt_m, msl.altitude_datum),
                (Some(30.0), AltitudeDatum::Msl)
            );
            for reference in [
                AltitudeReference::TakeoffRelative,
                AltitudeReference::Unknown,
            ] {
                let unknown = decode_one(&id, &sample(reference), team);
                assert_eq!(
                    (unknown.alt_m, unknown.altitude_datum),
                    (None, AltitudeDatum::Unknown)
                );
            }
            let mut bad = sample(AltitudeReference::Msl);
            bad.latitude_deg = 91.0;
            assert!(matches!(
                fix_message(&id, &bad, team),
                Err(FixError::Invalid(_))
            ));
        }
        // With a team the message is the shared object message (field 3 present);
        // without one it is still step 2a's field-13-only message.
        let with_team = fix_message(&id, &sample(AltitudeReference::Msl), Some(BLUE)).unwrap();
        let without = fix_message(&id, &sample(AltitudeReference::Msl), None).unwrap();
        let keys = |b: &[u8]| {
            let mut k: Vec<String> = r3akt_map_core::msgpack::to_json(b)
                .unwrap()
                .as_object()
                .unwrap()
                .keys()
                .cloned()
                .collect();
            k.sort();
            k
        };
        assert_eq!(keys(&with_team), ["11", "13", "3"]);
        assert_eq!(keys(&without), ["13"]);
    }

    #[test]
    fn publication_rate_is_1_hz_moving_and_0_2_hz_stationary() {
        let moving = sample(AltitudeReference::Msl);
        let at = |s: &TelemetrySample, t: u64| TelemetrySample {
            fix_time_s: t,
            ..s.clone()
        };
        assert!(due(None, &moving));
        assert!(!due(Some(100), &at(&moving, 100)));
        assert!(due(Some(100), &at(&moving, 101)));
        let still = TelemetrySample {
            ground_speed_mps: 0.4,
            ..moving
        };
        assert!(!due(Some(100), &at(&still, 104)));
        assert!(due(Some(100), &at(&still, 105)));
    }

    #[test]
    fn announce_names_the_app_like_a_rem_client() {
        use lxmf_core::announce::{
            capabilities_from_delivery_app_data, display_name_from_app_data,
        };
        let data = announce_app_data("8db226748847da27261859bff6e54ebe").unwrap();
        assert_eq!(
            display_name_from_app_data(Some(&data)).as_deref(),
            Some("R3AKT UAS 8db22674")
        );
        assert_eq!(
            capabilities_from_delivery_app_data(&data).unwrap(),
            ["r3akt", "emergencymessages", "telemetry"]
        );
    }

    #[test]
    fn non_canonical_team_is_refused_before_starting() {
        let err = publish_start(
            String::new(),
            "127.0.0.1:1".to_owned(),
            BLUE,
            Some("00112233445566778899aabbccddeeff".to_owned()),
        );
        assert!(
            matches!(err, Err(PublishError::Config { reason }) if reason.contains("canonical"))
        );
    }

    #[test]
    fn team_uid_is_lower_cased_and_checked() {
        assert_eq!(hex32(&BLUE.to_ascii_uppercase(), "team UID").unwrap(), BLUE);
        for bad in [
            "Blue",
            "",
            "43341e5c822d99857fa6e8641f2ca9c",
            "43341e5c822d99857fa6e8641f2ca9cz",
        ] {
            assert!(matches!(
                hex32(bad, "team UID"),
                Err(PublishError::Config { .. })
            ));
        }
    }
}
