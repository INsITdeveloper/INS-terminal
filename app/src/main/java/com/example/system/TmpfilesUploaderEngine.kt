package com.example.system

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class TmpfilesUploadResult(
    val success: Boolean,
    val pageUrl: String? = null,
    val directDownloadUrl: String? = null,
    val fileName: String? = null,
    val fileSizeFormatted: String? = null,
    val rawResponse: String? = null,
    val errorMessage: String? = null
)

object TmpfilesUploaderEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val API_ENDPOINT = "https://tmpfiles.org/api/v1/upload"

    suspend fun uploadFile(file: File): TmpfilesUploadResult = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext TmpfilesUploadResult(
                success = false,
                errorMessage = "File not found: ${file.absolutePath}"
            )
        }

        if (!file.canRead()) {
            return@withContext TmpfilesUploadResult(
                success = false,
                errorMessage = "Cannot read file (Check storage permission): ${file.absolutePath}"
            )
        }

        try {
            val fileName = file.name
            val fileSize = formatFileSize(file.length())
            val mediaType = guessMimeType(fileName).toMediaTypeOrNull()
            val requestBody = file.asRequestBody(mediaType)

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, requestBody)
                .build()

            val request = Request.Builder()
                .url(API_ENDPOINT)
                .post(multipartBody)
                .header("User-Agent", "InsTerminal-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext TmpfilesUploadResult(
                    success = false,
                    rawResponse = responseBody,
                    errorMessage = "HTTP ${response.code}: ${response.message}"
                )
            }

            parseResponse(responseBody, fileName, fileSize)
        } catch (e: Exception) {
            TmpfilesUploadResult(
                success = false,
                errorMessage = "Upload failed: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    suspend fun uploadUri(context: Context, uri: Uri): TmpfilesUploadResult = withContext(Dispatchers.IO) {
        try {
            val fileName = queryFileName(context, uri) ?: "upload_${System.currentTimeMillis()}"
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext TmpfilesUploadResult(
                    success = false,
                    errorMessage = "Unable to open input stream for selected file."
                )

            val bytes = inputStream.use { it.readBytes() }
            val fileSize = formatFileSize(bytes.size.toLong())
            val mediaType = (context.contentResolver.getType(uri) ?: guessMimeType(fileName)).toMediaTypeOrNull()
            val requestBody = bytes.toRequestBody(mediaType)

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, requestBody)
                .build()

            val request = Request.Builder()
                .url(API_ENDPOINT)
                .post(multipartBody)
                .header("User-Agent", "InsTerminal-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext TmpfilesUploadResult(
                    success = false,
                    rawResponse = responseBody,
                    errorMessage = "HTTP ${response.code}: ${response.message}"
                )
            }

            parseResponse(responseBody, fileName, fileSize)
        } catch (e: Exception) {
            TmpfilesUploadResult(
                success = false,
                errorMessage = "Upload failed: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    private fun parseResponse(jsonStr: String, fileName: String, fileSize: String): TmpfilesUploadResult {
        return try {
            val json = JSONObject(jsonStr)
            val status = json.optString("status", "")
            if (status == "success" || json.has("data")) {
                val dataObj = json.getJSONObject("data")
                val url = dataObj.getString("url")

                val directDownloadUrl = if (url.contains("tmpfiles.org/") && !url.contains("tmpfiles.org/dl/")) {
                    url.replace("tmpfiles.org/", "tmpfiles.org/dl/")
                } else {
                    url
                }

                TmpfilesUploadResult(
                    success = true,
                    pageUrl = url,
                    directDownloadUrl = directDownloadUrl,
                    fileName = fileName,
                    fileSizeFormatted = fileSize,
                    rawResponse = jsonStr
                )
            } else {
                TmpfilesUploadResult(
                    success = false,
                    rawResponse = jsonStr,
                    errorMessage = json.optString("message", "API response did not indicate success.")
                )
            }
        } catch (e: Exception) {
            TmpfilesUploadResult(
                success = false,
                rawResponse = jsonStr,
                errorMessage = "Failed to parse API response: ${e.message}"
            )
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "tar", "gz" -> "application/gzip"
            "txt", "log", "cfg", "conf", "sh", "json", "xml" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(java.util.Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(java.util.Locale.US, "%.2f GB", gb)
    }
}
