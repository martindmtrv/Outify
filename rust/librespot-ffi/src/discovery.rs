use std::sync::Mutex;

use futures_util::StreamExt;
use librespot_core::config::KEYMASTER_CLIENT_ID;
use librespot_discovery::Discovery;
use tokio::sync::oneshot;

use crate::TOKIO_RUNTIME;

/// What a running discovery reports back.
pub enum DiscoveryOutcome {
    /// A Spotify client picked this device and the session was rebuilt with
    /// the account it handed over.
    LoggedIn { username: String },
    /// Discovery stopped or the handed-over account could not be used.
    Failed(String),
}

/// Stops the running discovery when sent or dropped.
static STOP_TX: Mutex<Option<oneshot::Sender<()>>> = Mutex::new(None);

/// Announces this device on the local network as a Spotify Connect target.
///
/// Selecting it in a Spotify client hands over that client's account, which is
/// cached and used to rebuild the session. Runs until [`stop`].
pub fn start(
    on_outcome: impl Fn(DiscoveryOutcome) + Send + Sync + 'static,
) -> Result<(), String> {
    let rt = TOKIO_RUNTIME
        .get()
        .ok_or_else(|| "tokio runtime not available".to_string())?;

    stop();

    let builder = Discovery::builder(crate::session::device_id(), KEYMASTER_CLIENT_ID.to_owned())
        .name(crate::spirc::device_name())
        .device_type(crate::spirc::device_type());

    // The discovery server spawns onto the current runtime.
    let mut discovery = {
        let _enter = rt.enter();
        builder.launch().map_err(|e| {
            error!("discovery launch failed: {e}");
            e.to_string()
        })?
    };

    let (stop_tx, mut stop_rx) = oneshot::channel();
    *STOP_TX.lock().unwrap() = Some(stop_tx);
    info!("discovery started");

    rt.spawn(async move {
        loop {
            let credentials = tokio::select! {
                _ = &mut stop_rx => break,
                next = discovery.next() => match next {
                    Some(credentials) => credentials,
                    None => {
                        on_outcome(DiscoveryOutcome::Failed("discovery stopped".to_string()));
                        break;
                    }
                },
            };

            let username = credentials.username.clone().unwrap_or_default();
            info!("discovery received credentials for {username}");

            match crate::session::open_cache() {
                Ok(cache) => cache.save_credentials(&credentials),
                Err(e) => {
                    on_outcome(DiscoveryOutcome::Failed(e.to_string()));
                    continue;
                }
            }

            // The rebuilt session connects with the cached credentials and
            // replaces them with reusable ones.
            match crate::spirc::request_restart("discovery login").await {
                Ok(()) => on_outcome(DiscoveryOutcome::LoggedIn { username }),
                Err(e) => on_outcome(DiscoveryOutcome::Failed(e)),
            }
        }

        discovery.shutdown().await;
        info!("discovery stopped");
    });

    Ok(())
}

pub fn stop() {
    if let Some(tx) = STOP_TX.lock().unwrap().take() {
        let _ = tx.send(());
    }
}
