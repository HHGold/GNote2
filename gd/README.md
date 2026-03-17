# GNote2 Web — 網頁版使用說明

## 📁 檔案位置
`c:\AI\GNote2\web\`

## 🚀 啟動方式

在 PowerShell 中執行：
```powershell
python -m http.server 8080 --directory "c:\AI\GNote2\web"
```

然後在瀏覽器開啟：**http://localhost:8080**

> ⚠️ **注意**：必須使用 HTTP 伺服器，不能直接用 `file://` 開啟，因為 Firebase SDK 使用 ES 模組需要 HTTP 環境。

## ✨ 功能說明

| 功能 | 說明 |
|------|------|
| 🔐 Google 登入 | 點擊登入按鈕，選擇 Google 帳號 |
| 📋 全部筆記 | 顯示所有個人筆記 |
| 📁 資料夾 | 在側邊欄管理資料夾（新增/重命名/刪除） |
| 🤝 與我共享 | 顯示他人分享給你的筆記 |
| ✏️ 新增筆記 | 點擊右上角 ✏️ 按鈕 |
| 💾 自動儲存 | 停止輸入 1.2 秒後自動同步到 Firestore |
| 🔍 搜尋 | 在側邊欄頂部搜尋框即時過濾 |
| 🗑️ 刪除筆記 | 在編輯器右上角刪除按鈕 |

## 🔄 資料同步

- 使用與手機 App **完全相同的 Firebase 專案** (`noteapp-488414`)
- 即時雙向同步（Firestore `onSnapshot` 監聽）
- 手機修改 → 網頁立即更新；網頁修改 → 手機立即同步

## 🌐 Firestore 資料結構

```
users/{uid}/
  ├── folders/     ← 資料夾列表
  ├── notes/       ← 個人筆記
  └── sharedInbox/ ← 別人共享給我的筆記
```

## 📱 響應式設計

- 桌面版：三欄佈局（側邊欄 + 筆記列表 + 編輯器）
- 行動版：單頁切換（點選筆記展開編輯器，返回按鈕回列表）

## ⚙️ 部署到正式環境

若要讓其他人也能使用，需要：
1. 把 `web/` 資料夾部署到任何靜態托管服務（GitHub Pages、Firebase Hosting 等）
2. 在 Firebase Console → Authentication → 設定 → 授權網域 新增你的網域名稱
