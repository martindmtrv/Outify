use std::{
    collections::HashMap,
    fs::OpenOptions,
    os::unix::fs::OpenOptionsExt,
    time::{Duration, Instant},
};

use librespot_oauth::{OAuthClientBuilder, OAuthToken};
use oauth2::AuthorizationCode;

use crate::spotify::{
    error::SpotifyApiError,
    token::{TokenResponse, WebApiToken},
};

use super::{
    OAuthState, SPOTIFY_OAUTH_CALLBACK_URI, SPOTIFY_OAUTH_SCOPES, SpotifyClient,
    check_response_json,
};

impl SpotifyClient {
    pub async fn get_oauth_url(&self) -> String {
        SPOTIFY_OAUTH_CALLBACK_URI.to_string()
    }

    pub async fn start_oauth_flow(&self) -> Result<String, SpotifyApiError> {
        let client_id = self.client_id.lock().unwrap().clone();
        let oauth_client = OAuthClientBuilder::new(
            &client_id,
            SPOTIFY_OAUTH_CALLBACK_URI,
            SPOTIFY_OAUTH_SCOPES.to_vec(),
        )
        .build()
        .map_err(|e| SpotifyApiError::Generic(format!("Failed to build OAuth client: {}", e)))?;

        let (auth_url, pkce_verifier) = oauth_client.set_auth_url();

        let state = OAuthState {
            oauth_client,
            pkce_verifier: Some(pkce_verifier),
            created_at: Instant::now(),
        };

        let mut oauth_state_guard = self.oauth_state.write().await;
        *oauth_state_guard = Some(state);

        debug!("oauth flow started with url: {auth_url}");
        Ok(auth_url.to_string())
    }

    pub async fn complete_oauth_flow(&self, code: String) -> Result<WebApiToken, SpotifyApiError> {
        let mut oauth_state_guard = self.oauth_state.write().await;
        let state = oauth_state_guard.as_mut().ok_or(SpotifyApiError::Generic(
            "OAuth flow not started. Call start_oauth_flow first.".to_string(),
        ))?;

        if state.created_at.elapsed() > Duration::from_secs(600) {
            error!("oauth state expired");
            return Err(SpotifyApiError::Generic(
                "OAuth state expired. Please restart the flow.".to_string(),
            ));
        }

        let pkce_verifier = state.pkce_verifier.take().ok_or(SpotifyApiError::Generic(
            "PKCE verifier not found. OAuth flow may have already completed.".to_string(),
        ))?;
        let oauth_client = &state.oauth_client;

        let auth_code = AuthorizationCode::new(code);
        let token_response: OAuthToken = oauth_client
            .get_access_token_with_verifier_async(pkce_verifier, auth_code)
            .await
            .map_err(|e| {
                error!("oauth token exchange failed: {e}");
                SpotifyApiError::Generic(format!("Token exchange failed: {e}"))
            })?;

        let now = Instant::now();
        let expires_in = if token_response.expires_at > now {
            token_response.expires_at.duration_since(now).as_secs()
        } else {
            0
        };

        let new_token = WebApiToken::new(
            token_response.access_token,
            token_response.refresh_token,
            expires_in,
            token_response.scopes.join(" "),
        );

        let mut token_guard = self.token.write().await;
        *token_guard = Some(new_token.clone());

        drop(oauth_state_guard);
        let mut oauth_state_guard = self.oauth_state.write().await;
        *oauth_state_guard = None;

        debug!("oauth flow completed");

        match self.save_token(&new_token).await {
            Ok(_) => debug!("oauth token saved to account.json"),
            Err(e) => {
                error!("oauth token save failed: {e}");
            }
        };

        Ok(new_token)
    }

    pub async fn save_token(&self, token: &WebApiToken) -> Result<(), SpotifyApiError> {
        let mut path = crate::FILES_DIR
            .get()
            .ok_or_else(|| SpotifyApiError::Generic("Android file path is not set!".to_string()))?
            .clone();

        path.push("account.json");

        let mut file = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .mode(0o600)
            .open(&path)?;

        let json = serde_json::to_string(token).map_err(|e| {
            SpotifyApiError::Generic(format!("Failed to serialize WebApiToken: {e}"))
        })?;

        std::io::Write::write_all(&mut file, json.as_bytes())?;
        Ok(())
    }

    pub fn remove_token(&self) -> Result<(), SpotifyApiError> {
        let mut path = crate::FILES_DIR
            .get()
            .ok_or_else(|| SpotifyApiError::Generic("Android file path is not set!".to_string()))?
            .clone();

        path.push("account.json");

        match std::fs::remove_file(path) {
            Ok(_) => Ok(()),
            Err(e) => {
                error!("account.json removal failed: {e}");
                Err(SpotifyApiError::IO(e))
            }
        }
    }

    /// Whether a stored token exists or an active authenticated session exists.
    pub async fn is_oauth_authenticated(&self) -> bool {
        if matches!(self.read_token_file(), Ok(Some(_))) {
            return true;
        }

        match crate::session::with_session(|session| !session.is_invalid() && !session.username().is_empty()) {
            Ok(true) => true,
            _ => false,
        }
    }

    pub async fn get_scope(&self) -> Option<String> {
        match self.load_token().await {
            Ok(Some(t)) => Some(t.scope),
            _ => None,
        }
    }

    fn read_token_file(&self) -> Result<Option<WebApiToken>, SpotifyApiError> {
        let mut path = crate::FILES_DIR
            .get()
            .ok_or_else(|| SpotifyApiError::Generic("Android file path is not set!".to_string()))?
            .clone();

        path.push("account.json");

        match std::fs::read_to_string(&path) {
            Ok(contents) => serde_json::from_str::<WebApiToken>(&contents)
                .map(Some)
                .map_err(|e| SpotifyApiError::Generic(format!("Failed to parse token JSON: {e}"))),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(None),
            Err(e) => Err(SpotifyApiError::IO(e)),
        }
    }

    pub async fn load_token(&self) -> Result<Option<WebApiToken>, SpotifyApiError> {
        if let Some(token) = self.read_token_file()? {
            if token.is_expired() {
                let refreshed = self.refresh_token(&token).await?;
                return Ok(Some(refreshed));
            }
            return Ok(Some(token));
        }

        // Check if an in-memory token (e.g. from session) was already acquired and is valid
        {
            let guard = self.token.read().await;
            if let Some(ref t) = *guard {
                if !t.is_expired() {
                    return Ok(Some(t.clone()));
                }
            }
        }

        // Fall back to requesting a token from the active librespot session
        let session_res = crate::session::with_session_async(|session| {
            Box::pin(async move {
                if session.is_invalid() || session.username().is_empty() {
                    return None;
                }

                // 1. Try token_provider with official Android client ID (valid for Web API)
                let scopes = "user-read-private,user-read-email,user-top-read,user-library-read,playlist-read-private";
                let token_provider = session.token_provider();
                match token_provider.get_token_with_client_id(scopes, librespot_core::config::ANDROID_CLIENT_ID).await {
                    Ok(tok) => {
                        info!("acquired access_token via keymaster (ANDROID_CLIENT_ID with scopes)!");
                        return Some(tok);
                    }
                    Err(e) => {
                        warn!("keymaster ANDROID_CLIENT_ID with scopes failed: {e}");
                    }
                }

                // 2. Try token_provider with official Android client ID without scopes
                match token_provider.get_token_with_client_id("", librespot_core::config::ANDROID_CLIENT_ID).await {
                    Ok(tok) => {
                        info!("acquired access_token via keymaster (ANDROID_CLIENT_ID no-scope)!");
                        return Some(tok);
                    }
                    Err(e) => {
                        warn!("keymaster ANDROID_CLIENT_ID no-scope failed: {e}");
                    }
                }

                // 3. Try login5 auth_token (a user access token the Web API accepts)
                match session.login5().auth_token().await {
                    Ok(tok) => {
                        info!("acquired access_token via login5!");
                        return Some(tok);
                    }
                    Err(e) => {
                        warn!("login5 auth_token failed: {e}");
                    }
                }

                // 4. Try client_token from spclient. Not a user token: the Web API
                // rejects it with 401, so it is only good for spclient search.
                match session.spclient().client_token().await {
                    Ok(client_tok) => {
                        info!("acquired client_token from spclient!");
                        return Some(librespot_core::token::Token {
                            access_token: client_tok,
                            expires_in: std::time::Duration::from_secs(3600),
                            token_type: "Bearer".to_string(),
                            scopes: vec![],
                            timestamp: std::time::SystemTime::now(),
                        });
                    }
                    Err(e) => {
                        warn!("client_token fallback failed: {e}");
                    }
                }

                None
            })
        }).await;

        if let Ok(Some(tok)) = session_res {
            let web_token = WebApiToken::new(
                tok.access_token,
                String::new(),
                tok.expires_in.as_secs(),
                tok.scopes.join(" "),
            );
            let mut guard = self.token.write().await;
            *guard = Some(web_token.clone());
            return Ok(Some(web_token));
        }

        Ok(None)
    }

    pub(crate) async fn refresh_token(
        &self,
        token: &WebApiToken,
    ) -> Result<WebApiToken, SpotifyApiError> {
        let _guard = self.refresh_lock.lock().await;

        if token.refresh_token.is_empty() {
            let session_res = crate::session::with_session_async(|session| {
                Box::pin(async move {
                    if session.is_invalid() || session.username().is_empty() {
                        return None;
                    }

                    let scopes = "user-read-private,user-read-email,user-top-read,user-library-read,playlist-read-private";
                    if let Ok(tok) = session.token_provider().get_token_with_client_id(scopes, librespot_core::config::ANDROID_CLIENT_ID).await {
                        return Some(tok);
                    }

                    if let Ok(tok) = session.token_provider().get_token_with_client_id("", librespot_core::config::ANDROID_CLIENT_ID).await {
                        return Some(tok);
                    }

                    if let Ok(tok) = session.login5().auth_token().await {
                        return Some(tok);
                    }

                    if let Ok(client_tok) = session.spclient().client_token().await {
                        return Some(librespot_core::token::Token {
                            access_token: client_tok,
                            expires_in: std::time::Duration::from_secs(3600),
                            token_type: "Bearer".to_string(),
                            scopes: vec![],
                            timestamp: std::time::SystemTime::now(),
                        });
                    }

                    None
                })
            }).await;

            if let Ok(Some(tok)) = session_res {
                let new_token = WebApiToken::new(
                    tok.access_token,
                    String::new(),
                    tok.expires_in.as_secs(),
                    tok.scopes.join(" "),
                );
                let mut guard = self.token.write().await;
                *guard = Some(new_token.clone());
                return Ok(new_token);
            }

            return Err(SpotifyApiError::Generic("Cannot refresh session token".to_string()));
        }

        // Another request may have refreshed while we waited for the lock;
        // reusing the old (rotated) refresh token would then fail.
        if let Some(stored) = self.read_token_file()? {
            if stored.access_token != token.access_token && !stored.is_expired() {
                return Ok(stored);
            }
        }

        let mut form = HashMap::new();
        form.insert("grant_type", "refresh_token");
        form.insert("refresh_token", &token.refresh_token);
        let client_id = self.client_id.lock().unwrap().clone();
        form.insert("client_id", &client_id);

        let response = self
            .client
            .post("https://accounts.spotify.com/api/token")
            .form(&form)
            .send()
            .await?;

        if response.status() == reqwest::StatusCode::BAD_REQUEST {
            let body = response.text().await.unwrap_or_default();
            if body.contains("invalid_grant") {
                error!("refresh token revoked, removing stored token: {body}");
                let _ = self.remove_token();
            }
            return Err(SpotifyApiError::Http(400, body));
        }

        let response = check_response_json::<TokenResponse>("refresh_token", response).await?;

        let new_token = WebApiToken::from(response, Some(&token.refresh_token));
        self.save_token(&new_token).await?;
        Ok(new_token)
    }
}
