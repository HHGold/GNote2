# GNote2 — 原生 Android 版本功能說明文件

> **開發語言**：Kotlin + Jetpack Compose
> **套件識別碼**：`com.chinhsiang.premiumnotes`
> **最後更新**：2026-03-03
---

## 📖 專案簡介

GNote2 是 GNote 的全新原生 Android 進化版。捨棄了網頁封裝，改用 **Android 原生技術 (Jetpack Compose)** 從底層重寫。它保留了極簡奢華的 iOS 備忘錄風格，並帶來更流暢的效能、更深度的系統整合（如生物辨識）以及穩定的 Firebase 雲端同步與協作功能。

---

## ✨ 核心功能列表

### 1. 📂 資料夾管理
- **預設資料夾**：系統自動建立「全部」與「與我共享」資料夾。
- **與我共享 (Shared with me)**：自動匯集他人分享給您的所有筆記，此資料夾內不可建立新筆記。
- **新增資料夾**：底部「新增資料夾」按鈕，支援防呆機制。
- **資料夾操作**：在首頁**長按**資料夾可進行「重新命名」或「刪除」。
- **安全刪除機制**：刪除自訂資料夾時，其中的筆記會自動移至「全部」資料夾。

### 2. 📝 筆記管理與協作
- **新增筆記**：進入自訂或「全部」資料夾後點擊右下角 ✏️ 按鈕。
- **🤝 即時協作 (Sharing)**：
  - **快速邀請**：點擊 👤+ 圖示，輸入 Gmail 帳號（支援自動補完 `@gmail.com`）。
  - **權限控制**：只有「筆記擁有者」能管理分享名單；「受邀者」可查看名單但無法加入或移除成員。
  - **即時同步**：多人同時編輯時，系統將透過 Firebase 即時同步最新內容。
- **🔐 安全鎖定 (Biometric Lock)**：
  - **私密保障**：在編輯器點擊 🔓 鎖頭圖示即可使用生物辨識鎖定筆記。
  - **共享限制**：**共享中的筆記不開放上鎖功能**，確保協作過程所有人均能順暢存取。若筆記轉為共享狀態，原有鎖定將自動解除。
- **自動儲存**：停止輸入 1.5 秒後自動同步至雲端，確保資料不遺失。

### 3. ☁️ 雲端同步與帳號
- **Google 帳號登入**：支援原生 Google 登入，資料儲存於 Google Firebase。
- **離線可用**：離線編輯內容將在連線後自動上傳。
- **帳號設定**：提供雲端圖示狀態指示，並支援 Google 帳號安全登出。

---

## 🛠️ 技術架構

### 技術棧 (Tech Stack)
- **語言**：Kotlin 2.1.0+
- **UI**：Jetpack Compose (Material 3)
- **雲端服務**：Firebase (Auth, Firestore)
- **生物辨識**：AndroidX Biometric Library
- **導覽系統**：Compose Navigation

### 安全性設計
- **權限隔離**：嚴格區分「擁有者」與「編輯者」在分享名單管理上的權限。
- **鎖定邏輯**：系統層級判定筆記共享狀態，動態禁用鎖定按鈕以防資料存取衝突。

---

## 🚀 開發與部署

### 環境需求
- Android Studio Ladybug 以上
- Android 8.0 (API 26) 以上
- 必備檔案：`google-services.json` (Firebase) 與 `release.p12` (簽名)

### 打包指令
```powershell
# 建構 APK
.\gradlew assembleDebug
```
產出的 APK 會位於 `app/build/outputs/apk/debug/app-debug.apk`。

---
**Build with ❤️ for GNote Users by Antigravity**
