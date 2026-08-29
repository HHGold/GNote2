package com.chinhsiang.premiumnotes.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.chinhsiang.premiumnotes.data.db.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

// 資料模型
data class NoteFolder(val id: String = UUID.randomUUID().toString(), val name: String)
data class Note(
    val id: String = UUID.randomUUID().toString(),
    var folderId: String,
    var title: String = "",
    var content: String = "",
    var isLocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var ownerId: String? = null,
    var ownerEmail: String? = null,
    var ownerName: String? = null,
    var sharedWithEmails: List<String>? = null,
    var isSynced: Boolean = false
)

/** Session 內的刪除記錄（Tombstone）
 *  用來防止 realtimeNotesFlow 在 Firestore 刪除尚未完成時把筆記「復活」 */
object DeletedNoteTracker {
    private val _ids = mutableSetOf<String>()
    fun markDeleted(id: String) { _ids.add(id) }
    fun isDeleted(id: String): Boolean = _ids.contains(id)
    fun clear(id: String) { _ids.remove(id) }
}

// 本機資料管理（基於 Android 官方推薦的 Room SQLite 資料庫）
class NoteRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val noteDao = db.noteDao()
    private val folderDao = db.folderDao()
    private val prefs: SharedPreferences = context.getSharedPreferences("gnote_data", Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        migrateFromSharedPreferencesIfNeeded()
        ensureDefaultFolder()
    }

    /** 首次升級時，無縫將舊版 SharedPreferences 中的資料遷移至 Room SQLite */
    private fun migrateFromSharedPreferencesIfNeeded() {
        val hasMigrated = prefs.getBoolean("migrated_to_room_v1", false)
        if (!hasMigrated) {
            try {
                // 1. 遷移資料夾
                val foldersJson = prefs.getString("folders", null)
                if (!foldersJson.isNullOrEmpty()) {
                    val folderType = object : TypeToken<List<NoteFolder>>() {}.type
                    val oldFolders: List<NoteFolder>? = gson.fromJson(foldersJson, folderType)
                    if (!oldFolders.isNullOrEmpty()) {
                        folderDao.insertAll(oldFolders.mapIndexed { index, folder -> folder.toEntity(index) })
                        Log.d("NoteRepository", "成功遷移 ${oldFolders.size} 個資料夾至 Room")
                    }
                }

                // 2. 遷移筆記
                val notesJson = prefs.getString("notes", null)
                if (!notesJson.isNullOrEmpty()) {
                    val noteType = object : TypeToken<List<Note>>() {}.type
                    val oldNotes: List<Note>? = gson.fromJson(notesJson, noteType)
                    if (!oldNotes.isNullOrEmpty()) {
                        noteDao.insertAll(oldNotes.map { it.toEntity() })
                        Log.d("NoteRepository", "成功遷移 ${oldNotes.size} 篇筆記至 Room")
                    }
                }

                prefs.edit().putBoolean("migrated_to_room_v1", true).apply()
            } catch (e: Exception) {
                Log.e("NoteRepository", "SharedPreferences 資料遷移失敗", e)
            }
        }
    }

    // --- 資料夾 CRUD ---
    fun getFolders(): MutableList<NoteFolder> {
        val entities = folderDao.getAllFolders()
        val folders = entities.map { it.toDomain() }.toMutableList()
        if (folders.none { it.id == "all" }) {
            val allFolder = NoteFolder(id = "all", name = "全部")
            folderDao.insert(allFolder.toEntity(0))
            folders.add(0, allFolder)
        }
        return folders
    }

    private fun ensureDefaultFolder(): MutableList<NoteFolder> {
        val folders = folderDao.getAllFolders().map { it.toDomain() }.toMutableList()
        if (folders.none { it.id == "all" }) {
            val allFolder = NoteFolder(id = "all", name = "全部")
            folderDao.insert(allFolder.toEntity(0))
            folders.add(0, allFolder)
        }
        return folders
    }

    fun saveFolders(folders: List<NoteFolder>) {
        folderDao.insertAll(folders.mapIndexed { index, folder -> folder.toEntity(index) })
    }

    // 供 FirestoreSync 使用：完全覆蓋本機資料夾列表
    fun saveFoldersFromSync(folders: List<NoteFolder>) {
        val mutable = folders.toMutableList()
        if (mutable.none { it.id == "all" }) {
            mutable.add(0, NoteFolder(id = "all", name = "全部"))
        }
        folderDao.insertAll(mutable.mapIndexed { index, folder -> folder.toEntity(index) })
    }

    // 供 FirestoreSync 使用：完全覆蓋本機筆記列表
    fun saveNotesFromSync(notes: List<Note>) = saveAllNotes(notes)

    fun addFolder(name: String): NoteFolder? {
        val trimmed = name.trim()
        if (trimmed == "全部" || trimmed.isEmpty()) return null
        val existing = folderDao.getAllFolders()
        if (existing.any { it.name == trimmed }) return null

        val newFolder = NoteFolder(name = trimmed)
        val maxIndex = existing.maxOfOrNull { it.orderIndex } ?: 0
        folderDao.insert(newFolder.toEntity(maxIndex + 1))
        return newFolder
    }

    fun renameFolder(id: String, newName: String) {
        if (id == "all") return // 不允許重命名「全部」
        folderDao.renameFolder(id, newName.trim())
    }

    fun deleteFolder(id: String) {
        if (id == "all") return // 不允許刪除「全部」
        folderDao.deleteById(id)

        // 將該資料夾內的所有筆記搬移到「全部」
        val notesInFolder = noteDao.getNotesInFolder(id)
        notesInFolder.forEach {
            noteDao.moveNoteToFolder(it.id, "all", System.currentTimeMillis())
        }
    }

    // --- 筆記 CRUD ---
    fun getAllNotes(): MutableList<Note> {
        return noteDao.getAllNotes().map { it.toDomain() }.toMutableList()
    }

    private fun saveAllNotes(notes: List<Note>) {
        noteDao.insertAll(notes.map { it.toEntity() })
    }

    fun getNotesInFolder(folderId: String): List<Note> {
        return if (folderId == "all") {
            // 「全部」不包含共享給我的筆記
            noteDao.getAllNonSharedNotes().map { it.toDomain() }
        } else {
            noteDao.getNotesInFolder(folderId).map { it.toDomain() }
        }
    }

    fun getNote(noteId: String): Note? {
        return noteDao.getNoteById(noteId)?.toDomain()
    }

    fun createNote(folderId: String, ownerId: String? = null, ownerEmail: String? = null, ownerName: String? = null): Note {
        val note = Note(
            folderId = folderId,
            ownerId = ownerId,
            ownerEmail = ownerEmail,
            ownerName = ownerName
        )
        noteDao.insert(note.toEntity())
        return note
    }

    fun saveNote(noteId: String, title: String, content: String, isLocked: Boolean = false, sharedWithEmails: List<String>? = null) {
        val existing = noteDao.getNoteById(noteId)
        if (existing != null) {
            val updated = existing.copy(
                title = title,
                content = content,
                isLocked = isLocked,
                sharedWithEmails = sharedWithEmails ?: existing.sharedWithEmails,
                updatedAt = System.currentTimeMillis(),
                isSynced = false // 修改後重新標記為未同步
            )
            noteDao.update(updated)
        }
    }

    /** 將筆記移動至新的資料夾 */
    fun moveNoteToFolder(noteId: String, newFolderId: String) {
        noteDao.moveNoteToFolder(noteId, newFolderId, System.currentTimeMillis())
    }

    fun deleteNote(noteId: String) {
        noteDao.deleteById(noteId)
    }

    fun markAsSynced(noteId: String, status: Boolean = true) {
        noteDao.markAsSynced(noteId, status)
    }
}
