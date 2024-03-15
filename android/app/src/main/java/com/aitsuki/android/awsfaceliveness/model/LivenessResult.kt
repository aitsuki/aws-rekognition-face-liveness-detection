package com.aitsuki.android.awsfaceliveness.model

data class LivenessResult(
    val status: String,
    val confidence: Float?,
    val referenceImage: String?,
)