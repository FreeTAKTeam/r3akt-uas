//! Operator-placed markers (operator-marker plan, D-10 to D-13).
//!
//! Records, the local list and the command encoding are shared code:
//! `r3akt_map_core::{MarkerDraft, MarkerRecord}`, the map-core marker store and
//! `r3akt_profile_rch::encode_commands`. Durability belongs to the LXMF-rs custody
//! outbox driven by the publisher (`publish.rs`); nothing here queues or retries.

use std::path::Path;

use r3akt_map_core::{MarkerDraft, MarkerRecord, Position, marker};
use r3akt_profile_rch::{MissionCommandEnvelope, RchSource, encode_commands_for_team};
use rand_core::{OsRng, RngCore};

/// NATO affiliation; each is a map-core catalog symbol (D-11).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum Affiliation {
    Friendly,
    Hostile,
    Neutral,
    Unknown,
}

impl Affiliation {
    fn symbol(self) -> &'static str {
        match self {
            Self::Friendly => "friendly",
            Self::Hostile => "hostile",
            Self::Neutral => "neutral",
            Self::Unknown => "unknown",
        }
    }
}

/// The app's last **sent** state of one of its markers, never the hub's state.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct OwnMarker {
    pub object_id: String,
    pub name: String,
    pub symbol: String,
    pub lat: f64,
    pub lon: f64,
    pub notes: Option<String>,
}

impl From<&MarkerRecord> for OwnMarker {
    fn from(r: &MarkerRecord) -> Self {
        Self {
            object_id: r.object_destination_hash.clone(),
            name: r.name.clone(),
            symbol: r.symbol.clone(),
            lat: r.lat,
            lon: r.lon,
            notes: r.notes.clone(),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Change {
    Create,
    Move,
    Delete,
}

/// A new marker at the aircraft's position (mode D-10 (a)), labelled as such.
pub(crate) fn new_record(
    name: &str,
    affiliation: Affiliation,
    lat: f64,
    lon: f64,
    simulated: bool,
    now_ms: i64,
) -> Result<MarkerRecord, String> {
    let symbol = marker::marker_symbol(affiliation.symbol()).map_err(|e| e.to_string())?;
    let mut id = [0u8; 16];
    OsRng.fill_bytes(&mut id);
    let draft = MarkerDraft {
        marker_type: marker::marker_type(affiliation.symbol()).map_err(|e| e.to_string())?,
        category: symbol.clone(),
        symbol,
        name: marker::normalize_name(name).ok_or("marker name is empty")?,
        position: Position::new(lat, lon).map_err(|e| e.to_string())?,
        notes: Some(position_note(simulated)),
        origin_rch: String::new(),
    };
    Ok(MarkerRecord::create_with_id(
        draft,
        hex(&id),
        hex(&id),
        now_ms,
    ))
}

pub(crate) fn position_note(simulated: bool) -> String {
    format!(
        "{}at aircraft position",
        if simulated { "SIMULATED · " } else { "" }
    )
}

/// The fields map for one change: a mission command on field 9 and, with a team, field 11,
/// both written by the shared encoder; RCH relays the result to that team's REM clients.
/// One fixed `command_id` per stored message, so a re-sent copy is the same command.
pub(crate) fn message(
    change: Change,
    record: &MarkerRecord,
    source: &str,
    team: Option<&str>,
    now_ms: i64,
) -> Result<Vec<u8>, String> {
    let id = &record.object_destination_hash;
    let (command_type, command_id, args) = match change {
        Change::Create => (
            "mission.marker.create",
            format!("uas-marker-create-{id}"),
            serde_json::json!({
                "object_destination_hash": id, "marker_type": record.marker_type,
                "symbol": record.symbol, "name": record.name, "category": record.category,
                "lat": record.lat, "lon": record.lon, "notes": record.notes,
            }),
        ),
        Change::Move => (
            "mission.marker.position.patch",
            format!("uas-marker-move-{id}-{now_ms}"),
            serde_json::json!({"object_destination_hash": id, "lat": record.lat, "lon": record.lon}),
        ),
        Change::Delete => (
            "mission.marker.delete",
            format!("uas-marker-delete-{id}"),
            serde_json::json!({"object_destination_hash": id}),
        ),
    };
    encode_commands_for_team(
        &[MissionCommandEnvelope {
            command_id,
            source: RchSource::new(source),
            timestamp: r3akt_map_core::time::iso8601_from_unix_ms(now_ms),
            command_type: command_type.to_owned(),
            args,
            correlation_id: None,
            topics: Vec::new(),
        }],
        team,
    )
    .map_err(|e| e.to_string())
}

/// The custody-outbox replacement key: a newer move or a delete supersedes a pending move.
pub(crate) fn replacement_key(record: &MarkerRecord) -> String {
    format!("marker:{}", record.object_destination_hash)
}

// ---- UniFFI exports --------------------------------------------------------

use crate::publish::{PublishError, last_fix, store_dir, submit_marker};

fn refused(reason: impl Into<String>) -> PublishError {
    PublishError::Marker {
        reason: reason.into(),
    }
}

fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |d| i64::try_from(d.as_millis()).unwrap_or(i64::MAX))
}

fn placement() -> Result<(std::path::PathBuf, crate::telemetry::TelemetrySample), PublishError> {
    let dir = store_dir().ok_or_else(|| refused("start publishing first"))?;
    let fix = last_fix().ok_or_else(|| refused("aircraft position unknown"))?;
    Ok((dir, fix))
}

/// Places a marker at the aircraft's current position (D-10 (a)). Returns once the
/// custody outbox holds it; delivery to the hub follows ("sent", never "accepted").
///
/// # Errors
/// [`PublishError::Marker`] when publishing is off, the aircraft position or name is
/// missing, or the outbox could not take the change.
#[uniffi::export]
pub fn marker_create(name: &str, affiliation: Affiliation) -> Result<OwnMarker, PublishError> {
    let (dir, fix) = placement()?;
    let record = new_record(
        name,
        affiliation,
        fix.latitude_deg,
        fix.longitude_deg,
        fix.simulated,
        now_ms(),
    )
    .map_err(refused)?;
    submit_marker(Change::Create, record.clone()).map_err(refused)?;
    save(&dir, &record).map_err(refused)?;
    Ok(OwnMarker::from(&record))
}

/// Places a marker where the camera centre meets the ground (mode D-10 (c)), with the
/// shared ground point (RCH #313). The bound and ground model go in the notes until a
/// shared field carries them. Refused, never estimated, when the geometry is not valid.
///
/// # Errors
/// As [`marker_create`], or the reason the camera point cannot be placed.
#[uniffi::export]
pub fn marker_create_at_camera(
    name: &str,
    affiliation: Affiliation,
    pose: crate::camera::CameraPose,
) -> Result<OwnMarker, PublishError> {
    let (dir, fix) = placement()?;
    let now = now_ms();
    let point = crate::camera::camera_point(&fix, &pose, u64::try_from(now).unwrap_or(0))
        .map_err(|e| refused(format!("no camera point: {e}")))?;
    let mut record = new_record(
        name,
        affiliation,
        point.position.lat(),
        point.position.lon(),
        fix.simulated,
        now,
    )
    .map_err(refused)?;
    record.notes = Some(camera_note(fix.simulated, &point));
    submit_marker(Change::Create, record.clone()).map_err(refused)?;
    save(&dir, &record).map_err(refused)?;
    Ok(OwnMarker::from(&record))
}

pub(crate) fn camera_note(
    simulated: bool,
    point: &r3akt_map_core::camera_ground::GroundPoint,
) -> String {
    use r3akt_map_core::camera_ground::GroundModel;
    let ground = match point.ground {
        GroundModel::Flat { .. } => "flat ground",
        GroundModel::TakeoffRelative { .. } => "flat ground at take-off height",
    };
    format!(
        "{}camera point ±{:.0} m (bound, {ground}), {:.0} m from aircraft",
        if simulated { "SIMULATED · " } else { "" },
        point.uncertainty_radius_m.ceil(),
        point.ground_range_m,
    )
}

/// Moves one of the app's markers to the aircraft's current position.
///
/// # Errors
/// As [`marker_create`], or when the marker is not one of the app's markers.
#[uniffi::export]
pub fn marker_move(object_id: &str) -> Result<OwnMarker, PublishError> {
    let (dir, fix) = placement()?;
    let mut record = load(&dir, object_id)
        .map_err(refused)?
        .ok_or_else(|| refused("not one of this app's markers"))?;
    let position =
        Position::new(fix.latitude_deg, fix.longitude_deg).map_err(|e| refused(e.to_string()))?;
    record.move_to(position, now_ms());
    record.notes = Some(position_note(fix.simulated));
    submit_marker(Change::Move, record.clone()).map_err(refused)?;
    save(&dir, &record).map_err(refused)?;
    Ok(OwnMarker::from(&record))
}

/// Deletes one of the app's markers.
///
/// # Errors
/// As [`marker_move`].
#[uniffi::export]
pub fn marker_delete(object_id: &str) -> Result<(), PublishError> {
    let dir = store_dir().ok_or_else(|| refused("start publishing first"))?;
    let record = load(&dir, object_id)
        .map_err(refused)?
        .ok_or_else(|| refused("not one of this app's markers"))?;
    submit_marker(Change::Delete, record).map_err(refused)?;
    remove(&dir, object_id).map_err(refused)
}

/// The app's markers in their last sent state (never the hub's state).
#[uniffi::export]
#[must_use]
pub fn marker_list() -> Vec<OwnMarker> {
    store_dir()
        .and_then(|dir| list(&dir).ok())
        .map(|all| all.iter().map(OwnMarker::from).collect())
        .unwrap_or_default()
}

// ---- Local list of own markers, in the shared map-core marker store ----------

fn store(dir: &Path) -> Result<rusqlite::Connection, String> {
    let c =
        rusqlite::Connection::open(dir.join("own-markers.sqlite")).map_err(|e| e.to_string())?;
    r3akt_map_core::sqlite::ensure_schema(&c).map_err(|e| e.to_string())?;
    Ok(c)
}

pub(crate) fn save(dir: &Path, record: &MarkerRecord) -> Result<(), String> {
    r3akt_map_core::sqlite::upsert(&store(dir)?, record).map_err(|e| e.to_string())
}

pub(crate) fn remove(dir: &Path, object_id: &str) -> Result<(), String> {
    r3akt_map_core::sqlite::delete(&store(dir)?, object_id).map_err(|e| e.to_string())
}

pub(crate) fn load(dir: &Path, object_id: &str) -> Result<Option<MarkerRecord>, String> {
    r3akt_map_core::sqlite::get(&store(dir)?, object_id).map_err(|e| e.to_string())
}

pub(crate) fn list(dir: &Path) -> Result<Vec<MarkerRecord>, String> {
    r3akt_map_core::sqlite::load_all(&store(dir)?).map_err(|e| e.to_string())
}

fn hex(bytes: &[u8]) -> String {
    use std::fmt::Write;
    bytes.iter().fold(String::new(), |mut out, b| {
        let _ = write!(out, "{b:02x}");
        out
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use r3akt_profile_rch::decode_commands;

    #[test]
    fn create_move_delete_decode_with_the_shared_decoder() {
        let rec = new_record(
            "Alpha-1",
            Affiliation::Hostile,
            45.0,
            7.0,
            true,
            1_800_000_000_000,
        )
        .unwrap();
        assert_eq!(rec.symbol, "hostile");
        assert_eq!(
            rec.notes.as_deref(),
            Some("SIMULATED · at aircraft position")
        );
        for (change, ty) in [
            (Change::Create, "mission.marker.create"),
            (Change::Move, "mission.marker.position.patch"),
            (Change::Delete, "mission.marker.delete"),
        ] {
            let team = Some("43341e5c822d99857fa6e8641f2ca9c0");
            let bytes = message(
                change,
                &rec,
                "4d8ffce8db31d5f2a063a7906f46d191",
                team,
                1_800_000_000_000,
            )
            .unwrap();
            let fields = r3akt_map_core::msgpack::to_json(&bytes).unwrap();
            assert_eq!(fields.get("11").and_then(|t| t.as_str()), team);
            let commands = decode_commands(&bytes).unwrap();
            assert_eq!(commands.len(), 1);
            assert_eq!(commands[0].command_type, ty);
            assert_eq!(
                commands[0].args["object_destination_hash"],
                rec.object_destination_hash
            );
        }
        // The create is the same command when rebuilt: its id is fixed per marker.
        let a = decode_commands(&message(Change::Create, &rec, "s", None, 1).unwrap()).unwrap();
        let b = decode_commands(&message(Change::Create, &rec, "s", None, 2).unwrap()).unwrap();
        // Without a team only field 9 is written (RCH only, no REM relay).
        let keys =
            r3akt_map_core::msgpack::to_json(&message(Change::Delete, &rec, "s", None, 1).unwrap())
                .unwrap();
        assert_eq!(keys.as_object().unwrap().keys().collect::<Vec<_>>(), ["9"]);
        assert_eq!(a[0].command_id, b[0].command_id);
    }

    #[test]
    fn camera_note_carries_the_bound_and_ground_model() {
        let point = crate::camera::camera_point(
            &crate::sim::sample(10),
            &crate::sim::camera(10),
            (crate::sim::START_TIME_S + 10) * 1000,
        )
        .unwrap();
        let note = camera_note(true, &point);
        assert!(note.starts_with("SIMULATED · camera point ±"), "{note}");
        assert!(
            note.contains("flat ground") && note.ends_with("200 m from aircraft"),
            "{note}"
        );
    }

    #[test]
    fn local_list_round_trip_and_refusals() {
        let dir = std::env::temp_dir().join(format!("r3akt-uas-markers-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let mut rec = new_record("Bravo-1", Affiliation::Friendly, 45.0, 7.0, false, 1).unwrap();
        save(&dir, &rec).unwrap();
        rec.move_to(Position::new(45.1, 7.1).unwrap(), 2);
        save(&dir, &rec).unwrap();
        let got = load(&dir, &rec.object_destination_hash).unwrap().unwrap();
        assert_eq!(
            (got.lat, got.lon, got.notes.as_deref()),
            (45.1, 7.1, Some("at aircraft position"))
        );
        assert_eq!(list(&dir).unwrap().len(), 1);
        remove(&dir, &rec.object_destination_hash).unwrap();
        assert!(list(&dir).unwrap().is_empty());
        assert!(new_record("  ", Affiliation::Unknown, 45.0, 7.0, false, 1).is_err());
        assert!(new_record("x", Affiliation::Unknown, 91.0, 7.0, false, 1).is_err());
    }
}
