package com.typezero.siphon.update

import java.util.Base64

/** Release trust pinned from a locally provisioned P-384 public key, never from downloaded metadata. */
internal object VNextInstalledTrust {
    private const val RELEASE_KEY_ID = "typezero-siphon-vnext-release-01"
    private const val RELEASE_SPKI_SHA256 = "44f7f7b657f810af4c9a715233f456a56d7f15c684261f7197009592518ffb89"
    private const val RELEASE_SPKI_BASE64 = "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEULHOhottT99AwSY4FvuxXESJtzcoAW1QTYBnrKWvBCtBmO2WmUuyRJ9yb4vYGnfKbqxkoHuwmWBViU28n9sn5WBcwhwO2YEtBKBVzF2PoImSBHK02Ov5HQmURdQTeXHg"

    fun configuredPolicyOrNull(): VNextTrustPolicy? = VNextTrustPolicy.fromInstalledAnchors(
        listOf(
            VNextManifestSecurity.PinnedKey(
                keyId = RELEASE_KEY_ID,
                spkiSha256 = RELEASE_SPKI_SHA256,
                spkiDer = Base64.getDecoder().decode(RELEASE_SPKI_BASE64),
                lifecycle = VNextManifestSecurity.Lifecycle.ACTIVE
            )
        )
    )
}