//! Messages addressed to the app (UAS-PRD-013), received the way REM receives them: the shared
//! transport's received-data and resource events, `lxmf-core`'s inbound decode, and the shared
//! RCH event envelope and map replica rules. Only team marker replicas are kept, for the radar
//! and the map; everything else is ignored. Nothing here reaches the DJI interface (UAS-PRD-012).

use std::collections::BTreeMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};

use lxmf_core::inbound_decode::{InboundPayloadMode, decode_inbound_message};
use r3akt_map_core::MarkerRecord;
use r3akt_map_core::replica::{
    DELETE_EVENT, UPSERT_EVENTS, from_api_value, replica_id, supersedes,
};
use r3akt_profile_rch::{EventEnvelope, FIELD_EVENT};
use rns_transport::hash::AddressHash;
use rns_transport::resource::ResourceEventKind;
use rns_transport::transport::{ReceivedPayloadMode, Transport};

use crate::markers::OwnMarker;

/// Most markers kept; the oldest update goes first (UAS-VID-010, bounded storage).
const MAX_MARKERS: usize = 500;

/// A marker another identity placed, as last received.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ReceivedMarker {
    pub marker: OwnMarker,
    /// Who placed it: the RCH event source identity, or the LXMF sender when the event names none.
    pub source: String,
    /// When this app received the latest version, epoch milliseconds.
    pub received_ms: i64,
}

// shortcut: kept in memory only, so a restart shows nothing until the hub sends again; persist when REM-style replay is needed.
static MARKERS: Mutex<BTreeMap<String, ReceivedMarker>> = Mutex::new(BTreeMap::new());

// What reached the receiver, for the LOG report: packets to any destination, packets and
// resources for the app, LXMF messages decoded, messages with RCH events, marker events applied.
static COUNTS: [AtomicU64; 6] = [const { AtomicU64::new(0) }; 6];
const ANY: usize = 0;
const OURS: usize = 1;
const RESOURCES: usize = 2;
const DECODED: usize = 3;
const WITH_EVENTS: usize = 4;
const APPLIED: usize = 5;

fn count(which: usize) {
    COUNTS[which].fetch_add(1, Ordering::Relaxed);
}

/// One line for the LOG report: how far received data got.
#[uniffi::export]
#[must_use]
pub fn received_report() -> String {
    let n = |i: usize| COUNTS[i].load(Ordering::Relaxed);
    format!(
        "received: packets {} (tried as LXMF {}), resources {}, LXMF decoded {}, with RCH events {}, marker events applied {}, markers shown {}",
        n(ANY),
        n(OURS),
        n(RESOURCES),
        n(DECODED),
        n(WITH_EVENTS),
        n(APPLIED),
        lock().len()
    )
}

/// The received team markers, newest update first.
#[uniffi::export]
#[must_use]
pub fn received_markers() -> Vec<ReceivedMarker> {
    let mut all: Vec<_> = lock().values().cloned().collect();
    all.sort_by(|a, b| b.marker.updated_ms.cmp(&a.marker.updated_ms));
    all
}

fn lock() -> std::sync::MutexGuard<'static, BTreeMap<String, ReceivedMarker>> {
    MARKERS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
}

/// Listens on `transport` for messages to `address` until the runtime stops. Call from within
/// the runtime.
pub(crate) fn spawn(transport: &Arc<Transport>, address: AddressHash) {
    let mut destination = [0u8; 16];
    destination.copy_from_slice(address.as_slice());
    let mut data = transport.received_data_events();
    tokio::spawn(async move {
        loop {
            let event = data.recv().await;
            if event.is_ok() {
                count(ANY);
            }
            match event {
                // Single packets name our destination and strip it from the data. Link data
                // (RCH's direct delivery) names the link instead and carries the full message,
                // whose own destination `receive` checks, as REM does.
                Ok(event) => {
                    let mode = match event.payload_mode {
                        ReceivedPayloadMode::FullWire => InboundPayloadMode::FullWire,
                        ReceivedPayloadMode::DestinationStripped
                            if event.destination.as_slice() == destination =>
                        {
                            InboundPayloadMode::DestinationStripped
                        }
                        ReceivedPayloadMode::DestinationStripped => continue,
                    };
                    count(OURS);
                    receive(destination, event.data.as_slice(), mode, now_ms());
                }
                Err(tokio::sync::broadcast::error::RecvError::Lagged(_)) => {}
                Err(tokio::sync::broadcast::error::RecvError::Closed) => break,
            }
        }
    });
    // Larger messages arrive as a resource over a link, always as the full wire message.
    let mut resources = transport.resource_events();
    tokio::spawn(async move {
        loop {
            match resources.recv().await {
                Ok(event) => {
                    if let ResourceEventKind::Complete(complete) = event.kind {
                        count(RESOURCES);
                        receive(
                            destination,
                            &complete.data,
                            InboundPayloadMode::FullWire,
                            now_ms(),
                        );
                    }
                }
                Err(tokio::sync::broadcast::error::RecvError::Lagged(_)) => {}
                Err(tokio::sync::broadcast::error::RecvError::Closed) => break,
            }
        }
    });
}

/// One received payload: anything that is not an LXMF message to us with marker events is ignored.
fn receive(destination: [u8; 16], payload: &[u8], mode: InboundPayloadMode, now: i64) {
    let Ok(message) = decode_inbound_message(destination, payload, mode) else {
        return;
    };
    if message.destination != destination {
        return;
    }
    count(DECODED);
    let sender = hex(&message.source);
    // The decoded fields as JSON, as the shared `sensor_frame::decode_json` reads them.
    let Some(events) = message
        .fields
        .and_then(|fields| serde_json::to_value(fields).ok())
        .and_then(|fields| fields.get(FIELD_EVENT.to_string()).cloned())
    else {
        return;
    };
    count(WITH_EVENTS);
    apply(events, &sender, now);
}

/// Applies the `mission.marker.*` events in a `FIELD_EVENT` value (one envelope or a list).
fn apply(events: serde_json::Value, sender: &str, now: i64) {
    let events: Vec<EventEnvelope> = match events {
        serde_json::Value::Array(items) => items
            .into_iter()
            .filter_map(|e| serde_json::from_value(e).ok())
            .collect(),
        one => serde_json::from_value(one).into_iter().collect(),
    };
    let mut markers = lock();
    for event in events {
        let source = event
            .source
            .as_ref()
            .map_or_else(|| sender.to_owned(), |s| s.rns_identity.clone());
        if event.event_type == DELETE_EVENT {
            if let Ok(id) = replica_id(&event.payload) {
                markers.remove(&id);
            }
        } else if UPSERT_EVENTS.contains(&event.event_type.as_str())
            && let Ok(id) = replica_id(&event.payload)
            && let Ok(record) = from_api_value(&event.payload, id.clone())
        {
            let existing = markers.get(&id).map(|m| record_of(&m.marker));
            count(APPLIED);
            if supersedes(&record, existing.as_ref()) {
                markers.insert(
                    id,
                    ReceivedMarker {
                        marker: OwnMarker::from(&record),
                        source,
                        received_ms: now,
                    },
                );
            }
        }
    }
    while markers.len() > MAX_MARKERS {
        let oldest = markers
            .iter()
            .min_by_key(|(_, m)| m.marker.updated_ms)
            .map(|(id, _)| id.clone());
        if let Some(id) = oldest {
            markers.remove(&id);
        }
    }
}

/// Just enough of a stored marker for the shared last-writer-wins rule.
fn record_of(marker: &OwnMarker) -> MarkerRecord {
    MarkerRecord {
        local_id: marker.object_id.clone(),
        object_destination_hash: marker.object_id.clone(),
        origin_rch: String::new(),
        marker_type: String::new(),
        symbol: marker.symbol.clone(),
        name: marker.name.clone(),
        category: String::new(),
        lat: marker.lat,
        lon: marker.lon,
        notes: None,
        created_ts_ms: marker.updated_ms,
        updated_ts_ms: marker.updated_ms,
    }
}

fn hex(bytes: &[u8]) -> String {
    AddressHash::new_from_slice(bytes).to_hex_string()
}

fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |d| i64::try_from(d.as_millis()).unwrap_or(i64::MAX))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    // The marker list is process-wide; tests that use it run one at a time.
    static SERIAL: Mutex<()> = Mutex::new(());

    fn event(kind: &str, updated: &str, lat: f64) -> serde_json::Value {
        json!({
            "event_type": kind,
            "source": {"rns_identity": "aa11"},
            "payload": {
                "object_destination_hash": "0123456789abcdef0123456789abcdef",
                "type": "neutral", "symbol": "neutral", "name": "Neutral 2",
                "position": {"lat": lat, "lon": -66.1},
                "created_at": "2026-11-15T14:00:00Z", "updated_at": updated,
            },
        })
    }

    #[test]
    fn marker_events_are_kept_newest_wins_and_deletes_remove() {
        let _serial = SERIAL
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        lock().clear();
        apply(
            json!([event(
                "mission.marker.created",
                "2026-11-15T14:00:00Z",
                43.9
            )]),
            "hub",
            1,
        );
        let got = received_markers();
        assert_eq!(got.len(), 1);
        assert_eq!(
            (got[0].marker.name.as_str(), got[0].source.as_str()),
            ("Neutral 2", "aa11")
        );
        // An older update does not rewind it; a newer one moves it.
        apply(
            event("mission.marker.updated", "2026-11-15T13:00:00Z", 10.0),
            "hub",
            2,
        );
        assert!((received_markers()[0].marker.lat - 43.9).abs() < 1e-9);
        apply(
            event(
                "mission.marker.position.updated",
                "2026-11-15T14:05:00Z",
                44.0,
            ),
            "hub",
            3,
        );
        assert!((received_markers()[0].marker.lat - 44.0).abs() < 1e-9);
        // Other events are ignored; a delete removes it.
        apply(
            json!({"event_type": "mission.registry.origin.telemetry.upsert", "payload": {}}),
            "hub",
            4,
        );
        assert_eq!(received_markers().len(), 1);
        apply(event(DELETE_EVENT, "2026-11-15T14:06:00Z", 44.0), "hub", 5);
        assert!(received_markers().is_empty());
    }

    #[test]
    fn a_signed_lxmf_message_with_a_marker_event_reaches_the_list() {
        let _serial = SERIAL
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        lock().clear();
        let sender = rns_core::identity::PrivateIdentity::new_from_name("hub");
        let to = [9u8; 16];
        let mut message = lxmf_core::Message::new();
        message.destination_hash = Some(to);
        message.source_hash = Some([3u8; 16]);
        message.fields = serde_json::from_value(
            json!({ "13": [event("mission.marker.created", "2026-11-15T14:00:00Z", 43.9)] }),
        )
        .ok();
        let wire = message.to_wire(Some(&sender)).unwrap();
        receive(to, &wire, InboundPayloadMode::FullWire, 7);
        receive(to, &wire[16..], InboundPayloadMode::DestinationStripped, 8);
        receive([1u8; 16], &wire, InboundPayloadMode::FullWire, 9); // not ours: ignored
        let got = received_markers();
        assert!(
            got.iter()
                .any(|m| m.marker.name == "Neutral 2" && m.received_ms == 7),
            "{got:?}"
        );
    }

    #[test]
    fn non_lxmf_payloads_are_ignored() {
        receive(
            [7; 16],
            b"not an lxmf message",
            InboundPayloadMode::FullWire,
            1,
        );
    }
}
