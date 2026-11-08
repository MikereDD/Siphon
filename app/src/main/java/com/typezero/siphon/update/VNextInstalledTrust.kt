package com.typezero.siphon.update

/**
 * Fail-closed app-owned trust provisioning seam for Siphon vNext.
 *
 * This intentionally includes NO fixture keys and NO remote discovery. Populate
 * it only after generating/approving Siphon's real app-specific release public
 * identity, auditing recovery authorization, and validating the migration path.
 * Until then, no installed P-384 identity is authorized for vNext OTA.
 */
internal object VNextInstalledTrust {
    fun configuredPolicyOrNull(): VNextTrustPolicy? = null
}
