package com.typezero.siphon.data.model

/** Live or persisted state of an extraction job for the UI. */
data class JobState(
    val id: String,
    val title: String,
    val status: Status,
    val progress: Float = -1f,
    val line: String = "",
    val outputPath: String? = null,
    val outputUri: String? = null,
    val outputBytes: Long = 0L,
    val error: String? = null,
    val formatLabel: String = "Audio",
    val qualityLabel: String = "Best available",
    val sourceLabel: String = "Media",
    val createdAt: Long = 0L
) {
    enum class Status { QUEUED, RUNNING, DONE, FAILED, CANCELLED }
}
