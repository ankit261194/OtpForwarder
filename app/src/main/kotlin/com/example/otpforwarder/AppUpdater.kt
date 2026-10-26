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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object AppUpdater {
    private const val TAG = "AppUpdater"
    const val UPDATE_URL = "https://github.com/ankit261194/OtpForwarder/raw/main/release_apk/eDistrict_OTP_Forwarder.apk"

    suspend fun downloadAndInstall(
        context: Context,
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
            val url = URL(UPDATE_URL)
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

            val inputStream = currentConnection.inputStream
            val outputStream = FileOutputStream(cacheFile)

            val buffer = ByteArray(8192)
            var totalBytes = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
                if (contentLength > 0) {
                    val percent = (totalBytes * 100 / contentLength).toInt()
                    onProgress("Downloading: $percent%")
                } else {
                    onProgress("Downloading: ${totalBytes / 1024} KB...")
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
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

            Pair(true, "Update installer launched!")
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading update", e)
            Pair(false, e.localizedMessage ?: "Download failed")
        }
    }
}
