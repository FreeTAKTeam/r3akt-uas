//! Prints deterministic simulated aircraft telemetry fixtures as JSON lines.
//! Offline only: it never opens a hardware or network connection.
//!
//! uas-sim [--count N]          write N samples (default 10) to stdout
//! uas-sim --check FILE [...]   exit non-zero unless FILE matches the output

use std::fmt::Write as _;
use std::process::ExitCode;

use r3akt_uas_app::{sim, telemetry};
use serde_json::{Value, json};

fn hex(bytes: &[u8]) -> String {
    bytes.iter().fold(String::new(), |mut out, b| {
        let _ = write!(out, "{b:02x}");
        out
    })
}

fn line(seq: u32) -> Result<String, String> {
    let s = sim::sample(seq);
    let packed = telemetry::encode_telemetry(&s).map_err(|e| e.to_string())?;
    let decoded = telemetry::decode_telemetry(&packed).map_err(|e| e.to_string())?;
    let decoded: Value = serde_json::from_str(&decoded.json).map_err(|e| e.to_string())?;
    Ok(json!({
        "seq": seq,
        "sample": {
            "sample_time_s": s.sample_time_s,
            "fix_time_s": s.fix_time_s,
            "latitude_deg": s.latitude_deg,
            "longitude_deg": s.longitude_deg,
            "altitude_m": s.altitude_m,
            "altitude_reference": "msl",
            "ground_speed_mps": s.ground_speed_mps,
            "course_deg": s.course_deg,
            "horizontal_accuracy_m": s.horizontal_accuracy_m,
            "battery_percent": s.battery_percent,
            "simulated": s.simulated,
        },
        "field_telemetry_hex": hex(&packed),
        "decoded": decoded,
    })
    .to_string())
}

fn render(count: u32) -> Result<String, String> {
    (0..count).map(|seq| line(seq).map(|l| l + "\n")).collect()
}

fn run(args: &[String]) -> Result<(), String> {
    let mut count = 10;
    let mut check = None;
    let mut it = args.iter();
    while let Some(arg) = it.next() {
        match arg.as_str() {
            "--count" => {
                count = it
                    .next()
                    .and_then(|v| v.parse().ok())
                    .ok_or("--count needs a number")?;
            }
            "--check" => check = Some(it.next().ok_or("--check needs a file")?.clone()),
            other => return Err(format!("unknown argument {other}")),
        }
    }
    match check {
        None => {
            print!("{}", render(count)?);
            Ok(())
        }
        Some(path) => {
            let expected = std::fs::read_to_string(&path).map_err(|e| format!("{path}: {e}"))?;
            let lines = u32::try_from(expected.lines().count()).map_err(|e| e.to_string())?;
            if render(lines)? == expected {
                println!("{path}: {lines} samples match");
                Ok(())
            } else {
                Err(format!("{path} differs from simulator output"))
            }
        }
    }
}

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().skip(1).collect();
    match run(&args) {
        Ok(()) => ExitCode::SUCCESS,
        Err(e) => {
            eprintln!("uas-sim: {e}");
            ExitCode::FAILURE
        }
    }
}
