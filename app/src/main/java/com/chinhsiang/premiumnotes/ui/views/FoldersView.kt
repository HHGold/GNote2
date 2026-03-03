package com.chinhsiang.premiumnotes.ui.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.chinhsiang.premiumnotes.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersView(
    onNavigateToSettings: () -> Unit,
    onNavigateToFolder: (String, String) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { NoteRepository(context) }
    val sync = remember { FirestoreSync() }
    val coroutineScope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<NoteFolder>>(repo.getFolders()) }
    var noteCountTick by remember { mutableStateOf(0) }
    var isSyncing by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    // 1. 一次性初始同步（往返合併，確保離線資料也被上傳）
    LaunchedEffect(Unit) {
        try {
            kotlinx.coroutines.delay(800)
            if (sync.isLoggedIn()) {
                val (mergedFolders, mergedNotes) = withContext(Dispatchers.IO) {
                    sync.fullSync(repo.getFolders(), repo.getAllNotes())
                }
                withContext(Dispatchers.IO) {
                    repo.saveFoldersFromSync(mergedFolders)
                    repo.saveNotesFromSync(mergedNotes)
                }
                folders = repo.getFolders()
            }
        } catch (_: Throwable) { }
    }

    // 2. 即時監聽雲端資料夾（雲端刪除，另一台馬上不見）
    LaunchedEffect(Unit) {
        if (!sync.isLoggedIn()) return@LaunchedEffect
        sync.realtimeFoldersFlow()
            .catch { }
            .collect { cloudFolders ->
                withContext(Dispatchers.IO) {
                    val local = repo.getFolders()
                    val merged = mutableMapOf<String, NoteFolder>()
                    
                    // 1. 先放雲端快照
                    cloudFolders.forEach { merged[it.id] = it }
                    
                    // 2. 處理本機中，可能還沒上傳完成的新資料夾
                    local.forEach { localFolder ->
                        if (localFolder.id == "all") return@forEach 
                    }
                    
                    // 3. 確保「全部」永遠存在
                    val finalFolders = merged.values.toMutableList()
                    if (finalFolders.none { it.id == "all" }) {
                        finalFolders.add(0, NoteFolder(id = "all", name = "全部"))
                    } else {
                        finalFolders.sortWith(compareBy { if (it.id == "all") "" else it.name })
                    }
                    
                    repo.saveFoldersFromSync(finalFolders)
                }
                folders = repo.getFolders()
                noteCountTick++ // 刷新計數
            }
    }

    // 3. 即時監聽雲端筆記變動（為了更新資料夾旁的筆記數量）
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    LaunchedEffect(Unit) {
        if (!sync.isLoggedIn()) return@LaunchedEffect
        val myUid = auth.currentUser?.uid
        sync.realtimeNotesFlow()
            .catch { }
            .collect { cloudNotes ->
                withContext(Dispatchers.IO) {
                    val local = repo.getAllNotes()
                    val merged = mutableMapOf<String, Note>()
                    cloudNotes.forEach { merged[it.id] = it }
                    local.forEach { localNote ->
                        val cloud = merged[localNote.id]
                        if (cloud == null) {
                            if (localNote.ownerId == myUid) {
                                if (localNote.isSynced) {
                                    // 判定為在別台已被刪除
                                } else {
                                    merged[localNote.id] = localNote
                                }
                            }
                        } else {
                            if (localNote.updatedAt > cloud.updatedAt) {
                                merged[localNote.id] = localNote
                            }
                        }
                    }
                    val finalNotes = merged.values.filter { !com.chinhsiang.premiumnotes.data.DeletedNoteTracker.isDeleted(it.id) }.toList()
                    repo.saveNotesFromSync(finalNotes)
                }
                // 筆記變動時，重新刷新資料夾列表以更新數量顯示
                folders = repo.getFolders()
                noteCountTick++ // 刷新計數
            }
    }


    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("資料夾", fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        newFolderName = ""
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "新增")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新增資料夾")
                    }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "我的資料夾",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (folders.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("尚無資料夾，點擊下方「新增資料夾」開始使用",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        folders.forEachIndexed { index, folder ->
                            val noteCount = remember(folder.id, noteCountTick) {
                                repo.getNotesInFolder(folder.id).size
                            }
                            FolderItemRow(
                                name = folder.name,
                                count = noteCount,
                                showDivider = index < folders.size - 1,
                                onClick = { onNavigateToFolder(folder.id, folder.name) },
                                onLongClick = {
                                    selectedFolder = folder
                                    showActionSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 新增資料夾對話框
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增資料夾") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("資料夾名稱") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newFolderName.trim()
                    if (trimmed.isNotBlank()) {
                        // 新增本機
                        val newFolder = repo.addFolder(trimmed)
                        folders = repo.getFolders()
                        showAddDialog = false
                        newFolderName = ""
                        // 上傳雲端
                        if (sync.isLoggedIn() && newFolder != null) {
                            coroutineScope.launch(Dispatchers.IO) {
                                sync.uploadFolder(newFolder)
                            }
                        }
                    }
                }) { Text("新增") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }

    // 長按操作選單
    if (showActionSheet && selectedFolder != null) {
        AlertDialog(
            onDismissRequest = { showActionSheet = false },
            title = { Text("「${selectedFolder!!.name}」") },
            text = {
                Column {
                    TextButton(onClick = {
                        renameText = selectedFolder!!.name
                        showActionSheet = false
                        showRenameDialog = true
                    }) { Text("重新命名", color = MaterialTheme.colorScheme.primary) }
                    TextButton(onClick = {
                        val folderId = selectedFolder!!.id
                        // 刪除本機
                        repo.deleteFolder(folderId)
                        folders = repo.getFolders()
                        showActionSheet = false
                        // 刪除雲端
                        if (sync.isLoggedIn()) {
                            coroutineScope.launch(Dispatchers.IO) {
                                sync.deleteFolder(folderId)
                            }
                        }
                    }) { Text("刪除資料夾", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { showActionSheet = false }) { Text("取消") } }
        )
    }

    // 重新命名對話框
    if (showRenameDialog && selectedFolder != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重新命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("新名稱") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameText.trim()
                    if (trimmed.isNotBlank()) {
                        val folder = selectedFolder!!
                        // 改名本機
                        repo.renameFolder(folder.id, trimmed)
                        folders = repo.getFolders()
                        showRenameDialog = false
                        // 同步雲端
                        if (sync.isLoggedIn()) {
                            coroutineScope.launch(Dispatchers.IO) {
                                sync.uploadFolder(NoteFolder(id = folder.id, name = trimmed))
                            }
                        }
                    }
                }) { Text("確認") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItemRow(
    name: String,
    count: Int,
    showDivider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = name, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f))
            Text(text = count.toString(), fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        if (showDivider) {
            Divider(modifier = Modifier.padding(start = 52.dp),
                color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}
