package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val fileType: String, // "PHOTO" or "VIDEO"
    val secureFileName: String, // Actual file name in storage (e.g. encrypted_UUID)
    val folderId: Int?, // Linked folder, or null if root list
    val fileSize: Long,
    val duration: Long?, // In milliseconds, for videos
    val addedTimestamp: Long = System.currentTimeMillis()
)
