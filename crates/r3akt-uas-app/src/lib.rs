//! Thin R3AKT UAS application integration.
//!
//! This crate normalizes aircraft values at the DJI/simulator boundary and calls
//! the shared R3AKT libraries for every wire format. It must not grow its own
//! Reticulum stack, LXMF envelope, telemetry codec, routing policy or delivery
//! queue (see `AGENTS.md` and `docs/specification/architecture.md`).

#![allow(unsafe_code)] // Only UniFFI's generated scaffolding; hand-written code stays safe.
uniffi::setup_scaffolding!();

#[cfg(feature = "publish")]
pub mod aircraft;
pub mod camera;
pub mod dji;
#[cfg(feature = "publish")]
pub mod identity;
#[cfg(feature = "publish")]
pub mod markers;
#[cfg(feature = "publish")]
pub mod publish;
pub mod sim;
pub mod telemetry;

pub use dji::{AircraftPose, DjiFlightState, dji_sample, normalize_dji};
pub use telemetry::{AltitudeReference, DecodedTelemetry, TelemetrySample, UasError};
