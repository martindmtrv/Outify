package cc.tomko.outify.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cc.tomko.outify.ALBUM_COVER_URL
import cc.tomko.outify.R
import cc.tomko.outify.core.model.CoverSize
import cc.tomko.outify.core.model.PlayableAudio
import cc.tomko.outify.core.model.getCover
import cc.tomko.outify.core.spirc.SpircState
import cc.tomko.outify.playback.model.RepeatMode
import cc.tomko.outify.ui.components.WavyMusicSlider
import cc.tomko.outify.ui.notifications.InAppNotificationHost
import cc.tomko.outify.ui.viewmodel.SearchViewModel
import cc.tomko.outify.ui.viewmodel.TvHomeViewModel
import cc.tomko.outify.ui.viewmodel.TvViewModel
import cc.tomko.outify.ui.viewmodel.player.QueueViewModel
import coil3.compose.AsyncImage

@Composable
fun TvScreen(
    viewModel: TvViewModel,
    homeViewModel: TvHomeViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
    queueViewModel: QueueViewModel = hiltViewModel(),
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val audio by viewModel.currentAudio.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val contextMenu by viewModel.contextMenu.collectAsState()

    // Remote Back Handler: return to Home if currently on Search, Queue, or Now Playing
    BackHandler(enabled = selectedTab != TvNavTab.HOME) {
        viewModel.selectTab(TvNavTab.HOME)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Left Navigation Rail (Spotify TV Style Sidebar)
            TvSidebar(
                selectedTab = selectedTab,
                onSelectTab = { viewModel.selectTab(it) },
                currentAudio = audio,
                isPlaying = isPlaying,
                onMiniPlayerClick = { viewModel.selectTab(TvNavTab.NOW_PLAYING) },
            )

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 28.dp, top = 24.dp, end = 40.dp, bottom = 24.dp),
            ) {
                when (selectedTab) {
                    TvNavTab.SEARCH -> TvSearchScreen(
                        tvViewModel = viewModel,
                        searchViewModel = searchViewModel,
                    )
                    TvNavTab.HOME -> TvHomeScreen(
                        tvViewModel = viewModel,
                        homeViewModel = homeViewModel,
                    )
                    TvNavTab.NOW_PLAYING -> TvNowPlayingScreen(
                        viewModel = viewModel,
                        queueViewModel = queueViewModel,
                    )
                    TvNavTab.QUEUE -> TvQueueScreen(
                        tvViewModel = viewModel,
                        queueViewModel = queueViewModel,
                    )
                }
            }
        }

        // Global TV Context Menu
        contextMenu?.let { data ->
            TvContextMenuDialog(
                data = data,
                onDismiss = viewModel::dismissContextMenu,
            )
        }

        // Global In-App Notifications on TV
        InAppNotificationHost(
            modifier = Modifier.fillMaxSize(),
            maxWidthFraction = 0.5f,
            hostPaddingBottom = 32.dp,
        )
    }
}

@Composable
fun TvNowPlayingScreen(
    viewModel: TvViewModel,
    queueViewModel: QueueViewModel = hiltViewModel(),
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val spircState by viewModel.spircState.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val audio by viewModel.currentAudio.collectAsState()
    val isActive by viewModel.isActiveDevice.collectAsState()
    val error by viewModel.discoveryError.collectAsState()

    val nowPlaying = audio?.takeIf { isLoggedIn && isActive }

    AnimatedContent(targetState = nowPlaying, label = "tv-content") { current ->
        if (current != null) {
            NowPlaying(
                viewModel = viewModel,
                queueViewModel = queueViewModel,
                audio = current,
            )
        } else {
            PickThisDevice(
                deviceName = deviceName,
                isLoggedIn = isLoggedIn,
                isConnecting = spircState == SpircState.Starting || spircState == SpircState.Rebuilding,
                error = error,
            )
        }
    }
}

@Composable
private fun PickThisDevice(
    deviceName: String,
    isLoggedIn: Boolean,
    isConnecting: Boolean,
    error: String?,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Tv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (isConnecting) "Connecting…" else "Ready to play",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isLoggedIn) {
                "Open Spotify on any device and pick “$deviceName” from the devices list."
            } else {
                "Open Spotify on your phone, connected to the same network, and pick " +
                    "“$deviceName” from the devices list to sign in."
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 720.dp),
        )
        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NowPlaying(
    viewModel: TvViewModel,
    queueViewModel: QueueViewModel,
    audio: PlayableAudio,
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val shuffle by viewModel.shuffleEnabled.collectAsState()
    val repeat by viewModel.repeatMode.collectAsState()
    val playPauseFocus = remember { FocusRequester() }
    val queueButtonFocus = remember { FocusRequester() }

    var showQueue by remember { mutableStateOf(false) }

    // Intercept remote back to dismiss queue panel first
    BackHandler(enabled = showQueue) {
        showQueue = false
    }

    LaunchedEffect(Unit) {
        playPauseFocus.requestFocus()
    }

    // Refresh queue when opened or current audio changes
    LaunchedEffect(showQueue, audio) {
        if (showQueue) {
            queueViewModel.loadQueue(audio)
        }
    }

    var hasOpenedQueue by remember { mutableStateOf(false) }
    LaunchedEffect(showQueue) {
        if (showQueue) {
            hasOpenedQueue = true
        } else if (hasOpenedQueue) {
            runCatching { queueButtonFocus.requestFocus() }
        }
    }

    val cover = (audio.getCover(CoverSize.LARGE) ?: audio.covers.firstOrNull())
        ?.let { ALBUM_COVER_URL + it.uri }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Leaves the controls row its full width beside the 108dp rail on a 960dp-wide (1080p) TV.
                modifier = Modifier
                    .size(320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.name,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = audio.artists?.joinToString { it.name } ?: audio.showName.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(20.dp))

                val duration = audio.duration.coerceAtLeast(1)
                WavyMusicSlider(
                    value = (positionMs.toFloat() / duration).coerceIn(0f, 1f),
                    onValueChange = { frac ->
                        viewModel.seekTo((frac * duration).toLong())
                    },
                    isPlaying = isPlaying,
                    enabled = false,
                    trackHeight = 8.dp,
                    thumbRadius = 10.dp,
                    waveAmplitudeWhenPlaying = 4.dp,
                    waveLength = 90.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(formatTime(positionMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(audio.duration), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvToggleButton(
                        icon = Icons.Filled.Shuffle,
                        description = "Shuffle",
                        active = shuffle,
                        onClick = viewModel::toggleShuffle,
                    )
                    TvControlButton(
                        icon = Icons.Filled.SkipPrevious,
                        description = "Previous",
                        onClick = viewModel::previous,
                        size = 56.dp,
                        iconSize = 32.dp,
                    )
                    TvControlButton(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        description = if (isPlaying) "Pause" else "Play",
                        onClick = viewModel::playPause,
                        modifier = Modifier.focusRequester(playPauseFocus),
                        size = 64.dp,
                        iconSize = 36.dp,
                    )
                    TvControlButton(
                        icon = Icons.Filled.SkipNext,
                        description = "Next",
                        onClick = viewModel::next,
                        size = 56.dp,
                        iconSize = 32.dp,
                    )
                    TvToggleButton(
                        icon = if (repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        description = "Repeat",
                        active = repeat != RepeatMode.NONE,
                        onClick = viewModel::cycleRepeat,
                    )
                    TvToggleButton(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        description = stringResource(R.string.common_queue),
                        active = showQueue,
                        onClick = { showQueue = !showQueue },
                        modifier = Modifier.focusRequester(queueButtonFocus),
                    )
                }
            }
        }

        // Side panel overlay for viewing the queue
        AnimatedVisibility(
            visible = showQueue,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { showQueue = false },
                contentAlignment = Alignment.CenterEnd,
            ) {
                TvQueuePanel(
                    viewModel = viewModel,
                    queueViewModel = queueViewModel,
                    onClose = { showQueue = false },
                    modifier = Modifier.clickable(enabled = false) {},
                )
            }
        }
    }
}

@Composable
private fun TvControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    iconSize: androidx.compose.ui.unit.Dp = 36.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    FilledIconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (focused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (focused) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        modifier = modifier
            .size(size)
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape,
            ),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(iconSize))
    }
}

/** Smaller secondary control that shows whether its mode is on. */
@Composable
private fun TvToggleButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    FilledIconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = when {
                focused -> MaterialTheme.colorScheme.primaryContainer
                active -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            },
            contentColor = when {
                focused -> MaterialTheme.colorScheme.onPrimaryContainer
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        modifier = modifier
            .size(size)
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
