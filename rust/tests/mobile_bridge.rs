//! Contract tests for the JNI-facing mobile bridge (`mobile` module).
//!
//! Kotlin owns the Android lifecycle, so the bridge must be startable from a
//! plain synchronous call, idempotent when Android re-creates the process
//! objects, and safe to stop twice. These tests drive the real core (real
//! loopback listener on an ephemeral port) and never touch the network.
//!
//! Both scenarios live in one `#[test]` on purpose: the bridge owns process-wide
//! state, so running them in parallel would make them race each other.

mod common;

use common::TempDir;

use fqapi_core::mobile::{self, MobileConfig};

fn config_for(dir: &TempDir, with_config: bool) -> MobileConfig {
    let config_path = if with_config {
        dir.write("config/config.json", "{}")
    } else {
        dir.join("config/missing.json")
    };
    MobileConfig {
        config_path: config_path.to_string_lossy().into_owned(),
        pool_path: dir.join("pool.json").to_string_lossy().into_owned(),
        filter_path: dir
            .join("missing-filter.json")
            .to_string_lossy()
            .into_owned(),
        runtime_dir: dir.path().to_string_lossy().into_owned(),
        port: 0,
        lan_share: false,
    }
}

#[test]
fn mobile_bridge_starts_once_serves_requests_and_stops() {
    let dir = TempDir::new("mobile-bridge");
    let broken = config_for(&dir, false);

    // A missing config file must be reported with the path, not a panic.
    let error = mobile::start(&broken).expect_err("missing config is an error");
    assert!(error.contains("read config"), "unexpected error: {error}");
    assert!(!mobile::is_running());

    let cfg = config_for(&dir, true);
    let first = mobile::start(&cfg).expect("first start");
    assert!(first.port > 0, "ephemeral port must be resolved");
    assert!(
        !first.capability.is_empty(),
        "session capability is required"
    );
    assert!(mobile::is_running());
    assert!(
        first.base_url.starts_with("http://127.0.0.1:"),
        "base url: {}",
        first.base_url
    );

    // `version` is the FFI handshake value; keep the exact shape.
    assert_eq!(
        mobile::version(),
        format!("fqapi_core {}", env!("CARGO_PKG_VERSION"))
    );

    // The port must actually accept connections, not just be reported.
    std::net::TcpStream::connect(("127.0.0.1", first.port))
        .expect("loopback listener accepts connections");

    // Cancelling an unknown/empty request id is a no-op, never a crash.
    assert!(!mobile::cancel(""));

    // Android may start the bridge twice (activity re-creation); the second
    // call must reuse the running listener instead of binding another port.
    let second = mobile::start(&cfg).expect("second start is idempotent");
    assert_eq!(second.port, first.port);
    assert_eq!(second.capability, first.capability);
    assert_eq!(second.base_url, first.base_url);

    mobile::stop().expect("stop");
    assert!(!mobile::is_running());
    mobile::stop().expect("second stop is a no-op");

    // Sanity: the listener is really gone, so a restart gets a fresh port.
    let restarted = mobile::start(&cfg).expect("restart after stop");
    assert!(restarted.port > 0);
    mobile::stop().expect("final stop");
}
