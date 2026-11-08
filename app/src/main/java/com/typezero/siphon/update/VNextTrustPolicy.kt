package com.typezero.siphon.update

/**
 * Local vNext release-key authorization policy. These trust anchors must come from
 * the installed application or from a separately authenticated recovery protocol;
 * NEVER construct this registry from a release manifest or downloaded key list.
 *
 * Phase 3A does not implement recovery statements or persist lifecycle changes.
 */
internal class VNextTrustPolicy private constructor(
    private val anchors: Map<String, VNextManifestSecurity.PinnedKey>
) {
    companion object {
        fun fromInstalledAnchors(
            installedKeys: Collection<VNextManifestSecurity.PinnedKey>
        ): VNextTrustPolicy {
            require(installedKeys.isNotEmpty()) { "No installed vNext trust anchors" }
            val ids = mutableSetOf<String>()
            val fingerprints = mutableSetOf<String>()
            installedKeys.forEach { key ->
                require(key.keyId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) {
                    "Invalid installed key identifier"
                }
                require(key.spkiSha256.matches(Regex("[0-9a-f]{64}"))) {
                    "Invalid installed key fingerprint"
                }
                require(key.spkiDer.isNotEmpty()) { "Installed release public key is empty" }
                require(ids.add(key.keyId)) { "Duplicate installed release key ID" }
                require(fingerprints.add(key.spkiSha256)) { "Duplicate installed public-key identity" }
            }
            // Defensive copy so a caller cannot mutate key material after the policy is created.
            val snapshots = installedKeys.map { it.copy(spkiDer = it.spkiDer.clone()) }
            return VNextTrustPolicy(snapshots.associateBy { it.keyId })
        }
    }

    /** Only active/retiring local identities may authorize new release metadata. */
    fun approvedKeys(): List<VNextManifestSecurity.PinnedKey> = anchors.values
        .filter { it.lifecycle == VNextManifestSecurity.Lifecycle.ACTIVE ||
            it.lifecycle == VNextManifestSecurity.Lifecycle.RETIRING }
        .map { it.copy(spkiDer = it.spkiDer.clone()) }

    fun authorize(keyId: String, spkiSha256: String): VNextManifestSecurity.PinnedKey {
        val key = anchors[keyId] ?: error("Unapproved vNext release key")
        require(key.spkiSha256 == spkiSha256) { "Unapproved release key fingerprint" }
        require(key.lifecycle in setOf(
            VNextManifestSecurity.Lifecycle.ACTIVE,
            VNextManifestSecurity.Lifecycle.RETIRING
        )) { "Release key is not eligible for new releases" }
        return key.copy(spkiDer = key.spkiDer.clone())
    }
}
