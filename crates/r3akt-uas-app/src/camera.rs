//! Camera pointing and the ground point it looks at (operator-marker plan, task 6).
//!
//! The geometry is the shared `r3akt_map_core::camera_ground::ground_point` (RCH #313):
//! this module only maps the source's values onto it. It refuses rather than inventing
//! a point: no horizon distance and no default ground height, unlike `FreeTAKUAS`.

use r3akt_map_core::camera_ground::{
    Accuracy, GroundModel, GroundPoint, GroundPointError, Limits, ground_point,
};
use r3akt_map_core::sensor::{AltitudeDatum, OriginPosition, SensorState};

use crate::telemetry::{AltitudeReference, TelemetrySample};

/// The camera's pointing and the source's error bounds at one instant.
#[derive(Debug, Clone, Copy, PartialEq, uniffi::Record)]
pub struct CameraPose {
    /// Pose time, epoch milliseconds (within 2 s of the aircraft fix).
    pub sample_time_ms: u64,
    /// Absolute azimuth, degrees clockwise from true north (heading plus gimbal yaw).
    pub azimuth_deg: f64,
    /// Elevation, degrees; negative below the horizon.
    pub elevation_deg: f64,
    /// Ground height in the fix's altitude reference: MSL ground for an MSL fix, the
    /// take-off ground (usually 0) for a take-off-relative fix. Unknown means no point.
    pub ground_height_m: Option<f64>,
    /// Absolute error bounds supplied by the source, never defaults.
    pub ground_height_error_m: f64,
    pub altitude_error_m: f64,
    pub azimuth_error_deg: f64,
    pub elevation_error_deg: f64,
    /// Highest platform speed between the fix and the pose.
    pub max_speed_mps: f64,
}

// Policy, not geometry: a pose older than this, a ray flatter than this or a point
// farther than this is refused instead of placed.
const MAX_AGE_MS: u64 = 5_000;
const MIN_DEPRESSION_DEG: f64 = 5.0;
const MAX_SLANT_RANGE_M: f64 = 2_000.0;

/// The shared R3S origin for a fix. Take-off-relative and unknown altitudes have no
/// datum; they stay absent (G-07).
pub(crate) fn origin_position(sample: &TelemetrySample) -> OriginPosition {
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

/// Where the camera centre meets the ground, or why it cannot be placed.
///
/// # Errors
/// A pilot-readable reason: unknown altitude reference or ground height, stale or
/// unsynchronized values, a ray at or near the horizon, or a point out of range.
pub fn camera_point(
    fix: &TelemetrySample,
    pose: &CameraPose,
    now_ms: u64,
) -> Result<GroundPoint, String> {
    let origin = origin_position(fix);
    let ground = match (fix.altitude_reference, pose.ground_height_m) {
        (AltitudeReference::Unknown, _) => return Err(reason(GroundPointError::UnknownAltitude)),
        // AGL altitude is already the height above the ground below the aircraft.
        (AltitudeReference::Agl, _) => GroundModel::Flat {
            height_m: 0.0,
            datum: AltitudeDatum::Agl,
            height_error_m: pose.ground_height_error_m,
        },
        (_, None) => return Err("ground height unknown: no camera point".to_owned()),
        (AltitudeReference::TakeoffRelative, Some(height_m)) => GroundModel::TakeoffRelative {
            origin_altitude_m: fix.altitude_m,
            height_m,
            height_error_m: pose.ground_height_error_m,
        },
        (_, Some(height_m)) => GroundModel::Flat {
            height_m,
            datum: origin.altitude_datum,
            height_error_m: pose.ground_height_error_m,
        },
    };
    let sensor = SensorState {
        sensor_id: "camera".to_owned(),
        sample_at_ms: pose.sample_time_ms,
        azimuth_deg: Some(pose.azimuth_deg),
        elevation_deg: Some(pose.elevation_deg),
        roll_deg: None,
        horizontal_fov_deg: None,
        vertical_fov_deg: None,
        slant_range_m: None,
        image_north_deg: None,
        range_kind: r3akt_map_core::sensor::RangeKind::Unknown,
    };
    let accuracy = Accuracy {
        altitude_m: pose.altitude_error_m,
        azimuth_deg: pose.azimuth_error_deg,
        elevation_deg: pose.elevation_error_deg,
        roll_deg: None,
        pixel_deg: None,
        max_speed_mps: pose.max_speed_mps,
    };
    let limits = Limits {
        now_ms,
        max_age_ms: MAX_AGE_MS,
        min_depression_deg: MIN_DEPRESSION_DEG,
        max_slant_range_m: MAX_SLANT_RANGE_M,
    };
    ground_point(&origin, &sensor, None, ground, accuracy, limits).map_err(reason)
}

fn reason(error: GroundPointError) -> String {
    match error {
        GroundPointError::Horizon => format!(
            "camera too close to the horizon (needs at least {MIN_DEPRESSION_DEG}° down, with its error)"
        ),
        GroundPointError::RangeExceeded => {
            format!("camera point farther than {MAX_SLANT_RANGE_M} m")
        }
        other => other.to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::sim;

    const NOW_MS: u64 = (sim::START_TIME_S + 10) * 1000;

    fn fix_and_pose(seq: u32) -> (TelemetrySample, CameraPose) {
        (sim::sample(seq), sim::camera(seq))
    }

    #[test]
    fn simulated_camera_hits_the_orbit_centre_with_a_bound() {
        let (fix, pose) = fix_and_pose(10);
        let point = camera_point(&fix, &pose, NOW_MS).unwrap();
        let north_m = (point.position.lat() - sim::CENTER_LAT_DEG).to_radians() * 6_371_008.8;
        let east_m = (point.position.lon() - sim::CENTER_LON_DEG).to_radians()
            * 6_371_008.8
            * sim::CENTER_LAT_DEG.to_radians().cos();
        assert!(north_m.hypot(east_m) < 1.0, "{north_m} {east_m}");
        assert!((point.ground_range_m - sim::RADIUS_M).abs() < 0.5);
        assert!(point.uncertainty_radius_m > fix.horizontal_accuracy_m);
        assert!(point.uncertainty_radius_m < 50.0);
    }

    #[test]
    fn take_off_relative_altitude_uses_the_take_off_ground() {
        let (mut fix, mut pose) = fix_and_pose(10);
        fix.altitude_reference = AltitudeReference::TakeoffRelative;
        fix.altitude_m = sim::ALTITUDE_MSL_M - sim::GROUND_MSL_M;
        pose.ground_height_m = Some(0.0);
        let point = camera_point(&fix, &pose, NOW_MS).unwrap();
        assert!((point.ground_range_m - sim::RADIUS_M).abs() < 0.5);
        assert!(matches!(point.ground, GroundModel::TakeoffRelative { .. }));
    }

    #[test]
    fn refusals_never_invent_a_point() {
        let (fix, pose) = fix_and_pose(10);
        let level = CameraPose {
            elevation_deg: 0.0,
            ..pose
        };
        assert!(
            camera_point(&fix, &level, NOW_MS)
                .unwrap_err()
                .contains("horizon")
        );
        let no_ground = CameraPose {
            ground_height_m: None,
            ..pose
        };
        assert!(
            camera_point(&fix, &no_ground, NOW_MS)
                .unwrap_err()
                .contains("ground height")
        );
        let unknown = TelemetrySample {
            altitude_reference: AltitudeReference::Unknown,
            ..fix.clone()
        };
        assert!(
            camera_point(&unknown, &pose, NOW_MS)
                .unwrap_err()
                .contains("unknown")
        );
        assert!(
            camera_point(&fix, &pose, NOW_MS + 60_000)
                .unwrap_err()
                .contains("stale")
        );
        let far = CameraPose {
            elevation_deg: -5.5,
            ..pose
        };
        assert!(camera_point(&fix, &far, NOW_MS).is_err());
    }
}
