package cc.tomko.outify.ui.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cc.tomko.outify.core.spirc.SpircState
import cc.tomko.outify.ui.OutifyTheme
import cc.tomko.outify.ui.viewmodel.TvViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point on Android TV: a Spotify Connect receiver that other Spotify
 * clients pick and control.
 */
@AndroidEntryPoint
class TvActivity : ComponentActivity() {
    private val viewModel: TvViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keeps the device discoverable for as long as the receiver lives,
        // including while a screensaver or another app covers it.
        viewModel.startDiscovery()

        setContent {
            val audio by viewModel.currentAudio.collectAsState()
            val isPlaying by viewModel.isPlaying.collectAsState()
            val spircState by viewModel.spircState.collectAsState()

            // No screensaver over the now-playing screen while music plays.
            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            OutifyTheme(audio = audio, darkTheme = true) {
                if (spircState == SpircState.Stopped || spircState == SpircState.Starting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    TvScreen(viewModel)
                }
            }
        }
    }
}
