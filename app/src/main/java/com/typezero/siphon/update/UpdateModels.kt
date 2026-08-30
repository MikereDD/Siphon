package com.typezero.siphon.update

enum class UpdateChannel(val wire: String, val label: String) {
    STABLE("stable", "Stable"),
    DEVELOPMENT("development", "Development");

    companion object {
        fun fromWire(value: String): UpdateChannel =
            entries.firstOrNull { it.wire == value } ?: error("Unknown update channel")
    }
}

data class ReleaseSignature(
    val algorithm: String,
    val fileName: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
    val keyId: String,
    val publicKeySha256: String
)

data class ReleaseAsset(
    val fileName: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
    val signature: ReleaseSignature,
    val packageId: String,
    val signingCertificateSha256: String
)

data class ReleaseManifest(
    val schemaVersion: Int,
    val appId: String,
    val displayName: String,
    val platform: String,
    val architecture: String,
    val channel: UpdateChannel,
    val version: String,
    val publishedAt: String,
    val minimumVersion: String,
    val updaterProtocolVersion: Int,
    val minimumUpdaterProtocolVersion: Int,
    val mandatory: Boolean,
    val mandatoryReason: String?,
    val releaseNotesUrl: String,
    val changelogUrl: String,
    val assets: List<ReleaseAsset>
)

sealed interface UpdateCheckResult {
    data class Available(val manifest: ReleaseManifest, val asset: ReleaseAsset) : UpdateCheckResult
    data class Current(val version: String) : UpdateCheckResult
    data class NotPublished(val message: String) : UpdateCheckResult
}
