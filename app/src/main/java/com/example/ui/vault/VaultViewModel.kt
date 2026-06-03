package com.example.ui.vault

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.FolderEntity
import com.example.data.database.SettingsEntity
import com.example.data.database.VaultItemEntity
import com.example.data.repository.VaultRepository
import com.example.data.security.StorageManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class VaultEffect {
    data class ShowToast(val message: String) : VaultEffect()
    object LockVault : VaultEffect()
}

class VaultViewModel(private val repository: VaultRepository) : ViewModel() {

    private val TAG = "VaultViewModel"

    // Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Active Tab tracking ("PHOTOS" or "VIDEOS")
    private val _activeTab = MutableStateFlow("PHOTOS")
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    // Effects
    private val _effects = MutableSharedFlow<VaultEffect>()
    val effects: SharedFlow<VaultEffect> = _effects.asSharedFlow()

    // Settings
    val settingsState: StateFlow<SettingsEntity?> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val isVaultUnlocked: StateFlow<Boolean> = repository.isVaultUnlocked

    // Folders reactive queries based on current tab
    val foldersList: StateFlow<List<FolderEntity>> = _activeTab
        .combine(repository.getFoldersByType("PHOTO")) { tab, photoFolders ->
            // In Repository, we query by photo/video type.
            // Let's retrieve files based on appropriate category
            if (tab == "PHOTOS") {
                photoFolders
            } else {
                // If VIDEOS, we can fetch from repository video folders
                // We will collect folders of correct category
                repository.getFoldersByType("VIDEO").firstOrNull() ?: emptyList()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Items active query (root items of current category)
    val rootItemsList: StateFlow<List<VaultItemEntity>> = _activeTab
        .combine(repository.getAllItems()) { tab, allItems ->
            val expectedType = if (tab == "PHOTOS") "PHOTO" else "VIDEO"
            allItems.filter { it.folderId == null && it.fileType == expectedType }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // All Items combined for search view or calculations
    val allItemsList: StateFlow<List<VaultItemEntity>> = repository.getAllItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Search items results
    val searchResults: StateFlow<List<VaultItemEntity>> = _searchQuery
        .combine(repository.getAllItems()) { query, items ->
            if (query.trim().isEmpty()) {
                emptyList()
            } else {
                items.filter { it.fileName.contains(query, ignoreCase = true) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Storage info details
    private val _storageInfo = MutableStateFlow("Recalculating...")
    val storageInfo: StateFlow<String> = _storageInfo.asStateFlow()

    private val _vaultSizeBytes = MutableStateFlow(0L)
    val vaultSizeBytes: StateFlow<Long> = _vaultSizeBytes.asStateFlow()

    init {
        // Recalculate stats reactively
        viewModelScope.launch {
            repository.getAllItems().collect {
                // Trigger stats calculation
                _displayExpressionRecalculate()
            }
        }
    }

    fun setActiveTab(tab: String) {
        _activeTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun lockVault() {
        repository.lockVault()
        viewModelScope.launch {
            _effects.emit(VaultEffect.LockVault)
        }
    }

    // Creating folders
    fun createNewFolder(name: String) {
        if (name.trim().isEmpty()) return
        viewModelScope.launch {
            val type = if (_activeTab.value == "PHOTOS") "PHOTO" else "VIDEO"
            repository.createFolder(name, type)
            sendToast("Folder '$name' created successfully")
        }
    }

    // Delete folder
    fun deleteFolder(folder: FolderEntity, context: Context) {
        viewModelScope.launch {
            repository.deleteFolderWithContents(folder, context)
            _displayExpressionRecalculate()
            sendToast("Folder and all its contents deleted securely")
        }
    }

    // Import files
    fun importFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            val type = if (_activeTab.value == "PHOTOS") "PHOTO" else "VIDEO"
            val success = repository.importFile(
                context = context,
                uri = uri,
                type = type,
                folderId = null
            )
            if (success) {
                _displayExpressionRecalculate()
                sendToast("Import complete. File encrypted in Vault!")
            } else {
                sendToast("Failed of import. Ensure PIN code initialized.")
            }
        }
    }

    // Import file to inside visual folder spec
    fun importFileToFolder(context: Context, uri: Uri, folderId: Int) {
        viewModelScope.launch {
            val type = if (_activeTab.value == "PHOTOS") "PHOTO" else "VIDEO"
            val success = repository.importFile(
                context = context,
                uri = uri,
                type = type,
                folderId = folderId
            )
            if (success) {
                _displayExpressionRecalculate()
                sendToast("Import complete. File saved in folder!")
            } else {
                sendToast("Failed to import file.")
            }
        }
    }

    // Delete item
    fun deleteItem(context: Context, item: VaultItemEntity) {
        viewModelScope.launch {
            repository.deleteItem(context, item)
            _displayExpressionRecalculate()
            sendToast("File deleted securely")
        }
    }

    // Settings Toggle screenshot prevention
    fun toggleScreenshotPrevention(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsState.value ?: return@launch
            repository.updatePreferenceSettings(
                preventScreenshots = enabled,
                useDarkMode = current.useDarkMode
            )
            sendToast(if (enabled) "Screenshot protection enabled" else "Screenshot protection disabled")
        }
    }

    // Settings Toggle theme
    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsState.value ?: return@launch
            repository.updatePreferenceSettings(
                preventScreenshots = current.preventScreenshots,
                useDarkMode = enabled
            )
        }
    }

    // Change Secure PIN
    fun changeSecurePin(oldPin: String, newPin: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.changePin(oldPin, newPin)
            if (success) {
                sendToast("PIN updated successfully")
            } else {
                sendToast("Mismatch of old PIN")
            }
            onComplete(success)
        }
    }

    // Recalculates storage metrics safely
    private fun _displayExpressionRecalculate() {
        val allItems = allItemsList.value
        val totalVaultSize = allItems.sumOf { it.fileSize }
        _vaultSizeBytes.value = totalVaultSize

        val photoSize = allItems.filter { it.fileType == "PHOTO" }.sumOf { it.fileSize }
        val videoSize = allItems.filter { it.fileType == "VIDEO" }.sumOf { it.fileSize }

        val fmtTotal = StorageManager.formatFileSize(totalVaultSize)
        val fmtPhoto = StorageManager.formatFileSize(photoSize)
        val fmtVideo = StorageManager.formatFileSize(videoSize)

        _storageInfo.value = "Vault Size: $fmtTotal ($fmtPhoto Photos, $fmtVideo Videos)"
    }

    private fun sendToast(msg: String) {
        viewModelScope.launch {
            _effects.emit(VaultEffect.ShowToast(msg))
        }
    }

    // Imports sample mock files in database to let users test instantly
    fun seedDemoContentIfEmpty(context: Context) {
        viewModelScope.launch {
            val items = repository.getAllItems().firstOrNull() ?: emptyList()
            if (items.isNotEmpty()) return@launch

            // Create some pretty mock patterns like colored gradient boxes representing offline photos/videos
            val samplePhotoBytes = generateSolidBitmapBytes(accentColorRed = true)
            repository.importDemoContent(context, "Sunset_Preview.jpg", "PHOTO", samplePhotoBytes)

            val samplePhotoBytes2 = generateSolidBitmapBytes(accentColorRed = false)
            repository.importDemoContent(context, "Tech_Design_Idea.jpg", "PHOTO", samplePhotoBytes2)

            val sampleVideoBytes = generateSolidBitmapBytes(accentColorRed = true) // simulated video container
            repository.importDemoContent(context, "Vacation_Teaser.mp4", "VIDEO", sampleVideoBytes)
            
            _displayExpressionRecalculate()
        }
    }

    // Decrypts image directly to byte array for standard custom image viewers or Coil display
    fun getDecryptedBytes(context: Context, secureFileName: String): ByteArray? {
        val key = repository.getActiveMasterKey() ?: return null
        return StorageManager.decryptToBytes(context, secureFileName, key)
    }

    // Decrypts video file to an autonomous, clear temporary file for native Android video playback streaming
    fun getDecryptedTempFile(context: Context, secureFileName: String): java.io.File? {
        val key = repository.getActiveMasterKey() ?: return null
        return StorageManager.decryptToTempFile(context, secureFileName, key)
    }

    // Helper to generate elegant solid images in-memory to simulate initial camera capture imports if gallery is dry
    private fun generateSolidBitmapBytes(accentColorRed: Boolean): ByteArray {
        val width = 300
        val height = 300
        val r = if (accentColorRed) 0xFF else 0x0A
        val g = if (accentColorRed) 0x6B else 0xCE
        val b = if (accentColorRed) 0x3E else 0x5C

        // Very simple uncompressed BMP file wrapper for Compose Coil reading compatibility
        val fileSize = 54 + width * height * 3
        val bmp = ByteArray(fileSize)

        // BM header
        bmp[0] = 'B'.code.toByte()
        bmp[1] = 'M'.code.toByte()
        // File size
        bmp[2] = (fileSize and 0xff).toByte()
        bmp[3] = ((fileSize shr 8) and 0xff).toByte()
        bmp[4] = ((fileSize shr 16) and 0xff).toByte()
        bmp[5] = ((fileSize shr 24) and 0xff).toByte()
        // Offset
        bmp[10] = 54
        // Header size
        bmp[14] = 40
        // Width
        bmp[18] = (width and 0xff).toByte()
        bmp[19] = ((width shr 8) and 0xff).toByte()
        bmp[20] = ((width shr 16) and 0xff).toByte()
        bmp[21] = ((width shr 24) and 0xff).toByte()
        // Height
        bmp[22] = (height and 0xff).toByte()
        bmp[23] = ((height shr 8) and 0xff).toByte()
        bmp[24] = ((height shr 16) and 0xff).toByte()
        bmp[25] = ((height shr 24) and 0xff).toByte()
        // Planes
        bmp[26] = 1
        // Bits per pixel
        bmp[28] = 24

        // Raster bytes
        var idx = 54
        for (y in 0 until height) {
            for (x in 0 until width) {
                bmp[idx++] = b.toByte()
                bmp[idx++] = g.toByte()
                bmp[idx++] = r.toByte()
            }
        }
        return bmp
    }
}
