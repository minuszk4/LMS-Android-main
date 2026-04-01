package com.example.lms2.util

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.lms2.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

object CloudinaryManager {

    private var isInitialized = false
    private var appContext: Context? = null

    private const val MAX_DIRECT_IMAGE_BYTES = 8L * 1024L * 1024L
    private const val TARGET_COMPRESSED_IMAGE_BYTES = 4L * 1024L * 1024L
    private const val MAX_DIMENSION = 1920

    fun init(context: Context) {
        if (!isInitialized) {
            try {
                appContext = context.applicationContext
                val config = mapOf(
                    "cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME,
                    "secure" to true
                )
                MediaManager.init(context, config)
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    suspend fun uploadImage(uri: Uri): ResultState<Pair<String, String>> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val uploadUri = prepareImageForUpload(uri)
                MediaManager.get().upload(uploadUri)
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
                                continuation.resume(ResultState.Error(mapUploadError(error.description)))
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

    suspend fun uploadVideo(uri: Uri, fileName: String): ResultState<String> =
        suspendCancellableCoroutine { continuation ->
            try {
                MediaManager.get().upload(uri)
                    .unsigned(BuildConfig.CLOUDINARY_UPLOAD_PRESET)
                    .option("resource_type", "video")
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
                                    ResultState.Error(mapUploadError(error.description))
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
                                    ResultState.Error(mapUploadError(error.description))
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

    private fun prepareImageForUpload(uri: Uri): Uri {
        val context = appContext ?: return uri
        val sizeBytes = getUriSize(context, uri)
        if (sizeBytes in 1..MAX_DIRECT_IMAGE_BYTES) {
            return uri
        }

        val bitmap = decodeScaledBitmap(context, uri, MAX_DIMENSION) ?: return uri
        val compressedBytes = compressBitmap(bitmap, TARGET_COMPRESSED_IMAGE_BYTES)
        bitmap.recycle()

        val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { output ->
            output.write(compressedBytes)
        }
        return Uri.fromFile(file)
    }

    private fun getUriSize(context: Context, uri: Uri): Long {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0) afd.length else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }

        val inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqMaxDimension: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth > reqMaxDimension || currentHeight > reqMaxDimension) {
            currentWidth /= 2
            currentHeight /= 2
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun compressBitmap(bitmap: Bitmap, targetBytes: Long): ByteArray {
        var quality = 90
        var result = ByteArray(0)
        while (quality >= 45) {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val current = stream.toByteArray()
            if (current.size <= targetBytes || quality == 45) {
                result = current
                break
            }
            quality -= 15
        }
        return result
    }

    private fun mapUploadError(rawMessage: String?): String {
        val message = rawMessage?.trim().orEmpty()
        val lower = message.lowercase()
        return when {
            lower.contains("file size too large") ||
                lower.contains("max file size") ||
                lower.contains("too large") -> {
                "Anh qua lon so voi gioi han server. Vui long chon anh nhe hon hoac giam do phan giai."
            }
            message.isNotEmpty() -> message
            else -> "Tai len that bai"
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
