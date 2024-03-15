package com.aitsuki.android.awsfaceliveness.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aitsuki.android.awsfaceliveness.LocalAppState
import com.aitsuki.android.awsfaceliveness.LocalNavController
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
fun StartScreen() {
    Box(
        modifier = Modifier.padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val appState = LocalAppState.current
        val navController = LocalNavController.current
        val scope = rememberCoroutineScope()
        var isLoading by remember { mutableStateOf(false) }
        var hostUrl by remember { mutableStateOf("http://192.168.1.1:8080") }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(56.dp))
            } else {
                Spacer(modifier = Modifier.size(56.dp))
            }
            Spacer(modifier = Modifier.height(32.dp))
            TextField(
                value = hostUrl,
                onValueChange = { hostUrl = it },
                label = { Text(text = "Your server host url") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (isLoading) return@Button
                    val httpUrl = hostUrl.toHttpUrlOrNull()
                    if (httpUrl == null) {
                        appState.showSnackbar("Please input a valid url")
                    } else {
                        appState.setBaseUrl(httpUrl)
                        scope.launch {
                            isLoading = true
                            val result = appState.checkServer()
                            isLoading = false
                            result.onSuccess {
                                navController.navigate("/detection")
                            }
                            result.onFailure {
                                appState.showSnackbar(it)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Link start")
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}