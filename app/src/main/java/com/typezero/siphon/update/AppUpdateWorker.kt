package com.typezero.siphon.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class AppUpdateWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground("Preparing update", -1)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        createChannel()
        setForeground(foreground("Preparing update", -1))
        cleanupOld()

        try {
            require(UpdateTrust.cryptographicAnchorsConfigured) {
                "Updater trust anchors are not configured yet"
            }
            val asset = decodeAsset(requireNotNull(inputData.getString(KEY_ASSET_JSON)))
            val dir = File(applicationContext.cacheDir, "updates").apply { mkdirs() }
            val apk = File(dir, asset.fileName)
            val sig = File(dir, asset.signature.fileName)
            apk.delete()
            sig.delete()

            SafeHttp.download(asset.downloadUrl, apk) { done, total ->
                val p = if (total > 0) ((done * 80L) / total).toInt().coerceIn(0, 80) else -1
                setProgressAsync(Data.Builder().putInt(KEY_PROGRESS, p).putString(KEY_STAGE, "Downloading APK").build())
                setForegroundAsync(foreground("Downloading APK", p))
            }
            SafeHttp.download(asset.signature.downloadUrl, sig) { _, _ -> }

            setProgress(Data.Builder().putInt(KEY_PROGRESS, 88).putString(KEY_STAGE, "Verifying release signature").build())
            setForeground(foreground("Verifying release signature", 88))
            ReleaseVerifier.verify(apk, sig, asset)

            setProgress(Data.Builder().putInt(KEY_PROGRESS, 95).putString(KEY_STAGE, "Verifying APK identity").build())
            setForeground(foreground("Verifying APK identity", 95))
            ApkVerifier.verify(applicationContext, apk, asset)

            Result.success(
                Data.Builder()
                    .putString(KEY_APK_PATH, apk.absolutePath)
                    .putString(KEY_VERSION, inputData.getString(KEY_VERSION))
                    .build()
            )
        } catch (t: Throwable) {
            Result.failure(Data.Builder().putString(KEY_ERROR, t.message ?: "Update verification failed").build())
        }
    }

    private fun foreground(text: String, percent: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.typezero.siphon.R.drawable.ic_siphon_notification)
            .setContentTitle("Siphon application update")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Application updates", NotificationManager.IMPORTANCE_LOW)
                )
        }
    }

    private fun cleanupOld() {
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        File(applicationContext.cacheDir, "updates").listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < cutoff }
            .forEach { runCatching { it.delete() } }
    }

    private fun decodeAsset(json: String): ReleaseAsset {
        val o = JSONObject(json)
        val s = o.getJSONObject("signature")
        return ReleaseAsset(
            o.getString("fileName"), o.getString("downloadUrl"), o.getLong("size"), o.getString("sha256"),
            ReleaseSignature(
                s.getString("algorithm"), s.getString("fileName"), s.getString("downloadUrl"),
                s.getLong("size"), s.getString("sha256"), s.getString("keyId"), s.getString("publicKeySha256")
            ),
            o.getString("packageId"), o.getString("signingCertificateSha256")
        )
    }

    companion object {
        const val UNIQUE_WORK = "siphon-app-update"
        const val KEY_PROGRESS = "update_progress"
        const val KEY_STAGE = "update_stage"
        const val KEY_APK_PATH = "verified_apk_path"
        const val KEY_VERSION = "update_version"
        const val KEY_ERROR = "update_error"
        private const val KEY_ASSET_JSON = "asset_json"
        private const val CHANNEL_ID = "siphon_app_updates"
        private const val NOTIFICATION_ID = 7302

        fun inputData(version: String, asset: ReleaseAsset): Data {
            val s = asset.signature
            val json = JSONObject()
                .put("fileName", asset.fileName).put("downloadUrl", asset.downloadUrl)
                .put("size", asset.size).put("sha256", asset.sha256)
                .put("packageId", asset.packageId)
                .put("signingCertificateSha256", asset.signingCertificateSha256)
                .put("signature", JSONObject()
                    .put("algorithm", s.algorithm).put("fileName", s.fileName)
                    .put("downloadUrl", s.downloadUrl).put("size", s.size)
                    .put("sha256", s.sha256).put("keyId", s.keyId)
                    .put("publicKeySha256", s.publicKeySha256))
            return Data.Builder().putString(KEY_VERSION, version).putString(KEY_ASSET_JSON, json.toString()).build()
        }
    }
}
