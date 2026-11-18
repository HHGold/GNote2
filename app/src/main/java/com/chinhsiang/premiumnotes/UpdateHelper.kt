package com.chinhsiang.premiumnotes

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object UpdateHelper {
    private const val GITHUB_API_URL = "https://api.github.com/repos/HHGold/GNote2/releases/latest"
    private var downloadId: Long = -1
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun checkForUpdate(context: Context, currentVersionName: String, onChecking: () -> Unit, onNoUpdate: () -> Unit, onUpdateFound: () -> Unit) {
        onChecking()
        thread {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val jsonPart = JSONObject(response)
                    var latestVersion = jsonPart.getString("tag_name")
                    if (latestVersion.startsWith("v")) {
                        latestVersion = latestVersion.substring(1)
                    }

                    if (isNewerVersion(currentVersionName, latestVersion)) {
                        val assets = jsonPart.getJSONArray("assets")
                        if (assets.length() > 0) {
                            var apkUrl = ""
                            var shaUrl = ""
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val assetName = asset.getString("name")
                                if (assetName.endsWith(".apk") && apkUrl.isEmpty()) {
                                    apkUrl = asset.getString("browser_download_url")
                                } else if (assetName.endsWith(".apk.sha256")) {
                                    shaUrl = asset.getString("browser_download_url")
                                }
                            }
                            val expectedSha = if (shaUrl.startsWith(TRUSTED_PREFIX)) fetchSha256(shaUrl) else null
                            if (apkUrl.isNotEmpty() && expectedSha != null) {
                                mainHandler.post {
                                    onUpdateFound()
                                    downloadUpdate(context, apkUrl, latestVersion, expectedSha)
                                }
                            } else {
                                mainHandler.post { onNoUpdate() }
                            }
                        } else {
                            mainHandler.post { onNoUpdate() }
                        }
                    } else {
                        mainHandler.post { onNoUpdate() }
                    }
                } else {
                    mainHandler.post { onNoUpdate() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post { onNoUpdate() }
            }
        }
    }

    private const val TRUSTED_PREFIX = "https://github.com/HHGold/GNote2/releases/download/"

    /** 下載 .sha256 檔並取出第一個雜湊值（64 位十六進位）。 */
    private fun fetchSha256(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val hash = text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()
            if (hash != null && Regex("[0-9a-f]{64}").matches(hash)) hash else null
        } catch (e: Exception) {
            null
        }
    }

    private fun verifyAndInstall(context: Context, version: String, expectedSha: String) {
        val file = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GNote2_$version.apk")
        val actual = try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
        if (actual != expectedSha) {
            file.delete()
            mainHandler.post {
                Toast.makeText(context, "更新檔驗證失敗，已取消安裝", Toast.LENGTH_LONG).show()
            }
            return
        }
        mainHandler.post { installApk(context, version) }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            val currParts = current.split(".").map { it.toInt() }
            val latParts = latest.split(".").map { it.toInt() }
            val maxLen = maxOf(currParts.size, latParts.size)
            for (i in 0 until maxLen) {
                val c = currParts.getOrElse(i) { 0 }
                val l = latParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (c > l) return false
            }
            false
        } catch (e: Exception) {
            current != latest
        }
    }

    private fun downloadUpdate(context: Context, url: String, version: String, expectedSha: String) {
        try {
            // 只接受來自本專案 GitHub release 的 HTTPS 下載連結
            if (!url.startsWith(TRUSTED_PREFIX)) {
                Toast.makeText(context, "更新來源不受信任，已取消", Toast.LENGTH_SHORT).show()
                return
            }
            // 清掉同名的舊檔，避免 DownloadManager 改名後驗證到錯的檔案
            java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GNote2_$version.apk").delete()
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("更新 GNote2 v$version")
                setDescription("正在下載最新版本...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "GNote2_$version.apk")
            }

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = manager.enqueue(request)
            Toast.makeText(context, "發現新版本，開始下載...", Toast.LENGTH_SHORT).show()

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        val pending = goAsync()
                        thread {
                            try {
                                verifyAndInstall(context, version, expectedSha)
                            } finally {
                                pending.finish()
                            }
                        }
                        try {
                            ctx.unregisterReceiver(this)
                        } catch (e: Exception) {}
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "下載失敗：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(context: Context, version: String) {
        try {
            val file = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GNote2_$version.apk")
            if (!file.exists()) return

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            mainHandler.post {
                Toast.makeText(context, "安裝失敗，無法開啟 APK", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
