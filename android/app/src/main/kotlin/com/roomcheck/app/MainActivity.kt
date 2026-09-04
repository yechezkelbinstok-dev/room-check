package com.roomcheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.NightStore
import com.roomcheck.app.data.Tab
import com.roomcheck.app.ui.CheckScreen
import com.roomcheck.app.ui.NamesScreen
import com.roomcheck.app.ui.RoomCheckTheme
import com.roomcheck.app.ui.SettingsScreen
import com.roomcheck.app.ui.TimesScreen

class AppViewModelFactory(private val store: NightStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return AppViewModel(store) as T
    }
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels { AppViewModelFactory(NightStore(applicationContext)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sync whenever the app comes to the front, then keep pulling while it stays there, so two
        // people marking at the same time see each other's rooms rather than finding out at the
        // end. repeatOnLifecycle cancels the whole loop the moment the app leaves the screen -
        // nothing ticks in your pocket, which is what keeps this costing nothing.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    vm.syncNow()
                    delay(AppViewModel.FOREGROUND_SYNC_MS)
                }
            }
        }
        setContent {
            RoomCheckTheme {
                val state by vm.state.collectAsState()
                when (state.tab) {
                    Tab.CHECK -> CheckScreen(vm)
                    Tab.NAMES -> NamesScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                    Tab.TIMES -> TimesScreen(vm)
                }
            }
        }
    }
}
