package cc.tomko.outify.ui.viewmodel

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.tomko.outify.core.AuthManager
import cc.tomko.outify.core.AuthStateEventBus
import cc.tomko.outify.core.Discovery
import cc.tomko.outify.core.DiscoveryCallback
import cc.tomko.outify.core.model.PlayableAudio
import cc.tomko.outify.core.spirc.SpircController
import cc.tomko.outify.core.spirc.SpircState
import cc.tomko.outify.core.spirc.SpircWrapper
import cc.tomko.outify.data.repository.SettingsRepository
import cc.tomko.outify.playback.PlaybackStateHolder
import cc.tomko.outify.playback.model.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "TvViewModel"

/**
 * Drives the TV receiver screen: announces the device while it is shown and
 * mirrors whatever another Spotify client plays on it.
 */
@HiltViewModel
class TvViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val spircController: SpircController,
    private val playbackStateHolder: PlaybackStateHolder,
    val spirc: SpircWrapper,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _discoveryError = MutableStateFlow<String?>(null)
    val discoveryError: StateFlow<String?> = _discoveryError.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    val deviceName: StateFlow<String> = settingsRepository.deviceName
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Outify")

    val spircState: StateFlow<SpircState> = spircController.state

    val currentAudio: StateFlow<PlayableAudio?> = playbackStateHolder.state
        .map { it.currentAudio }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isPlaying: StateFlow<Boolean> = playbackStateHolder.state
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val shuffleEnabled: StateFlow<Boolean> = playbackStateHolder.state
        .map { it.shuffleEnabled }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val repeatMode: StateFlow<RepeatMode> = playbackStateHolder.state
        .map { it.repeatMode }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, RepeatMode.NONE)

    val isActiveDevice: StateFlow<Boolean> = playbackStateHolder.state
        .map { it.isActiveDevice }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Lets mDNS queries from the network reach the app over Wi-Fi. */
    private val multicastLock = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("outify-discovery")
        .apply { setReferenceCounted(false) }

    private var discoveryJob: Job? = null

    init {
        refreshLogin()

        viewModelScope.launch {
            while (isActive) {
                _positionMs.value = playbackStateHolder.estimatePosition().inWholeMilliseconds
                delay(500L)
            }
        }
    }

    /** Starts announcing the device until the receiver screen is closed. */
    fun startDiscovery() {
        if (discoveryJob != null) return

        discoveryJob = viewModelScope.launch {
            // Discovery needs the native runtime, which the application starts asynchronously.
            spircState.first { it != SpircState.Stopped }

            multicastLock.acquire()
            val started = withContext(Dispatchers.IO) {
                Discovery.start(object : DiscoveryCallback {
                    override fun onLoggedIn(username: String) {
                        Log.i(TAG, "logged in via discovery as $username")
                        _discoveryError.value = null
                        refreshLogin()
                        AuthStateEventBus.tryEmitPlaybackLoggedIn()
                    }

                    override fun onFailed(reason: String) {
                        Log.e(TAG, "discovery failed: $reason")
                        _discoveryError.value = reason
                    }
                })
            }

            if (!started) _discoveryError.value = "Could not announce this device on the network"
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        Discovery.stop()
        if (multicastLock.isHeld) multicastLock.release()
    }

    fun playPause() {
        spirc.playerPlayPause()
    }

    fun next() {
        spirc.playerNext()
    }

    fun previous() {
        spirc.playerPrevious()
    }

    fun toggleShuffle() {
        spirc.shuffle(!shuffleEnabled.value)
    }

    /** Cycles off -> all -> one, like the Spotify apps. */
    fun cycleRepeat() {
        val next = repeatMode.value.next()
        spirc.repeat(next.repeat, next.repeatTrack)
    }

    private fun refreshLogin() {
        viewModelScope.launch {
            _isLoggedIn.value = withContext(Dispatchers.IO) { authManager.hasCachedCredentials() }
        }
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }
}
