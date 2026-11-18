# GNote2 — 原生 Android 筆記 App

> **開發語言**：Kotlin + Jetpack Compose
> **當前版本**：v1.0.7（2026-11-18）
> **網頁版**：見 [`gd/`](gd/README.md)

---

## 📖 專案簡介

GNote2 是以 **Jetpack Compose** 開發的原生 Android 筆記 App，採用極簡的 iOS 備忘錄風格，搭配 Firebase 雲端同步、筆記共享與生物辨識鎖定。另有使用同一個雲端專案的網頁版（`gd/`）。

---

## ✨ 功能

### 資料夾與搜尋
- 預設資料夾：「全部」與「與我共享」。
- 全域搜尋（標題 / 內容）、資料夾新增 / 重新命名 / 刪除（長按資料夾）。
- 將筆記移動到其他資料夾。

### 筆記與共享
- 自動儲存。
- **共享**：點擊 👤+ 輸入對方 Gmail。擁有者管理共享名單；被共享者可閱讀並編輯標題與內容，但**不能更改共享名單或擁有者**。
- 共享中的筆記不開放上鎖。

### 安全鎖定
- 點擊 🔓 鎖頭，以生物辨識或螢幕密碼鎖定筆記。
- 已上鎖筆記在**手機本機資料庫**加密儲存。
- ⚠️ 雲端與網頁版**不會**加密或遮蔽上鎖筆記，請勿把高度敏感的內容放在雲端同步的筆記中。
- App 無法截圖，最近工作畫面也不顯示內容。

### 帳號與資料隔離
- Google 登入。
- **登出會清除本機所有筆記與資料夾**（雲端資料不受影響，重新登入後會同步回來）。若有尚未同步的筆記，登出前會警告。
- 偵測到換了不同帳號時，會先清除舊帳號的本機資料，避免混入新帳號的雲端。

### 備份與更新
- 設定頁可匯出備份 JSON（⚠️ 為明文，包含上鎖筆記，請妥善保管）。
- **自動檢查更新**：「設定」→「檢查最新版本」。更新檔會先驗證完整性，驗證失敗會取消安裝。

---

## 🛠️ 技術架構

| 項目 | 內容 |
|------|------|
| 語言 | Kotlin 1.9.24 |
| UI | Jetpack Compose（Material 3） |
| 本機資料庫 | Room（SQLite） |
| 雲端 | Firebase Auth + Firestore |
| 最低 / 目標 SDK | 24 / 34 |
| 版本管理 | `versionName` 取自最新 Git Tag，`versionCode = 主*10000 + 次*100 + 修` |

### 資料結構
```
users/{uid}                    個人資料
users/{uid}/folders/{id}       資料夾
users/{uid}/notes/{id}         個人筆記
users/{uid}/sharedInbox/{id}   別人共享給我的筆記副本
emailLookup/{email}            email 對照表
```
存取規則定義在 [`firestore.rules`](firestore.rules)，修改後需要在 Firebase Console 重新發布。

---

## 🚀 開發與發布

### 發布新版本
```powershell
git add -A
git commit -m "..."
git tag v1.0.8
git push origin main
git push origin v1.0.8
```
推送版本標籤會觸發 GitHub Actions 自動建置並發布 Release（含 APK 與其雜湊檔）。

### 本機打包
需要 JDK 17 與 Android SDK。
```powershell
.\gradlew assembleDebug      # Debug 版
.\gradlew assembleRelease    # 正式版（需另行提供簽章設定）
```
產出位於 `app/build/outputs/apk/`。

---
**Build with ❤️ for GNote Users**
