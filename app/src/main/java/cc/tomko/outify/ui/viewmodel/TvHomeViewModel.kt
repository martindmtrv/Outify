package cc.tomko.outify.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.tomko.outify.ALBUM_COVER_URL
import cc.tomko.outify.core.SpClient
import cc.tomko.outify.core.UserProfile
import cc.tomko.outify.core.model.Album
import cc.tomko.outify.core.model.CoverSize
import cc.tomko.outify.core.model.Playlist
import cc.tomko.outify.core.model.Profile
import cc.tomko.outify.core.model.getCover
import cc.tomko.outify.core.spirc.SpircController
import cc.tomko.outify.core.spirc.SpircState
import cc.tomko.outify.data.metadata.Metadata
import cc.tomko.outify.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "TvHomeViewModel"
private const val SHELF_SIZE = 30

/** One card on a TV Home shelf. */
sealed interface TvHomeCard {
    val uri: String

    data class PlaylistCard(val playlist: Playlist, val artworkUrl: String?) : TvHomeCard {
        override val uri: String get() = playlist.uri
    }

    data class AlbumCard(val album: Album) : TvHomeCard {
        override val uri: String get() = album.uri
    }

    data class ArtistCard(override val uri: String, val name: String, val imageUrl: String?) : TvHomeCard
}

/** A shelf's cards, or null while its first load is still running. */
data class TvHomeUiState(
    val recentlyPlayed: List<TvHomeCard>? = null,
    val likedSongsCount: Int? = null,
    val playlists: List<TvHomeCard>? = null,
    val savedAlbums: List<TvHomeCard>? = null,
) {
    val isLoaded: Boolean
        get() = recentlyPlayed != null && likedSongsCount != null && playlists != null && savedAlbums != null

    val isEmpty: Boolean
        get() = isLoaded && recentlyPlayed.isNullOrEmpty() && likedSongsCount == 0 &&
            playlists.isNullOrEmpty() && savedAlbums.isNullOrEmpty()
}

/**
 * Feeds the TV Home from Spotify's internal services (recently played, rootlist,
 * collection), which take the Connect session's own token. The Web API that the phone
 * Home uses only gets librespot's shared, rate-limited client ID with a Connect login.
 */
@HiltViewModel
class TvHomeViewModel @Inject constructor(
    private val spClient: SpClient,
    private val metadata: Metadata,
    private val userProfile: UserProfile,
    private val settingsRepository: SettingsRepository,
    private val spircController: SpircController,
    private val json: Json,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TvHomeUiState())
    val uiState: StateFlow<TvHomeUiState> = _uiState.asStateFlow()

    val username: Flow<String?> = settingsRepository.username

    /** Bounds the metadata requests a shelf fires at once. */
    private val metadataPermits = Semaphore(6)

    private var loadJob: Job? = null

    /**
     * Reloads the shelves. Each one shows as soon as it is ready, since playlist metadata
     * can take a while on a first run, and keeps its current cards until then.
     */
    fun refresh() {
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            // Every call below needs the session, which comes up asynchronously.
            spircController.state.first { it == SpircState.Ready }
            launch { loadUserProfile() }

            withContext(Dispatchers.IO) {
                launch {
                    val recent = loadRecentlyPlayed()
                    _uiState.update { it.copy(recentlyPlayed = recent) }
                }
                launch {
                    val (likedCount, albums) = loadCollection()
                    _uiState.update { it.copy(likedSongsCount = likedCount, savedAlbums = albums) }
                }
                launch {
                    val playlists = loadPlaylists()
                    _uiState.update { it.copy(playlists = playlists) }
                }
            }
        }
    }

    private suspend fun loadRecentlyPlayed(): List<TvHomeCard> {
        val raw = spClient.getRecentlyPlayed(SHELF_SIZE) ?: return emptyList()
        val uris = try {
            json.parseToJsonElement(raw).jsonObject["playContexts"]?.jsonArray
                ?.mapNotNull { it.jsonObject["uri"]?.jsonPrimitive?.content }
                .orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "unreadable recently played response", e)
            return emptyList()
        }
        return resolveCards(uris.distinct())
    }

    /** Liked songs count and saved albums, from the same collection set. */
    private suspend fun loadCollection(): Pair<Int, List<TvHomeCard>> {
        val raw = spClient.getCollectionUris("collection") ?: return 0 to emptyList()
        val uris = try {
            json.decodeFromString<List<String>>(raw)
        } catch (e: Exception) {
            Log.w(TAG, "unreadable collection response", e)
            return 0 to emptyList()
        }
        val likedCount = uris.count { it.startsWith("spotify:track:") }
        val albums = uris.filter { it.startsWith("spotify:album:") }.take(SHELF_SIZE)
        return likedCount to resolveCards(albums)
    }

    private suspend fun loadPlaylists(): List<TvHomeCard> =
        resolveCards(metadata.getPlaylistUris().take(SHELF_SIZE))

    /** Cards for playlist, album and artist URIs in order, dropping others and failures. */
    private suspend fun resolveCards(uris: List<String>): List<TvHomeCard> = coroutineScope {
        uris.map { uri ->
            async { metadataPermits.withPermit { resolveCard(uri) } }
        }.awaitAll().filterNotNull()
    }

    private suspend fun resolveCard(uri: String): TvHomeCard? = try {
        when {
            uri.startsWith("spotify:playlist:") ->
                metadata.getPlaylistMetadata(uri, allowCached = true)?.let { playlist ->
                    TvHomeCard.PlaylistCard(playlist, playlist.getCover(metadata))
                }

            uri.startsWith("spotify:album:") ->
                metadata.getAlbumMetadata(uri)?.let { TvHomeCard.AlbumCard(it) }

            uri.startsWith("spotify:artist:") ->
                metadata.getArtistMetadata(uri)?.let { artist ->
                    val cover = artist.getCover(CoverSize.MEDIUM) ?: artist.covers.firstOrNull()
                    TvHomeCard.ArtistCard(uri, artist.name, cover?.let { ALBUM_COVER_URL + it.uri })
                }

            else -> null
        }
    } catch (e: Exception) {
        Log.w(TAG, "no metadata for $uri", e)
        null
    }

    private suspend fun loadUserProfile() {
        try {
            val userId = withContext(Dispatchers.IO) { spClient.username() } ?: return
            val profileJson = withContext(Dispatchers.IO) { userProfile.getUserProfile(userId) }
            val profile = profileJson?.let {
                try {
                    json.decodeFromString<Profile>(it)
                } catch (_: Exception) {
                    null
                }
            }
            settingsRepository.saveUserProfile(userId, profile?.name, profile?.imageUrl)
        } catch (e: Exception) {
            Log.w(TAG, "could not load the user profile", e)
        }
    }
}
