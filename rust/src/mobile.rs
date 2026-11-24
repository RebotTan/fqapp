//! JNI-facing bridge for the native Android client.
//!
//! The Flutter app reaches the core through flutter_rust_bridge; the Kotlin
//! client only needs to boot the loopback listener and then speak plain HTTP,
//! so everything here is synchronous and thread-safe enough to call straight
//! from a JNI binding.
//!
//! Threading: the runtime is created once and kept alive for the process. Call
//! these functions from a Java thread (JNI always is one); calling them from a
//! tokio worker would deadlock `block_on`.

use std::sync::OnceLock;

use tokio::runtime::Runtime;

use crate::api;
use crate::server::ListenMode;

/// Paths and ports Android hands to the core at boot.
pub struct MobileConfig {
    pub config_path: String,
    pub pool_path: String,
    pub filter_path: String,
    pub runtime_dir: String,
    /// `0` binds an ephemeral port, which is what Android should pass.
    pub port: u16,
    pub lan_share: bool,
}

/// What the listener settled on. Stable across repeated `start` calls.
#[derive(Debug, Clone)]
pub struct MobileHandle {
    pub port: u16,
    pub base_url: String,
    pub capability: String,
}

fn runtime() -> &'static Runtime {
    static RUNTIME: OnceLock<Runtime> = OnceLock::new();
    RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .worker_threads(4)
            .enable_all()
            .thread_name("fqapi-core")
            .build()
            .expect("build tokio runtime")
    })
}

/// Starts the core, or returns the already-running listener.
pub fn start(cfg: &MobileConfig) -> Result<MobileHandle, String> {
    let rt = runtime();
    let mode = if cfg.lan_share {
        ListenMode::Lan
    } else {
        ListenMode::Loopback
    };
    rt.block_on(api::init_core(
        cfg.config_path.clone(),
        cfg.pool_path.clone(),
        cfg.filter_path.clone(),
        cfg.runtime_dir.clone(),
        cfg.port,
        mode,
        None,
    ))?;

    // `init_core` answers "running" on a repeat call, so read the endpoint back
    // instead of trusting the local arguments.
    let base_url = rt.block_on(api::base_url());
    let capability = rt.block_on(api::session_capability());
    let port = base_url
        .rsplit(':')
        .next()
        .and_then(|tail| tail.parse().ok())
        .unwrap_or(0);
    Ok(MobileHandle {
        port,
        base_url,
        capability,
    })
}

/// Stops the listener. Safe to call when nothing is running.
pub fn stop() -> Result<(), String> {
    runtime().block_on(api::shutdown())
}

pub fn is_running() -> bool {
    runtime().block_on(api::status()) == "running"
}

pub fn version() -> String {
    api::version()
}

/// Aborts an in-flight request; unknown ids are remembered so a late arrival is
/// cancelled on sight. Returns whether a running request was hit.
pub fn cancel(request_id: &str) -> bool {
    api::cancel(request_id.to_string())
}
