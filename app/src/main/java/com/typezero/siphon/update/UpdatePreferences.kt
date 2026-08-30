package com.typezero.siphon.update

import android.content.Context
import com.typezero.siphon.BuildConfig

class UpdatePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)

    fun channel(): UpdateChannel {
        val default = if (BuildConfig.VERSION_NAME.contains("-dev")) {
            UpdateChannel.DEVELOPMENT
        } else {
            UpdateChannel.STABLE
        }
        return prefs.getString(KEY_CHANNEL, null)
            ?.let { runCatching { UpdateChannel.fromWire(it) }.getOrNull() }
            ?: default
    }

    fun setChannel(channel: UpdateChannel) {
        prefs.edit().putString(KEY_CHANNEL, channel.wire).apply()
    }

    companion object { private const val KEY_CHANNEL = "channel" }
}
