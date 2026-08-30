package com.typezero.siphon.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.typezero.siphon.BuildConfig
import java.io.File

object UpdateInstaller {
    /**
     * Returns false when Android first needs the user to allow installs from
     * Siphon. The caller should ask the user to tap Install again after returning.
     */
    fun launch(context: Context, apk: File): Boolean {
        require(apk.isFile) { "Verified update APK is missing" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.files",
            apk
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        return true
    }
}
