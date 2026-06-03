package com.example.data.security

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGORITHM = "AES"

    // Generates a fully random 256-bit AES Master Key
    fun generateMasterKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    // Encrypts the Master Key with the user's PIN to store in DB
    fun encryptMasterKey(pin: String, masterKey: ByteArray): ByteArray {
        val pinKeySpec = deriveKeyFromPin(pin)
        val iv = deriveIvFromPin(pin)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, pinKeySpec, iv)
        return cipher.doFinal(masterKey)
    }

    // Decrypts the Master Key using the user's PIN
    fun decryptMasterKey(pin: String, encryptedMasterKey: ByteArray): ByteArray? {
        return try {
            val pinKeySpec = deriveKeyFromPin(pin)
            val iv = deriveIvFromPin(pin)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, pinKeySpec, iv)
            cipher.doFinal(encryptedMasterKey)
        } catch (e: Exception) {
            null
        }
    }

    // Encryption of private files using the decrypted Master Key
    fun encryptFile(masterKey: ByteArray, input: InputStream, output: OutputStream) {
        val keySpec = SecretKeySpec(masterKey, KEY_ALGORITHM)
        // For files, we use a constant derived IV from the master key to keep it 100% self-contained
        val ivSpec = deriveIvFromKey(masterKey)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        
        CipherOutputStream(output, cipher).use { cos ->
            val buffer = ByteArray(16384)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                cos.write(buffer, 0, bytesRead)
            }
            cos.flush()
        }
    }

    // Decryption of private files
    fun decryptFile(masterKey: ByteArray, input: InputStream, output: OutputStream) {
        val keySpec = SecretKeySpec(masterKey, KEY_ALGORITHM)
        val ivSpec = deriveIvFromKey(masterKey)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        
        CipherInputStream(input, cipher).use { cis ->
            val buffer = ByteArray(16384)
            var bytesRead: Int
            while (cis.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            output.flush()
        }
    }

    // Decryption directly to bytes (ideal for photos displayed in Compose)
    fun decryptFileToBytes(masterKey: ByteArray, input: InputStream): ByteArray {
        val keySpec = SecretKeySpec(masterKey, KEY_ALGORITHM)
        val ivSpec = deriveIvFromKey(masterKey)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        
        CipherInputStream(input, cipher).use { cis ->
            return cis.readBytes()
        }
    }

    // Derivatives
    private fun deriveKeyFromPin(pin: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(hashed, KEY_ALGORITHM)
    }

    private fun deriveIvFromPin(pin: String): IvParameterSpec {
        val digest = MessageDigest.getInstance("MD5")
        val hashed = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return IvParameterSpec(hashed)
    }

    private fun deriveIvFromKey(key: ByteArray): IvParameterSpec {
        val digest = MessageDigest.getInstance("MD5")
        val ivBytes = digest.digest(key)
        return IvParameterSpec(ivBytes)
    }
}
