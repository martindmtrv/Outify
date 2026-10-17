package cc.tomko.outify.ui.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.tomko.outify.ALBUM_COVER_URL
import cc.tomko.outify.core.model.CoverSize
import cc.tomko.outify.core.model.PlayableAudio
import cc.tomko.outify.core.model.getCover
import cc.tomko.outify.core.spirc.SpircState
import cc.tomko.outify.playback.model.RepeatMode
import cc.tomko.outify.ui.viewmodel.TvViewModel
import coil3.compose.AsyncImage

@Composable
fun TvScreen(viewModel: TvViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val spircState by viewModel.spircState.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val audio by viewModel.currentAudio.collectAsState()
    val isActive by viewModel.isActiveDevice.collectAsState()
    val error by viewModel.discoveryError.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Overscan-safe margins recommended for TV layouts.
            .padding(horizontal = 58.dp, vertical = 28.dp),
    ) {
        val nowPlaying = audio?.takeIf { isLoggedIn && isActive }

        AnimatedContent(targetState = nowPlaying, label = "tv-content") { current ->
            if (current != null) {
                NowPlaying(viewModel, current)
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
private fun NowPlaying(viewModel: TvViewModel, audio: PlayableAudio) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val shuffle by viewModel.shuffleEnabled.collectAsState()
    val repeat by viewModel.repeatMode.collectAsState()
    val playPauseFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        playPauseFocus.requestFocus()
    }

    val cover = (audio.getCover(CoverSize.LARGE) ?: audio.covers.firstOrNull())
        ?.let { ALBUM_COVER_URL + it.uri }

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(360.dp)
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
            Spacer(Modifier.height(32.dp))

            val duration = audio.duration.coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { (positionMs.toFloat() / duration).coerceIn(0f, 1f) },
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
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvToggleButton(
                    icon = Icons.Filled.Shuffle,
                    description = "Shuffle",
                    active = shuffle,
                    onClick = viewModel::toggleShuffle,
                )
                TvControlButton(Icons.Filled.SkipPrevious, "Previous", viewModel::previous)
                TvControlButton(
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    description = if (isPlaying) "Pause" else "Play",
                    onClick = viewModel::playPause,
                    modifier = Modifier.focusRequester(playPauseFocus),
                )
                TvControlButton(Icons.Filled.SkipNext, "Next", viewModel::next)
                TvToggleButton(
                    icon = if (repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    description = "Repeat",
                    active = repeat != RepeatMode.NONE,
                    onClick = viewModel::cycleRepeat,
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    FilledIconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = if (focused) {
            IconButtonDefaults.filledIconButtonColors()
        } else {
            IconButtonDefaults.filledTonalIconButtonColors()
        },
        modifier = modifier
            .size(72.dp)
            .border(
                width = 3.dp,
                color = if (focused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background,
                shape = CircleShape,
            ),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(40.dp))
    }
}

/** Smaller secondary control that shows whether its mode is on. */
@Composable
private fun TvToggleButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(56.dp)
            .border(
                width = 3.dp,
                color = if (focused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background,
                shape = CircleShape,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
