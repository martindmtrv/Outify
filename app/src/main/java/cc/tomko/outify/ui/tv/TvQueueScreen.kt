package cc.tomko.outify.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.tomko.outify.ALBUM_COVER_URL
import cc.tomko.outify.R
import cc.tomko.outify.core.model.CoverSize
import cc.tomko.outify.core.model.getCover
import cc.tomko.outify.ui.viewmodel.TvViewModel
import cc.tomko.outify.ui.viewmodel.player.QueueViewModel

/**
 * Android TV Full Screen Queue view accessible directly from the main sidebar navigation rail.
 */
@Composable
fun TvQueueScreen(
    tvViewModel: TvViewModel,
    queueViewModel: QueueViewModel,
    modifier: Modifier = Modifier,
) {
    val queueState by queueViewModel.queueState.collectAsState()
    val currentAudio by tvViewModel.currentAudio.collectAsState()
    val isPlaying by tvViewModel.isPlaying.collectAsState()
    val listState = rememberLazyListState()

    // Remote Back Handler: return to Home
    BackHandler {
        tvViewModel.selectTab(TvNavTab.HOME)
    }

    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(currentAudio) {
        queueViewModel.loadQueue(currentAudio)
        androidx.compose.runtime.withFrameNanos { }
        runCatching { firstItemFocus.requestFocus() }
    }

    // Pagination support
    LaunchedEffect(listState.firstVisibleItemIndex, listState.layoutInfo.visibleItemsInfo.size) {
        val visibleInfo = listState.layoutInfo.visibleItemsInfo
        if (visibleInfo.isNotEmpty()) {
            queueViewModel.onScrollPositionChanged(
                firstVisibleIndex = visibleInfo.first().index,
                lastVisibleIndex = visibleInfo.last().index,
                currentAudio = currentAudio,
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
    ) {
        // Queue Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.common_queue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (queueState.totalSize > 0) {
                Spacer(Modifier.width(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "${queueState.totalSize}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        if (queueState.isLoading && queueState.tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = stringResource(R.string.player_loading_queue),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (queueState.error != null && queueState.tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = queueState.error ?: "Failed to load queue",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (queueState.tracks.isEmpty() && currentAudio == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.player_queue_empty),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val nowPlayingEntry = queueState.tracks.getOrNull(queueState.currentIndex)
            val upcomingTracks = queueState.tracks.drop(queueState.currentIndex + 1)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Now playing section
                if (nowPlayingEntry != null || currentAudio != null) {
                    item(key = "now-playing-header") {
                        Text(
                            text = stringResource(R.string.queue_now_playing),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp),
                        )
                    }

                    val activeAudio = nowPlayingEntry?.audio ?: currentAudio!!
                    item(key = "now-playing-item") {
                        TvQueueTrackRow(
                            audio = activeAudio,
                            isCurrent = true,
                            isPlaying = isPlaying,
                            indexText = null,
                            onClick = { tvViewModel.playPause() },
                            modifier = Modifier.focusRequester(firstItemFocus),
                        )
                    }
                }

                // Next in queue section
                item(key = "next-in-queue-header") {
                    Text(
                        text = stringResource(R.string.queue_next_in_queue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp),
                    )
                }

                if (upcomingTracks.isEmpty()) {
                    item(key = "upcoming-empty") {
                        Text(
                            text = stringResource(R.string.player_queue_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    itemsIndexed(
                        items = upcomingTracks,
                        key = { index, entry -> "${entry.id}_${entry.audio.uri}_$index" },
                    ) { index, entry ->
                        val cover = (entry.audio.getCover(CoverSize.SMALL) ?: entry.audio.covers.firstOrNull())
                            ?.let { ALBUM_COVER_URL + it.uri }

                        TvQueueTrackRow(
                            audio = entry.audio,
                            isCurrent = false,
                            isPlaying = false,
                            indexText = "${index + 1}",
                            onClick = {
                                queueViewModel.playQueueTrack(entry)
                            },
                            onLongClick = {
                                tvViewModel.openContextMenu(
                                    TvContextMenuData(
                                        title = entry.audio.name,
                                        subtitle = entry.audio.artists?.joinToString { it.name } ?: entry.audio.showName,
                                        imageUrl = cover,
                                        onPlay = { queueViewModel.playQueueTrack(entry) },
                                        onPlayNext = { tvViewModel.playNext(entry.audio.uri) },
                                        onAddToQueue = { tvViewModel.addToQueue(entry.audio.uri) },
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
