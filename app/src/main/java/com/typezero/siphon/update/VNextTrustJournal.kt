package com.typezero.siphon.update

import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.util.Base64

/**
 * Phase 4B: verified local recovery journal, not automatic trust enrollment.
 * Each entry is independently signed by the installed recovery authority and
 * transitions are checked by replaying from the immutable installed baseline.
 *
 * The separately stored high-water mark detects a rollback of the journal alone.
 * Both stores can be rolled back together by an attacker with control of app data;
 * this is not hardware-backed anti-rollback protection.
 */
internal class VNextTrustJournal(
    private val storage: Storage,
    private val installedRecovery: VNextRecoveryAuthority.RecoveryAnchor,
    private val installedBaseline: VNextRecoveryAuthority.TrustedState,
    private val expectedAppId: String,
    private val clock: Clock = Clock.systemUTC()
) {
    interface Storage {
        /** Null means this record has never been written. A failed read MUST throw. */
        fun readJournal(): ByteArray?
        fun readHighWater(): ByteArray?
        /** Each write MUST replace its own record atomically or throw. */
        fun writeJournal(bytes: ByteArray)
        fun writeHighWater(bytes: ByteArray)
    }

    private data class Proof(val statement: ByteArray, val signature: ByteArray)
    private companion object {
        const val MAX_ENTRIES = 32
        const val MAX_JOURNAL_BYTES = 5 * 1024 * 1024
        const val MAX_PROOF_BYTES = 128 * 1024
    }

    fun load(): VNextRecoveryAuthority.TrustedState {
        val journal = readProofs(storage.readJournal())
        val minimum = readHighWater(storage.readHighWater())
        val state = replay(journal)
        require(state.sequence >= minimum) { "Recovery journal rollback detected" }
        return copyState(state)
    }

    /** Returns a verified proposal only after durable journal and watermark writes. */
    fun applySignedRecovery(statement: ByteArray, signature: ByteArray): VNextRecoveryAuthority.TrustedState {
        val proofs = readProofs(storage.readJournal())
        val minimum = readHighWater(storage.readHighWater())
        val current = replay(proofs)
        require(current.sequence >= minimum) { "Recovery journal rollback detected" }
        require(proofs.size < MAX_ENTRIES) { "Recovery journal capacity reached; explicit migration required" }
        val next = VNextRecoveryAuthority.verifyAndPropose(
            statement, signature, installedRecovery, current, expectedAppId, clock.instant()
        )
        val updated = encodeProofs(proofs + Proof(statement.clone(), signature.clone()))
        // Write journal first. If the process dies before the watermark write,
        // replay still verifies the new signed statement and advances trust.
        storage.writeJournal(updated)
        storage.writeHighWater(next.sequence.toString().toByteArray(Charsets.US_ASCII))
        return copyState(next)
    }

    private fun replay(proofs: List<Proof>): VNextRecoveryAuthority.TrustedState {
        var state = copyState(installedBaseline)
        require(state.sequence >= 0) { "Invalid installed baseline sequence" }
        proofs.forEach { proof ->
            state = VNextRecoveryAuthority.verifyAndPropose(
                proof.statement, proof.signature, installedRecovery, state, expectedAppId, clock.instant()
            )
        }
        return state
    }

    private fun readHighWater(bytes: ByteArray?): Long {
        if (bytes == null) return installedBaseline.sequence
        val text = bytes.toString(Charsets.US_ASCII)
        require(text.matches(Regex("0|[1-9][0-9]{0,18}"))) { "Corrupt trust high-water mark" }
        val sequence = text.toLong()
        require(sequence >= installedBaseline.sequence) { "Trust high-water mark below installed baseline" }
        return sequence
    }

    private fun readProofs(bytes: ByteArray?): List<Proof> {
        if (bytes == null) return emptyList()
        require(bytes.isNotEmpty() && bytes.size <= MAX_JOURNAL_BYTES) { "Invalid recovery journal size" }
        val obj = JSONObject(bytes.toString(Charsets.UTF_8))
        require(obj.keys().asSequence().toSet() == setOf("schemaVersion", "proofs")) { "Unknown recovery journal fields" }
        require(obj.getInt("schemaVersion") == 1) { "Unsupported recovery journal schema" }
        val a = obj.getJSONArray("proofs")
        require(a.length() in 1..MAX_ENTRIES) { "Empty or oversized recovery journal" }
        return (0 until a.length()).map { i ->
            val proof = a.getJSONObject(i)
            require(proof.keys().asSequence().toSet() == setOf("statementBase64", "signatureBase64")) { "Invalid recovery proof fields" }
            val statement = Base64.getDecoder().decode(proof.getString("statementBase64"))
            val signature = Base64.getDecoder().decode(proof.getString("signatureBase64"))
            require(statement.isNotEmpty() && statement.size <= MAX_PROOF_BYTES) { "Invalid stored recovery statement" }
            require(signature.isNotEmpty() && signature.size <= 144) { "Invalid stored recovery signature" }
            Proof(statement, signature)
        }
    }

    private fun encodeProofs(proofs: List<Proof>): ByteArray {
        val arr = JSONArray()
        proofs.forEach { proof ->
            arr.put(JSONObject().apply {
                put("statementBase64", Base64.getEncoder().encodeToString(proof.statement))
                put("signatureBase64", Base64.getEncoder().encodeToString(proof.signature))
            })
        }
        val bytes = JSONObject().put("schemaVersion", 1).put("proofs", arr)
            .toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_JOURNAL_BYTES) { "Recovery journal too large" }
        return bytes
    }

    private fun copyState(s: VNextRecoveryAuthority.TrustedState) =
        VNextRecoveryAuthority.TrustedState(s.sequence, s.releaseKeys.map { it.copy(spkiDer = it.spkiDer.clone()) })
}
