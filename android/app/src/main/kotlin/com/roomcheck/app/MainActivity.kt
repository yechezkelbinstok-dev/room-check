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
        setContent {
            RoomCheckTheme {
                val state by vm.state.collectAsState()
                when (state.tab) {
                    Tab.CHECK -> CheckScreen(vm)
                    Tab.NAMES -> NamesScreen(vm)
                }
            }
        }
    }
}
