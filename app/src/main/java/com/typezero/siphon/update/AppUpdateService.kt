package com.typezero.siphon.update

import android.content.Context
import com.typezero.siphon.BuildConfig

class AppUpdateService(private val context: Context) {
    val preferences = UpdatePreferences(context)

    fun check(channel: UpdateChannel = preferences.channel()): UpdateCheckResult {
        val text = try {
            SafeHttp.getText(UpdateTrust.MANIFEST_URL)
        } catch (_: SafeHttp.NotFoundException) {
            return UpdateCheckResult.NotPublished(
                "No Siphon application release manifest has been published yet."
            )
        }

        val manifest = ManifestParser.parse(text)
        validateManifest(manifest, channel)

        val installed = TypezeroVersion.parse(BuildConfig.VERSION_NAME)
        val candidate = TypezeroVersion.parse(manifest.version)
        val minimum = TypezeroVersion.parse(manifest.minimumVersion)

        require(installed >= minimum) {
            "This release cannot directly update ${BuildConfig.VERSION_NAME}; a newer bootstrap build is required."
        }

        if (candidate <= installed) return UpdateCheckResult.Current(BuildConfig.VERSION_NAME)

        val asset = manifest.assets.singleOrNull {
            it.packageId == UpdateTrust.PACKAGE_ID &&
                (manifest.architecture == "arm64-v8a" || manifest.architecture == "android-universal")
        } ?: error("No compatible Siphon Android asset was found")

        SafeHttp.validateUrl(asset.downloadUrl)
        SafeHttp.validateUrl(asset.signature.downloadUrl)
        require(asset.fileName == "Siphon-v${manifest.version}.apk") {
            "Release asset name does not match the exact version"
        }
        require(asset.signature.fileName == "${asset.fileName}.sig") {
            "Detached signature name does not match the APK"
        }

        return UpdateCheckResult.Available(manifest, asset)
    }

    private fun validateManifest(m: ReleaseManifest, selected: UpdateChannel) {
        require(m.schemaVersion == UpdateTrust.MANIFEST_SCHEMA_VERSION) {
            "Unsupported release manifest schema"
        }
        require(m.appId == UpdateTrust.APP_ID) { "Release manifest app ID mismatch" }
        require(m.platform == "android") { "Release manifest platform mismatch" }
        require(m.channel == selected) { "Release belongs to ${m.channel.label}, not ${selected.label}" }
        require(m.minimumUpdaterProtocolVersion <= m.updaterProtocolVersion) {
            "Invalid updater protocol range"
        }
        require(UpdateTrust.UPDATER_PROTOCOL_VERSION >= m.minimumUpdaterProtocolVersion) {
            "This Siphon updater is too old for that release"
        }
        require(m.architecture in setOf("arm64-v8a", "android-universal")) {
            "Release architecture is not compatible with this build"
        }
        if (m.mandatory) require(!m.mandatoryReason.isNullOrBlank()) {
            "Mandatory update is missing its reason"
        }
        TypezeroVersion.parse(m.version)
        TypezeroVersion.parse(m.minimumVersion)
    }

    fun currentChannel(): UpdateChannel = preferences.channel()
}
