package com.aitsuki.android.awsfaceliveness.data

import com.aitsuki.android.awsfaceliveness.model.Credentials
import com.aitsuki.android.awsfaceliveness.model.LivenessResult
import com.aitsuki.android.awsfaceliveness.model.LivenessSession
import com.amplifyframework.auth.AWSCredentials
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor

class Repository(private val baseUrl: HttpUrl) {

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        .build()

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    suspend fun checkServer(): Result<Unit> {
        return get<Unit>(baseUrl.newBuilder().addPathSegment("health").build())
    }

    suspend fun getCredentials(): Result<AWSCredentials> {
        return get<Credentials>(baseUrl.newBuilder().addPathSegment("credentials").build())
            .mapCatching { it.toAwsCredentials() }
    }

    suspend fun createLivenessSession(): Result<LivenessSession> {
        return get<LivenessSession>(
            baseUrl.newBuilder()
                .addPathSegment("liveness")
                .addPathSegment("session")
                .build()
        )
    }

    suspend fun getLivenessResult(sessionId: String): Result<LivenessResult> {
        return get<LivenessResult>(
            baseUrl.newBuilder()
                .addPathSegment("liveness")
                .addPathSegment("session")
                .addPathSegment(sessionId)
                .build()
        )
    }

    private suspend inline fun <reified T> get(url: HttpUrl): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                val resp = client.newCall(request).execute()
                try {
                    if (resp.isSuccessful) {
                        if (T::class.java == String::class.java) {
                            return@runCatching resp.body.string() as T
                        } else if (T::class.java == Unit::class.java) {
                            return@runCatching Unit as T
                        }
                        return@runCatching gson.fromJson(resp.body.charStream(), T::class.java)
                    } else {
                        // get error body and throw
                        val body = gson.fromJson(resp.body.string(), ErrorBody::class.java)
                        throw ApiException(body.error)
                    }
                } catch (e: Exception) {
                    if (e is ApiException) {
                        throw e
                    }
                    throw RuntimeException("Unknown error", e)
                }
            }
        }

    data class ErrorBody(val error: String)

    class ApiException(message: String) : RuntimeException(message)
}