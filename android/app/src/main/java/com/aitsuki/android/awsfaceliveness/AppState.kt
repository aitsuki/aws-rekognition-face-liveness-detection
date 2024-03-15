package com.aitsuki.android.awsfaceliveness

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import com.aitsuki.android.awsfaceliveness.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

val LocalAppState = staticCompositionLocalOf<AppState> { error("AppState not found!") }

class AppState(
    private val coroutineScope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
) {
    private lateinit var repository: Repository

    fun setBaseUrl(url: HttpUrl) {
        repository = Repository(url)
    }

    fun showSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun showSnackbar(throwable: Throwable) {
        showSnackbar(throwable.message ?: "Unknown error")
    }

    suspend fun checkServer() = repository.checkServer()
    suspend fun getCredentials() = repository.getCredentials()
    suspend fun createLivenessSession() = repository.createLivenessSession()
    suspend fun getLivenessResult(sessionId: String) = repository.getLivenessResult(sessionId)
}