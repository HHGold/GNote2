package com.chinhsiang.premiumnotes.data

import android.util.Log
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

    // 共享收件箱：users/{uid}/sharedInbox/
    private fun sharedInboxRef(uid: String) = db.collection("users").document(uid).collection("sharedInbox")

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

    /** 即時監聽「別人共享給我」的筆記 — 透過個人 sharedInbox 集合 */
    fun realtimeSharedNotesFlow(): Flow<List<Note>> = callbackFlow {
        val uid = uid ?: run { close(); return@callbackFlow }
        val listener = sharedInboxRef(uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val notes = snapshot.documents.mapNotNull { doc ->
                parseNote(doc)
            }
            Log.d("FirestoreSync", "收到共享筆記更新，共 ${notes.size} 筆")
            trySend(notes)
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

            // 先讀取 Firestore 中舊的 sharedWithEmails（用於比較哪些被移除）
            val oldDoc = notesRef(uid).document(note.id).get().await()
            val oldSharedEmails = (oldDoc.get("sharedWithEmails") as? List<*>)
                ?.filterIsInstance<String>()
                ?.map { it.lowercase().trim() }
                ?: emptyList()

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

            // === 共享同步：比較新舊名單，新增/更新/刪除 ===
            val newSharedEmails = note.sharedWithEmails?.map { it.lowercase().trim() } ?: emptyList()
            syncSharedInbox(note.id, data, newSharedEmails, oldSharedEmails)

            true
        } catch (e: Throwable) {
            Log.e("FirestoreSync", "uploadNote 失敗", e)
            false
        }
    }

    /**
     * 共享同步邏輯：
     * 1. 比較新舊 sharedWithEmails 名單
     * 2. 被移除者 → 刪除他們 sharedInbox 中的副本
     * 3. 保留/新增者 → 寫入/更新他們的 sharedInbox
     */
    private suspend fun syncSharedInbox(
        noteId: String,
        noteData: Map<String, Any>,
        newEmails: List<String>,
        oldEmails: List<String>
    ) {
        try {
            val myUid = uid ?: return
            val myEmail = email?.lowercase()?.trim() ?: ""

            // 1. 找出被移除的 email → 刪除他們 sharedInbox 中的副本
            val removedEmails = oldEmails.filter { it !in newEmails && it != myEmail }
            for (removedEmail in removedEmails) {
                try {
                    val targetUid = findUidByEmail(removedEmail)
                    if (targetUid != null && targetUid != myUid) {
                        sharedInboxRef(targetUid).document(noteId).delete().await()
                        Log.d("FirestoreSync", "已從 $removedEmail 的 sharedInbox 移除筆記 $noteId")
                    }
                } catch (e: Throwable) {
                    Log.w("FirestoreSync", "從 $removedEmail 移除共享失敗", e)
                }
            }

            // 2. 如果新名單為空（全部取消共享），結束
            if (newEmails.isEmpty()) return

            // 3. 新增/更新保留的共享者
            for (emailAddr in newEmails) {
                try {
                    // 跳過自己的 email
                    if (emailAddr == myEmail) continue

                    val targetUid = findUidByEmail(emailAddr)
                    if (targetUid != null && targetUid != myUid) {
                        sharedInboxRef(targetUid).document(noteId)
                            .set(noteData, SetOptions.merge()).await()
                        Log.d("FirestoreSync", "已投遞共享筆記到 $emailAddr 的 sharedInbox")
                    } else if (targetUid == null) {
                        Log.w("FirestoreSync", "找不到 email=$emailAddr 對應的 UID，暫時跳過")
                    }
                } catch (e: Throwable) {
                    Log.w("FirestoreSync", "投遞共享筆記到 $emailAddr 失敗", e)
                }
            }
        } catch (e: Throwable) {
            Log.e("FirestoreSync", "syncSharedInbox 失敗", e)
        }
    }

    /** 當筆記不再共享給任何人時，清理所有使用者的 sharedInbox 中的該筆記（備用方法） */
    private suspend fun cleanupSharedInboxForNote(noteId: String) {
        try {
            val snapshots = db.collectionGroup("sharedInbox")
                .whereEqualTo("id", noteId)
                .get().await()
            for (doc in snapshots.documents) {
                doc.reference.delete().await()
                Log.d("FirestoreSync", "已清理 sharedInbox 中的筆記 $noteId")
            }
        } catch (e: Throwable) {
            Log.w("FirestoreSync", "cleanupSharedInboxForNote 失敗（可能缺少索引）", e)
        }
    }

    /** 根據 email 查詢使用者的 UID */
    private suspend fun findUidByEmail(email: String): String? {
        return try {
            // 先查 Firestore 中的使用者名冊
            val snapshot = db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get().await()
            if (snapshot.documents.isNotEmpty()) {
                snapshot.documents[0].id
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.w("FirestoreSync", "findUidByEmail 失敗", e)
            null
        }
    }

    /** 確保當前使用者的基本資訊（email、displayName）已寫入 Firestore，
     *  以便共享功能可以透過 email 查找 UID */
    suspend fun ensureUserProfile() {
        try {
            val uid = uid ?: return
            val email = email ?: return
            val name = displayName ?: ""
            val data = mapOf(
                "email" to email,
                "displayName" to name,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("users").document(uid).set(data, SetOptions.merge()).await()
            Log.d("FirestoreSync", "已更新使用者 profile: $email")
        } catch (e: Throwable) {
            Log.w("FirestoreSync", "ensureUserProfile 失敗", e)
        }
    }

    suspend fun deleteNote(noteId: String) {
        try {
            val uid = uid ?: return
            notesRef(uid).document(noteId).delete().await()
            // 同時清理所有 sharedInbox 中的副本
            cleanupSharedInboxForNote(noteId)
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

    /** 取得別人共享給我的所有筆記 */
    suspend fun fetchSharedNotes(): List<Note> {
        return try {
            val uid = uid ?: return emptyList()
            sharedInboxRef(uid).get().await().documents.mapNotNull { doc ->
                parseNote(doc)
            }
        } catch (e: Throwable) {
            Log.w("FirestoreSync", "fetchSharedNotes 失敗", e)
            emptyList()
        }
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
            // 確保使用者 profile 已存入 Firestore（以便 email 查 UID）
            ensureUserProfile()

            val cloudFolders = fetchFolders()
            val cloudNotes = fetchNotes() // 這裡拿到的 cloudNotes 已經是 isSynced = true
            val sharedNotes = fetchSharedNotes() // 取得別人共享給我的筆記

            // 1. 合併資料夾
            val mergedFolders = mutableMapOf<String, NoteFolder>()
            localFolders.forEach { mergedFolders[it.id] = it }
            cloudFolders.forEach { mergedFolders[it.id] = it }

            // 2. 合併筆記庫（三方對齊邏輯）
            val finalNotes = mutableMapOf<String, Note>()
            
            // A. 以雲端為基準：如果雲端有的，先放入 finalNotes
            cloudNotes.forEach { finalNotes[it.id] = it }
            
            // B. 放入共享筆記（標記為 sharedInbox，folderId 改為 "shared_with_me"）
            sharedNotes.forEach { sharedNote ->
                // 共享筆記如果和自己的筆記 ID 相同（不太可能），以自己的為準
                if (!finalNotes.containsKey(sharedNote.id)) {
                    finalNotes[sharedNote.id] = sharedNote.copy(folderId = "shared_with_me")
                }
            }

            // C. 處理本機筆記
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
                    // 兩邊都有：取 updatedAt 較新者（但不覆蓋 shared 的 folderId）
                    if (local.updatedAt > cloud.updatedAt && cloud.folderId != "shared_with_me") {
                        finalNotes[local.id] = local.copy(isSynced = false) // 標記為待上傳
                    }
                }
            }

            // 3. 將還沒同步好的上傳（不上傳別人共享給我的筆記）
            finalNotes.values
                .filter { !it.isSynced && it.folderId != "shared_with_me" }
                .forEach { note ->
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

    /** 更新共享筆記的內容（被共享者進行編輯後，回寫到擁有者那邊） */
    suspend fun updateSharedNote(note: Note): Boolean {
        return try {
            val ownerUid = note.ownerId ?: return false
            val myUid = uid ?: return false
            if (ownerUid == myUid) {
                // 這是自己的筆記，直接用 uploadNote
                return uploadNote(note)
            }

            // 從自己的 sharedInbox 讀取原始 folderId
            // （不能直接讀擁有者的 notes 集合，安全規則不允許）
            val myDoc = sharedInboxRef(myUid).document(note.id).get().await()
            val originalFolderId = myDoc.getString("folderId")?.takeIf { it != "shared_with_me" } ?: "all"

            // 給擁有者的資料：使用原始 folderId
            val ownerData = mapOf(
                "id" to note.id,
                "folderId" to originalFolderId,
                "title" to note.title,
                "content" to note.content,
                "isLocked" to note.isLocked,
                "createdAt" to note.createdAt,
                "updatedAt" to note.updatedAt,
                "ownerId" to (note.ownerId ?: ""),
                "ownerEmail" to (note.ownerEmail ?: ""),
                "ownerName" to (note.ownerName ?: ""),
                "sharedWithEmails" to (note.sharedWithEmails ?: emptyList<String>())
            )

            // 給 sharedInbox 的資料：folderId 保留原始的（給前端自行轉換為 shared_with_me）
            val sharedData = ownerData // 相同內容

            // 回寫到擁有者的 notes 集合
            notesRef(ownerUid).document(note.id).set(ownerData, SetOptions.merge()).await()

            // 同時更新自己 sharedInbox 中的副本
            sharedInboxRef(myUid).document(note.id).set(sharedData, SetOptions.merge()).await()

            // 更新所有其他被共享者的 sharedInbox
            val sharedEmails = note.sharedWithEmails ?: emptyList()
            val myEmail = email ?: ""
            for (e in sharedEmails) {
                if (e.lowercase().trim() == myEmail) continue
                try {
                    val targetUid = findUidByEmail(e.lowercase().trim())
                    if (targetUid != null && targetUid != ownerUid) {
                        sharedInboxRef(targetUid).document(note.id)
                            .set(sharedData, SetOptions.merge()).await()
                    }
                } catch (_: Throwable) { }
            }

            Log.d("FirestoreSync", "已回寫共享筆記到擁有者 $ownerUid")
            true
        } catch (e: Throwable) {
            Log.e("FirestoreSync", "updateSharedNote 失敗", e)
            false
        }
    }
}
