package com.example.otpforwarder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseNotes: String,
    val apkUrl: String,
    val errorMessage: String? = null
)

object AppUpdater {
    private const val TAG = "AppUpdater"
    private const val VERSION_JSON_URL = "https://raw.githubusercontent.com/ankit261194/OtpForwarder/main/version.json"
    const val DEFAULT_UPDATE_URL = "https://github.com/ankit261194/OtpForwarder/raw/main/release_apk/Smart_Notifier.apk"

    fun getCurrentVersionCode(context: Context): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "4.5"
        } catch (e: Exception) {
            "4.5"
        }
    }

    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(VERSION_JSON_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "SmartNotifier/1.0")

            if (connection.responseCode == 200) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonText)
                val remoteVersionCode = json.optInt("versionCode", 1)
                val remoteVersionName = json.optString("versionName", "1.0")
                val releaseNotes = json.optString("releaseNotes", "New performance and stability improvements")
                val apkUrl = json.optString("apkUrl", DEFAULT_UPDATE_URL)

                val localVersionCode = getCurrentVersionCode(context)
                val localVersionName = getCurrentVersionName(context)

                val hasUpdate = remoteVersionCode > localVersionCode

                UpdateCheckResult(
                    hasUpdate = hasUpdate,
                    currentVersion = localVersionName,
                    latestVersion = remoteVersionName,
                    releaseNotes = releaseNotes,
                    apkUrl = apkUrl
                )
            } else {
                UpdateCheckResult(
                    hasUpdate = false,
                    currentVersion = getCurrentVersionName(context),
                    latestVersion = getCurrentVersionName(context),
                    releaseNotes = "",
                    apkUrl = DEFAULT_UPDATE_URL,
                    errorMessage = "Server responded with HTTP ${connection.responseCode}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking update", e)
            UpdateCheckResult(
                hasUpdate = false,
                currentVersion = getCurrentVersionName(context),
                latestVersion = getCurrentVersionName(context),
                releaseNotes = "",
                apkUrl = DEFAULT_UPDATE_URL,
                errorMessage = e.localizedMessage ?: "Network error"
            )
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String = DEFAULT_UPDATE_URL,
        onProgress: (String) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Check unknown source install permission on Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return@withContext Pair(false, "Pehle 'Allow from this source' ko ON karein, fir dubara update dabayein.")
                }
            }

            onProgress("Connecting to GitHub...")
            val url = URL(apkUrl)
            var currentConnection = url.openConnection() as HttpURLConnection
            currentConnection.instanceFollowRedirects = true
            currentConnection.connectTimeout = 15000
            currentConnection.readTimeout = 30000

            var code = currentConnection.responseCode
            var redirects = 0
            while ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) && redirects < 5) {
                val newUrl = currentConnection.getHeaderField("Location") ?: break
                currentConnection.disconnect()
                currentConnection = URL(newUrl).openConnection() as HttpURLConnection
                currentConnection.connectTimeout = 15000
                currentConnection.readTimeout = 30000
                code = currentConnection.responseCode
                redirects++
            }

            if (code != 200) {
                return@withContext Pair(false, "Server Error: HTTP $code")
            }

            val contentLength = currentConnection.contentLength
            val cacheFile = File(context.cacheDir, "update.apk")
            if (cacheFile.exists()) cacheFile.delete()

            // High-Performance 64KB Buffered Stream Pipeline (Downloads in 2-3 seconds)
            val bufferSize = 65536
            val bis = BufferedInputStream(currentConnection.inputStream, bufferSize)
            val bos = BufferedOutputStream(FileOutputStream(cacheFile), bufferSize)

            val buffer = ByteArray(bufferSize)
            var totalBytes = 0L
            var bytesRead: Int
            var lastProgressReportTime = 0L

            while (bis.read(buffer).also { bytesRead = it } != -1) {
                bos.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
                
                val now = System.currentTimeMillis()
                // Throttle UI progress update to 150ms to eliminate UI stutter and maximize download speed
                if (now - lastProgressReportTime >= 150) {
                    lastProgressReportTime = now
                    if (contentLength > 0) {
                        val percent = (totalBytes * 100 / contentLength).toInt()
                        val mbDownloaded = String.format("%.1f", totalBytes / (1024f * 1024f))
                        val mbTotal = String.format("%.1f", contentLength / (1024f * 1024f))
                        onProgress("Downloading: $percent% ($mbDownloaded / $mbTotal MB)")
                    } else {
                        val mbDownloaded = String.format("%.1f", totalBytes / (1024f * 1024f))
                        onProgress("Downloading: $mbDownloaded MB...")
                    }
                }
            }

            bos.flush()
            bos.close()
            bis.close()
            currentConnection.disconnect()

            onProgress("Installer launching...")
            Log.d(TAG, "Update downloaded successfully: ${cacheFile.length()} bytes")

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                cacheFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Pair(true, "Installation started")
        } catch (e: Exception) {
            Log.e(TAG, "Download/Install failed", e)
            Pair(false, e.localizedMessage ?: "Download failed")
        }
    }
}
