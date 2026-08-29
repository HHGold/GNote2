package com.chinhsiang.premiumnotes.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotesFlow(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getNotesInFolder(folderId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getNotesInFolderFlow(folderId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE folderId != 'shared_with_me' ORDER BY updatedAt DESC")
    fun getAllNonSharedNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    fun getNoteById(noteId: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(notes: List<NoteEntity>)

    @Update
    fun update(note: NoteEntity)

    @Query("UPDATE notes SET folderId = :newFolderId, updatedAt = :updatedAt, isSynced = 0 WHERE id = :noteId")
    fun moveNoteToFolder(noteId: String, newFolderId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isSynced = :status WHERE id = :noteId")
    fun markAsSynced(noteId: String, status: Boolean = true)

    @Query("DELETE FROM notes WHERE id = :noteId")
    fun deleteById(noteId: String)

    @Query("DELETE FROM notes WHERE folderId = :folderId")
    fun deleteByFolderId(folderId: String)

    @Query("DELETE FROM notes")
    fun deleteAll()
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY orderIndex ASC, name ASC")
    fun getAllFolders(): List<FolderEntity>

    @Query("SELECT * FROM folders ORDER BY orderIndex ASC, name ASC")
    fun getAllFoldersFlow(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :folderId LIMIT 1")
    fun getFolderById(folderId: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(folders: List<FolderEntity>)

    @Update
    fun update(folder: FolderEntity)

    @Query("UPDATE folders SET name = :newName WHERE id = :folderId")
    fun renameFolder(folderId: String, newName: String)

    @Query("DELETE FROM folders WHERE id = :folderId")
    fun deleteById(folderId: String)

    @Query("DELETE FROM folders")
    fun deleteAll()
}
