package com.typezero.siphon.update

/** Persists the authenticated high-water mark before returning an accepted manifest. */
internal class VNextReplayGate(private val store: Store) {
    interface Store {
        fun read(scope: String): VNextManifestSecurity.AcceptedState?
        /** Must synchronously and durably write, or throw. */
        fun write(scope: String, state: VNextManifestSecurity.AcceptedState)
    }

    companion object { private val processLock = Any() }

    /** Serialize all gates against the same preference state within this process. */
    fun accept(scope: String, candidate: VNextManifestSecurity.AcceptedState) {
        synchronized(processLock) {
            require(scope.isNotBlank()) { "Replay scope is empty" }
            require(candidate.sequence > 0 && candidate.manifestSha256.matches(Regex("[0-9a-f]{64}"))) {
                "Invalid authenticated replay state"
            }
            val previous = store.read(scope)
            if (previous != null) {
                require(candidate.sequence >= previous.sequence) { "Manifest replay rejected" }
                require(candidate.sequence != previous.sequence ||
                    candidate.manifestSha256 == previous.manifestSha256) {
                    "Manifest sequence collision rejected"
                }
                if (candidate.sequence == previous.sequence) return@synchronized
            }
            store.write(scope, candidate)
        }
    }
}
