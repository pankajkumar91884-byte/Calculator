package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1, // Singleton row
    val isPinSet: Boolean = false,
    val pinHash: String = "",
    val encryptedMasterKey: ByteArray? = null,
    val preventScreenshots: Boolean = false,
    val useDarkMode: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SettingsEntity

        if (id != other.id) return false
        if (isPinSet != other.isPinSet) return false
        if (pinHash != other.pinHash) return false
        if (encryptedMasterKey != null) {
            if (other.encryptedMasterKey == null) return false
            if (!encryptedMasterKey.contentEquals(other.encryptedMasterKey)) return false
        } else if (other.encryptedMasterKey != null) return false
        if (preventScreenshots != other.preventScreenshots) return false
        if (useDarkMode != other.useDarkMode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + isPinSet.hashCode()
        result = 31 * result + pinHash.hashCode()
        result = 31 * result + (encryptedMasterKey?.contentHashCode() ?: 0)
        result = 31 * result + preventScreenshots.hashCode()
        result = 31 * result + useDarkMode.hashCode()
        return result
    }
}
