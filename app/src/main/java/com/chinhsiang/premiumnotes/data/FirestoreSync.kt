package com.chinhsiang.premiumnotes.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreSync {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val uid: String? get() = try { auth.currentUser?.uid } catch (_: Throwable) { null }
    private val email: String? get() = try { auth.currentUser?.email?.lowercase() } catch (_: Throwable) { null }
    private val displayName: String? get() = try { auth.currentUser?.displayName } catch (_: Throwable) { null }

    fun isLoggedIn(): Boolean = uid != null

    // 個人資料夾：users/{uid}/folders/
    private fun foldersRef(uid: String) = db.collection("users").document(uid).collection("folders")

    // 個人筆記：users/{uid}/notes/
    private fun notesRef(uid: String) = db.collection("users").document(uid).collection("notes")

    // ===== 即時監聽（Snapshot Listener → Flow）=====

    /** 即時監聽雲端資料夾，有變動就推送新列表 */
    fun realtimeFoldersFlow(): Flow<List<NoteFolder>> = callbackFlow {
        val uid = uid ?: run { close(); return@callbackFlow }
        val listener = foldersRef(uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val folders = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: return@mapNotNull null
                val name = doc.getString("name") ?: return@mapNotNull null
                NoteFolder(id = id, name = name)
            }
            trySend(folders)
        }
        awaitClose { listener.remove() }
    }

    /** 即時監聽雲端筆記，有變動就推送新列表 */
    fun realtimeNotesFlow(): Flow<List<Note>> = callbackFlow {
        val uid = uid ?: run { close(); return@callbackFlow }
        val listener = notesRef(uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val notes = snapshot.documents.mapNotNull { doc ->
                parseNote(doc)
            }
            trySend(notes)
        }
        awaitClose { listener.remove() }
    }

        awaitClose { listener.remove() }
    }

    // ===== 資料夾 =====

    suspend fun uploadFolder(folder: NoteFolder): Boolean {
        return try {
            val uid = uid ?: return false
            val data = mapOf("id" to folder.id, "name" to folder.name)
            foldersRef(uid).document(folder.id).set(data, SetOptions.merge()).await()
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun deleteFolder(folderId: String) {
        try {
            val uid = uid ?: return
            foldersRef(uid).document(folderId).delete().await()
        } catch (_: Throwable) { }
    }

    suspend fun fetchFolders(): List<NoteFolder> {
        return try {
            val uid = uid ?: return emptyList()
            foldersRef(uid).get().await().documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: return@mapNotNull null
                val name = doc.getString("name") ?: return@mapNotNull null
                NoteFolder(id = id, name = name)
            }
        } catch (_: Throwable) { emptyList() }
    }

    // ===== 筆記 =====

    suspend fun uploadNote(note: Note): Boolean {
        return try {
            val uid = uid ?: return false
            val ownerId = if (note.ownerId.isNullOrEmpty()) uid else note.ownerId!!
            val ownerEmail = if (note.ownerEmail.isNullOrEmpty()) (email ?: "") else note.ownerEmail!!
            val ownerName = if (note.ownerName.isNullOrEmpty()) (displayName ?: "") else note.ownerName!!

            val data = mapOf(
                "id" to note.id,
                "folderId" to note.folderId,
                "title" to note.title,
                "content" to note.content,
                "isLocked" to note.isLocked,
                "createdAt" to note.createdAt,
                "updatedAt" to note.updatedAt,
                "ownerId" to ownerId,
                "ownerEmail" to ownerEmail,
                "ownerName" to ownerName,
                "sharedWithEmails" to (note.sharedWithEmails ?: emptyList<String>())
            )
            // 上傳到自己的個人筆記集合
            notesRef(uid).document(note.id).set(data, SetOptions.merge()).await()
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun deleteNote(noteId: String) {
        try {
            val uid = uid ?: return
            notesRef(uid).document(noteId).delete().await()
        } catch (_: Throwable) { }
    }

    suspend fun fetchNotes(): List<Note> {
        return try {
            val uid = uid ?: return emptyList()
            notesRef(uid).get().await().documents.mapNotNull { doc ->
                parseNote(doc)
            }
        } catch (_: Throwable) { emptyList() }
    }

    private fun parseNote(doc: com.google.firebase.firestore.DocumentSnapshot): Note? {
        val id = doc.getString("id") ?: return null
        val folderId = doc.getString("folderId") ?: "all"
        return Note(
            id = id,
            folderId = folderId,
            title = doc.getString("title") ?: "",
            content = doc.getString("content") ?: "",
            isLocked = doc.getBoolean("isLocked") ?: false,
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
            ownerId = doc.getString("ownerId"),
            ownerEmail = doc.getString("ownerEmail"),
            ownerName = doc.getString("ownerName"),
            sharedWithEmails = (doc.get("sharedWithEmails") as? List<*>)
                ?.filterIsInstance<String>(),
            isSynced = true
        )
    }

    // ===== 全量同步 =====

    suspend fun fullSync(
        localFolders: List<NoteFolder>,
        localNotes: List<Note>
    ): Pair<List<NoteFolder>, List<Note>> {
        if (!isLoggedIn()) return Pair(localFolders, localNotes)
        return try {
            val cloudFolders = fetchFolders()
            val cloudNotes = fetchNotes() // 這裡拿到的 cloudNotes 已經是 isSynced = true

            // 1. 合併資料夾
            val mergedFolders = mutableMapOf<String, NoteFolder>()
            localFolders.forEach { mergedFolders[it.id] = it }
            cloudFolders.forEach { mergedFolders[it.id] = it }

            // 2. 合併筆記庫（三方對齊邏輯）
            val finalNotes = mutableMapOf<String, Note>()
            
            // A. 以雲端為基準：如果雲端有的，先放入 finalNotes
            cloudNotes.forEach { finalNotes[it.id] = it }
            
            // B. 處理本機筆記
            localNotes.forEach { local ->
                val cloud = finalNotes[local.id]
                if (cloud == null) {
                    // 本機有，雲端沒有
                    if (local.isSynced) {
                        // 雖然標記為已同步，但雲端現在沒有 -> 判定為「在別台設備被刪除」
                        // 除非是非常近期（3秒內）的修改可能還在排隊上傳
                        if (System.currentTimeMillis() - local.updatedAt < 3000) {
                            finalNotes[local.id] = local
                        }
                    } else {
                        // 它是全新的本機筆記（剛寫完還沒同步過）-> 保留它，稍後上傳
                        finalNotes[local.id] = local
                    }
                } else {
                    // 兩邊都有：取 updatedAt 較新者
                    if (local.updatedAt > cloud.updatedAt) {
                        finalNotes[local.id] = local.copy(isSynced = false) // 標記為待上傳
                    }
                }
            }

            // 3. 將還沒同步好的上傳
            finalNotes.values.filter { !it.isSynced }.forEach { note ->
                val success = uploadNote(note)
                if (success) {
                    finalNotes[note.id] = note.copy(isSynced = true)
                }
            }

            Pair(mergedFolders.values.toList(), finalNotes.values.toList())
        } catch (_: Throwable) {
            Pair(localFolders, localNotes)
        }
    }
}
