package com.example.data.security

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat

object StorageManager {
    private const val TAG = "StorageManager"
    private const val VAULT_DIR = "secure_vault"
    private const val TEMP_DIR = "temp_cache"

    fun getVaultFolder(context: Context): File {
        val folder = File(context.filesDir, VAULT_DIR)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }

    private fun getTempFolder(context: Context): File {
        val folder = File(context.cacheDir, TEMP_DIR)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }

    fun cleanTempFolder(context: Context) {
        try {
            getTempFolder(context).deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning temp folder", e)
        }
    }

    // Encrypts and imports a file from a URI (from device gallery/picker)
    fun importAndEncryptFile(
        context: Context,
        uri: Uri,
        masterKey: ByteArray,
        outFileName: String
    ): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for URI: $uri")
                return null
            }

            val targetFile = File(getVaultFolder(context), outFileName)
            val outputStream = FileOutputStream(targetFile)

            CryptoManager.encryptFile(masterKey, inputStream, outputStream)
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed importing and encrypting file", e)
            null
        }
    }

    // Imports a file from a custom simulation source (e.g. assets or system resources) or raw data
    fun importRawBytesAndEncrypt(
        context: Context,
        bytes: ByteArray,
        masterKey: ByteArray,
        outFileName: String
    ): File? {
        return try {
            val targetFile = File(getVaultFolder(context), outFileName)
            val outputStream = FileOutputStream(targetFile)
            CryptoManager.encryptFile(masterKey, bytes.inputStream(), outputStream)
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed importing raw bytes", e)
            null
        }
    }

    // Decrypts an encrypted file to a temporary file for media players (e.g. Video View)
    fun decryptToTempFile(
        context: Context,
        secureFileName: String,
        masterKey: ByteArray
    ): File? {
        return try {
            val secureFile = File(getVaultFolder(context), secureFileName)
            if (!secureFile.exists()) return null

            val tempFile = File.createTempFile("playing_", ".mp4", getTempFolder(context))
            tempFile.deleteOnExit()

            val inputStream = FileInputStream(secureFile)
            val outputStream = FileOutputStream(tempFile)

            CryptoManager.decryptFile(masterKey, inputStream, outputStream)
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed decrypting to temp file", e)
            null
        }
    }

    // Reads and decrypts an encrypted file directly into bytes (perfect for Coil gallery images)
    fun decryptToBytes(
        context: Context,
        secureFileName: String,
        masterKey: ByteArray
    ): ByteArray? {
        return try {
            val secureFile = File(getVaultFolder(context), secureFileName)
            if (!secureFile.exists()) return null

            val inputStream = FileInputStream(secureFile)
            inputStream.use {
                CryptoManager.decryptFileToBytes(masterKey, it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed decrypting file to bytes", e)
            null
        }
    }

    // Deletes an encrypted file from the vault
    fun deleteVaultFile(context: Context, secureFileName: String): Boolean {
        val file = File(getVaultFolder(context), secureFileName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    // Get statistics: (vault size, free space, etc.)
    fun getVaultSize(context: Context): Long {
        var size = 0L
        val vaultDir = getVaultFolder(context)
        vaultDir.listFiles()?.forEach {
            if (it.isFile) {
                size += it.length()
            }
        }
        return size
    }

    fun getFreeSpaceBytes(context: Context): Long {
        return context.filesDir.freeSpace
    }

    fun getTotalSpaceBytes(context: Context): Long {
        return context.filesDir.totalSpace
    }

    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    fun getFileNameAndSizeFromUri(context: Context, uri: Uri): Pair<String, Long> {
        var name = "unknown_${System.currentTimeMillis()}"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex)
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
        return Pair(name, size)
    }
}
