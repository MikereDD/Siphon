package com.typezero.siphon.update

import org.json.JSONObject
import java.time.Clock

/**
 * Explicit pre-release vNext metadata check. It never invokes the installer or accepts
 * a network-supplied trust key. Production feed/key provisioning and vNext APK handoff
 * are separate, future milestones. The deployed schema-2 path remains unchanged.
 */
internal class VNextUpdateCheck(
    private val store: VNextReplayGate.Store,
    private val fetch: (String, Int) -> ByteArray = SafeHttp::getBytes,
    private val clock: Clock = Clock.systemUTC()
) {
    data class Feed(
        val manifestUrl: String,
        val signatureUrl: String,
        val pinnedKeys: List<VNextManifestSecurity.PinnedKey>
    )

    data class Metadata(
        val version: String,
        val sequence: Long,
        val signingKeyId: String,
        val assetCount: Int
    )

    fun check(feed: Feed, channel: UpdateChannel): Metadata {
        require(feed.pinnedKeys.isNotEmpty()) { "No locally configured vNext trust anchors" }
        SafeHttp.validateUrl(feed.manifestUrl)
        SafeHttp.validateUrl(feed.signatureUrl)
        // Prevent arbitrary remote signature-location substitution or ambiguity.
        require(feed.signatureUrl == feed.manifestUrl + ".sig") {
            "Manifest and signature URLs do not correspond"
        }
        val raw = fetch(feed.manifestUrl, 1024 * 1024)
        val signature = fetch(feed.signatureUrl, 144)
        val verified = VNextManifestSecurity.verify(
            raw, signature, feed.pinnedKeys, UpdateTrust.APP_ID,
            channel.wire, clock.instant()
        )
        val json = verified.manifest
        require(json.getString("platform") == "android") { "Wrong update platform" }
        val version = json.getString("version")
        TypezeroVersion.parse(version)
        require(json.getJSONArray("assets").length() > 0) { "No update assets" }
        val assets = json.getJSONArray("assets")
        val names = mutableSetOf<String>()
        for (index in 0 until assets.length()) {
            val a: JSONObject = assets.getJSONObject(index)
            val name = a.getString("name")
            require(name.isNotBlank() && '/' !in name && '\\' !in name) { "Invalid asset name" }
            require(names.add(name)) { "Duplicate release asset name" }
            require(a.getLong("size") > 0) { "Invalid asset size" }
            require(a.getString("sha256").matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid asset hash" }
            SafeHttp.validateUrl(a.getString("url"))
            if (a.has("signature")) {
                val detached = a.getJSONObject("signature")
                require(detached.getString("name") == "$name.sig") { "Asset signature name mismatch" }
                SafeHttp.validateUrl(detached.getString("url"))
                require(detached.getString("sha256").matches(Regex("[0-9a-fA-F]{64}"))) {
                    "Invalid asset signature hash"
                }
            }
        }
        // Only persist after authentication and complete semantic validation.
        // Scope by app + channel + feed URL so development and stable do not share state.
        val scope = "${UpdateTrust.APP_ID}:${channel.wire}:${feed.manifestUrl}"
        VNextReplayGate(store).accept(scope, verified.state)
        return Metadata(version, verified.state.sequence, verified.keyId, assets.length())
    }
}
