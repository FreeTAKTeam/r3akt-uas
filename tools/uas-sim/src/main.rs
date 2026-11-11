//! Prints deterministic simulated aircraft telemetry fixtures as JSON lines.
//! Offline only: it never opens a hardware or network connection.
//!
//! uas-sim [--count N]          write N samples (default 10) to stdout
//! uas-sim --check FILE [...]   exit non-zero unless FILE matches the output
//!
//! Built with `--features publish` only (milestone 2a, host proof):
//! `uas-sim --publish HOST:PORT HUB_DEST --store DIR [--seconds N] [--resend-at N] [--team UID]`
//!   flies the orbit at 1 Hz with wall-clock fix times and publishes it to RCH;
//!   `--resend-at N` re-sends the last fix's exact bytes once after N seconds;
//!   `--team UID` sends the shared object message with that RCH team (step 2b).
//!   Prints the publisher status as one JSON line per second.

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
            "--publish" => return publish(args),
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

#[cfg(not(feature = "publish"))]
fn publish(_: &[String]) -> Result<(), String> {
    Err("--publish needs a build with --features publish".into())
}

#[cfg(feature = "publish")]
fn publish(args: &[String]) -> Result<(), String> {
    use r3akt_uas_app::publish as p;
    use std::time::{Duration, SystemTime, UNIX_EPOCH};

    let mut it = args.iter().skip_while(|a| *a != "--publish").skip(1);
    let address = it.next().ok_or("--publish needs HOST:PORT")?;
    let hub = it
        .next()
        .ok_or("--publish needs the hub destination hash")?;
    let (mut store, mut seconds, mut resend_at, mut team) = (None, 60_u32, None, None);
    let mut it = args.iter();
    while let Some(arg) = it.next() {
        let mut value = || it.next().ok_or(format!("{arg} needs a value"));
        match arg.as_str() {
            "--store" => store = Some(value()?.clone()),
            "--team" => team = Some(value()?.clone()),
            "--seconds" => seconds = value()?.parse().map_err(|_| "--seconds needs a number")?,
            "--resend-at" => {
                resend_at = Some(
                    value()?
                        .parse::<u32>()
                        .map_err(|_| "--resend-at needs a number")?,
                );
            }
            _ => {}
        }
    }
    let store = store.ok_or("--publish needs --store DIR")?;
    p::publish_start(store, address.clone(), hub, team).map_err(|e| e.to_string())?;
    for seq in 0..seconds {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|e| e.to_string())?
            .as_secs();
        // The orbit's shape with wall-clock time: the hub ignores old and far-future fixes.
        let sample = telemetry::TelemetrySample {
            sample_time_s: now,
            fix_time_s: now,
            ..sim::sample(seq)
        };
        p::publish_offer_fix(sample);
        if resend_at == Some(seq) {
            p::publish_resend_last_fix();
        }
        std::thread::sleep(Duration::from_secs(1));
        println!("{}", status_json(&p::publish_status()));
    }
    p::publish_stop();
    println!("{}", status_json(&p::publish_status()));
    Ok(())
}

#[cfg(feature = "publish")]
fn status_json(s: &r3akt_uas_app::publish::PublishStatus) -> Value {
    json!({
        "state": format!("{:?}", s.state),
        "destination_hash": s.destination_hash,
        "object_id": s.object_id,
        "team_uid": s.team_uid,
        "hub_connected": s.hub_connected,
        "create_sent": s.create_sent,
        "fixes_sent": s.fixes_sent,
        "last_sent_fix": s.last_sent_fix.as_ref().map(|f| json!({
            "fix_time_s": f.fix_time_s, "lat": f.latitude_deg, "lon": f.longitude_deg,
        })),
        "last_error": s.last_error,
    })
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
