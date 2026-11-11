//! Aircraft position publication as the shared R3S origin-fix event (UAS-PRF-010).
//!
//! Every wire type here is shared: `r3akt_map_core::sensor_wire` owns the event
//! and its validation, `r3akt_profile_rch::encode_events` owns field 13. This
//! module only maps a [`TelemetrySample`] onto the shared `OriginPosition`.

use std::path::Path;
use std::sync::mpsc::{RecvTimeoutError, TryRecvError};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use base64::Engine as _;
use base64::engine::general_purpose::STANDARD as BASE64_STANDARD;
use lxmf_runtime::{
    EXT_FIELDS_BASE64, EXT_LINK_CONNECT_TIMEOUT_MS, EXT_RAW_BYTES_BASE64, InProcessBackend,
    InProcessBackendConfig,
};
use lxmf_sdk::{SdkBackend, SendRequest};
use r3akt_map_core::sensor::{AltitudeDatum, OriginPosition};
use r3akt_map_core::sensor_wire::{OriginFixEvent, SensorEvent, encode_object_fields};
use r3akt_profile_rch::{EventEnvelope, RchSource, encode_events};
use rns_transport::destination::{DestinationName, SingleInputDestination};
use rns_transport::identity::PrivateIdentity;
use rns_transport::iface::tcp_client::{TcpClient, TcpRuntimeStatusHandle};
use rns_transport::iface::{IfaceRole, InterfaceMode};
use rns_transport::transport::{Transport, TransportConfig};

use crate::aircraft::create_command;
use crate::identity::AppIdentity;
use crate::telemetry::{AltitudeReference, TelemetrySample};

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

fn origin_position(sample: &TelemetrySample) -> OriginPosition {
    // Take-off-relative and unknown altitudes have no datum; they stay absent (G-07).
    let datum = match sample.altitude_reference {
        AltitudeReference::Msl => Some(AltitudeDatum::Msl),
        AltitudeReference::Hae => Some(AltitudeDatum::Hae),
        AltitudeReference::Agl => Some(AltitudeDatum::Agl),
        AltitudeReference::TakeoffRelative | AltitudeReference::Unknown => None,
    };
    OriginPosition {
        lat: sample.latitude_deg,
        lon: sample.longitude_deg,
        alt_m: datum.map(|_| sample.altitude_m),
        altitude_datum: datum.unwrap_or_default(),
        fix_at_ms: sample.fix_time_s.saturating_mul(1000),
        speed_mps: Some(sample.ground_speed_mps),
        course_deg: Some(sample.course_deg),
        accuracy_m: Some(sample.horizontal_accuracy_m),
    }
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
}

#[derive(Debug, thiserror::Error, PartialEq, Eq, uniffi::Error)]
pub enum PublishError {
    #[error("publisher is already running")]
    AlreadyRunning,
    #[error("invalid publisher configuration: {reason}")]
    Config { reason: String },
}

enum Cmd {
    Fix(TelemetrySample),
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
    let team = team_uid.map(|t| hex32(&t, "team UID")).transpose()?;
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
};

/// Lower-cases a 32-hex hash. RCH matches team UIDs byte for byte against its
/// lower-case canonical list, which stays the hub's (it is not a shared crate).
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

/// Offers the latest fix. It replaces any fix not yet sent; nothing is queued.
#[uniffi::export]
pub fn publish_offer_fix(sample: TelemetrySample) {
    send_cmd(Cmd::Fix(sample));
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

/// Constructs the transport, the announced delivery destination and one TCP client.
fn connect(
    runtime: &tokio::runtime::Runtime,
    private: &PrivateIdentity,
    hub_address: &str,
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
    let (transport, destination, tcp) = connect(&runtime, &private, hub_address)?;
    let source = runtime.block_on(async { destination.lock().await.desc.address_hash });
    let backend = InProcessBackend::new(InProcessBackendConfig::new(
        "r3akt-uas",
        runtime.handle().clone(),
        transport.clone(),
        private,
        source,
    ));
    let send = |fields: &[u8]| send_fields(&backend, &source_hex, hub, fields);

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
            runtime.block_on(transport.send_announce(&destination, None));
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

/// One fields-only LXMF message to the hub through the shared in-process backend.
fn send_fields(
    backend: &InProcessBackend,
    source: &str,
    hub: &str,
    fields: &[u8],
) -> Result<(), String> {
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
    backend.send(request).map(|_| ()).map_err(|e| e.to_string())
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
            course_deg: 90.0,
            horizontal_accuracy_m: 2.0,
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
