package com.storytold.craftsuite.core.fileaccess

import android.content.ContentResolver
import android.net.Uri
import com.storytold.craftsuite.core.model.EditorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

sealed class FileAccessResult<out T> {
    data class Success<out T>(val data: T) : FileAccessResult<T>()
    data class Error(val errorType: FileErrorType, val message: String, val cause: Throwable? = null) : FileAccessResult<Nothing>()
}

enum class FileErrorType {
    PERMISSION_DENIED,
    FILE_NOT_FOUND,
    INVALID_FORMAT,
    FILE_TOO_LARGE,
    IO_ERROR,
    UNSUPPORTED_FORMAT
}

interface DocumentIoHandler {
    suspend fun readBytes(uri: Uri): FileAccessResult<ByteArray>
    suspend fun writeBytes(uri: Uri, bytes: ByteArray): FileAccessResult<Unit>
    fun validateHeader(bytes: ByteArray, expectedType: EditorType): FileAccessResult<Unit>
}

class AndroidSafDocumentManager(
    private val contentResolver: ContentResolver,
    private val maxFileSizeBytes: Long = 50 * 1024 * 1024 // 50MB safety limit
) : DocumentIoHandler {

    override suspend fun readBytes(uri: Uri): FileAccessResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext FileAccessResult.Error(
                    FileErrorType.FILE_NOT_FOUND,
                    "Unable to open input stream for URI: $uri"
                )

            inputStream.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val temp = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (stream.read(temp).also { bytesRead = it } != -1) {
                    totalRead += bytesRead
                    if (totalRead > maxFileSizeBytes) {
                        return@withContext FileAccessResult.Error(
                            FileErrorType.FILE_TOO_LARGE,
                            "File exceeds maximum allowed size (${maxFileSizeBytes / (1024 * 1024)}MB)"
                        )
                    }
                    buffer.write(temp, 0, bytesRead)
                }

                FileAccessResult.Success(buffer.toByteArray())
            }
        } catch (e: SecurityException) {
            FileAccessResult.Error(FileErrorType.PERMISSION_DENIED, "Access permission revoked or expired", e)
        } catch (e: Exception) {
            FileAccessResult.Error(FileErrorType.IO_ERROR, "Failed to read file: ${e.localizedMessage}", e)
        }
    }

    override suspend fun writeBytes(uri: Uri, bytes: ByteArray): FileAccessResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val outputStream = contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext FileAccessResult.Error(
                    FileErrorType.FILE_NOT_FOUND,
                    "Unable to open output stream for URI: $uri"
                )

            outputStream.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            FileAccessResult.Success(Unit)
        } catch (e: SecurityException) {
            FileAccessResult.Error(FileErrorType.PERMISSION_DENIED, "Write permission denied for document", e)
        } catch (e: Exception) {
            FileAccessResult.Error(FileErrorType.IO_ERROR, "Failed to write file: ${e.localizedMessage}", e)
        }
    }

    override fun validateHeader(bytes: ByteArray, expectedType: EditorType): FileAccessResult<Unit> {
        if (bytes.isEmpty()) {
            return FileAccessResult.Error(FileErrorType.INVALID_FORMAT, "File is empty (0 bytes)")
        }

        // Standard OOXML (docx, xlsx, pptx) are ZIP archives starting with magic bytes: PK\x03\x04
        val isZip = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte()) &&
                (bytes[3] == 0x04.toByte() || bytes[3] == 0x06.toByte() || bytes[3] == 0x08.toByte())

        // Also permit JSON/plain text serialization for internal session recovery
        val isJsonOrText = bytes[0] == '{'.code.toByte() || bytes[0] == '['.code.toByte() || bytes[0] > 0x1F.toByte()

        return if (isZip || isJsonOrText) {
            FileAccessResult.Success(Unit)
        } else {
            FileAccessResult.Error(
                FileErrorType.INVALID_FORMAT,
                "File does not have a valid ${expectedType.extension.uppercase()} or compatible document signature"
            )
        }
    }
}
