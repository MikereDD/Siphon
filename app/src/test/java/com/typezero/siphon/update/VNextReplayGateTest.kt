package com.typezero.siphon.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VNextReplayGateTest {
    private class MemoryStore : VNextReplayGate.Store {
        val states = mutableMapOf<String, VNextManifestSecurity.AcceptedState>()
        var failWrites = false
        override fun read(scope: String) = states[scope]
        override fun write(scope: String, state: VNextManifestSecurity.AcceptedState) {
            if (failWrites) error("disk failure")
            states[scope] = state
        }
    }

    private fun state(n: Long, hex: Char = 'a') =
        VNextManifestSecurity.AcceptedState(n, hex.toString().repeat(64))

    @Test fun advancingAndIdempotentSequences() {
        val store = MemoryStore()
        val gate = VNextReplayGate(store)
        gate.accept("siphon:development", state(1))
        gate.accept("siphon:development", state(1))
        gate.accept("siphon:development", state(2))
        assertEquals(state(2), store.read("siphon:development"))
    }

    @Test fun replayCollisionAndMissingPersistenceRejected() {
        val store = MemoryStore()
        val gate = VNextReplayGate(store)
        gate.accept("siphon:development", state(4))
        assertThrows(IllegalArgumentException::class.java) {
            gate.accept("siphon:development", state(3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            gate.accept("siphon:development", state(4, 'b'))
        }
        store.failWrites = true
        assertThrows(IllegalStateException::class.java) {
            gate.accept("siphon:development", state(5))
        }
        assertEquals(state(4), store.read("siphon:development"))
    }

    @Test fun scopesAreIndependent() {
        val store = MemoryStore()
        val gate = VNextReplayGate(store)
        gate.accept("siphon:development", state(8))
        gate.accept("siphon:stable", state(1))
        assertEquals(state(8), store.read("siphon:development"))
        assertEquals(state(1), store.read("siphon:stable"))
    }
}
