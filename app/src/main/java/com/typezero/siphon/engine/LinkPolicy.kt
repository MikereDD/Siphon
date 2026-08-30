package com.typezero.siphon.engine

/** Siphon 0.3 still intentionally processes one remote media item per job. */
object LinkPolicy {
    fun collectionReason(url: String): String? {
        val u = url.lowercase()
        return when {
            "youtube.com/playlist" in u ->
                "Playlist extraction is not enabled yet. Paste an individual video URL."
            "youtube.com/channel/" in u || Regex("""youtube\.com/@[^/?#]+/?(?:\?.*)?$""").containsMatchIn(u) ->
                "Channel extraction is not enabled yet. Paste an individual video URL."
            else -> null
        }
    }
}
