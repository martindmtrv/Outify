package cc.tomko.outify.ui.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

            // No screensaver over the now-playing screen while music plays.
            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            OutifyTheme(audio = audio, darkTheme = true) {
                TvScreen(viewModel)
            }
        }
    }
}
