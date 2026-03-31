package com.example.lms.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.lms.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object CloudinaryManager {

    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            try {
                val config = mapOf(
                    "cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME,
                    "secure" to true
                )
                MediaManager.init(context, config)
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun uploadImage(uri: Uri): ResultState<Pair<String, String>> {
        return suspendCancellableCoroutine { continuation ->
            try {
                MediaManager.get().upload(uri)
                    .unsigned(BuildConfig.CLOUDINARY_UPLOAD_PRESET)
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {}
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            val url = resultData["secure_url"] as? String ?: ""
                            val publicId = resultData["public_id"] as? String ?: ""
                            if (continuation.isActive) {
                                continuation.resume(ResultState.Success(Pair(url, publicId)))
                            }
                        }
                        override fun onError(requestId: String, error: ErrorInfo) {
                            if (continuation.isActive) {
                                continuation.resume(ResultState.Error(error.description ?: "Tải ảnh lên thất bại"))
                            }
                        }
                        override fun onReschedule(requestId: String, error: ErrorInfo) {
                            if (continuation.isActive) {
                                continuation.resume(ResultState.Error("Tải ảnh bị hoãn, vui lòng thử lại"))
                            }
                        }
                    }).dispatch()
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(ResultState.Error(e.message ?: "Lỗi hệ thống"))
                }
            }
        }
    }

    suspend fun uploadFile(uri: Uri, fileName: String): ResultState<String> =
        suspendCancellableCoroutine { continuation ->
            try {
                MediaManager.get().upload(uri)
                    .unsigned(BuildConfig.CLOUDINARY_UPLOAD_PRESET)
                    .option("resource_type", "raw")
                    .option("public_id", fileName)
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {}
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            val url = resultData["secure_url"] as? String
                            if (continuation.isActive) {
                                if (url != null) {
                                    continuation.resume(ResultState.Success(url))
                                } else {
                                    continuation.resume(ResultState.Error("Không lấy được URL"))
                                }
                            }
                        }
                        override fun onError(requestId: String, error: ErrorInfo) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    ResultState.Error(error.description ?: "Upload thất bại")
                                )
                            }
                        }
                        override fun onReschedule(requestId: String, error: ErrorInfo) {}
                    })
                    .dispatch()
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(ResultState.Error(e.message ?: "Lỗi hệ thống"))
                }
            }
        }

    fun downloadFile(context: Context, url: String, fileName: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Đang tải tài liệu...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(context, "Bắt đầu tải: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khi tải: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
