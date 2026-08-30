package com.typezero.siphon.update

import org.json.JSONArray
import org.json.JSONObject

object ManifestParser {
    fun parse(text: String): ReleaseManifest {
        val o = JSONObject(text)
        requireExactKeys(
            o, setOf(
                "schemaVersion", "appId", "displayName", "platform", "architecture",
                "channel", "version", "publishedAt", "minimumVersion",
                "updaterProtocolVersion", "minimumUpdaterProtocolVersion",
                "mandatory", "mandatoryReason", "releaseNotesUrl", "changelogUrl",
                "assets", "source", "rollback"
            ), optional = setOf("mandatoryReason")
        )
        val assets = o.getJSONArray("assets").mapObjects(::asset)
        require(assets.isNotEmpty()) { "Manifest contains no release assets" }
        return ReleaseManifest(
            schemaVersion = o.getInt("schemaVersion"),
            appId = o.getString("appId"),
            displayName = o.getString("displayName"),
            platform = o.getString("platform"),
            architecture = o.getString("architecture"),
            channel = UpdateChannel.fromWire(o.getString("channel")),
            version = o.getString("version"),
            publishedAt = o.getString("publishedAt"),
            minimumVersion = o.getString("minimumVersion"),
            updaterProtocolVersion = o.getInt("updaterProtocolVersion"),
            minimumUpdaterProtocolVersion = o.getInt("minimumUpdaterProtocolVersion"),
            mandatory = o.getBoolean("mandatory"),
            mandatoryReason = if (o.has("mandatoryReason") && !o.isNull("mandatoryReason")) o.getString("mandatoryReason") else null,
            releaseNotesUrl = o.getString("releaseNotesUrl"),
            changelogUrl = o.getString("changelogUrl"),
            assets = assets
        )
    }

    private fun asset(o: JSONObject): ReleaseAsset {
        requireExactKeys(
            o, setOf(
                "fileName", "downloadUrl", "size", "sha256", "signature",
                "packageId", "signingCertificateSha256"
            )
        )
        val s = o.getJSONObject("signature")
        requireExactKeys(
            s, setOf(
                "algorithm", "fileName", "downloadUrl", "size", "sha256",
                "keyId", "publicKeySha256"
            )
        )
        return ReleaseAsset(
            fileName = o.getString("fileName"),
            downloadUrl = o.getString("downloadUrl"),
            size = o.getLong("size"),
            sha256 = o.getString("sha256"),
            signature = ReleaseSignature(
                algorithm = s.getString("algorithm"),
                fileName = s.getString("fileName"),
                downloadUrl = s.getString("downloadUrl"),
                size = s.getLong("size"),
                sha256 = s.getString("sha256"),
                keyId = s.getString("keyId"),
                publicKeySha256 = s.getString("publicKeySha256")
            ),
            packageId = o.getString("packageId"),
            signingCertificateSha256 = o.getString("signingCertificateSha256")
        )
    }

    private fun requireExactKeys(o: JSONObject, allowed: Set<String>, optional: Set<String> = emptySet()) {
        val actual = buildSet {
            val it = o.keys()
            while (it.hasNext()) add(it.next())
        }
        val required = allowed - optional
        require(actual.containsAll(required)) { "Manifest is missing required fields" }
        require(actual.all { it in allowed }) { "Manifest contains unknown fields" }
    }

    private fun JSONArray.mapObjects(transform: (JSONObject) -> ReleaseAsset): List<ReleaseAsset> =
        (0 until length()).map { transform(getJSONObject(it)) }
}
