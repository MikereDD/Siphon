package com.typezero.siphon.update

import android.content.Context

/** Strict, synchronous replay state persistence for a single-process updater. */
internal class VNextPreferencesStore(context: Context) : VNextReplayGate.Store {
    private val prefs = context.getSharedPreferences("siphon_vnext_replay_v1", Context.MODE_PRIVATE)

    override fun read(scope: String): VNextManifestSecurity.AcceptedState? {
        val sequenceKey = "seq:$scope"
        val digestKey = "digest:$scope"
        val hasSeq = prefs.contains(sequenceKey)
        val hasDigest = prefs.contains(digestKey)
        require(hasSeq == hasDigest) { "Trusted updater replay state is incomplete" }
        if (!hasSeq) return null
        val sequence = prefs.getLong(sequenceKey, -1L)
        val digest = prefs.getString(digestKey, null).orEmpty()
        require(sequence > 0 && digest.matches(Regex("[0-9a-f]{64}"))) {
            "Trusted updater replay state is invalid"
        }
        return VNextManifestSecurity.AcceptedState(sequence, digest)
    }

    override fun write(scope: String, state: VNextManifestSecurity.AcceptedState) {
        check(prefs.edit()
            .putLong("seq:$scope", state.sequence)
            .putString("digest:$scope", state.manifestSha256)
            .commit()) { "Unable to persist trusted manifest replay state" }
    }
}
