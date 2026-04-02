package org.role.samples_button

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.role.samples_button.core.designsystem.SamplesButtonTheme
import org.role.samples_button.feature.soundboard.impl.SoundBoardScreen
import org.role.samples_button.feature.soundboard.impl.SoundBoardViewModel

@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {
    private val viewModel: SoundBoardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamplesButtonTheme {
                SoundBoardScreen(viewModel = viewModel)
            }
        }
    }
}
