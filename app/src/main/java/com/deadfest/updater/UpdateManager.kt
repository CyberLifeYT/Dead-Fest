package com.deadfest.updater

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

class UpdateManager(private val context: Context) {

    data class DownloadStatus(
        val progress: Int = 0,
        val isCompleted: Boolean = false,
        val isFailed: Boolean = false,
        val error: String? = null
    )

    suspend fun downloadAndInstall(
        url: String,
        versionName: String,
        onProgress: (DownloadStatus) -> Unit
    ) {
        val fileName = "Dead-Fest-v$versionName.apk"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        
        if (file.exists()) {
            // Reuse a finished download, but never hand a truncated/corrupt APK to the installer.
            if (isValidApk(file)) {
                onProgress(DownloadStatus(progress = 100, isCompleted = true))
                installApk(file)
                return
            }
            file.delete()
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Dead-Fest Update")
            .setDescription("Downloading v$versionName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to enqueue download", e)
            onProgress(DownloadStatus(isFailed = true, error = e.message))
            return
        }

        onProgress(DownloadStatus(progress = 0))

        try {
            while (currentCoroutineContext().isActive) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor != null && cursor.moveToFirst()) {
                    val statusColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val downloadedBytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalBytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    if (statusColumnIndex != -1) {
                        val status = cursor.getInt(statusColumnIndex)
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                cursor.close()
                                onProgress(DownloadStatus(progress = 100, isCompleted = true))
                                installApk(file)
                                return
                            }
                            DownloadManager.STATUS_FAILED -> {
                                // Read the reason *before* closing the cursor (reading after close crashed).
                                val reasonColumn = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val reason = if (reasonColumn != -1) cursor.getInt(reasonColumn) else -1
                                cursor.close()
                                file.delete()
                                onProgress(DownloadStatus(isFailed = true, error = describeFailure(reason)))
                                return
                            }
                            DownloadManager.STATUS_RUNNING -> {
                                if (downloadedBytesIndex != -1 && totalBytesIndex != -1) {
                                    val downloaded = cursor.getLong(downloadedBytesIndex)
                                    val total = cursor.getLong(totalBytesIndex)
                                    if (total > 0) {
                                        val progress = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                                        onProgress(DownloadStatus(progress = progress))
                                    }
                                }
                            }
                        }
                    }
                }
                cursor?.close()
                delay(500L)
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error tracking download progress", e)
            onProgress(DownloadStatus(isFailed = true, error = e.message))
        }
    }

    private fun isValidApk(file: File): Boolean =
        file.length() > 0 && try {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) != null
        } catch (_: Exception) {
            false
        }

    private fun describeFailure(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space for the update."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage isn't available right now."
        DownloadManager.ERROR_HTTP_DATA_ERROR,
        DownloadManager.ERROR_TOO_MANY_REDIRECTS,
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The update server didn't respond properly. Try again later."
        DownloadManager.ERROR_CANNOT_RESUME -> "Download was interrupted. Please try again."
        else -> "Download failed (code $reason). Check your connection and try again."
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "com.deadfest.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Installation failed", e)
            Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
