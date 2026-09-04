package com.roomcheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
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
        // Sync whenever the app comes to the front. A check written on the website an hour ago
        // should be on the phone by the time it is opened, without anyone pressing anything.
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) { vm.syncNow() }
        })
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
