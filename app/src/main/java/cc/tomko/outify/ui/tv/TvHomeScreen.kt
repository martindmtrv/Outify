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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.tomko.outify.ui.viewmodel.TvHomeCard
import cc.tomko.outify.ui.viewmodel.TvHomeViewModel
import cc.tomko.outify.ui.viewmodel.TvViewModel

/**
 * Android TV Home: recently played, your playlists (with Liked Songs) and saved albums,
 * all from Spotify's internal services so a Connect login is enough.
 */
@Composable
fun TvHomeScreen(
    tvViewModel: TvViewModel,
    homeViewModel: TvHomeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val username by homeViewModel.username.collectAsState(initial = null)
    val currentAudio by tvViewModel.currentAudio.collectAsState()
    val isPlaying by tvViewModel.isPlaying.collectAsState()
    val deviceName by tvViewModel.deviceName.collectAsState()

    // Without an initially focused element the first OK press on the remote does nothing.
    val firstFocus = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }

    val shelves = listOf(
        "Recently played" to uiState.recentlyPlayed,
        "Your playlists" to uiState.playlists?.takeIf { uiState.likedSongsCount != null },
        "Saved albums" to uiState.savedAlbums,
    )
    val likedSongsCount = uiState.likedSongsCount ?: 0
    fun hasCards(title: String, cards: List<TvHomeCard>?) =
        cards != null && (cards.isNotEmpty() || (title == "Your playlists" && likedSongsCount > 0))
    // The first card on screen takes the initial focus, whichever shelf loads first.
    val focusShelf = shelves.firstOrNull { (title, cards) -> hasCards(title, cards) }?.first

    LaunchedEffect(Unit) {
        homeViewModel.refresh()
    }
    LaunchedEffect(focusShelf != null || uiState.isEmpty) {
        if ((focusShelf == null && !uiState.isEmpty) || focusedOnce) return@LaunchedEffect
        // Lazy rows compose their items during layout, after this effect first runs.
        repeat(10) {
            withFrameNanos { }
            if (runCatching { firstFocus.requestFocus() }.isSuccess) {
                focusedOnce = true
                return@LaunchedEffect
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Text(
                text = if (username != null) "Welcome back, $username" else "Home",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        if (uiState.isEmpty) {
            item {
                if (username != null) {
                    TvConnectedHomeCard(
                        username = username ?: "",
                        currentAudio = currentAudio,
                        isPlaying = isPlaying,
                        onOpenNowPlaying = { tvViewModel.selectTab(TvNavTab.NOW_PLAYING) },
                        onSearchClick = { tvViewModel.selectTab(TvNavTab.SEARCH) },
                        primaryFocus = firstFocus,
                    )
                } else {
                    TvEmptyHomeCard(
                        deviceName = deviceName,
                        onConnectClick = { tvViewModel.selectTab(TvNavTab.NOW_PLAYING) },
                        primaryFocus = firstFocus,
                    )
                }
            }
        }

        shelves.forEach { (title, cards) ->
            when {
                cards == null -> item(key = title) {
                    TvSkeletonShelf(title = title, isCircle = false)
                }

                hasCards(title, cards) -> item(key = title) {
                    TvShelf(title = title) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            val withLikedSongs = title == "Your playlists" && likedSongsCount > 0
                            val takesFocus = title == focusShelf
                            if (withLikedSongs) {
                                item(key = "liked-songs", contentType = "liked") {
                                    TvLikedSongsCard(
                                        count = likedSongsCount,
                                        onClick = { tvViewModel.playLikedSongs() },
                                        modifier = if (takesFocus) Modifier.focusRequester(firstFocus) else Modifier,
                                    )
                                }
                            }
                            itemsIndexed(
                                items = cards,
                                key = { _, card -> card.uri },
                                contentType = { _, card -> card::class },
                            ) { index, card ->
                                val cardModifier = if (takesFocus && index == 0 && !withLikedSongs) {
                                    Modifier.focusRequester(firstFocus)
                                } else {
                                    Modifier
                                }
                                TvHomeCardItem(
                                    card = card,
                                    onClick = { tvViewModel.playUri(card.uri) },
                                    modifier = cardModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvHomeCardItem(card: TvHomeCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    when (card) {
        is TvHomeCard.PlaylistCard -> TvPlaylistCard(
            playlist = card.playlist,
            artworkUrl = card.artworkUrl,
            onClick = onClick,
            modifier = modifier,
        )
        is TvHomeCard.AlbumCard -> TvAlbumCard(album = card.album, onClick = onClick, modifier = modifier)
        is TvHomeCard.ArtistCard -> TvArtistCard(
            name = card.name,
            imageUrl = card.imageUrl,
            onClick = onClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun TvEmptyHomeCard(
    deviceName: String,
    onConnectClick: () -> Unit,
    primaryFocus: FocusRequester,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Tv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Connect with Spotify",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Open the Spotify app on your phone, tablet, or computer and select “$deviceName” to bring your playlists, saved albums and listening history here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Spacer(Modifier.height(24.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            Button(
                onClick = onConnectClick,
                interactionSource = interactionSource,
                modifier = Modifier.focusRequester(primaryFocus),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onPrimary,
                ),
                border = BorderStroke(
                    width = 2.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                ),
            ) {
                Text(
                    text = "Open Spotify Connect Screen",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TvConnectedHomeCard(
    username: String,
    currentAudio: cc.tomko.outify.core.model.PlayableAudio?,
    isPlaying: Boolean,
    onOpenNowPlaying: () -> Unit,
    onSearchClick: () -> Unit,
    primaryFocus: FocusRequester,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Welcome, $username",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            val artistStr = currentAudio?.artists?.joinToString(", ") { it.name }
                ?: currentAudio?.showName.orEmpty()
            Text(
                text = if (currentAudio != null) {
                    if (artistStr.isNotEmpty()) "Now playing: ${currentAudio.name} • $artistStr" else "Now playing: ${currentAudio.name}"
                } else {
                    "Search for songs, artists, and albums, or play any track from Spotify on your phone."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.75f),
            )
            Spacer(Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val searchInteraction = remember { MutableInteractionSource() }
                val searchFocused by searchInteraction.collectIsFocusedAsState()

                Button(
                    onClick = onSearchClick,
                    interactionSource = searchInteraction,
                    modifier = Modifier.focusRequester(primaryFocus),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (searchFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (searchFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = BorderStroke(
                        width = 2.dp,
                        color = if (searchFocused) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                    ),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Search Music",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                val playerInteraction = remember { MutableInteractionSource() }
                val playerFocused by playerInteraction.collectIsFocusedAsState()

                OutlinedButton(
                    onClick = onOpenNowPlaying,
                    interactionSource = playerInteraction,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(
                        width = 2.dp,
                        color = if (playerFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (playerFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (playerFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.Tv,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (currentAudio != null) "View Player" else "Spotify Connect",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

