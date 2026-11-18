package com.chinhsiang.premiumnotes.ui.views

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.chinhsiang.premiumnotes.auth.GoogleAuthManager
import com.chinhsiang.premiumnotes.data.FirestoreSync
import com.chinhsiang.premiumnotes.data.NoteRepository
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    onNavigateBack: () -> Unit,
    googleAuthManager: GoogleAuthManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repo = remember { NoteRepository(context) }
    var currentUser by remember { mutableStateOf(googleAuthManager.getCurrentUser()) }
    var isLoading by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var unsyncedCount by remember { mutableStateOf(0) }

    // 登出：清除本機資料，避免換帳號後看到上一個帳號的筆記（雲端資料不受影響）
    fun doSignOut() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) { repo.clearAllLocalData() }
            googleAuthManager.signOut()
            currentUser = null
            Toast.makeText(context, "已登出，本機資料已清除", Toast.LENGTH_SHORT).show()
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("尚有未同步的筆記") },
            text = {
                Text("有 $unsyncedCount 則筆記還沒同步到雲端，登出會一併清除本機資料，這些筆記將無法復原。確定要登出嗎？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    doSignOut()
                }) { Text("仍要登出", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("取消") }
            }
        )
    }

    // 定義顏色
    val cardBackground = Color(0xFF1C1C1E)
    val premiumOrange = Color(0xFFEBB02D)
    val premiumGreen = Color(0xFF00C853)
    val premiumBlue = Color(0xFF0A84FF)
    val secondaryText = Color(0xFF8E8E93)

    // 產生備份 JSON 資料字串
    fun generateBackupJson(): String {
        val folders = repo.getFolders()
        val notes = repo.getAllNotes()
        val backupData = mapOf(
            "appName" to "GNote2",
            "version" to "1.0.3",
            "exportedAt" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            "totalFolders" to folders.size,
            "totalNotes" to notes.size,
            "folders" to folders,
            "notes" to notes
        )
        return GsonBuilder().setPrettyPrinting().create().toJson(backupData)
    }

    // 1. SAF 本地儲存檔案啟動器
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val jsonContent = generateBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonContent.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "備份檔案已成功儲存至本機！", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "匯出失敗：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 2. 快速分享備份檔
    fun shareBackupFile() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val jsonContent = generateBackupJson()
                val fileName = "GNote2_Backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"
                val cacheFile = File(context.cacheDir, fileName)
                cacheFile.writeText(jsonContent, Charsets.UTF_8)

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, "GNote2 筆記備份檔案")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(shareIntent, "分享或儲存 GNote2 備份檔"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "分享失敗：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("設定", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "返回",
                            tint = premiumOrange,
                            modifier = Modifier.size(28.dp)
                        )
                        Text("返回", color = premiumOrange, fontSize = 17.sp)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 1. 雲端同步卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackground)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 雲端圖示
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2C2C2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudQueue,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = premiumOrange
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("雲端同步", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (currentUser != null) {
                            Text(currentUser?.displayName ?: "已登入", fontSize = 16.sp, color = premiumOrange)
                            Text(currentUser?.email ?: "", fontSize = 14.sp, color = secondaryText)
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val unsynced = withContext(Dispatchers.IO) { repo.unsyncedNoteCount() }
                                        if (unsynced > 0) {
                                            unsyncedCount = unsynced
                                            showSignOutConfirm = true
                                        } else {
                                            doSignOut()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E))
                            ) {
                                Text("登出帳號", color = Color.Red, fontSize = 16.sp)
                            }
                        } else {
                            Text("登入 Google 帳號", fontSize = 17.sp, color = secondaryText)
                            Text(
                                "在多個裝置間同步你的備忘錄\n並可將備忘錄分享給其他人",
                                fontSize = 14.sp,
                                color = secondaryText.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            OutlinedButton(
                                onClick = {
                                    isLoading = true
                                    coroutineScope.launch {
                                        val result = googleAuthManager.signIn(context as Activity)
                                        result.onSuccess { user ->
                                            withContext(Dispatchers.IO) { repo.handleAccountSwitch(user.uid) }
                                            currentUser = user
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    FirestoreSync().ensureUserProfile()
                                                } catch (_: Throwable) { }
                                            }
                                        }.onFailure { error ->
                                            Toast.makeText(context, "失敗：${error.message}", Toast.LENGTH_SHORT).show()
                                        }
                                        isLoading = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF3A3A3C)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("G", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("使用 Google 帳號登入", fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 2. 本地資料備份與匯出卡片 (全新功能)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(premiumBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("資料備份與匯出", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("匯出所有筆記與資料夾為本地檔案", fontSize = 14.sp, color = secondaryText)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 匯出至本地檔案按鈕
                        Button(
                            onClick = {
                                val defaultFileName = "GNote2_Backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"
                                exportFileLauncher.launch(defaultFileName)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = premiumBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("儲存備份至手機 (JSON)", color = Color.White, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 快速分享備份檔按鈕
                        OutlinedButton(
                            onClick = { shareBackupFile() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF3A3A3C)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = secondaryText, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("分享備份檔 (LINE/雲端硬碟)", color = secondaryText, fontSize = 15.sp)
                        }
                    }
                }
            }

            // 3. 隱私安全卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackground)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(premiumOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("隱私安全", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("本機使用 Room SQLite 資料庫，雲端使用 Google Firebase 加密儲存", fontSize = 14.sp, color = secondaryText)
                        }
                    }
                }
            }

            // 4. 版本與更新卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(premiumGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("版本與更新", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                val versionName = try {
                                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                                } catch (e: Exception) { "1.0.3" }
                                Text("當前版本: v$versionName", fontSize = 14.sp, color = secondaryText)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        OutlinedButton(
                            onClick = {
                                isCheckingUpdate = true
                                val currentVersionName = try {
                                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                                } catch (e: Exception) { "1.0.0" }
                                
                                com.chinhsiang.premiumnotes.UpdateHelper.checkForUpdate(
                                    context = context,
                                    currentVersionName = currentVersionName,
                                    onChecking = { isCheckingUpdate = true },
                                    onNoUpdate = {
                                        isCheckingUpdate = false
                                        Toast.makeText(context, "目前已是最新版本 ($currentVersionName)", Toast.LENGTH_SHORT).show()
                                    },
                                    onUpdateFound = {
                                        isCheckingUpdate = false
                                        // UpdateHelper handles the Toast and Download
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, premiumGreen),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = premiumGreen)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = premiumGreen, strokeWidth = 2.dp)
                            } else {
                                Text("檢查最新版本", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                val versionName = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) { "v1.0.3" }
                
                Text(
                    "GNote $versionName",
                    color = secondaryText.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
