package com.fap.app

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.fap.app.di.Graph

public class FapApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }

    /**
     * Coil's default loader renders a GIF as a single still frame. Registering
     * the animated decoders from coil-gif makes GIFs (and animated WebP on
     * API 28+) animate in place everywhere AsyncImage is used, the viewer
     * included. ImageDecoderDecoder needs API 28; GifDecoder covers the rest
     * down to minSdk 26.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
