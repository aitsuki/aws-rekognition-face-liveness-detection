package com.aitsuki.android.awsfaceliveness.model

import com.amplifyframework.auth.AWSCredentials
import java.time.Instant

data class Credentials(
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String,
    val expiration: String
) {
    fun toAwsCredentials(): AWSCredentials {
        return AWSCredentials.createAWSCredentials(
            accessKeyId,
            secretAccessKey,
            sessionToken,
            Instant.parse(expiration).epochSecond,
        )!!
    }
}