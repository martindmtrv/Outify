package cc.tomko.outify.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import cc.tomko.outify.ALBUM_COVER_URL
import cc.tomko.outify.R
import cc.tomko.outify.core.model.CoverSize
import cc.tomko.outify.core.model.getCover
import cc.tomko.outify.core.model.toPlayableAudio
import cc.tomko.outify.ui.viewmodel.SearchUiModel
import cc.tomko.outify.ui.viewmodel.SearchViewModel
import cc.tomko.outify.ui.viewmodel.TvViewModel

private data class GenreCategory(
    val name: String,
    val gradientColors: List<Color>,
)

private val BROWSE_GENRES = listOf(
    GenreCategory("Pop", listOf(Color(0xFFE13300), Color(0xFF8C1932))),
    GenreCategory("Hip-Hop", listOf(Color(0xFFBA5D07), Color(0xFF6B3604))),
    GenreCategory("Rock", listOf(Color(0xFFE91429), Color(0xFF800A17))),
    GenreCategory("Electronic", listOf(Color(0xFF8D67AB), Color(0xFF4C365C))),
    GenreCategory("Indie", listOf(Color(0xFF1E3264), Color(0xFF0F1932))),
    GenreCategory("R&B", listOf(Color(0xFFDC148C), Color(0xFF7A0B4D))),
    GenreCategory("Chill", listOf(Color(0xFF477D95), Color(0xFF233E4A))),
    GenreCategory("Workout", listOf(Color(0xFF777777), Color(0xFF333333))),
    GenreCategory("Focus", listOf(Color(0xFF503750), Color(0xFF281C28))),
    GenreCategory("Jazz", listOf(Color(0xFF148A08), Color(0xFF0A4504))),
    GenreCategory("Classical", listOf(Color(0xFF8C1932), Color(0xFF460D19))),
    GenreCategory("Party", listOf(Color(0xFFAF2896), Color(0xFF57144B))),
)

/**
 * Android TV Search screen with a search bar, browse genres, recent searches,
 * and categorized horizontal result shelves (Tracks, Artists, Albums, Playlists).
 */
@Composable
fun TvSearchScreen(
    tvViewModel: TvViewModel,
    searchViewModel: SearchViewModel,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results by searchViewModel.results.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    val currentAudio by tvViewModel.currentAudio.collectAsState()
    val isPlaying by tvViewModel.isPlaying.collectAsState()

    val searchBarInteraction = remember { MutableInteractionSource() }
    val searchBarFocused by searchBarInteraction.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Search bar header
        item {
            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                Text(
                    text = stringResource(R.string.search),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        searchViewModel.onQueryChange(it)
                    },
                    interactionSource = searchBarInteraction,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = if (searchBarFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                searchViewModel.onQueryChange("")
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.common_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            text = "Search songs, artists, albums, or playlists…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    ),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(58.dp)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            } else {
                                false
                            }
                        },
                )
            }
        }

        // Empty Query: Show Browse Shelves
        if (query.isBlank()) {
            // Browse All Categories Shelf
            item {
                TvShelf(
                    title = "Browse Categories",
                    subtitle = "Explore popular genres & moods",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        val row1 = BROWSE_GENRES.take(BROWSE_GENRES.size / 2)
                        val row2 = BROWSE_GENRES.drop(BROWSE_GENRES.size / 2)

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            items(row1) { genre ->
                                TvGenreCard(
                                    name = genre.name,
                                    gradientColors = genre.gradientColors,
                                    onClick = {
                                        query = genre.name
                                        searchViewModel.onQueryChange(genre.name)
                                    },
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            items(row2) { genre ->
                                TvGenreCard(
                                    name = genre.name,
                                    gradientColors = genre.gradientColors,
                                    onClick = {
                                        query = genre.name
                                        searchViewModel.onQueryChange(genre.name)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Results Mode
            val tracks = results.filterIsInstance<SearchUiModel.TrackItem>()
            val artists = results.filterIsInstance<SearchUiModel.ArtistItem>()
            val albums = results.filterIsInstance<SearchUiModel.AlbumItem>()
            val playlists = results.filterIsInstance<SearchUiModel.PlaylistItem>()
            val isSearching = results.any { it is SearchUiModel.SkeletonItem }

            if (isSearching && tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()) {
                item {
                    TvSkeletonShelf(
                        title = stringResource(R.string.search_section_tracks),
                        isCircle = false,
                    )
                }
                item {
                    TvSkeletonShelf(
                        title = stringResource(R.string.search_section_artists),
                        isCircle = true,
                    )
                }
            } else if (!isSearching && tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No results found for “$query”",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Please check your spelling or try another artist, song, or album title.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                // 1. Songs Shelf
                if (tracks.isNotEmpty()) {
                    item {
                        TvShelf(
                            title = stringResource(R.string.search_section_tracks),
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(tracks) { item ->
                                    val isCurrent = currentAudio?.uri == item.track.uri
                                    TvTrackCard(
                                        track = item.track,
                                        isCurrentAudio = isCurrent,
                                        isPlaying = isPlaying,
                                        onClick = { tvViewModel.playTrack(item.track.toPlayableAudio()) },
                                    )
                                }
                            }
                        }
                    }
                }

                val derivedArtists = if (artists.isNotEmpty()) artists.map { it.artist }
                    else tracks.flatMap { it.track.artists }.distinctBy { it.id }

                val derivedAlbums = if (albums.isNotEmpty()) albums.map { it.album }
                    else tracks.mapNotNull { it.track.album }.distinctBy { it.id }

                // 2. Artists Shelf
                if (derivedArtists.isNotEmpty()) {
                    item {
                        TvShelf(
                            title = stringResource(R.string.search_section_artists),
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(derivedArtists) { artist ->
                                    val imageUrl by produceState<String?>(
                                        initialValue = (artist.getCover(CoverSize.MEDIUM)
                                            ?: artist.covers.firstOrNull())?.let { ALBUM_COVER_URL + it.uri },
                                        key1 = artist.uri
                                    ) {
                                        if (value == null && artist.uri.startsWith("spotify:artist:")) {
                                            try {
                                                val fullArtist = withContext(Dispatchers.IO) {
                                                    searchViewModel.metadata.getArtistMetadata(artist.uri)
                                                }
                                                val cover = fullArtist?.getCover(CoverSize.MEDIUM)
                                                    ?: fullArtist?.covers?.firstOrNull()
                                                if (cover != null) {
                                                    value = ALBUM_COVER_URL + cover.uri
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }

                                    TvArtistCard(
                                        name = artist.name,
                                        imageUrl = imageUrl,
                                        subtitle = "Artist",
                                        onClick = { tvViewModel.playUri(artist.uri) },
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Albums Shelf
                if (derivedAlbums.isNotEmpty()) {
                    item {
                        TvShelf(
                            title = stringResource(R.string.search_section_albums),
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(derivedAlbums) { album ->
                                    TvAlbumCard(
                                        album = album,
                                        onClick = { tvViewModel.playUri(album.uri) },
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Playlists Shelf
                if (playlists.isNotEmpty()) {
                    item {
                        TvShelf(
                            title = stringResource(R.string.search_section_playlists),
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(playlists) { item ->
                                    val playlist = item.playlist
                                    val artworkUrl by produceState<String?>(initialValue = null, playlist.uri) {
                                        value = if (playlist.attributes.pictureId.isNotEmpty()) {
                                            ALBUM_COVER_URL + playlist.attributes.pictureId
                                        } else {
                                            searchViewModel.getArtworkUrl(playlist)
                                        }
                                    }

                                    TvPlaylistCard(
                                        playlist = playlist,
                                        artworkUrl = artworkUrl,
                                        onClick = { tvViewModel.playUri(playlist.uri) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
