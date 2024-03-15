package com.aitsuki.android.awsfaceliveness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.aitsuki.android.awsfaceliveness.ui.theme.AwsfacelivenessTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AwsfacelivenessTheme {
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                val appState = remember(scope, snackbarHostState) {
                    AppState(
                        coroutineScope = scope,
                        snackbarHostState = snackbarHostState,
                    )
                }

                CompositionLocalProvider(LocalAppState provides appState) {
                    Scaffold {
                        AppNavGraph(
                            modifier = Modifier
                                .padding(it)
                                .fillMaxSize()
                        )
                        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(it))
                    }
                }
            }
        }
    }
}