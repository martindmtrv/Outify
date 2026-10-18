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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.tomko.outify.R
import cc.tomko.outify.core.model.toPlayableAudio
import cc.tomko.outify.ui.viewmodel.HomeUiState
import cc.tomko.outify.ui.viewmodel.HomeViewModel
import cc.tomko.outify.ui.viewmodel.TopItemsDuration
import cc.tomko.outify.ui.viewmodel.TvViewModel

/**
 * Android TV Home screen displaying top artists, top tracks, and recommendations
 * with a 10-foot D-pad remote-optimized UI.
 */
@Composable
fun TvHomeScreen(
    tvViewModel: TvViewModel,
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val username by homeViewModel.username.collectAsState(initial = null)
    val selectedDuration by homeViewModel.selectedDuration.collectAsState()
    val currentAudio by tvViewModel.currentAudio.collectAsState()
    val isPlaying by tvViewModel.isPlaying.collectAsState()
    val deviceName by tvViewModel.deviceName.collectAsState()

    LaunchedEffect(Unit) {
        homeViewModel.refreshPlaybackLoginState()
        homeViewModel.loadData()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Top Header
        item {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    text = if (username != null) "Welcome back, $username" else "Home",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(Modifier.height(14.dp))

                // Time Duration Filter Chips (Last 4 weeks, Last 6 months, Last year)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopItemsDuration.entries.forEach { duration ->
                        val isSelected = selectedDuration == duration
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        Surface(
                            onClick = { homeViewModel.setDuration(duration) },
                            interactionSource = interactionSource,
                            shape = RoundedCornerShape(20.dp),
                            color = when {
                                isFocused -> MaterialTheme.colorScheme.primary
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                            border = BorderStroke(
                                width = 2.dp,
                                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else Color.Transparent,
                            ),
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = duration.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isFocused -> MaterialTheme.colorScheme.onPrimary
                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        // Content Area according to HomeUiState
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                item {
                    TvSkeletonShelf(
                        title = stringResource(R.string.homescreen_top_artists),
                        isCircle = true,
                    )
                }
                item {
                    TvSkeletonShelf(
                        title = stringResource(R.string.homescreen_top_tracks),
                        isCircle = false,
                    )
                }
            }

            is HomeUiState.Success -> {
                // Top Artists Shelf
                if (state.topArtists.isNotEmpty()) {
                    item {
                        TvShelf(
                            title = stringResource(R.string.homescreen_top_artists),
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(state.topArtists) { artist ->
                                    TvArtistCard(
                                        name = artist.name,
                                        imageUrl = artist.imageUrl,
                                        subtitle = "#${artist.rank} Artist",
                                        onClick = { tvViewModel.playUri(artist.uri) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Top Tracks Shelf
                if (state.topTracks.isNotEmpty()) {
                    item {
                        TvShelf(
                            title = stringResource(R.string.homescreen_top_tracks),
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(state.topTracks) { track ->
                                    val isCurrent = currentAudio?.uri == track.uri
                                    TvTrackCard(
                                        track = track,
                                        isCurrentAudio = isCurrent,
                                        isPlaying = isPlaying,
                                        onClick = { tvViewModel.playTrack(track.toPlayableAudio()) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.topArtists.isEmpty() && state.topTracks.isEmpty()) {
                    item {
                        TvEmptyHomeCard(
                            deviceName = deviceName,
                            onConnectClick = { tvViewModel.selectTab(TvNavTab.NOW_PLAYING) },
                        )
                    }
                }
            }

            is HomeUiState.NotAuthenticated, is HomeUiState.EmptyResult -> {
                item {
                    TvEmptyHomeCard(
                        deviceName = deviceName,
                        onConnectClick = { tvViewModel.selectTab(TvNavTab.NOW_PLAYING) },
                    )
                }
            }

            is HomeUiState.Error -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { homeViewModel.loadData() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvEmptyHomeCard(
    deviceName: String,
    onConnectClick: () -> Unit,
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
                text = "Open the Spotify app on your phone, tablet, or computer and select “$deviceName” to sync your listening history, top artists, and tracks here.",
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
