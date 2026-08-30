package com.typezero.siphon.engine

/** User-facing extraction failures without leaking source URLs or private paths. */
object ExtractorErrorMapper {
    fun friendly(raw: String?): String {
        val msg = raw ?: "Unknown error"
        return when {
            msg.contains("HTTP Error 403", true) || msg.contains("403: Forbidden", true) ->
                "The site rejected this request (HTTP 403). Update the extractor first, then retry; protected sources may also need fresh cookies."
            msg.contains("Unsupported URL", true) ->
                "That link isn't supported."
            msg.contains("Video unavailable", true) ->
                "The source is unavailable or private."
            msg.contains("No such file", true) ->
                "The selected file could not be read."
            msg.contains("Requested format", true) ->
                "No matching audio stream is available for that format."
            msg.contains("Sign in to confirm", true) ->
                "The site requires fresh sign-in cookies for this source."
            else -> msg.lineSequence().lastOrNull { it.isNotBlank() } ?: msg
        }
    }
}
