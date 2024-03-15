package com.aitsuki.android.awsfaceliveness.screen

import android.Manifest
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import com.aitsuki.android.awsfaceliveness.model.LivenessResult
import com.aitsuki.android.awsfaceliveness.model.LivenessSession
import com.aitsuki.android.awsfaceliveness.ui.component.Base64Image
import com.amplifyframework.auth.AWSCredentials
import com.amplifyframework.auth.AWSCredentialsProvider
import com.amplifyframework.auth.AuthException
import com.amplifyframework.core.Consumer
import com.amplifyframework.ui.liveness.ui.FaceLivenessDetector
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DetectionScreen() {
    var isLoading by remember { mutableStateOf(false) }
    val permissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    var session: LivenessSession? by remember { mutableStateOf(null) }
    var livenessResult: LivenessResult? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()
    val appState = LocalAppState.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val text =
                if (livenessResult == null) "" else "Status: ${livenessResult?.status}, Confidence: ${livenessResult?.confidence}"
            Text(text = text)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                livenessResult?.referenceImage?.let { base64Image ->
                    Base64Image(base64 = base64Image, modifier = Modifier.fillMaxSize())
                }
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(56.dp))
                }
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(), onClick = {
                if (isLoading) return@Button
                scope.launch {
                    if (permissionState.status == PermissionStatus.Granted) {
                        isLoading = true
                        val result = appState.createLivenessSession()
                        isLoading = false
                        result.onSuccess {
                            session = it
                        }
                        result.onFailure {
                            appState.showSnackbar(it)
                        }
                    } else {
                        permissionState.launchPermissionRequest()
                    }
                }
            }) {
            Text(text = "Start Liveness Detection")
        }
    }

    session?.let {
        FaceLivenessDetector(
            sessionId = it.sessionId,
            region = it.region,
            credentialsProvider = object : AWSCredentialsProvider<AWSCredentials> {
                override fun fetchAWSCredentials(
                    onSuccess: Consumer<AWSCredentials>,
                    onError: Consumer<AuthException>
                ) {
                    scope.launch {
                        appState.getCredentials().onSuccess { credentials ->
                            onSuccess.accept(credentials)
                        }.onFailure { t ->
                            onError.accept(
                                AuthException(
                                    t.message ?: "Failed to get credentials", "", t
                                )
                            )
                        }
                    }
                }
            },
            onComplete = {
                session = null
                scope.launch {
                    isLoading = true
                    val result = appState.getLivenessResult(it.sessionId)
                    isLoading = false
                    result.onSuccess { r ->
                        livenessResult = r
                    }
                    result.onFailure { t ->
                        livenessResult = null
                        appState.showSnackbar(t)
                    }
                }
            },
            onError = { e ->
                Log.e(
                    "LivenessDetector",
                    "${e.message}, ${e.recoverySuggestion}",
                    e.throwable
                )
                session = null
                livenessResult = null
                appState.showSnackbar(e.message)
            }
        )
    }
}