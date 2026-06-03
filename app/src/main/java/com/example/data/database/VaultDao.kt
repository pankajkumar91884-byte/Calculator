package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    // Settings Operations
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<SettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: SettingsEntity)

    // Folder Operations
    @Query("SELECT * FROM folders WHERE folderType = :type ORDER BY name ASC")
    fun getFoldersByTypeFlow(type: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Int): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    // Vault Item Operations
    @Query("SELECT * FROM vault_items WHERE folderId IS NULL AND fileType = :type ORDER BY addedTimestamp DESC")
    fun getRootItemsByTypeFlow(type: String): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE folderId = :folderId ORDER BY addedTimestamp DESC")
    fun getItemsInFolderFlow(folderId: Int): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE fileType = :type ORDER BY addedTimestamp DESC")
    fun getAllItemsByTypeFlow(type: String): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items ORDER BY addedTimestamp DESC")
    fun getAllItemsFlow(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getItemById(id: Int): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE fileName LIKE '%' || :query || '%' ORDER BY addedTimestamp DESC")
    fun searchItemsFlow(query: String): Flow<List<VaultItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: VaultItemEntity)

    @Delete
    suspend fun deleteItem(item: VaultItemEntity)
    
    @Query("DELETE FROM vault_items WHERE folderId = :folderId")
    suspend fun deleteItemsInFolder(folderId: Int)
}
