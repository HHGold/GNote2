package com.chinhsiang.premiumnotes.ui.views

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.chinhsiang.premiumnotes.auth.BiometricHelper
import com.chinhsiang.premiumnotes.data.Note
import com.chinhsiang.premiumnotes.data.NoteRepository
import com.chinhsiang.premiumnotes.data.FirestoreSync
import com.chinhsiang.premiumnotes.data.DeletedNoteTracker
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesListView(
    folderId: String,
    folderName: String,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String, String) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { NoteRepository(context) }
    val auth = remember { FirebaseAuth.getInstance() }
    val sync = remember { FirestoreSync() }
    val coroutineScope = rememberCoroutineScope()
    var notes by remember { mutableStateOf(repo.getNotesInFolder(folderId)) }
    var selectedNote by remember { mutableStateOf<Note?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 初始載入
    LaunchedEffect(folderId) {
        notes = repo.getNotesInFolder(folderId)
    }

    // 即時監聽雲端筆記變動 → UI 直接更新
    LaunchedEffect(folderId) {
        if (!sync.isLoggedIn()) return@LaunchedEffect
        val myUid = auth.currentUser?.uid
        
        if (folderId == "shared_with_me") {
            // 共享資料夾：監聽 sharedInbox
            sync.realtimeSharedNotesFlow()
                .catch { }
                .collect { sharedNotes ->
                    withContext(Dispatchers.IO) {
                        val mapped = sharedNotes.map { it.copy(folderId = "shared_with_me") }
                        // 取出本機筆記中非共享的部分
                        val ownNotes = repo.getAllNotes().filter { it.folderId != "shared_with_me" }
                        repo.saveNotesFromSync(ownNotes + mapped)
                    }
                    notes = repo.getNotesInFolder(folderId)
                }
        } else {
            // 普通資料夾：監聽自己的筆記
            sync.realtimeNotesFlow()
                .catch { }
                .collect { cloudNotes ->
                    withContext(Dispatchers.IO) {
                        val local = repo.getAllNotes().filter { it.folderId != "shared_with_me" }
                        val merged = mutableMapOf<String, Note>()
                        
                        // 1. 先放入雲端的所有資料（這是最真實的狀態）
                        cloudNotes.forEach { merged[it.id] = it }
                        
                        // 2. 處理本機資料
                        local.forEach { localNote ->
                            val cloudNote = merged[localNote.id]
                            if (cloudNote == null) {
                                // 雲端沒有這筆資料
                                if (localNote.ownerId == myUid) {
                                    if (localNote.isSynced) {
                                        // 已經跟雲端建立過聯繫，但現在雲端沒了 -> 表示剛才在別台被刪除了
                                        // 不加入 merged = 從本機抹除
                                    } else {
                                        // 它是全新本機筆記，還沒同步過 -> 保留它
                                        merged[localNote.id] = localNote
                                    }
                                } else {
                                    // 別人的分享被取消了 -> 抹除
                                }
                            } else {
                                // 兩邊都有：取 updatedAt 較新者
                                if (localNote.updatedAt > cloudNote.updatedAt) {
                                    merged[localNote.id] = localNote
                                }
                            }
                        }
                        
                        // 3. 過濾掉墓碑標記
                        val finalNotes = merged.values.filter { !DeletedNoteTracker.isDeleted(it.id) }.toList()
                        // 保留共享筆記
                        val sharedLocal = repo.getAllNotes().filter { it.folderId == "shared_with_me" }
                        repo.saveNotesFromSync(finalNotes + sharedLocal)
                    }
                    notes = repo.getNotesInFolder(folderId)
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth().padding(end = 64.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = folderName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", 
                            tint = MaterialTheme.colorScheme.primary)
                        Text("返回", color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            // 在「共享給我」資料夾中不允許建立新筆記
            if (folderId != "shared_with_me") {
                FloatingActionButton(
                    onClick = {
                        val user = auth.currentUser
                        val newNote = repo.createNote(
                            folderId = folderId,
                            ownerId = user?.uid,
                            ownerEmail = user?.email,
                            ownerName = user?.displayName
                        )
                        onNavigateToEditor(newNote.id, folderId)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "撰寫新筆記")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Folder name is now in the TopAppBar

            if (notes.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("尚無筆記，點擊右下角的筆圖示新增",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp)
                    }
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    val myUid = auth.currentUser?.uid
                    val sharedEmails = note.sharedWithEmails ?: emptyList()
                    val noteOwnerId = note.ownerId ?: ""
                    val isShared = sharedEmails.isNotEmpty() || (noteOwnerId.isNotEmpty() && noteOwnerId != myUid)
                    // 如果是共享筆記，顯示擁有者名稱
                    val ownerLabel = if (folderId == "shared_with_me" && noteOwnerId != myUid) {
                        note.ownerName?.takeIf { it.isNotBlank() } ?: note.ownerEmail ?: ""
                    } else ""
                    
                    NoteCard(
                        title = note.title,
                        preview = if (note.isLocked) "已上鎖 🔒" else note.content,
                        date = formatDate(note.updatedAt),
                        isLocked = note.isLocked,
                        isShared = isShared,
                        ownerLabel = ownerLabel,
                        onClick = {
                            if (note.isLocked) {
                                BiometricHelper.authenticate(
                                    activity = context as FragmentActivity,
                                    onSuccess = { onNavigateToEditor(note.id, folderId) },
                                    onError = { error ->
                                        Toast.makeText(context, "驗證失敗: $error", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                onNavigateToEditor(note.id, folderId)
                            }
                        },
                        onLongClick = {
                            selectedNote = note
                            showDeleteDialog = true
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "${notes.size} 篇筆記",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    if (showDeleteDialog && selectedNote != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("刪除筆記") },
            text = { Text("確定要刪除「${selectedNote!!.title.ifEmpty { "無標題" }}」嗎？此動作無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    val noteIdToDelete = selectedNote!!.id
                    // 0. 先記錄 tombstone（防止 Flow 復活）
                    DeletedNoteTracker.markDeleted(noteIdToDelete)
                    // 1. 刪除本機
                    repo.deleteNote(noteIdToDelete)
                    notes = repo.getNotesInFolder(folderId)
                    showDeleteDialog = false
                    // 2. 同步刪除雲端，完成後清除 tombstone
                    if (sync.isLoggedIn()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            sync.deleteNote(noteIdToDelete)
                            DeletedNoteTracker.clear(noteIdToDelete)
                        }
                    } else {
                        DeletedNoteTracker.clear(noteIdToDelete)
                    }
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(title: String, preview: String, date: String, isLocked: Boolean, isShared: Boolean, ownerLabel: String = "", onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.ifEmpty { "無標題" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isShared) {
                    Icon(Icons.Default.People, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp))
                }
                if (isLocked) {
                    Icon(Icons.Default.Lock, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = date, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp))
                if (ownerLabel.isNotEmpty()) {
                    Text(
                        text = "來自 $ownerLabel",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(text = preview.replace("\n", " "), fontSize = 14.sp,
                    color = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return if (diff < 24 * 60 * 60 * 1000) {
        SimpleDateFormat("a hh:mm", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
    }
}
