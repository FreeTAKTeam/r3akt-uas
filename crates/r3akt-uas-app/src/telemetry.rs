//! Aircraft telemetry normalization onto the shared Telemeter codec
//! (`r3akt_map_core::telemetry_encode` / `r3akt_map_core::telemetry`).
//!
//! Requirement anchors: UAS-PRF-004 (altitude reference), UAS-PRF-007 (units),
//! UAS-PRF-008 (baseline compatibility), UAS-RAD-009 (simulated identification).

use r3akt_map_core::{telemetry, telemetry_encode};
use serde_json::Value as JsonValue;

/// Telemeter Information sensor text marking simulator output (UAS-RAD-009).
pub const SIMULATED_INFORMATION: &str = "r3akt-uas simulated aircraft";

const MPS_TO_KMH: f64 = 3.6;

/// What an altitude value is measured from. Mirrors the shared
/// `r3akt_map_core::sensor::AltitudeDatum` plus DJI SDK 4's take-off-relative
/// altitude, which must never be relabelled MSL or AGL (UAS-PRF-004).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AltitudeReference {
    /// Mean sea level: the only datum the standard Telemeter location carries.
    Msl,
    /// Height above the WGS-84 ellipsoid.
    Hae,
    /// Above ground level.
    Agl,
    /// Relative to the take-off point (DJI `FlightControllerState` altitude).
    TakeoffRelative,
    /// Reference not known.
    Unknown,
}

/// One aircraft position sample in SI units, before Telemeter conversion.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TelemetrySample {
    /// Telemeter sample time, epoch seconds.
    pub sample_time_s: u64,
    /// Source position-fix time, epoch seconds (kept distinct from sample time).
    pub fix_time_s: u64,
    pub latitude_deg: f64,
    pub longitude_deg: f64,
    pub altitude_m: f64,
    pub altitude_reference: AltitudeReference,
    /// Ground speed, metres per second.
    pub ground_speed_mps: f64,
    /// Course over ground, degrees true. Not aircraft heading (UAS-PRF-005).
    pub course_deg: f64,
    pub horizontal_accuracy_m: f64,
    /// Aircraft battery, percent (0-100), when reported.
    pub battery_percent: Option<f64>,
    /// True for simulator output; encoded in the Information sensor.
    pub simulated: bool,
}

/// A Telemeter payload decoded by the shared receive contract.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DecodedTelemetry {
    /// Humanized sensor map produced by `r3akt_map_core::telemetry::decode_field`.
    pub json: String,
    pub timestamp_s: Option<i64>,
}

#[derive(Debug, thiserror::Error, PartialEq, Eq, uniffi::Error)]
pub enum UasError {
    #[error("altitude is not MSL; the standard Telemeter location cannot carry it")]
    AltitudeReferenceNotMsl,
    #[error("shared codec rejected the sample: {reason}")]
    Codec { reason: String },
}

fn codec(reason: &str) -> UasError {
    UasError::Codec {
        reason: reason.to_owned(),
    }
}

/// Encodes a sample as LXMF `FIELD_TELEMETRY` bytes with the shared encoder.
///
/// # Errors
/// [`UasError::AltitudeReferenceNotMsl`] for non-MSL altitude, or the shared
/// codec's diagnostic for invalid readings.
#[uniffi::export]
pub fn encode_telemetry(sample: &TelemetrySample) -> Result<Vec<u8>, UasError> {
    // Standard sensor 2 has no datum or unknown-altitude slot (gap G-03); other
    // datums travel in the R3S origin position, never relabelled here.
    if sample.altitude_reference != AltitudeReference::Msl {
        return Err(UasError::AltitudeReferenceNotMsl);
    }
    let location = telemetry_encode::Location {
        latitude: sample.latitude_deg,
        longitude: sample.longitude_deg,
        altitude: sample.altitude_m,
        speed: sample.ground_speed_mps * MPS_TO_KMH,
        bearing: sample.course_deg,
        accuracy: sample.horizontal_accuracy_m,
        updated_at_s: sample.fix_time_s,
    };
    telemetry_encode::encode_with_information(
        sample.sample_time_s,
        Some(location),
        // Shared encoder has no unknown-charging value (gap G-11).
        sample.battery_percent.map(|percent| (percent, false)),
        sample.simulated.then_some(SIMULATED_INFORMATION),
    )
    .map_err(codec)
}

/// Decodes `FIELD_TELEMETRY` bytes with the shared receive contract.
///
/// # Errors
/// The shared codec's privacy-safe diagnostic.
#[uniffi::export]
pub fn decode_telemetry(packed: &[u8]) -> Result<DecodedTelemetry, UasError> {
    let payload = JsonValue::Array(packed.iter().map(|b| JsonValue::from(*b)).collect());
    let checked = telemetry::decode_field(&payload).map_err(codec)?;
    Ok(DecodedTelemetry {
        json: checked.telemetry.to_string(),
        timestamp_s: checked.timestamp_s,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::sim;

    #[test]
    fn simulated_sample_round_trips_through_shared_codec() {
        let sample = sim::sample(7);
        let packed = encode_telemetry(&sample).unwrap();
        let decoded = decode_telemetry(&packed).unwrap();
        let json: JsonValue = serde_json::from_str(&decoded.json).unwrap();
        let location = &json["location"];
        assert!((location["latitude"].as_f64().unwrap() - sample.latitude_deg).abs() < 1e-6);
        assert!((location["longitude"].as_f64().unwrap() - sample.longitude_deg).abs() < 1e-6);
        assert!((location["altitude"].as_f64().unwrap() - sample.altitude_m).abs() < 0.01);
        // Shared wire speed is km/h (UAS-PRF-007).
        let kmh = sample.ground_speed_mps * MPS_TO_KMH;
        assert!((location["speed"].as_f64().unwrap() - kmh).abs() < 0.01);
        assert_eq!(
            decoded.timestamp_s,
            i64::try_from(sample.sample_time_s).ok()
        );
        assert!(decoded.json.contains(SIMULATED_INFORMATION));
    }

    #[test]
    fn takeoff_relative_and_unknown_altitude_are_not_published() {
        for reference in [
            AltitudeReference::TakeoffRelative,
            AltitudeReference::Unknown,
        ] {
            let sample = TelemetrySample {
                altitude_reference: reference,
                ..sim::sample(0)
            };
            assert_eq!(
                encode_telemetry(&sample),
                Err(UasError::AltitudeReferenceNotMsl)
            );
        }
    }

    #[test]
    fn invalid_readings_are_rejected_by_shared_codec() {
        let sample = TelemetrySample {
            latitude_deg: 91.0,
            ..sim::sample(0)
        };
        assert!(matches!(
            encode_telemetry(&sample),
            Err(UasError::Codec { .. })
        ));
        assert!(decode_telemetry(&[0xc1]).is_err());
    }
}
