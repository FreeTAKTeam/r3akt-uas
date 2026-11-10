//! Deterministic simulated aircraft for the simulator variant and `uas-sim`.
//!
//! The track is a fixed circular orbit so fixtures are reproducible. Every
//! sample is marked simulated (UAS-RAD-009) and uses an explicit, simulated
//! MSL altitude rather than a converted take-off-relative value.

use crate::telemetry::{AltitudeReference, TelemetrySample};

/// Orbit start, 2026-11-04T00:00:00Z.
pub const START_TIME_S: u64 = 1_793_750_400;
pub const CENTER_LAT_DEG: f64 = 45.0;
pub const CENTER_LON_DEG: f64 = 7.0;
pub const RADIUS_M: f64 = 200.0;
pub const PERIOD_S: u32 = 120;
pub const ALTITUDE_MSL_M: f64 = 400.0;
const EARTH_RADIUS_M: f64 = 6_371_008.8;

/// Sample `seq` of the orbit, one per second (1 Hz in-flight target, UAS-VID-005).
#[uniffi::export]
#[must_use]
pub fn sample(seq: u32) -> TelemetrySample {
    let angle = std::f64::consts::TAU * f64::from(seq % PERIOD_S) / f64::from(PERIOD_S);
    let north_m = RADIUS_M * angle.cos();
    let east_m = RADIUS_M * angle.sin();
    let lat = CENTER_LAT_DEG + (north_m / EARTH_RADIUS_M).to_degrees();
    let lon = CENTER_LON_DEG
        + (east_m / (EARTH_RADIUS_M * CENTER_LAT_DEG.to_radians().cos())).to_degrees();
    // Clockwise orbit seen from above: course is tangent, 90 degrees ahead of the radius.
    let course = (angle.to_degrees() + 90.0).rem_euclid(360.0);
    let time = START_TIME_S + u64::from(seq);
    TelemetrySample {
        sample_time_s: time,
        fix_time_s: time,
        latitude_deg: lat,
        longitude_deg: lon,
        altitude_m: ALTITUDE_MSL_M,
        altitude_reference: AltitudeReference::Msl,
        ground_speed_mps: std::f64::consts::TAU * RADIUS_M / f64::from(PERIOD_S),
        course_deg: course,
        horizontal_accuracy_m: 1.5,
        battery_percent: Some((100.0 - f64::from(seq) * 0.1).max(0.0)),
        simulated: true,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn orbit_is_deterministic_and_closes() {
        assert_eq!(sample(3), sample(3));
        let start = sample(0);
        let lap = sample(PERIOD_S);
        assert!((start.latitude_deg - lap.latitude_deg).abs() < 1e-12);
        assert_eq!(lap.sample_time_s - start.sample_time_s, u64::from(PERIOD_S));
        assert!((start.course_deg - 90.0).abs() < 1e-9);
    }
}
