# GNote2 — 原生 Android 版本功能說明文件

> **開發語言**：Kotlin + Jetpack Compose
> **套件識別碼**：`com.chinhsiang.premiumnotes`
> **當前版本**：v1.01 (2026-03-03)
---

## 📖 專案簡介

GNote2 是 GNote 的全新原生 Android 進化版。捨棄了網頁封裝，改用 **Android 原生技術 (Jetpack Compose)** 從底層重寫。它保留了極簡奢華的 iOS 備忘錄風格，並帶來更流暢的效能、更深度的系統整合（如生物辨識）以及穩定的 Firebase 雲端同步與協作功能。

---

## ✨ 核心功能列表

### 1. 📂 資料夾與搜尋
- **預設資料夾**：系統自動建立「全部」與「與我共享」資料夾。
- **與我共享 (Shared with me)**：自動匯集他人分享給您的所有筆記。
- **🔍 全域搜尋**：首頁提供搜尋框，可即時過濾標題或內容包含關鍵字的筆記。
- **新增資料夾**：底部「新增資料夾」按鈕，支援防呆機制。
- **資料夾操作**：長按資料夾可進行「重新命名」或「刪除」。

### 2. 📝 筆記管理與協作
- **新增筆記**：進入資料夾後點擊右下角 ✏️ 按鈕。
- **🤝 即時協作 (Sharing)**：
  - **快速邀請**：點擊 👤+ 圖示，輸入 Gmail 帳號。
  - **權限控制**：擁有者可管理名單；受邀者僅可檢視。
  - **即時同步**：多人同時編輯，內容透過 Firebase 秒級同步。
- **🔐 安全鎖定 (Biometric Lock)**：
  - **私密保障**：點擊 🔓 鎖頭即可使用生物辨識（指紋/人臉）鎖定筆記。
  - **限制**：共享中的筆記不開放上鎖。
- **自動儲存**：遺失防範機制。

### 3. ☁️ 雲端與更新
- **Google 帳號登入**：支援原生 Google Sign-In。
- **🔄 自動檢查更新**：
  - 進入「設定」>「版本與更新」> 點擊「檢查最新版本」。
  - 系統會比對 GitHub 上的最新 Tag，若有新版將**自動下載並引導安裝**。

---

## 🛠️ 技術架構

### 技術棧 (Tech Stack)
- **語言**：Kotlin 2.1.0+
- **UI**：Jetpack Compose (Material 3)
- **雲端服務**：Firebase (Auth, Firestore)
- **自動化版本**：Gradle 動態讀取 Git Tags (v1.0.x 格式)

---

## 🚀 開發與部署

### 版本管理說明
本專案已實現**版本號與 Git Tag 同步自動化**。
- `versionName`：取自當前最新的 Git Tag 名稱（由 `git describe` 取得）。
- `versionCode`：根據 Tag 自動計算（例如 `v1.01` -> `10100`）。

### 發布新版本流程
當您完成功能開發並準備發布時，請執行：
```powershell
# 1. 建立新的版本標籤 (例如 v1.02)
git tag v1.02

# 2. 推送到 GitHub (這會觸發 GitHub Actions 自動打包 APK 並發布 Release)
git push origin main --tags
```

### 本地打包
```powershell
# 建構 APK (Debug 版)
.\gradlew assembleDebug
```
產出的 APK 會位於 `app/build/outputs/apk/debug/app-debug.apk`。

---
**Build with ❤️ for GNote Users by Antigravity**
