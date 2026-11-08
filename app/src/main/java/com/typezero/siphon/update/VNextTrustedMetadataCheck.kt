package com.typezero.siphon.update

import java.time.Clock

/**
 * Phase 4A: fail-closed handoff from app-owned release trust into signed metadata checks.
 * Not an installer, download approval, or production key provisioning mechanism.
 */
internal class VNextTrustedMetadataCheck(
    private val store: VNextReplayGate.Store,
    private val fetch: (String, Int) -> ByteArray = SafeHttp::getBytes,
    private val clock: Clock = Clock.systemUTC()
) {
    fun check(
        manifestUrl: String,
        installedPolicy: VNextTrustPolicy,
        channel: UpdateChannel
    ): VNextUpdateCheck.Metadata {
        val approved = installedPolicy.approvedKeys()
        require(approved.isNotEmpty()) { "No active or retiring vNext release identity is installed" }
        // Signature location is derived locally; never supplied by downloaded metadata.
        val signatureUrl = "$manifestUrl.sig"
        SafeHttp.validateUrl(manifestUrl)
        SafeHttp.validateUrl(signatureUrl)
        return VNextUpdateCheck(store, fetch, clock).check(
            VNextUpdateCheck.Feed(manifestUrl, signatureUrl, approved), channel
        )
    }
}
