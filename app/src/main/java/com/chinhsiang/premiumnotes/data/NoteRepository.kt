package com.chinhsiang.premiumnotes.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

// 資料模型
data class NoteFolder(val id: String = UUID.randomUUID().toString(), val name: String)
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val folderId: String,
    var title: String = "",
    var content: String = "",
    var isLocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var ownerId: String? = null,
    var ownerEmail: String? = null,
    var ownerName: String? = null,
    var sharedWithEmails: List<String>? = null,
    val isSynced: Boolean = false
)

/** Session 內的刪除記錄（Tombstone）
 *  用來防止 realtimeNotesFlow 在 Firestore 刪除尚未完成時把筆記「復活」 */
object DeletedNoteTracker {
    private val _ids = mutableSetOf<String>()
    fun markDeleted(id: String) { _ids.add(id) }
    fun isDeleted(id: String): Boolean = _ids.contains(id)
    fun clear(id: String) { _ids.remove(id) }
}

// 本機資料管理
class NoteRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gnote_data", Context.MODE_PRIVATE)
    private val gson = Gson()

    // --- 資料夾 CRUD ---
    fun getFolders(): MutableList<NoteFolder> {
        val json = prefs.getString("folders", null) ?: return ensureDefaultFolder()
        val type = object : TypeToken<MutableList<NoteFolder>>() {}.type
        val folders: MutableList<NoteFolder> = gson.fromJson(json, type) ?: ensureDefaultFolder()
        if (folders.none { it.id == "all" }) {
            folders.add(0, NoteFolder(id = "all", name = "全部"))
            saveFolders(folders)
        }
        return folders
    }

    private fun ensureDefaultFolder(): MutableList<NoteFolder> {
        val folders = mutableListOf(NoteFolder(id = "all", name = "全部"))
        saveFolders(folders)
        return folders
    }

    fun saveFolders(folders: List<NoteFolder>) {
        prefs.edit().putString("folders", gson.toJson(folders)).apply()
    }

    // 供 FirestoreSync 使用：完全覆蓋本機資料夾列表
    fun saveFoldersFromSync(folders: List<NoteFolder>) {
        val mutable = folders.toMutableList()
        if (mutable.none { it.id == "all" }) {
            mutable.add(0, NoteFolder(id = "all", name = "全部"))
        }
        saveFolders(mutable)
    }

    // 供 FirestoreSync 使用：完全覆蓋本機筆記列表
    fun saveNotesFromSync(notes: List<Note>) = saveAllNotes(notes)

    fun addFolder(name: String): NoteFolder? {
        if (name.trim() == "全部") return null
        val folders = getFolders()
        if (folders.any { it.name == name.trim() }) return null
        
        val folder = NoteFolder(name = name.trim())
        folders.add(folder)
        saveFolders(folders)
        return folder
    }

    fun renameFolder(id: String, newName: String) {
        if (id == "all") return // 不允許重命名「全部」
        val folders = getFolders()
        val index = folders.indexOfFirst { it.id == id }
        if (index >= 0) {
            folders[index] = folders[index].copy(name = newName)
            saveFolders(folders)
        }
    }

    fun deleteFolder(id: String) {
        if (id == "all") return // 不允許刪除「全部」
        val folders = getFolders().filter { it.id != id }
        saveFolders(folders)
        
        // 將該資料夾內的所有筆記搬移到「全部」
        val notes = getAllNotes()
        notes.forEachIndexed { index, note ->
            if (note.folderId == id) {
                notes[index] = note.copy(folderId = "all")
            }
        }
        saveAllNotes(notes)
    }

    // --- 筆記 CRUD ---
    fun getAllNotes(): MutableList<Note> {
        val json = prefs.getString("notes", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<Note>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun saveAllNotes(notes: List<Note>) {
        prefs.edit().putString("notes", gson.toJson(notes)).apply()
    }

    fun getNotesInFolder(folderId: String): List<Note> {
        val allNotes = getAllNotes()
        return if (folderId == "all") {
            // 「全部」不包含共享給我的筆記
            allNotes.filter { it.folderId != "shared_with_me" }
                .sortedByDescending { it.updatedAt }
        } else {
            allNotes.filter { it.folderId == folderId }
                .sortedByDescending { it.updatedAt }
        }
    }

    fun getNote(noteId: String): Note? {
        return getAllNotes().find { it.id == noteId }
    }

    fun createNote(folderId: String, ownerId: String? = null, ownerEmail: String? = null, ownerName: String? = null): Note {
        val notes = getAllNotes()
        val note = Note(
            folderId = folderId,
            ownerId = ownerId,
            ownerEmail = ownerEmail,
            ownerName = ownerName
        )
        notes.add(note)
        saveAllNotes(notes)
        return note
    }

    fun saveNote(noteId: String, title: String, content: String, isLocked: Boolean = false, sharedWithEmails: List<String>? = null) {
        val notes = getAllNotes()
        val index = notes.indexOfFirst { it.id == noteId }
        if (index >= 0) {
            notes[index] = notes[index].copy(
                title = title,
                content = content,
                isLocked = isLocked,
                sharedWithEmails = sharedWithEmails ?: notes[index].sharedWithEmails,
                updatedAt = System.currentTimeMillis(),
                isSynced = false // 修改後重新標記為未同步
            )
            saveAllNotes(notes)
        }
    }

    fun deleteNote(noteId: String) {
        val notes = getAllNotes().filter { it.id != noteId }
        saveAllNotes(notes)
    }

    fun markAsSynced(noteId: String, status: Boolean = true) {
        val notes = getAllNotes()
        val index = notes.indexOfFirst { it.id == noteId }
        if (index >= 0) {
            notes[index] = notes[index].copy(isSynced = status)
            saveAllNotes(notes)
        }
    }
}
