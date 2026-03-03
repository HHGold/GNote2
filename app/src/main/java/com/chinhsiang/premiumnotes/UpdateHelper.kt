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
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                if (asset.getString("name").endsWith(".apk")) {
                                    apkUrl = asset.getString("browser_download_url")
                                    break
                                }
                            }
                            if (apkUrl.isNotEmpty()) {
                                mainHandler.post {
                                    onUpdateFound()
                                    downloadUpdate(context, apkUrl, latestVersion)
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

    private fun downloadUpdate(context: Context, url: String, version: String) {
        try {
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
                        installApk(context, version)
                        try {
                            ctx.unregisterReceiver(this)
                        } catch (e: Exception) {}
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
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
