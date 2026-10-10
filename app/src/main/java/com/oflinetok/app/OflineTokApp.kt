package com.oflinetok.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class OflineTokApp : Application(), ImageLoaderFactory {
    // Lets Coil render video frames as thumbnails.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
