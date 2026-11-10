//! DJI SDK 4 value normalization (UAS-ARC-005): `FlightControllerState` values
//! as the SDK reports them in, SI cockpit values out. What DJI does not know
//! stays unknown; nothing is replaced by zero.

use crate::telemetry::AltitudeReference;

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
    /// Positive up.
    pub vertical_speed_mps: f64,
}

/// Normalizes one DJI flight-controller update for the cockpit.
#[uniffi::export]
#[must_use]
pub fn normalize_dji(raw: &DjiFlightState) -> AircraftPose {
    let (lat, lon) = (raw.latitude_deg, raw.longitude_deg);
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
        ground_speed_mps: raw.velocity_north_mps.hypot(raw.velocity_east_mps),
        vertical_speed_mps: -raw.velocity_down_mps,
    }
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
}
