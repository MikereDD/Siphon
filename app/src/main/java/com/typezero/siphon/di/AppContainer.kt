package com.typezero.siphon.di

import android.content.Context
import com.typezero.siphon.data.MediaStoreVideoRepository
import com.typezero.siphon.engine.CookieStore
import com.typezero.siphon.engine.LocalFfmpegExtractor
import com.typezero.siphon.engine.OutputResolver
import com.typezero.siphon.engine.YtdlpEngine

/** Manual application-scoped dependency container. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val videoRepository by lazy { MediaStoreVideoRepository(appContext) }
    val outputResolver by lazy { OutputResolver(appContext) }
    val engine by lazy { YtdlpEngine(appContext) }
    val localExtractor by lazy { LocalFfmpegExtractor(appContext, engine) }
    val cookieStore by lazy { CookieStore(appContext) }
}
