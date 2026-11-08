package com.typezero.siphon.update

import android.content.Context
import java.io.File
import java.time.Clock

/**
 * Integration boundary for a vNext candidate. It intentionally DOES NOT commit
 * the PackageInstaller session. A returned session must be explicitly abandoned
 * unless a future, independently reviewed commit flow authorizes it.
 *
 * This is not an installer endpoint until genuine app-owned trust anchors and an
 * exact approved development feed are configured. Never accept trust from JSON.
 */
internal class VNextEndToEndStage(
    private val context: Context,
    private val replayStore: VNextReplayGate.Store = VNextPreferencesStore(context),
    private val clock: Clock = Clock.systemUTC()
) {
    /** Caller must have downloaded manifest, its detached signature, APK and APK signature. */
    fun stage(
        manifestUrl: String,
        channel: UpdateChannel,
        manifestBytes: ByteArray,
        manifestSignature: ByteArray,
        assetName: String,
        downloadedApk: File,
        apkSignature: File
    ): VNextVerifiedSessionStage.StagedSession {
        val policy = VNextInstalledTrust.configuredPolicyOrNull()
            ?: error("vNext release trust is not provisioned")
        return stageAuthorized(
            manifestUrl, channel, manifestBytes, manifestSignature,
            assetName, downloadedApk, apkSignature, policy
        )
    }

    /** Internal test seam: tests may supply a locally generated key, never a network key. */
    internal fun stageAuthorized(
        manifestUrl: String,
        channel: UpdateChannel,
        manifestBytes: ByteArray,
        manifestSignature: ByteArray,
        assetName: String,
        downloadedApk: File,
        apkSignature: File,
        policy: VNextTrustPolicy
    ): VNextVerifiedSessionStage.StagedSession {
        val verified = VNextStageAuthorization.authenticateAndPersist(
            manifestUrl, channel, manifestBytes, manifestSignature,
            policy, replayStore, clock
        )
        val prepared = VNextPrivateApkStage(VNextApkIntegrityGate(clock)).prepare(
            context, downloadedApk, apkSignature,
            manifestBytes, manifestSignature, policy, channel, assetName
        )
        try {
            require(prepared.manifestSequence == verified.sequence) {
                "APK authorized against a different manifest sequence"
            }
            // Fail if another update check advanced replay state while this APK
            // was being staged. Do not install a superseded candidate.
            VNextReplayGate(replayStore).accept(verified.scope, verified.state)
            return VNextVerifiedSessionStage.stage(context, prepared)
        } finally {
            // Once streamed into PackageInstaller, the private file is unnecessary.
            // SessionStage abandons an unsuccessfully populated session itself.
            prepared.file.delete()
        }
    }
}

/** Pure, host-unit-testable replay and signed-manifest admission boundary. */
internal object VNextStageAuthorization {
    data class Admission(
        val scope: String,
        val sequence: Long,
        val state: VNextManifestSecurity.AcceptedState
    )

    fun authenticateAndPersist(
        manifestUrl: String,
        channel: UpdateChannel,
        manifestBytes: ByteArray,
        manifestSignature: ByteArray,
        policy: VNextTrustPolicy,
        store: VNextReplayGate.Store,
        clock: Clock = Clock.systemUTC()
    ): Admission {
        require(policy.approvedKeys().isNotEmpty()) { "No approved vNext signing identity" }
        SafeHttp.validateUrl(manifestUrl)
        SafeHttp.validateUrl("$manifestUrl.sig")
        require(manifestUrl.endsWith(".json")) { "Unexpected vNext manifest URL" }
        val scope = "${UpdateTrust.APP_ID}:${channel.wire}:$manifestUrl"
        val verified = VNextManifestSecurity.verify(
            manifestBytes, manifestSignature, policy.approvedKeys(),
            UpdateTrust.APP_ID, channel.wire, clock.instant()
        )
        require(verified.manifest.getString("platform") == "android") {
            "Wrong update platform"
        }
        // Persist before allowing a candidate to enter private staging.
        // The storage adapter must synchronously fail on incomplete writes.
        VNextReplayGate(store).accept(scope, verified.state)
        return Admission(scope, verified.state.sequence, verified.state)
    }
}
