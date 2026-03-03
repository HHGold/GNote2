package com.chinhsiang.premiumnotes.ui.views

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.chinhsiang.premiumnotes.auth.BiometricHelper
import com.chinhsiang.premiumnotes.data.DeletedNoteTracker
import com.chinhsiang.premiumnotes.data.FirestoreSync
import com.chinhsiang.premiumnotes.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorView(
    noteId: String,
    folderId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { NoteRepository(context) }
    val sync = remember { FirestoreSync() }
    val coroutineScope = rememberCoroutineScope()
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var shareEmail by remember { mutableStateOf("") }

    // 載入筆記資料
    val note = remember(noteId) { repo.getNote(noteId) }
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val myUid = auth.currentUser?.uid
    // 判斷此筆記是否為別人共享給我的（非我擁有）
    val isSharedNote = remember(note) {
        val ownerId = note?.ownerId
        !ownerId.isNullOrEmpty() && ownerId != myUid
    }
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var isLocked by remember { mutableStateOf(note?.isLocked ?: false) }
    var sharedWithEmails by remember { mutableStateOf(note?.sharedWithEmails ?: emptyList()) }

    // 當雲端有更新時 (由 Sync 機制觸發本機廣播或直接 Reload) - 這裡簡化處理
    // 實作中如果 fullSync 跑完，repository 的資料會變，這裡加個 LaunchedEffect 監聽 noteId
    LaunchedEffect(noteId) {
        val n = repo.getNote(noteId) ?: return@LaunchedEffect
        title = n.title
        content = n.content
        isLocked = n.isLocked
        sharedWithEmails = n.sharedWithEmails ?: emptyList()
    }

    // 自動儲存：停止輸入 1.5 秒後存本機，再上傳雲端
    LaunchedEffect(title, content, isLocked, sharedWithEmails) {
        delay(1500)
        if (noteId.isNotEmpty()) {
            repo.saveNote(noteId, title, content, isLocked, sharedWithEmails)
            if (sync.isLoggedIn()) {
                val updated = repo.getNote(noteId)
                if (updated != null) {
                    val success = withContext(Dispatchers.IO) {
                        if (isSharedNote) {
                            // 共享筆記：回寫到擁有者端
                            sync.updateSharedNote(updated)
                        } else {
                            // 自己的筆記：直接上傳
                            sync.uploadNote(updated)
                        }
                    }
                    if (success) repo.markAsSynced(noteId)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        repo.saveNote(noteId, title, content, isLocked, sharedWithEmails)
                        if (sync.isLoggedIn()) {
                            coroutineScope.launch(Dispatchers.IO) {
                                repo.getNote(noteId)?.let { updatedNote ->
                                    val success = if (isSharedNote) {
                                        sync.updateSharedNote(updatedNote)
                                    } else {
                                        sync.uploadNote(updatedNote)
                                    }
                                    if (success) repo.markAsSynced(noteId)
                                }
                            }
                        }
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    // 分享按鈕
                    IconButton(onClick = { 
                        if (!sync.isLoggedIn()) {
                            Toast.makeText(context, "請先登入帳號才能分享筆記", Toast.LENGTH_SHORT).show()
                        } else {
                            // 即使是被分享者也可以點開看名單，但在 Dialog 內限制編輯
                            showShareDialog = true 
                        }
                    }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "分享",
                            tint = if (sharedWithEmails.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // 鎖定按鈕 (共享的備忘錄不允許上鎖)
                    if (!isSharedNote && sharedWithEmails.isEmpty()) {
                        IconButton(onClick = {
                            if (!isLocked) {
                                if (BiometricHelper.canAuthenticate(context)) {
                                    isLocked = true
                                    Toast.makeText(context, "這篇筆記已上鎖", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "此裝置不支援生物辨識", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                BiometricHelper.authenticate(
                                    activity = context as FragmentActivity,
                                    onSuccess = {
                                        isLocked = false
                                        Toast.makeText(context, "已移除鎖定", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { error ->
                                        Toast.makeText(context, "驗證失敗: $error", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "上鎖/解鎖",
                                tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (isLocked) {
                        // 如果原本有鎖但後來變共享(極少見)，自動解鎖以確保共享者可看，或至少不顯示鎖定按鈕
                        isLocked = false
                    }

                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "刪除",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // 隱藏共享名單文字 (依使用者要求不顯示)
            /*
            if (sharedWithEmails.isNotEmpty()) {
                Text(
                    text = "分享給: ${sharedWithEmails.joinToString(", ")}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            */

            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text("標題", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                textStyle = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            TextField(
                value = content,
                onValueChange = { content = it },
                placeholder = {
                    Text("開始輸入內容...", fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                textStyle = TextStyle(
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("刪除筆記") },
            text = { Text("確定要刪除這篇筆記嗎？此動作無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    // 0. 先記錄 tombstone
                    DeletedNoteTracker.markDeleted(noteId)
                    // 1. 刪除本機
                    repo.deleteNote(noteId)
                    // 2. 同步刪除雲端，完成後清除 tombstone
                    if (sync.isLoggedIn()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            sync.deleteNote(noteId)
                            DeletedNoteTracker.clear(noteId)
                        }
                    } else {
                        DeletedNoteTracker.clear(noteId)
                    }
                    onNavigateBack()
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("分享筆記") },
            text = {
                Column {
                    if (!isSharedNote) {
                        Text("輸入對方的 Email 分享此筆記，對方即可查看並編輯。")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = shareEmail,
                            onValueChange = { shareEmail = it },
                            placeholder = { Text("Email 地址") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("此筆記為分享內容，只有擁有者具備編輯分享名單的權限。")
                    }
                    
                    if (sharedWithEmails.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("目前分享對象:", fontWeight = FontWeight.Bold)
                        sharedWithEmails.forEach { email ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(email, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                if (!isSharedNote) {
                                    IconButton(onClick = {
                                        sharedWithEmails = sharedWithEmails.filter { it != email }
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    } else if (isSharedNote) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("目前無其他分享對象", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                if (!isSharedNote) {
                    Button(onClick = {
                        val trimmedEmail = shareEmail.trim()
                        if (trimmedEmail.isEmpty()) return@Button
                        
                        val finalEmail = if (trimmedEmail.contains("@")) {
                            trimmedEmail
                        } else {
                            "$trimmedEmail@gmail.com"
                        }

                        if (!sharedWithEmails.contains(finalEmail)) {
                            sharedWithEmails = sharedWithEmails + finalEmail
                            shareEmail = ""
                        } else {
                            Toast.makeText(context, "該 Email 已在名單中", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("加入") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareDialog = false }) { Text("關閉") }
            }
        )
    }
}
