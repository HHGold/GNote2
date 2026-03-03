# GNote2 — 原生 Android 版本功能說明文件

> **版本**：v1.0.0
> **開發語言**：Kotlin + Jetpack Compose
> **套件識別碼**：`com.chinhsiang.premiumnotes`
> **最後更新**：2026-03-03
---

## 📖 專案簡介

GNote2 是 GNote 的全新原生 Android 進化版。捨棄了網頁封裝，改用 **Android 原生技術 (Jetpack Compose)** 從底層重寫。它保留了極簡奢華的 iOS 備忘錄風格，並帶來更流暢的效能、更深度的系統整合（如生物辨識）以及穩定的 Firebase 雲端同步功能。

---

## 🎨 設計風格

| 項目 | 說明 |
|------|------|
| **設計語言** | 仿 iOS 備忘錄風格，圓角卡片、現代簡潔介面 |
| **UI 框架** | Jetpack Compose (Material 3) |
| **主題色** | 橘金色系 (`#FF9500` 淺色 / `#FF9F0A` 暗色) |
| **暗黑模式** | 完整支援，隨系統主題動態切換 |
| **動畫效果** | Compose 本地動畫，提供極速響應的互動體驗 |

### 色彩配置

| 項目 | 淺色模式 | 暗黑模式 |
|----------|----------|----------|
| **背景顏色** | `#F7F7F2` | `#000000` |
| **表面顏色** | `#FFFFFF` | `#1C1C1E` |
| **文字主色** | `#1C1C1E` | `#FFFFFF` |
| **強調色 (Accent)** | `#FF9500` | `#FF9F0A` |
| **分割線** | `#E5E5EA` | `#38383A` |

---

## ✨ 核心功能列表

### 1. 📂 資料夾管理
- **預設「全部」資料夾**：系統自動建立且不可刪除、不可重新命名，確保所有資料都有歸處。
- **新增資料夾**：底部「新增資料夾」按鈕，支援防呆機制（不允許空白或重複名稱）。
- **資料夾操作**：在首頁**長按**資料夾可進行「重新命名」或「刪除」。
- **安全刪除機制**：刪除自訂資料夾時，其中的筆記會自動移至「全部」資料夾，防止意外遺失。
- **即時數值**：資料夾列表右側即時顯示該資料夾內的筆記數量。

### 2. 📝 筆記管理
- **新增筆記**：進入資料夾後點擊右下角 ✏️ 按鈕。
- **編輯器功能**：
  - **自動儲存**：停止輸入 1.5 秒後自動儲存至本機與雲端，工具列會顯示「已儲存」提示。
  - **返回即儲存**：點擊左上角返回鍵時會強制進行最後一次資料同步。
- **🔐 生物辨識鎖定 (Biometric Lock)**：
  - 在編輯器點擊 🔓 鎖頭圖示即可鎖定筆記。
  - 支援指紋辨識、臉部辨識或系統密碼（視手機支援程度而定）。
  - 已上鎖的筆記在列表中會隱藏內容預覽，需驗證身份後方可進入。
- **列表呈現**：卡片式佈局，顯示標題、日期、內容預覽及上鎖狀態。

### 3. ☁️ 雲端同步與帳號
- **Google 帳號登入**：使用 Firebase Auth 支援原生 Google 登入。
- **雙向同步**：
  - App 啟動時自動與 Firebase Firestore 對接，標題列顯示同步指示器。
  - 採用 `updatedAt` 衝突解決機制，確保多裝置間資料一律以最新版為準。
  - **離線可用**：即便在無網路環境下，資料也會先存於本地（SharedPreferences），待連線後自動補齊。
- **帳號設定**：顯示使用者顯示名稱、Email、頭像，並支援安全登出。

### 4. ⚙️ 系統與更新
- **檢查更新**：設定頁面提供「檢查更新」按鈕（目前模擬 GitHub API 檢查）。
- **圖示整合**：使用經典 GNote 圖示，完美適應 Android 各種解析度與圖示形狀。

---

## 🛠️ 技術架構

### 技術棧 (Tech Stack)
- **語言**：Kotlin 2.1.0+
- **UI**：Jetpack Compose (Material 3)
- **資料持久化**：SharedPreferences + Gson
- **雲端服務**：Firebase (Auth, Firestore)
- **生物辨識**：AndroidX Biometric Library
- **導覽系統**：Compose Navigation
- **圖片載入**：Coil

### Firestore 資料結構
```
users/{uid}/
  ├── folders/{folderId}        # 資料夾文件 (id, name)
  └── notes/{noteId}            # 筆記文件 (id, folderId, title, content, isLocked, createdAt, updatedAt)
```

---

## 🚀 開發與部署

### 環境需求
- Android Studio Ladybug 以上
- Android 8.0 (API 26) 以上

### 打包指令
```powershell
# 建構 Debug 測試版
.\gradlew assembleDebug

# 建構 Release 簽名版 (需 local.properties 密鑰配置)
.\gradlew assembleRelease
```

### CI/CD 自動化
已整合 GitHub Actions，推送標籤 (Tag) 如 `v1.0.0` 時，雲端會自動進行編譯並產生 Release APK 到 GitHub 專案。

---
**Build with ❤️ for GNote Users by Antigravity**
