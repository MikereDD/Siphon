package com.typezero.siphon.engine

/**
 * Maps engine-local progress into whole-job progress.
 *
 * Extraction is not complete when yt-dlp/FFmpeg reports 100%: tagging,
 * post-processing and MediaStore export can still be running. Engine progress
 * is therefore capped below the final save stage.
 */
object ProgressMapper {
    data class Mapped(val percent: Int, val stage: String)

    fun map(rawProgress: Float, line: String): Mapped {
        val lower = line.lowercase()
        val stage = when {
            "download" in lower || "fragment" in lower -> "Downloading"
            "extractaudio" in lower || "post-process" in lower || "postprocess" in lower ->
                "Converting audio"
            "metadata" in lower || "thumbnail" in lower -> "Writing metadata"
            "webpage" in lower || "extracting url" in lower -> "Preparing source"
            else -> "Processing"
        }

        if (rawProgress < 0f) return Mapped(-1, stage)

        val rawPercent = (rawProgress * 100f).toInt().coerceIn(0, 100)
        val mapped = when (stage) {
            "Preparing source" -> (5 + rawPercent * 0.10).toInt()
            "Downloading" -> (10 + rawPercent * 0.75).toInt()
            "Converting audio" -> (86 + rawPercent * 0.06).toInt()
            "Writing metadata" -> (92 + rawPercent * 0.02).toInt()
            else -> (8 + rawPercent * 0.76).toInt()
        }.coerceIn(0, 94)

        return Mapped(mapped, stage)
    }
}
