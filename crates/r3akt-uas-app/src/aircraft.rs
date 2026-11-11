//! The aircraft object's `mission.marker.create` command (UAS-PRF-003, D-3).
//!
//! The command is encoded once from the first position and its bytes are stored
//! beside the identity. Every later start re-sends those exact bytes: the hub
//! de-duplicates a byte-identical command by `command_id` and refuses the same
//! id with different content, so the command is never re-encoded.

use std::path::Path;

use r3akt_profile_rch::{MissionCommandEnvelope, RchSource, decode_commands, encode_commands};
use rns_core::key_manager::{FileKeyManager, KeyManagerBackend, KeyPurpose, StoredKey};
use serde_json::json;

use crate::identity::{AppIdentity, StoreError};
use crate::telemetry::TelemetrySample;

const CREATE_KEY: &str = "aircraft-create";
const CREATE_COMMAND: &str = "mission.marker.create";

#[derive(Debug, thiserror::Error)]
pub enum CreateError {
    #[error(transparent)]
    Store(#[from] StoreError),
    #[error("shared command encoder failed: {0}")]
    Encode(String),
}

/// Returns the stored create bytes (LXMF fields map holding field 9), building
/// and storing them from `first` when none exist yet.
///
/// # Errors
/// [`CreateError::Store`] when the store fails or holds a create for another
/// identity or object, [`CreateError::Encode`] when the shared encoder fails.
pub fn create_command(
    dir: &Path,
    identity: &AppIdentity,
    first: &TelemetrySample,
) -> Result<Vec<u8>, CreateError> {
    let store = FileKeyManager::new(dir).map_err(|_| StoreError::Io("open the directory"))?;
    let stored = store
        .get(CREATE_KEY)
        .map_err(|_| StoreError::Io("read the create command"))?;
    if let Some(key) = stored {
        if !matches_identity(&key.material, identity) {
            return Err(StoreError::Corrupt("create command belongs to another identity").into());
        }
        return Ok(key.material);
    }
    let bytes = encode_commands(&[envelope(identity, first)])
        .map_err(|error| CreateError::Encode(error.to_string()))?;
    store
        .put(StoredKey {
            key_id: CREATE_KEY.to_owned(),
            purpose: KeyPurpose::Custom(CREATE_KEY.to_owned()),
            material: bytes.clone(),
        })
        .map_err(|_| StoreError::Io("write the create command"))?;
    Ok(bytes)
}

fn envelope(identity: &AppIdentity, first: &TelemetrySample) -> MissionCommandEnvelope {
    let object_id = identity.object_id();
    let kind = if first.simulated {
        "SIMULATED UAS"
    } else {
        "UAS"
    };
    // shortcut: no typed create builder exists upstream (G-16); args written by name.
    let args = json!({
        "object_destination_hash": object_id,
        "name": format!("{kind} {}", &object_id[..8]),
        "category": "uav",
        "symbol": "uav",
        "lat": first.latitude_deg,
        "lon": first.longitude_deg,
    });
    let fix_ms = i64::try_from(first.fix_time_s.saturating_mul(1000)).unwrap_or(i64::MAX);
    MissionCommandEnvelope {
        command_id: format!("uas-create-{object_id}"),
        // The hub compares this with the LXMF sender, the delivery destination.
        source: RchSource::new(identity.destination_hash()),
        timestamp: r3akt_map_core::time::iso8601_from_unix_ms(fix_ms),
        command_type: CREATE_COMMAND.to_owned(),
        args,
        correlation_id: None,
        topics: Vec::new(),
    }
}

fn matches_identity(bytes: &[u8], identity: &AppIdentity) -> bool {
    let Ok(commands) = decode_commands(bytes) else {
        return false;
    };
    let [command] = commands.as_slice() else {
        return false;
    };
    command.command_type == CREATE_COMMAND
        && command.source.rns_identity == identity.destination_hash()
        && command.args["object_destination_hash"] == identity.object_id()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::telemetry::AltitudeReference;
    use std::sync::atomic::{AtomicU32, Ordering};

    fn temp_dir() -> std::path::PathBuf {
        static N: AtomicU32 = AtomicU32::new(0);
        let dir = std::env::temp_dir().join(format!(
            "r3akt-uas-aircraft-{}-{}",
            std::process::id(),
            N.fetch_add(1, Ordering::Relaxed)
        ));
        let _ = std::fs::remove_dir_all(&dir);
        dir
    }

    fn sample(lat: f64, fix_time_s: u64) -> TelemetrySample {
        TelemetrySample {
            sample_time_s: fix_time_s,
            fix_time_s,
            latitude_deg: lat,
            longitude_deg: 13.4,
            altitude_m: 30.0,
            altitude_reference: AltitudeReference::TakeoffRelative,
            ground_speed_mps: 5.0,
            course_deg: Some(90.0),
            horizontal_accuracy_m: Some(2.0),
            battery_percent: None,
            simulated: true,
        }
    }

    #[test]
    fn stored_create_decodes_to_one_marker_create_for_the_object() {
        let dir = temp_dir();
        let id = AppIdentity::load_or_create(&dir).unwrap();
        let bytes = create_command(&dir, &id, &sample(52.5, 1_800_000_000)).unwrap();

        let commands = decode_commands(&bytes).unwrap();
        assert_eq!(commands.len(), 1);
        let c = &commands[0];
        assert_eq!(c.command_type, "mission.marker.create");
        assert_eq!(c.args["object_destination_hash"], id.object_id());
        assert_eq!(c.source.rns_identity, id.destination_hash());
        assert!(c.args["name"].as_str().unwrap().contains("SIMULATED"));
        assert_eq!(c.args["lat"], 52.5);
        assert_eq!(c.timestamp, "2027-01-15T08:00:00Z");
    }

    #[test]
    fn second_start_returns_the_same_bytes() {
        let dir = temp_dir();
        let id = AppIdentity::load_or_create(&dir).unwrap();
        let first = create_command(&dir, &id, &sample(52.5, 1_800_000_000)).unwrap();
        let reloaded = AppIdentity::load_or_create(&dir).unwrap();
        let again = create_command(&dir, &reloaded, &sample(48.1, 1_900_000_000)).unwrap();
        assert_eq!(first, again);
    }

    #[test]
    fn create_of_another_identity_is_refused() {
        let dir = temp_dir();
        let id = AppIdentity::load_or_create(&dir).unwrap();
        create_command(&dir, &id, &sample(52.5, 1_800_000_000)).unwrap();
        let other = AppIdentity::load_or_create(&temp_dir()).unwrap();
        let err = create_command(&dir, &other, &sample(52.5, 1_800_000_000)).unwrap_err();
        assert!(matches!(err, CreateError::Store(StoreError::Corrupt(_))));
    }
}
