package com.typezero.siphon.data.model

/** A display-safe description of an app-private file eligible for cleanup. */
data class StorageFileInfo(
    val name: String,
    val bytes: Long,
    val lastModified: Long
)

data class StorageGroupSummary(
    val count: Int = 0,
    val bytes: Long = 0L,
    val files: List<StorageFileInfo> = emptyList(),
    val hasMore: Boolean = false
)

data class StorageSnapshot(
    val legacy: StorageGroupSummary = StorageGroupSummary(),
    val abandonedStaging: StorageGroupSummary = StorageGroupSummary()
) {
    val totalCount: Int get() = legacy.count + abandonedStaging.count
    val totalBytes: Long get() = legacy.bytes + abandonedStaging.bytes
}

data class CleanupResult(
    val deletedCount: Int,
    val deletedBytes: Long,
    val failedCount: Int
)

enum class CleanupTarget { LEGACY, ABANDONED_STAGING }
