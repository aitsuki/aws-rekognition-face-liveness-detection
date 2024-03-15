package com.aitsuki.android.awsfaceliveness.ui.component

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun Base64Image(
    modifier: Modifier = Modifier,
    base64: String,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var isLoading by remember { mutableStateOf(false) }
    var bitmapPainter by remember(base64) { mutableStateOf<BitmapPainter?>(null) }

    LaunchedEffect(key1 = base64) {
        isLoading = true
        runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bitmapPainter = BitmapPainter(bitmap.asImageBitmap())
        }
        isLoading = false
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        bitmapPainter?.let {
            Image(
                painter = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(56.dp))
        }
    }
}