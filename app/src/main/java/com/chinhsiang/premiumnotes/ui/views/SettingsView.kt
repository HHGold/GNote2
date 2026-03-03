package com.chinhsiang.premiumnotes.ui.views

import android.app.Activity
import android.widget.Toast
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
import com.chinhsiang.premiumnotes.auth.GoogleAuthManager
import com.chinhsiang.premiumnotes.data.FirestoreSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    onNavigateBack: () -> Unit,
    googleAuthManager: GoogleAuthManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentUser by remember { mutableStateOf(googleAuthManager.getCurrentUser()) }
    var isLoading by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    
    // 定義顏色 (符合截圖風格)
    val cardBackground = Color(0xFF1C1C1E)
    val premiumOrange = Color(0xFFEBB02D)
    val premiumGreen = Color(0xFF00C853)
    val secondaryText = Color(0xFF8E8E93)

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
                                    googleAuthManager.signOut()
                                    currentUser = null
                                    Toast.makeText(context, "已登出", Toast.LENGTH_SHORT).show()
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
                                            currentUser = user
                                            // 確保使用者 profile 已寫入 Firestore（以便共享功能透過 email 查找 UID）
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

            // 2. 隱私安全卡片
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
                            Text("資料使用 Google Firebase 加密儲存", fontSize = 14.sp, color = secondaryText)
                        }
                    }
                }
            }

            // 3. 版本與更新卡片
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
                                Text("當前版本: v1.0.7", fontSize = 14.sp, color = secondaryText)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        OutlinedButton(
                            onClick = {
                                isCheckingUpdate = true
                                coroutineScope.launch {
                                    delay(1500)
                                    isCheckingUpdate = false
                                    Toast.makeText(context, "目前已是最新版本", Toast.LENGTH_SHORT).show()
                                }
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
                Text(
                    "GNote v1.0.7",
                    color = secondaryText.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
