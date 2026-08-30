package com.typezero.siphon.update

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object SafeHttp {
    data class Download(val bytes: Long, val finalUrl: String)

    fun getText(url: String): String {
        val connection = openApproved(url)
        return try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) throw NotFoundException()
            require(code in 200..299) { "Update server returned HTTP $code" }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun download(url: String, destination: File, onBytes: (Long, Long) -> Unit): Download {
        var current = url
        repeat(6) {
            val c = openApproved(current)
            val code = c.responseCode
            if (code in 300..399) {
                val next = c.getHeaderField("Location") ?: error("Update redirect had no destination")
                c.disconnect()
                current = URL(URL(current), next).toString()
                validateUrl(current)
                return@repeat
            }
            try {
                require(code in 200..299) { "Update server returned HTTP $code" }
                val total = c.contentLengthLong.coerceAtLeast(-1L)
                var copied = 0L
                FileOutputStream(destination).use { out ->
                    c.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            copied += n
                            onBytes(copied, total)
                        }
                    }
                }
                return Download(copied, current)
            } finally {
                c.disconnect()
            }
        }
        error("Too many update redirects")
    }

    private fun openApproved(url: String): HttpURLConnection {
        validateUrl(url)
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Siphon-Updater")
        }
    }

    fun validateUrl(value: String) {
        val u = URL(value)
        require(u.protocol.equals("https", true)) { "Update origin must use HTTPS" }
        require(u.host.lowercase() in UpdateTrust.APPROVED_HOSTS) { "Unapproved update origin" }
    }

    class NotFoundException : Exception()
}
