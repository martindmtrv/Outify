package cc.tomko.outify.core.spirc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import cc.tomko.outify.playback.PlaybackStateHolder
import cc.tomko.outify.utils.isTelevision
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class VolumeController @Inject constructor(
    private val spirc: SpircWrapper,
    private val playbackStateHolder: PlaybackStateHolder,
    @ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * On TV, Connect volume drives the device's media volume instead of scaling
     * the app's audio, unless the device keeps that volume fixed (e.g. audio
     * routed to an external amplifier).
     */
    val usesSystemVolume: Boolean = context.isTelevision() && !audioManager.isVolumeFixed

    /** When Connect last changed the volume, see [startSystemVolumeSync]. */
    @Volatile
    private var lastRemoteChangeAt = 0L
    private var systemVolumeReceiver: BroadcastReceiver? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var syncJob: Job? = null

    companion object {
        const val SPOTIFY_MAX_VOLUME = 65535

        // Sent by the system for every stream volume change; not in the public SDK.
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        private const val REMOTE_QUIET_MS = 1_500L
        private const val SYSTEM_REPORT_DEBOUNCE_MS = 400L
    }

    fun start() {
        val initial = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val spotifyVolume = (initial.toDouble() / max * SPOTIFY_MAX_VOLUME).toInt()
        playbackStateHolder.setVolume(spotifyVolume)
    }

    fun onRemoteVolumeChanged(spotifyVolume: Int) {
        playbackStateHolder.setVolume(spotifyVolume)

        if (!usesSystemVolume && !playbackStateHolder.state.value.isPlaying) return

        val androidVolume = mapSpotifyToAndroid(spotifyVolume)
        if (usesSystemVolume) {
            lastRemoteChangeAt = SystemClock.elapsedRealtime()
            syncJob?.cancel()
        }
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != androidVolume) {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                androidVolume,
                0,
            )
        }
    }

    fun onAndroidVolumeChanged() {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val spotifyVolume =
            (current.toDouble() / max * SPOTIFY_MAX_VOLUME).toInt().coerceIn(0, SPOTIFY_MAX_VOLUME)

        playbackStateHolder.setVolume(spotifyVolume)

        if (!playbackStateHolder.state.value.isPlaying) return

        syncJob?.cancel()
        syncJob = scope.launch {
            delay(50)
            spirc.setVolume(spotifyVolume)
        }
    }

    /**
     * Reports the system media volume to Connect now and whenever it changes,
     * e.g. from the TV remote. Only used with [usesSystemVolume].
     */
    fun startSystemVolumeSync() {
        if (!usesSystemVolume) return

        pushSystemVolume()
        if (systemVolumeReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return
                // Changes we applied, and late echoes of them from an external
                // amplifier, must not be reported back as a new volume.
                if (SystemClock.elapsedRealtime() - lastRemoteChangeAt < REMOTE_QUIET_MS) return

                // One report per burst of remote-control key presses.
                syncJob?.cancel()
                syncJob = scope.launch {
                    delay(SYSTEM_REPORT_DEBOUNCE_MS)
                    pushSystemVolume()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(VOLUME_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        systemVolumeReceiver = receiver
    }

    private fun pushSystemVolume() {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        // Rounded both ways so a round trip through Connect lands on the same step.
        val spotifyVolume =
            (current.toDouble() / max * SPOTIFY_MAX_VOLUME).roundToInt().coerceIn(0, SPOTIFY_MAX_VOLUME)

        playbackStateHolder.setVolume(spotifyVolume)
        spirc.setVolume(spotifyVolume)
    }

    private fun mapSpotifyToAndroid(spotifyVolume: Int): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return (spotifyVolume.toDouble() / SPOTIFY_MAX_VOLUME * max).roundToInt().coerceIn(0, max)
    }
}
