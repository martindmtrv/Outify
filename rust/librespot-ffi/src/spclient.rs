use http::Method;
use librespot_core::{SpotifyId, SpotifyUri, spclient::SpClientResult};
use librespot_protocol::context::Context;

use crate::session::with_session;

pub async fn get_context(uri: &str) -> Result<Context, librespot_core::error::Error> {
    let session = match with_session(|s| s.clone()) {
        Ok(s) => s,
        Err(e) => {
            error!("failed to clone session for get_context: {e}");
            return Err(librespot_core::Error::internal(
                "failed to clone session for get_context",
            ));
        }
    };
    let spclient = session.spclient();
    spclient.get_context(uri).await
}

pub async fn get_rootlist() -> SpClientResult {
    let session = match with_session(|s| s.clone()) {
        Ok(s) => s,
        Err(e) => {
            error!("failed to clone session for get_rootlist: {e}");
            return Err(librespot_core::Error::internal(
                "failed to clone session for get_rootlist",
            ));
        }
    };
    let spclient = session.spclient();
    spclient.get_rootlist(0, None).await
}

pub async fn get_radio_for_track(track_uri: &SpotifyUri) -> SpClientResult {
    let session = match with_session(|s| s.clone()) {
        Ok(s) => s,
        Err(e) => {
            error!("failed to clone session for get_radio: {e}");
            return Err(librespot_core::Error::internal(
                "failed to clone session for get_radio",
            ));
        }
    };

    let spclient = session.spclient();
    spclient.get_radio_for_track(track_uri).await
}

pub async fn get_lyrics(track_id: &SpotifyId) -> SpClientResult {
    let session = match with_session(|s| s.clone()) {
        Ok(s) => s,
        Err(e) => {
            error!("failed to clone session for get_lyrics: {e}");
            return Err(librespot_core::Error::internal(
                "failed to clone session for get_lyrics",
            ));
        }
    };

    let spclient = session.spclient();
    spclient.get_lyrics(track_id).await
}

pub fn get_username() -> Result<String, librespot_core::error::Error> {
    match with_session(|s| s.clone()) {
        Ok(s) => Ok(s.username()),
        Err(e) => {
            error!("failed to clone session for get_username: {e}");
            Err(e)
        }
    }
}

/// Contexts (playlists, albums, artists, shows) the user played recently, newest first,
/// as the JSON the desktop client gets. Unlike the Web API's recently-played this takes
/// the session's own token, so it works with a Connect-only login.
pub async fn get_recently_played(limit: u32) -> SpClientResult {
    let session = match with_session(|s| s.clone()) {
        Ok(s) => s,
        Err(e) => {
            error!("failed to clone session for get_recently_played: {e}");
            return Err(librespot_core::Error::internal(
                "failed to clone session for get_recently_played",
            ));
        }
    };

    let endpoint = format!(
        "/recently-played/v3/user/{}/recently-played?format=json&offset=0&limit={limit}&filter=default,collection-new-episodes&market=from_token",
        session.username()
    );
    session
        .spclient()
        .request_as_json(&Method::GET, &endpoint, None, None)
        .await
}

/// One page of a collection set ("collection" holds liked tracks and saved albums,
/// "artist" followed artists, "show" saved shows), from the internal collection
/// service rather than the Web API.
pub async fn get_collection_page(
    set: &str,
    pagination_token: &str,
    limit: i32,
) -> Result<collection::PageResponse, librespot_core::Error> {
    let session = match with_session(|s| s.clone()) {
        Ok(s) => s,
        Err(e) => {
            error!("failed to clone session for get_collection_page: {e}");
            return Err(librespot_core::Error::internal(
                "failed to clone session for get_collection_page",
            ));
        }
    };

    let body = collection::encode_page_request(&session.username(), set, pagination_token, limit);
    let mut headers = http::HeaderMap::new();
    // The service answers 400 to plain application/x-protobuf.
    let proto = http::HeaderValue::from_static("application/vnd.collection-v2.spotify.proto");
    headers.insert(http::header::CONTENT_TYPE, proto.clone());
    headers.insert(http::header::ACCEPT, proto);

    let bytes = session
        .spclient()
        .request(&Method::POST, "/collection/v2/paging", Some(headers), Some(&body))
        .await?;
    collection::decode_page_response(&bytes)
        .map_err(|e| librespot_core::Error::internal(format!("bad collection page: {e}")))
}

/// Hand-rolled encoding of the few `collection2v2.proto` messages used here, which
/// librespot-protocol does not compile.
pub mod collection {
    pub struct CollectionItem {
        pub uri: String,
        pub added_at: i64,
        pub is_removed: bool,
    }

    pub struct PageResponse {
        pub items: Vec<CollectionItem>,
        pub next_page_token: String,
    }

    fn put_varint(out: &mut Vec<u8>, mut value: u64) {
        while value >= 0x80 {
            out.push((value as u8) | 0x80);
            value >>= 7;
        }
        out.push(value as u8);
    }

    fn put_string(out: &mut Vec<u8>, field: u64, value: &str) {
        if value.is_empty() {
            return;
        }
        put_varint(out, field << 3 | 2);
        put_varint(out, value.len() as u64);
        out.extend_from_slice(value.as_bytes());
    }

    /// `PageRequest { username = 1; set = 2; pagination_token = 3; limit = 4 }`
    pub fn encode_page_request(username: &str, set: &str, pagination_token: &str, limit: i32) -> Vec<u8> {
        let mut out = Vec::new();
        put_string(&mut out, 1, username);
        put_string(&mut out, 2, set);
        put_string(&mut out, 3, pagination_token);
        put_varint(&mut out, 4 << 3);
        put_varint(&mut out, limit as u64);
        out
    }

    struct Reader<'a> {
        buf: &'a [u8],
        pos: usize,
    }

    impl<'a> Reader<'a> {
        fn varint(&mut self) -> Result<u64, &'static str> {
            let mut value = 0u64;
            for shift in (0..64).step_by(7) {
                let byte = *self.buf.get(self.pos).ok_or("truncated varint")?;
                self.pos += 1;
                value |= u64::from(byte & 0x7f) << shift;
                if byte & 0x80 == 0 {
                    return Ok(value);
                }
            }
            Err("varint too long")
        }

        fn bytes(&mut self) -> Result<&'a [u8], &'static str> {
            let len = self.varint()? as usize;
            let end = self.pos.checked_add(len).filter(|&e| e <= self.buf.len()).ok_or("truncated field")?;
            let slice = &self.buf[self.pos..end];
            self.pos = end;
            Ok(slice)
        }

        /// Calls `on_field(field, wire_type, reader)`; the callback must consume the
        /// value for the wire types it handles and return false for the rest.
        fn fields(
            &mut self,
            mut on_field: impl FnMut(u64, u64, &mut Self) -> Result<bool, &'static str>,
        ) -> Result<(), &'static str> {
            while self.pos < self.buf.len() {
                let key = self.varint()?;
                let (field, wire) = (key >> 3, key & 7);
                if !on_field(field, wire, self)? {
                    match wire {
                        0 => {
                            self.varint()?;
                        }
                        1 => self.pos += 8,
                        2 => {
                            self.bytes()?;
                        }
                        5 => self.pos += 4,
                        _ => return Err("unsupported wire type"),
                    }
                }
            }
            Ok(())
        }
    }

    fn string(bytes: &[u8]) -> String {
        String::from_utf8_lossy(bytes).into_owned()
    }

    /// `PageResponse { repeated CollectionItem items = 1; next_page_token = 2; ... }`
    /// with `CollectionItem { uri = 1; added_at = 2; is_removed = 3; ... }`
    pub fn decode_page_response(buf: &[u8]) -> Result<PageResponse, &'static str> {
        let mut response = PageResponse { items: Vec::new(), next_page_token: String::new() };
        Reader { buf, pos: 0 }.fields(|field, wire, r| match (field, wire) {
            (1, 2) => {
                let mut item = CollectionItem { uri: String::new(), added_at: 0, is_removed: false };
                Reader { buf: r.bytes()?, pos: 0 }.fields(|field, wire, r| match (field, wire) {
                    (1, 2) => {
                        item.uri = string(r.bytes()?);
                        Ok(true)
                    }
                    (2, 0) => {
                        item.added_at = r.varint()? as i64;
                        Ok(true)
                    }
                    (3, 0) => {
                        item.is_removed = r.varint()? != 0;
                        Ok(true)
                    }
                    _ => Ok(false),
                })?;
                response.items.push(item);
                Ok(true)
            }
            (2, 2) => {
                response.next_page_token = string(r.bytes()?);
                Ok(true)
            }
            _ => Ok(false),
        })?;
        Ok(response)
    }
}
