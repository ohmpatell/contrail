package com.contrail.contrail.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * A logostream logo. Its free plan forbids caching logo assets, so the image is never written
 * to disk (Coil's disk cache is off and the request asks OkHttp not to store it). It is kept
 * in memory only while the app is running.
 */
@Composable
fun LiveLogo(url: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    if (url == null) return
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .diskCachePolicy(CachePolicy.DISABLED)
            .httpHeaders(NetworkHeaders.Builder().set("Cache-Control", "no-store").build())
            .build()
    }
    AsyncImage(model = request, contentDescription = contentDescription, contentScale = ContentScale.Fit, modifier = modifier)
}
