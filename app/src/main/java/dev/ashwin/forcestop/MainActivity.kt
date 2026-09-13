package dev.ashwin.forcestop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.ashwin.forcestop.ui.AppListScreen
import dev.ashwin.forcestop.ui.ForceStopTheme
import dev.ashwin.forcestop.ui.ForceStopViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ForceStopViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ForceStopTheme {
                AppListScreen(viewModel)
            }
        }
    }
}
