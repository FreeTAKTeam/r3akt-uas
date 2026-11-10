//! Aircraft position publication as the shared R3S origin-fix event (UAS-PRF-010).
//!
//! Every wire type here is shared: `r3akt_map_core::sensor_wire` owns the event
//! and its validation, `r3akt_profile_rch::encode_events` owns field 13. This
//! module only maps a [`TelemetrySample`] onto the shared `OriginPosition`.

use r3akt_map_core::sensor::{AltitudeDatum, OriginPosition};
use r3akt_map_core::sensor_wire::{OriginFixEvent, SensorEvent};
use r3akt_profile_rch::{EventEnvelope, RchSource, encode_events};

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

/// Encodes `sample` as one origin-fix event for the aircraft object: the LXMF
/// fields map holding field 13 only.
///
/// `fix_time_s` must be wall-clock time; the hub ignores older fixes and
/// refuses fixes more than 30 s ahead of its clock.
///
/// # Errors
/// [`FixError::Invalid`] when shared validation rejects the values,
/// [`FixError::Encode`] when the shared encoder fails.
pub fn fix_message(identity: &AppIdentity, sample: &TelemetrySample) -> Result<Vec<u8>, FixError> {
    let event = SensorEvent::OriginFix(OriginFixEvent {
        origin_id: identity.object_id().to_owned(),
        position: origin_position(sample),
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

    fn decode_one(id: &AppIdentity, s: &TelemetrySample) -> OriginPosition {
        let bytes = fix_message(id, s).unwrap();
        let frame = sensor_frame::decode(&bytes, &id.destination_hash()).unwrap();
        assert!(frame.samples.is_empty() && frame.diagnostics.is_empty());
        let [SensorEvent::OriginFix(fix)] = frame.events.as_slice() else {
            panic!("expected exactly one origin fix, got {:?}", frame.events);
        };
        assert_eq!(fix.origin_id, id.object_id());
        fix.position.clone()
    }

    #[test]
    fn fix_decodes_to_one_origin_fix_for_the_object() {
        let dir = std::env::temp_dir().join(format!("r3akt-uas-publish-{}", std::process::id()));
        let id = AppIdentity::load_or_create(&dir).unwrap();

        let msl = decode_one(&id, &sample(AltitudeReference::Msl));
        assert_eq!(
            (msl.lat, msl.lon, msl.fix_at_ms),
            (52.5, 13.4, 1_800_000_000_000)
        );
        assert_eq!(
            (msl.alt_m, msl.altitude_datum),
            (Some(30.0), AltitudeDatum::Msl)
        );

        let relative = decode_one(&id, &sample(AltitudeReference::TakeoffRelative));
        assert_eq!(
            (relative.alt_m, relative.altitude_datum),
            (None, AltitudeDatum::Unknown)
        );

        let mut bad = sample(AltitudeReference::Msl);
        bad.latitude_deg = 91.0;
        assert!(matches!(fix_message(&id, &bad), Err(FixError::Invalid(_))));
    }
}
