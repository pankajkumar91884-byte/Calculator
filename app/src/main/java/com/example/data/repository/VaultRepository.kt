package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.database.FolderEntity
import com.example.data.database.SettingsEntity
import com.example.data.database.VaultDao
import com.example.data.database.VaultItemEntity
import com.example.data.security.CryptoManager
import com.example.data.security.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class VaultRepository(private val vaultDao: VaultDao) {

    companion object {
        private const val TAG = "VaultRepository"
    }

    // In-memory decrypted master key
    private var activeMasterKey: ByteArray? = null
    
    // Reactive verification of unlock state
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    fun getActiveMasterKey(): ByteArray? = activeMasterKey

    // Hashes a PIN code using SHA-256 for secure DB comparison
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // Get reactive Settings Flow
    val settingsFlow: Flow<SettingsEntity?> = vaultDao.getSettingsFlow()

    suspend fun getSettingsDirect(): SettingsEntity? = withContext(Dispatchers.IO) {
        vaultDao.getSettingsDirect()
    }

    // Initialize PIN and Master Key for the very first launch
    suspend fun setupPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val masterKey = CryptoManager.generateMasterKey()
            val encryptedKey = CryptoManager.encryptMasterKey(pin, masterKey)
            val hash = hashPin(pin)

            val currentSettings = vaultDao.getSettingsDirect() ?: SettingsEntity()
            val newSettings = currentSettings.copy(
                isPinSet = true,
                pinHash = hash,
                encryptedMasterKey = encryptedKey
            )
            vaultDao.upsertSettings(newSettings)
            
            // Auto unlock with the newly set PIN
            activeMasterKey = masterKey
            _isVaultUnlocked.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up PIN", e)
            false
        }
    }

    // Unlocks the Vault by checking pinHash and decrypting the stored master key in memory
    suspend fun unlockVault(pin: String): Boolean = withContext(Dispatchers.IO) {
        val settings = vaultDao.getSettingsDirect() ?: return@withContext false
        if (!settings.isPinSet) return@withContext false

        val enteredHash = hashPin(pin)
        if (settings.pinHash != enteredHash) {
            return@withContext false
        }

        val encryptedMk = settings.encryptedMasterKey ?: return@withContext false
        val decryptedMk = CryptoManager.decryptMasterKey(pin, encryptedMk)

        if (decryptedMk != null) {
            activeMasterKey = decryptedMk
            _isVaultUnlocked.value = true
            true
        } else {
            false
        }
    }

    // Lock the Vault securely clearing keys from memory
    fun lockVault() {
        activeMasterKey = null
        _isVaultUnlocked.value = false
    }

    // Changes the PIN without re-encrypting the vault (it just re-encrypts the master key!)
    suspend fun changePin(oldPin: String, newPin: String): Boolean = withContext(Dispatchers.IO) {
        val settings = vaultDao.getSettingsDirect() ?: return@withContext false
        val oldHash = hashPin(oldPin)
        if (settings.pinHash != oldHash) return@withContext false

        val currentMk = activeMasterKey ?: CryptoManager.decryptMasterKey(oldPin, settings.encryptedMasterKey ?: return@withContext false)
        if (currentMk == null) return@withContext false

        val newEncryptedMk = CryptoManager.encryptMasterKey(newPin, currentMk)
        val newHash = hashPin(newPin)

        val updatedSettings = settings.copy(
            pinHash = newHash,
            encryptedMasterKey = newEncryptedMk
        )
        vaultDao.upsertSettings(updatedSettings)
        
        // Refresh active master key
        activeMasterKey = currentMk
        true
    }

    // Update other simple configurations
    suspend fun updatePreferenceSettings(preventScreenshots: Boolean, useDarkMode: Boolean) = withContext(Dispatchers.IO) {
        val settings = vaultDao.getSettingsDirect() ?: SettingsEntity()
        vaultDao.upsertSettings(
            settings.copy(
                preventScreenshots = preventScreenshots,
                useDarkMode = useDarkMode
            )
        )
    }

    // Folder access
    fun getFoldersByType(type: String): Flow<List<FolderEntity>> = vaultDao.getFoldersByTypeFlow(type)
    
    suspend fun getFolderById(id: Int): FolderEntity? = withContext(Dispatchers.IO) {
        vaultDao.getFolderById(id)
    }

    suspend fun createFolder(name: String, type: String) = withContext(Dispatchers.IO) {
        vaultDao.insertFolder(FolderEntity(name = name, folderType = type))
    }

    suspend fun deleteFolderWithContents(folder: FolderEntity, context: Context) = withContext(Dispatchers.IO) {
        try {
            val items = vaultDao.getItemsInFolderFlow(folder.id).firstOrNull()
            items?.forEach { item ->
                StorageManager.deleteVaultFile(context, item.secureFileName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting physical files for folder: ${folder.name}", e)
        }
        vaultDao.deleteItemsInFolder(folder.id)
        vaultDao.deleteFolder(folder)
    }

    // Items access
    fun getRootItemsByType(type: String): Flow<List<VaultItemEntity>> = vaultDao.getRootItemsByTypeFlow(type)
    
    fun getItemsInFolder(folderId: Int): Flow<List<VaultItemEntity>> = vaultDao.getItemsInFolderFlow(folderId)

    fun getAllItems(): Flow<List<VaultItemEntity>> = vaultDao.getAllItemsFlow()

    fun searchItems(query: String): Flow<List<VaultItemEntity>> = vaultDao.searchItemsFlow(query)

    // Securely import a photo/video file from android picker Uri
    suspend fun importFile(
        context: Context,
        uri: Uri,
        type: String, // "PHOTO" or "VIDEO"
        folderId: Int? = null,
        duration: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val mKey = activeMasterKey ?: return@withContext false
        try {
            val (originalName, size) = StorageManager.getFileNameAndSizeFromUri(context, uri)
            val extension = if (type == "VIDEO") "mp4" else "jpg"
            val secureName = "enc_${UUID.randomUUID()}.$extension"

            val importedFile = StorageManager.importAndEncryptFile(context, uri, mKey, secureName)
            if (importedFile != null && importedFile.exists()) {
                val entity = VaultItemEntity(
                    fileName = originalName,
                    fileType = type,
                    secureFileName = secureName,
                    folderId = folderId,
                    fileSize = size,
                    duration = duration
                )
                vaultDao.insertItem(entity)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import file: $uri", e)
            false
        }
    }

    // Delete item from vault safely
    suspend fun deleteItem(context: Context, item: VaultItemEntity) = withContext(Dispatchers.IO) {
        StorageManager.deleteVaultFile(context, item.secureFileName)
        vaultDao.deleteItem(item)
    }

    // Create a demo photo/video inside the vault if user wants to play immediately, or for visual preview on first launch
    suspend fun importDemoContent(context: Context, name: String, type: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val mKey = activeMasterKey ?: return@withContext
        val extension = if (type == "VIDEO") "mp4" else "jpg"
        val secureName = "enc_demo_${UUID.randomUUID()}.$extension"
        val file = StorageManager.importRawBytesAndEncrypt(context, bytes, mKey, secureName)
        if (file != null && file.exists()) {
            val entity = VaultItemEntity(
                fileName = name,
                fileType = type,
                secureFileName = secureName,
                folderId = null,
                fileSize = bytes.size.toLong(),
                duration = if (type == "VIDEO") 5000L else null
            )
            vaultDao.insertItem(entity)
        }
    }
}
