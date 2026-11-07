package com.typezero.siphon.engine

import java.net.URI

/** Siphon 0.3 still intentionally processes one remote media item per job. */
object LinkPolicy {
    fun extractUrl(text: String): String? {
        val match = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).find(text) ?: return null
        val opener = text.getOrNull(match.range.first - 1)
        val afterMatch = match.range.last + 1
        val insideAngleBrackets = opener == '<' && text.getOrNull(afterMatch) == '>'
        val hasSurroundingText = text.take(match.range.first).isNotBlank() ||
            text.drop(afterMatch).isNotBlank()
        val candidate = if (insideAngleBrackets || !hasSurroundingText) {
            match.value
        } else {
            match.value
                .trimEnd('.', ',')
                .removeClosingWrapper(opener)
        }
        return candidate.takeIf { runCatching { URI(it).host != null }.getOrDefault(false) }
    }

    private fun String.removeClosingWrapper(opener: Char?): String {
        val closer = when (opener) {
            '(' -> ')'
            '[' -> ']'
            '{' -> '}'
            '"' -> '"'
            '\'' -> '\''
            else -> return this
        }
        return if (endsWith(closer)) dropLast(1) else this
    }

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
