package com.pulsetime.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavHostState
import com.pulsetime.PulseController
import com.pulsetime.data.PulseRepository
import com.pulsetime.data.PulseSettings
import com.pulsetime.presentation.screens.HomeScreen
import com.pulsetime.presentation.screens.QuietHoursScreen
import com.pulsetime.presentation.screens.SettingsScreen
import com.pulsetime.presentation.screens.TimePickerScreen
import com.pulsetime.presentation.theme.PulseTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PulseTheme { PulseApp() } }
    }
}

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val QUIET = "quiet"
    const val PICK_START = "pick_start"
    const val PICK_END = "pick_end"
}

/**
 * Navigation depth: Home -> (Settings | Quiet Hours) -> Time picker. Max 2 levels below Home.
 * The shared SwipeToDismissBoxState lets the picker screen use edgeSwipeToDismiss, so
 * swipe-back only triggers from the screen edge and never fights the pickers.
 */
@Composable
fun PulseApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsFlow = remember { PulseRepository.flow(context) }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = PulseSettings())

    val swipeState = rememberSwipeToDismissBoxState()
    val navController = rememberSwipeDismissableNavController()

    fun update(markCustom: Boolean = false, t: (PulseSettings) -> PulseSettings) {
        scope.launch { PulseController.updateSettings(context, markCustom, t) }
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.HOME,
        state = rememberSwipeDismissableNavHostState(swipeState)
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                settings = settings,
                onToggle = { scope.launch { PulseController.toggle(context) } },
                onPreset = { preset -> scope.launch { PulseController.start(context, preset) } },
                onSnooze = { scope.launch { PulseController.toggleSnooze(context) } },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenQuiet = { navController.navigate(Routes.QUIET) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(settings = settings, onUpdate = { custom, t -> update(custom, t) })
        }
        composable(Routes.QUIET) {
            QuietHoursScreen(
                settings = settings,
                onToggle = { on -> update { it.copy(quietEnabled = on) } },
                onEditStart = { navController.navigate(Routes.PICK_START) },
                onEditEnd = { navController.navigate(Routes.PICK_END) }
            )
        }
        composable(Routes.PICK_START) {
            TimePickerScreen(
                title = "Quiet starts",
                initialMinuteOfDay = settings.quietStartMinute,
                swipeState = swipeState,
                onConfirm = { m ->
                    update { it.copy(quietStartMinute = m) }
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.PICK_END) {
            TimePickerScreen(
                title = "Quiet ends",
                initialMinuteOfDay = settings.quietEndMinute,
                swipeState = swipeState,
                onConfirm = { m ->
                    update { it.copy(quietEndMinute = m) }
                    navController.popBackStack()
                }
            )
        }
    }
}
