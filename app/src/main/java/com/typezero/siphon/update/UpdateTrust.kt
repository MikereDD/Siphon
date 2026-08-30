package com.typezero.siphon.update

/**
 * Locally trusted updater configuration.
 *
 * The release public key and canonical Siphon APK signing certificate are
 * pinned here and must never be replaced from remote manifest data.
 */
object UpdateTrust {
    const val MANIFEST_SCHEMA_VERSION = 2
    const val UPDATER_PROTOCOL_VERSION = 2

    const val APP_ID = "siphon"
    const val PACKAGE_ID = "com.typezero.siphon"

    const val MANIFEST_URL =
        "https://raw.githubusercontent.com/MikereDD/Siphon/main/release-manifest.json"

    val APPROVED_HOSTS = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    const val RELEASE_KEY_ID = "typezero-siphon-release-01"

    const val RELEASE_PUBLIC_KEY_SHA256 =
        "3a08b42ab07b9d87ded218cb4df49d4a77c265f2a5f1a8a9847d5c0780c6546f"

    const val APK_SIGNING_CERT_SHA256 =
        "4f933b15ebef515aaa3e441579e768aaf314d5d4e27ec27fd1e94ecf9501513c"

    val RELEASE_PUBLIC_KEY_PEM = """
        -----BEGIN PUBLIC KEY-----
        MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAthez/rkDFHNT113Cs6UM
        RKZH6dHYSOa7uVet93RdhNYMwhaqNWRUT6J4QONvN3TVFRuGZ7v6Qn6XDuNh8m3s
        QNYh8apL1hHrzCn3towXvpf9HwRYkrLT61Vn9lvpa9xYEfJqBoMEVVER+NBS67xL
        FgtJGSYkk4crhSsD7exQYHWNWGcejQ1yewqbe+50KHRE97LM7Rp6/XNcPkQ72hVB
        4p6c2+7Yv2G7TC4ua40ErzQXxr0jwadspE4E8VjImcMNQMb3gxPAjydAxYNyKhAk
        wV6bJsVAxaM4RfvHH9A87aKa1vxaBbvB9E4J9BUJsAQbmAjwHtZzs3uUHi09LnyN
        2NFqbyR1PjPpllGOm0Eke1DG+Hmz617ueWNQKjR5zr6DCgMJxdSCo6YIizGObK0w
        R6X6whGUpapi88Gs3qZfqcuLhQmDb95i8ScpsarKOf6DGuMHWOdJZ0HPVUsyOWYk
        aGjE2XOuYyiLsbGyU+VnBNRtQi+5L0HIK87I4Am1sme1EyGGG0aah0Uk4S/pg8Gy
        d7E8SKZWeJfftJiy5empYOqm+VtWmmy92oOQ5fH2Q6VxMeXKIKNj3S5QuoO7aFaN
        jQFOBDZC8LgGK0VIT8hVZ+3J8F6BD6X0UVpuSrmhaN1jD7ZaZIZ6C0FSQJfRwSfg
        Lj7IY1+3BYWtRqEAmW/vlJECAwEAAQ==
        -----END PUBLIC KEY-----
    """.trimIndent()

    val cryptographicAnchorsConfigured: Boolean
        get() = RELEASE_KEY_ID.isNotBlank() &&
            RELEASE_PUBLIC_KEY_SHA256.matches(Regex("[0-9a-fA-F]{64}")) &&
            RELEASE_PUBLIC_KEY_PEM.contains("BEGIN PUBLIC KEY") &&
            APK_SIGNING_CERT_SHA256.matches(Regex("[0-9a-fA-F]{64}"))
}
