package com.typezero.siphon.update

/** Exact Typezer∅ numeric version grammar and comparator. */
data class TypezeroVersion(
    val core: List<Int>,
    val development: List<Int>?
) : Comparable<TypezeroVersion> {
    override fun compareTo(other: TypezeroVersion): Int {
        compareParts(core, other.core).let { if (it != 0) return it }
        if (development == null && other.development != null) return 1
        if (development != null && other.development == null) return -1
        if (development == null) return 0
        return compareParts(development, requireNotNull(other.development))
    }

    companion object {
        private val regex = Regex("""^(\d+(?:\.\d+)*)(?:-dev\.(\d+(?:\.\d+)*))?$""")

        fun parse(value: String): TypezeroVersion {
            val m = regex.matchEntire(value) ?: error("Invalid Typezer∅ version: $value")
            fun parts(s: String) = s.split('.').map {
                it.toIntOrNull() ?: error("Invalid numeric version component")
            }
            return TypezeroVersion(
                core = parts(m.groupValues[1]),
                development = m.groupValues[2].takeIf { it.isNotBlank() }?.let(::parts)
            )
        }

        private fun compareParts(a: List<Int>, b: List<Int>): Int {
            val max = maxOf(a.size, b.size)
            for (i in 0 until max) {
                val av = a.getOrElse(i) { 0 }
                val bv = b.getOrElse(i) { 0 }
                if (av != bv) return av.compareTo(bv)
            }
            return 0
        }
    }
}
