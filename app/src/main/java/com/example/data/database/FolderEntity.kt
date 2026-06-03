package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val folderType: String, // "PHOTO" or "VIDEO"
    val createdTimestamp: Long = System.currentTimeMillis()
)
