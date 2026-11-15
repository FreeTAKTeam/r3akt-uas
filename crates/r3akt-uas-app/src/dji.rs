//! DJI SDK 4 value normalization (UAS-ARC-005): `FlightControllerState` values
//! as the SDK reports them in, SI cockpit values out. What DJI does not know
//! stays unknown; nothing is replaced by zero.

use crate::camera::CameraPose;
use crate::telemetry::{AltitudeReference, TelemetrySample};

/// Below this ground speed the aircraft counts as stationary: no course over ground,
/// and the stationary publication rate (UAS-VID-005).
pub const MIN_MOVING_SPEED_MPS: f64 = 1.0;
/// DJI `GPSSignalLevel` needed before a position is published (owner ruling D-15).
pub const MIN_GPS_LEVEL: u8 = 3;
/// A DJI state older than this is not offered as a fix.
pub const MAX_STATE_AGE_MS: u64 = 2_000;

/// DJI SDK 4 `FlightControllerState` values, exactly as reported.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct DjiFlightState {
    /// `aircraftLocation`; NaN (on some firmware 0, 0) without a GPS fix.
    pub latitude_deg: f64,
    pub longitude_deg: f64,
    /// `aircraftLocation` altitude: barometric, metres above the take-off point.
    pub altitude_m: f64,
    /// `attitude.yaw`: degrees from true north, -180..180.
    pub yaw_deg: f64,
    /// `velocityX`, `velocityY`, `velocityZ`: north, east, down, metres per second.
    pub velocity_north_mps: f64,
    pub velocity_east_mps: f64,
    pub velocity_down_mps: f64,
}

/// Aircraft pose for the cockpit, SI units.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AircraftPose {
    /// `None` without a valid fix.
    pub latitude_deg: Option<f64>,
    pub longitude_deg: Option<f64>,
    pub altitude_m: Option<f64>,
    /// Always take-off-relative for DJI SDK 4; never MSL, HAE or AGL (UAS-PRF-004).
    pub altitude_reference: AltitudeReference,
    /// Aircraft heading, degrees true, 0..360. Not course (UAS-PRF-005).
    pub heading_deg: Option<f64>,
    pub ground_speed_mps: f64,
    /// Course over ground, degrees true, 0..360; unknown while stationary (UAS-PRF-005).
    pub course_deg: Option<f64>,
    /// Positive up.
    pub vertical_speed_mps: f64,
}

/// Normalizes one DJI flight-controller update for the cockpit.
#[uniffi::export]
#[must_use]
pub fn normalize_dji(raw: &DjiFlightState) -> AircraftPose {
    let (lat, lon) = (raw.latitude_deg, raw.longitude_deg);
    let speed = raw.velocity_north_mps.hypot(raw.velocity_east_mps);
    let fix = lat.is_finite()
        && lon.is_finite()
        && lat.abs() <= 90.0
        && lon.abs() <= 180.0
        && (lat.abs() > 0.0 || lon.abs() > 0.0);
    AircraftPose {
        latitude_deg: fix.then_some(lat),
        longitude_deg: fix.then_some(lon),
        altitude_m: raw.altitude_m.is_finite().then_some(raw.altitude_m),
        altitude_reference: AltitudeReference::TakeoffRelative,
        heading_deg: raw
            .yaw_deg
            .is_finite()
            .then(|| raw.yaw_deg.rem_euclid(360.0)),
        ground_speed_mps: speed,
        course_deg: (speed >= MIN_MOVING_SPEED_MPS).then(|| {
            raw.velocity_east_mps
                .atan2(raw.velocity_north_mps)
                .to_degrees()
                .rem_euclid(360.0)
        }),
        vertical_speed_mps: -raw.velocity_down_mps,
    }
}

/// A DJI pose as a publishable fix, or `None` when DJI does not give one: no position
/// or altitude, a GPS signal level below [`MIN_GPS_LEVEL`] or unknown, or a state not
/// received within [`MAX_STATE_AGE_MS`]. The fix time is when the state arrived.
/// Accuracy stays unknown (DJI SDK 4 reports none) and the altitude take-off-relative.
#[uniffi::export]
#[must_use]
pub fn dji_sample(
    pose: &AircraftPose,
    gps_level: Option<u8>,
    battery_percent: Option<f64>,
    received_at_ms: u64,
    now_ms: u64,
) -> Option<TelemetrySample> {
    if gps_level? < MIN_GPS_LEVEL
        || received_at_ms > now_ms
        || now_ms - received_at_ms > MAX_STATE_AGE_MS
    {
        return None;
    }
    let time = received_at_ms / 1000;
    Some(TelemetrySample {
        sample_time_s: time,
        fix_time_s: time,
        latitude_deg: pose.latitude_deg?,
        longitude_deg: pose.longitude_deg?,
        altitude_m: pose.altitude_m?,
        altitude_reference: pose.altitude_reference,
        ground_speed_mps: pose.ground_speed_mps,
        course_deg: pose.course_deg,
        horizontal_accuracy_m: None,
        battery_percent,
        simulated: false,
    })
}

// Aim-point bounds for DJI SDK 4 (owner ruling D-15 (b), 15 November 2026), which reports none.
// shortcut: Mavic Air 2 figures used for every DJI aircraft; add a per-model table when a second model flies.
/// DJI Mavic Air 2 specification, hovering accuracy with GPS: horizontal ±1.5 m.
pub const DJI_HORIZONTAL_ERROR_M: f64 = 1.5;
/// DJI Mavic Air 2 specification, hovering accuracy with GPS: vertical ±0.5 m.
pub const DJI_ALTITUDE_ERROR_M: f64 = 0.5;
/// Our estimate: DJI publishes no compass or gimbal yaw accuracy.
pub const DJI_AZIMUTH_ERROR_DEG: f64 = 5.0;
/// Our estimate: DJI publishes no gimbal pitch accuracy.
pub const DJI_ELEVATION_ERROR_DEG: f64 = 1.0;
/// Our estimate: how far the ground at the target may lie above or below the take-off point.
pub const DJI_GROUND_ERROR_M: f64 = 5.0;

/// Where a DJI camera points for the fix `fix`, or `None` when DJI does not report the
/// aircraft heading or the gimbal angles. The azimuth is the aircraft heading plus the gimbal
/// yaw relative to it, the elevation the gimbal pitch, and the ground the take-off point's
/// level (no terrain data), all with the bounds above.
#[uniffi::export]
#[must_use]
pub fn dji_camera_pose(
    fix: &TelemetrySample,
    heading_deg: Option<f64>,
    gimbal_yaw_relative_deg: Option<f64>,
    gimbal_pitch_deg: Option<f64>,
) -> Option<CameraPose> {
    let azimuth = (heading_deg? + gimbal_yaw_relative_deg?).rem_euclid(360.0);
    let elevation = gimbal_pitch_deg?;
    if !azimuth.is_finite() || !elevation.is_finite() {
        return None;
    }
    Some(CameraPose {
        sample_time_ms: fix.fix_time_s.saturating_mul(1000),
        azimuth_deg: azimuth,
        elevation_deg: elevation,
        ground_height_m: Some(0.0),
        ground_height_error_m: DJI_GROUND_ERROR_M,
        altitude_error_m: DJI_ALTITUDE_ERROR_M,
        azimuth_error_deg: DJI_AZIMUTH_ERROR_DEG,
        elevation_error_deg: DJI_ELEVATION_ERROR_DEG,
        max_speed_mps: fix.ground_speed_mps,
        horizontal_error_m: Some(DJI_HORIZONTAL_ERROR_M),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn raw() -> DjiFlightState {
        DjiFlightState {
            latitude_deg: 45.5,
            longitude_deg: -73.6,
            altitude_m: 12.0,
            yaw_deg: -90.0,
            velocity_north_mps: 3.0,
            velocity_east_mps: 4.0,
            velocity_down_mps: -1.5,
        }
    }

    #[test]
    fn dji_values_are_normalized_to_cockpit_units() {
        let pose = normalize_dji(&raw());
        assert_eq!(pose.heading_deg, Some(270.0));
        assert!((pose.ground_speed_mps - 5.0).abs() < 1e-9);
        assert!((pose.vertical_speed_mps - 1.5).abs() < 1e-9);
        // North 3, east 4: course atan2(4, 3).
        assert!((pose.course_deg.unwrap() - 53.130_102_354).abs() < 1e-6);
        assert_eq!(pose.altitude_m, Some(12.0));
        assert_eq!(pose.altitude_reference, AltitudeReference::TakeoffRelative);
        assert_eq!(
            (pose.latitude_deg, pose.longitude_deg),
            (Some(45.5), Some(-73.6))
        );
    }

    #[test]
    fn unreported_values_stay_unknown() {
        for (lat, lon) in [(f64::NAN, f64::NAN), (0.0, 0.0), (91.0, 10.0)] {
            let pose = normalize_dji(&DjiFlightState {
                latitude_deg: lat,
                longitude_deg: lon,
                altitude_m: f64::NAN,
                yaw_deg: f64::NAN,
                ..raw()
            });
            assert_eq!((pose.latitude_deg, pose.longitude_deg), (None, None));
            assert_eq!(pose.heading_deg, None);
            assert_eq!(pose.altitude_m, None);
        }
    }

    #[test]
    fn hovering_has_no_course() {
        let pose = normalize_dji(&DjiFlightState {
            velocity_north_mps: 0.3,
            velocity_east_mps: 0.4,
            ..raw()
        });
        assert_eq!(pose.course_deg, None);
    }

    #[test]
    fn dji_sample_is_real_take_off_relative_and_never_invents_accuracy() {
        let pose = normalize_dji(&raw());
        let s = dji_sample(&pose, Some(4), Some(80.0), 1_000_500, 1_001_000).unwrap();
        assert_eq!(
            (s.fix_time_s, s.latitude_deg, s.altitude_m),
            (1_000, 45.5, 12.0)
        );
        assert_eq!(s.altitude_reference, AltitudeReference::TakeoffRelative);
        assert_eq!(s.horizontal_accuracy_m, None);
        assert!(!s.simulated);
    }

    #[test]
    fn dji_sample_refuses_weak_gps_stale_state_and_no_fix() {
        let pose = normalize_dji(&raw());
        assert!(dji_sample(&pose, Some(2), None, 1_000, 1_000).is_none());
        assert!(dji_sample(&pose, None, None, 1_000, 1_000).is_none());
        assert!(dji_sample(&pose, Some(5), None, 1_000, 3_001).is_none());
        assert!(dji_sample(&pose, Some(5), None, 2_000, 1_000).is_none());
        let no_fix = normalize_dji(&DjiFlightState {
            latitude_deg: f64::NAN,
            ..raw()
        });
        assert!(dji_sample(&no_fix, Some(5), None, 1_000, 1_000).is_none());
    }

    #[test]
    fn dji_aim_point_follows_heading_plus_gimbal_yaw_at_take_off_level() {
        let fix = TelemetrySample {
            altitude_m: 30.0,
            ground_speed_mps: 0.0,
            ..dji_sample(&normalize_dji(&raw()), Some(4), None, 1_000_000, 1_000_000).unwrap()
        };
        // Heading 270 plus gimbal yaw +90 looks north; 45° down from 30 m is 30 m away.
        let pose = dji_camera_pose(&fix, Some(270.0), Some(90.0), Some(-45.0)).unwrap();
        assert!(pose.azimuth_deg.abs() < 1e-9);
        let point = crate::camera::camera_point(&fix, &pose, 1_000_000).unwrap();
        assert!(
            (point.ground_range_m - 30.0).abs() < 0.5,
            "{}",
            point.ground_range_m
        );
        assert!(point.position.lat() > fix.latitude_deg);
        assert!((point.position.lon() - fix.longitude_deg).abs() < 1e-6);
        assert!(point.uncertainty_radius_m > DJI_HORIZONTAL_ERROR_M);
        // The published fix still carries no accuracy: the bound is only the aim point's.
        assert_eq!(fix.horizontal_accuracy_m, None);
        // No heading or no gimbal angle: no pose.
        assert!(dji_camera_pose(&fix, None, Some(0.0), Some(-45.0)).is_none());
        assert!(dji_camera_pose(&fix, Some(0.0), Some(0.0), None).is_none());
        // Level camera: refused by the shared geometry, never a horizon guess.
        let level = dji_camera_pose(&fix, Some(0.0), Some(0.0), Some(0.0)).unwrap();
        assert!(crate::camera::camera_point(&fix, &level, 1_000_000).is_err());
    }
}
