// ============================================================
// GNote2 Web App — Firebase + Google 登入 + 即時同步
// 使用 Firebase JS SDK v9+ (ESM 模組)
// ============================================================

import { initializeApp } from "https://www.gstatic.com/firebasejs/11.4.0/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  onAuthStateChanged,
  signOut
} from "https://www.gstatic.com/firebasejs/11.4.0/firebase-auth.js";
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  deleteDoc,
  onSnapshot,
  serverTimestamp,
  query,
  orderBy
} from "https://www.gstatic.com/firebasejs/11.4.0/firebase-firestore.js";

// ============================================================
// Firebase 設定（與 App 相同的 Firebase 專案）
// ============================================================
const firebaseConfig = {
  apiKey: "AIzaSyCYQB-ik7DH1BFOs8XZusXsJ8w2hjDQNXQ",
  // ⚠️ authDomain 說明：
  // 目前使用 Firebase 預設網域，Google 登入彈窗會顯示 "noteapp-488414.firebaseapp.com"
  // 若部署到 wisepro.com.tw 並設定 Firebase Hosting 自訂網域後，
  // 可改為: authDomain: "wisepro.com.tw"，登入彈窗就會顯示應用程式名稱 "GNote2"
  authDomain: "noteapp-488414.firebaseapp.com",
  projectId: "noteapp-488414",
  storageBucket: "noteapp-488414.firebasestorage.app",
  messagingSenderId: "613935979196",
  appId: "1:613935979196:web:gnote2web"
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

// ============================================================
// 狀態管理
// ============================================================
let currentUser = null;
let currentFolderId = "all";
let currentNoteId = null;
let folders = [];
let notes = [];
let sharedNotes = [];
let unsubFolders = null;
let unsubNotes = null;
let unsubShared = null;
let saveTimer = null;
let searchQuery = "";
let renamingFolderId = null;
let isDirty = false;      // 追蹤是否有未儲存的修改
let lastSavedAt = 0;      // 記錄最近一次自己儲存的時間戳，防止誤判為遠端衝突

// ============================================================
// DOM 參考
// ============================================================
const $ = id => document.getElementById(id);

const loginScreen = $("login-screen");
const appScreen = $("app-screen");
const btnGoogleLogin = $("btn-google-login");
const btnLogout = $("btn-logout");
const userAvatar = $("user-avatar");
const userName = $("user-name");
const userEmail = $("user-email");

const sidebar = $("sidebar");
const sidebarOverlay = $("sidebar-overlay");
const btnMenu = $("btn-menu");
const folderList = $("folder-list");
const btnAddFolder = $("btn-add-folder");
const searchInput = $("search-input");

const notesList = $("notes-list");
const notesEmpty = $("notes-empty");
const currentFolderName = $("current-folder-name");
const btnNewNote = $("btn-new-note");

const editorPanel = $("editor-panel");
const editorPlaceholder = $("editor-placeholder");
const editorArea = $("editor-area");
const noteTitleInput = $("note-title");
const noteContentInput = $("note-content");
const saveIndicator = $("save-indicator");
const saveStatus = $("save-status");
const noteMetaTime = $("note-meta-time");
const btnDeleteNote = $("btn-delete-note");
const btnBackMobile = $("btn-back-mobile");

// ============================================================
// 工具函式
// ============================================================

/** 顯示 Toast 通知 */
function showToast(msg, type = "info", icon = "ℹ️") {
  const container = $("toast-container");
  const toast = document.createElement("div");
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span class="toast-icon">${icon}</span><span>${msg}</span>`;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.animation = "toastOut 0.3s ease forwards";
    setTimeout(() => toast.remove(), 300);
  }, 3000);
}

/** 格式化時間戳 */
function formatTime(ts) {
  if (!ts) return "–";
  const d = new Date(typeof ts === "number" ? ts : ts.toMillis?.() ?? Date.now());
  const now = new Date();
  const diffMs = now - d;
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);
  if (diffMins < 1) return "剛剛";
  if (diffMins < 60) return `${diffMins} 分鐘前`;
  if (diffHours < 24) return `${diffHours} 小時前`;
  if (diffDays < 7) return `${diffDays} 天前`;
  return d.toLocaleDateString("zh-TW", { year: "numeric", month: "2-digit", day: "2-digit" });
}

/** 產生唯一 ID */
function genId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 9);
}

/** Firestore 路徑 */
const foldersRef = uid => collection(db, "users", uid, "folders");
const notesRef = uid => collection(db, "users", uid, "notes");
const sharedRef = uid => collection(db, "users", uid, "sharedInbox");

// ============================================================
// 畫面切換
// ============================================================
function showLogin() {
  loginScreen.classList.add("active");
  appScreen.classList.remove("active");
}

function showApp() {
  loginScreen.classList.remove("active");
  appScreen.classList.add("active");
}

// ============================================================
// Google 登入
// ============================================================
btnGoogleLogin.addEventListener("click", async () => {
  try {
    btnGoogleLogin.disabled = true;
    btnGoogleLogin.innerHTML = `<span class="loading-spinner"></span> 登入中...`;
    const provider = new GoogleAuthProvider();
    await signInWithPopup(auth, provider);
  } catch (e) {
    console.error(e);
    showToast("登入失敗：" + (e.message || "未知錯誤"), "error", "❌");
    btnGoogleLogin.disabled = false;
    btnGoogleLogin.innerHTML = `<svg class="google-icon" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/><path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/><path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z" fill="#FBBC05"/><path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/></svg> 使用 Google 帳號登入`;
  }
});

// ============================================================
// 登出
// ============================================================
btnLogout.addEventListener("click", async () => {
  unsubscribeAll();
  await signOut(auth);
  currentUser = null;
  folders = [];
  notes = [];
  sharedNotes = [];
  currentNoteId = null;
  showLogin();
  showToast("已登出", "info", "👋");
});

// ============================================================
// 取消所有監聽
// ============================================================
function unsubscribeAll() {
  if (unsubFolders) { unsubFolders(); unsubFolders = null; }
  if (unsubNotes) { unsubNotes(); unsubNotes = null; }
  if (unsubShared) { unsubShared(); unsubShared = null; }
}

// ============================================================
// 認證狀態監聽
// ============================================================
onAuthStateChanged(auth, async user => {
  if (user) {
    currentUser = user;
    userAvatar.src = user.photoURL || "";
    userName.textContent = user.displayName || "使用者";
    userEmail.textContent = user.email || "";
    showApp();

    // 確保 user profile 存入 Firestore（供 email 查 UID 使用）
    await ensureUserProfile(user);
    // 開始即時監聽
    startListeners(user.uid);
  } else {
    showLogin();
  }
});

async function ensureUserProfile(user) {
  try {
    const ref = doc(db, "users", user.uid);
    await setDoc(ref, {
      email: user.email?.toLowerCase() ?? "",
      displayName: user.displayName ?? "",
      updatedAt: Date.now()
    }, { merge: true });
  } catch (e) {
    console.warn("ensureUserProfile 失敗", e);
  }
}

// ============================================================
// 即時監聽 Firestore 資料
// ============================================================
function startListeners(uid) {
  unsubscribeAll();

  // 監聽資料夾
  unsubFolders = onSnapshot(foldersRef(uid), snap => {
    const raw = snap.docs.map(d => ({ id: d.data().id || d.id, name: d.data().name || "未命名" }));
    // 確保「全部」始終存在
    if (!raw.some(f => f.id === "all")) raw.unshift({ id: "all", name: "全部" });
    folders = raw;
    renderFolders();
  }, err => {
    console.error("資料夾監聽失敗", err);
    showToast("資料夾載入失敗", "error", "❌");
  });

  // 監聽個人筆記
  unsubNotes = onSnapshot(notesRef(uid), snap => {
    const newNotes = snap.docs.map(d => parseNoteDoc(d)).filter(Boolean);

    // 偵測目前開啟的筆記是否在雲端被更新了
    if (currentNoteId) {
      const updatedNote = newNotes.find(n => n.id === currentNoteId);
      const oldNote = notes.find(n => n.id === currentNoteId);
      if (updatedNote && oldNote && updatedNote.updatedAt > oldNote.updatedAt) {
        handleRemoteNoteUpdate(updatedNote);
      }
    }

    notes = newNotes;
    renderFolders(); // 更新計數
    renderCurrentView();
  }, err => {
    console.error("筆記監聽失敗", err);
  });

  // 監聽共享收件箱
  unsubShared = onSnapshot(sharedRef(uid), snap => {
    sharedNotes = snap.docs.map(d => ({ ...parseNoteDoc(d), folderId: "shared_with_me" })).filter(Boolean);
    renderCurrentView();
  }, err => {
    console.error("共享筆記監聽失敗", err);
  });
}

function parseNoteDoc(doc) {
  const d = doc.data();
  if (!d.id && !doc.id) return null;
  return {
    id: d.id || doc.id,
    folderId: d.folderId || "all",
    title: d.title || "",
    content: d.content || "",
    isLocked: d.isLocked || false,
    createdAt: d.createdAt || Date.now(),
    updatedAt: d.updatedAt || Date.now(),
    ownerId: d.ownerId || null,
    ownerEmail: d.ownerEmail || null,
    ownerName: d.ownerName || null,
    sharedWithEmails: d.sharedWithEmails || []
  };
}

// ============================================================
// 渲染資料夾列表
// ============================================================
function renderFolders() {
  folderList.innerHTML = "";
  const allFolders = [
    ...folders,
    { id: "shared_with_me", name: "與我共享", isSpecial: true }
  ];

  allFolders.forEach(folder => {
    const isAll = folder.id === "all";
    const isShared = folder.id === "shared_with_me";
    const isSpecial = isAll || isShared;

    const count = isShared
      ? sharedNotes.length
      : isAll
        ? notes.filter(n => n.folderId !== "shared_with_me").length
        : notes.filter(n => n.folderId === folder.id).length;

    const icon = isShared ? "🤝" : isAll ? "📋" : "📁";

    const li = document.createElement("li");
    li.className = "folder-item" + (currentFolderId === folder.id ? " active" : "");
    li.dataset.folderId = folder.id;
    li.innerHTML = `
      <span class="folder-icon">${icon}</span>
      <span class="folder-name">${escapeHtml(folder.name)}</span>
      <span class="folder-count">${count}</span>
      ${!isSpecial ? `
      <div class="folder-actions">
        <button class="folder-action-btn rename-btn" title="重新命名" data-id="${folder.id}">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3l-9 9L9 16l1.5-3.5 9-9z"/>
          </svg>
        </button>
        <button class="folder-action-btn delete delete-btn" title="刪除" data-id="${folder.id}">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/>
            <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
            <path d="M9 6V4h6v2"/>
          </svg>
        </button>
      </div>` : ""}
    `;

    li.addEventListener("click", e => {
      if (e.target.closest(".folder-action-btn")) return;
      selectFolder(folder.id, folder.name);
      closeSidebar();
    });

    // 重命名
    li.querySelectorAll(".rename-btn").forEach(btn => {
      btn.addEventListener("click", e => {
        e.stopPropagation();
        openRenameModal(folder.id, folder.name);
      });
    });

    // 刪除資料夾
    li.querySelectorAll(".delete-btn").forEach(btn => {
      btn.addEventListener("click", e => {
        e.stopPropagation();
        openDeleteFolderConfirm(folder.id, folder.name);
      });
    });

    folderList.appendChild(li);
  });
}

function escapeHtml(str) {
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// ============================================================
// 選擇資料夾
// ============================================================
function selectFolder(folderId, folderName) {
  currentFolderId = folderId;
  currentFolderName.textContent = folderName || "資料夾";
  // 重設搜尋
  searchInput.value = "";
  searchQuery = "";
  renderFolders();
  renderCurrentView();
  // 行動版：關閉編輯器
  closeEditorMobile();
}

// ============================================================
// 渲染筆記列表
// ============================================================
function renderCurrentView() {
  let visibleNotes = [];

  if (currentFolderId === "all") {
    visibleNotes = [...notes].sort((a, b) => b.updatedAt - a.updatedAt);
  } else if (currentFolderId === "shared_with_me") {
    visibleNotes = [...sharedNotes].sort((a, b) => b.updatedAt - a.updatedAt);
  } else {
    visibleNotes = notes.filter(n => n.folderId === currentFolderId)
      .sort((a, b) => b.updatedAt - a.updatedAt);
  }

  // 套用搜尋過濾
  if (searchQuery.trim()) {
    const q = searchQuery.toLowerCase();
    visibleNotes = visibleNotes.filter(n =>
      n.title.toLowerCase().includes(q) || n.content.toLowerCase().includes(q)
    );
  }

  notesList.innerHTML = "";

  if (visibleNotes.length === 0) {
    notesEmpty.style.display = "flex";
    notesList.style.display = "none";
  } else {
    notesEmpty.style.display = "none";
    notesList.style.display = "block";

    visibleNotes.forEach(note => {
      const li = document.createElement("li");
      li.className = "note-item" + (currentNoteId === note.id ? " active" : "");
      li.dataset.noteId = note.id;

      const isShared = currentFolderId === "shared_with_me" || note.folderId === "shared_with_me";
      const titleDisplay = highlight(note.title || "（無標題）", searchQuery);
      const previewDisplay = highlight(note.content.slice(0, 80).replace(/\n/g, " "), searchQuery);

      li.innerHTML = `
        <div class="note-item-title">${titleDisplay || "（無標題）"}</div>
        <div class="note-item-preview">${previewDisplay || "（無內容）"}</div>
        <div class="note-item-meta">
          <span>${formatTime(note.updatedAt)}</span>
          ${isShared ? `<span class="note-shared-badge">🤝 ${escapeHtml(note.ownerName || note.ownerEmail || "共享")}</span>` : ""}
        </div>
      `;

      li.addEventListener("click", () => openNote(note));
      notesList.appendChild(li);
    });
  }
}

function highlight(text, query) {
  if (!query.trim()) return escapeHtml(text);
  const escaped = escapeHtml(text);
  const regex = new RegExp(`(${query.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")})`, "gi");
  return escaped.replace(regex, "<mark>$1</mark>");
}

// ============================================================
// 處理雲端筆記被遠端更新（手機修改後同步過來）
// ============================================================
function handleRemoteNoteUpdate(remoteNote) {
  if (!currentNoteId || remoteNote.id !== currentNoteId) return;

  // 如果是自己剛儲存觸發的 snapshot，跳過（否則會誤判為衝突）
  if (remoteNote.updatedAt === lastSavedAt) return;

  if (!isDirty) {
    // 用戶沒有未儲存的修改 → 直接靜默更新編輯器內容
    noteTitleInput.value = remoteNote.title;
    noteContentInput.value = remoteNote.content;
    noteMetaTime.textContent = `最後修改：${formatTime(remoteNote.updatedAt)}`;
    showToast("筆記已由其他裝置更新", "info", "🔄");
  } else {
    // 用戶有進行中的修改 → 彈出衝突警告
    showConflictModal(remoteNote);
  }
}

/** 衝突對話框：讓用戶選擇要保留哪個版本 */
function showConflictModal(remoteNote) {
  // 防止重複彈出
  if (document.getElementById("modal-conflict")?.style.display === "flex") return;

  const modal = document.createElement("div");
  modal.id = "modal-conflict";
  modal.className = "modal-overlay";
  modal.style.display = "flex";
  modal.innerHTML = `
    <div class="modal-card" style="max-width:420px;">
      <h3 class="modal-title">⚠️ 資料衝突</h3>
      <p class="modal-msg" style="margin-bottom:16px;">
        此筆記在<strong>其他裝置</strong>上已被修改（${formatTime(remoteNote.updatedAt)}），
        與您目前正在編輯的內容不同。
        請選擇要保留哪個版本：
      </p>
      <div style="display:flex;gap:10px;flex-direction:column;margin-bottom:18px;">
        <div style="background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.1);border-radius:8px;padding:12px;">
          <div style="font-size:11px;color:#8890a0;margin-bottom:6px;">📱 其他裝置的版本（較新）</div>
          <div style="font-size:13px;font-weight:600;color:#eef0f5;margin-bottom:4px;">${escapeHtml(remoteNote.title || '（無標題）')}</div>
          <div style="font-size:12px;color:#8890a0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(remoteNote.content.slice(0, 80) || '（無內容）')}</div>
        </div>
        <div style="background:rgba(124,110,245,0.08);border:1px solid rgba(124,110,245,0.2);border-radius:8px;padding:12px;">
          <div style="font-size:11px;color:#8890a0;margin-bottom:6px;">💻 您目前正在編輯的版本</div>
          <div style="font-size:13px;font-weight:600;color:#eef0f5;margin-bottom:4px;">${escapeHtml(noteTitleInput.value || '（無標題）')}</div>
          <div style="font-size:12px;color:#8890a0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(noteContentInput.value.slice(0, 80) || '（無內容）')}</div>
        </div>
      </div>
      <div class="modal-actions">
        <button id="conflict-keep-remote" class="btn-modal-cancel" style="flex:1;">使用其他裝置版本</button>
        <button id="conflict-keep-local" class="btn-modal-confirm" style="flex:1;">保留我的編輯</button>
      </div>
    </div>
  `;
  document.body.appendChild(modal);

  // 使用其他裝置版本 → 直接覆蓋編輯器
  document.getElementById("conflict-keep-remote").addEventListener("click", () => {
    noteTitleInput.value = remoteNote.title;
    noteContentInput.value = remoteNote.content;
    noteMetaTime.textContent = `最後修改：${formatTime(remoteNote.updatedAt)}`;
    isDirty = false;
    setSaveStatus("saved");
    modal.remove();
    showToast("已切換為其他裝置的版本", "info", "🔄");
  });

  // 保留我的編輯 → 立即觸發儲存，以覆蓋遠端版本
  document.getElementById("conflict-keep-local").addEventListener("click", () => {
    modal.remove();
    clearTimeout(saveTimer);
    saveCurrentNote(); // 立即儲存，覆蓋掉舊版本
    showToast("已保留您的編輯並儲存", "success", "✅");
  });
}

// ============================================================
// 開啟筆記
// ============================================================
function openNote(note) {
  currentNoteId = note.id;
  isDirty = false; // 重置修改旗標

  // 更新列表高亮
  document.querySelectorAll(".note-item").forEach(el => {
    el.classList.toggle("active", el.dataset.noteId === note.id);
  });

  editorPlaceholder.style.display = "none";
  editorArea.style.display = "flex";
  editorArea.style.flexDirection = "column";
  editorArea.style.flex = "1";

  noteTitleInput.value = note.title;
  noteContentInput.value = note.content;
  noteMetaTime.textContent = `最後修改：${formatTime(note.updatedAt)}`;

  setSaveStatus("saved");

  // 行動版：展開編輯器
  editorPanel.classList.add("open");
}

// ============================================================
// 自動儲存
// ============================================================
noteTitleInput.addEventListener("input", scheduleSave);
noteContentInput.addEventListener("input", scheduleSave);

function scheduleSave() {
  isDirty = true; // 標記有未儲存的修改
  setSaveStatus("saving");
  clearTimeout(saveTimer);
  saveTimer = setTimeout(saveCurrentNote, 1200);
}

async function saveCurrentNote() {
  if (!currentNoteId || !currentUser) return;

  const title = noteTitleInput.value;
  const content = noteContentInput.value;
  const uid = currentUser.uid;

  // 找出筆記（包含共享）
  let note = notes.find(n => n.id === currentNoteId)
    || sharedNotes.find(n => n.id === currentNoteId);

  if (!note) return;

  const isSharedNote = note.folderId === "shared_with_me" || sharedNotes.some(n => n.id === currentNoteId);

  try {
    const updatedAt = Date.now();
    lastSavedAt = updatedAt; // 記錄自己的儲存時間，從而認別此次 snapshot 是自己觸發的
    const data = {
      id: note.id,
      folderId: isSharedNote ? (note.folderId === "shared_with_me" ? "all" : note.folderId) : note.folderId,
      title,
      content,
      isLocked: note.isLocked || false,
      createdAt: note.createdAt,
      updatedAt,
      ownerId: note.ownerId || uid,
      ownerEmail: note.ownerEmail || currentUser.email,
      ownerName: note.ownerName || currentUser.displayName,
      sharedWithEmails: note.sharedWithEmails || []
    };

    if (isSharedNote && note.ownerId && note.ownerId !== uid) {
      // 回寫到擁有者的 notes 集合
      await setDoc(doc(db, "users", note.ownerId, "notes", note.id), data, { merge: true });
      // 更新自己的 sharedInbox
      await setDoc(doc(db, "users", uid, "sharedInbox", note.id), data, { merge: true });
    } else {
      // 上傳自己的筆記
      await setDoc(doc(db, "users", uid, "notes", note.id), data, { merge: true });
    }

    setSaveStatus("saved");
    isDirty = false; // 儲存成功，清除修改旗標
    noteMetaTime.textContent = `最後修改：${formatTime(updatedAt)}`;
  } catch (e) {
    console.error("儲存失敗", e);
    setSaveStatus("error");
    showToast("儲存失敗：" + (e.message || "未知錯誤"), "error", "❌");
  }
}

function setSaveStatus(state) {
  saveIndicator.className = "save-indicator " + (state === "saving" ? "saving" : "");
  if (state === "saving") {
    saveStatus.textContent = "儲存中...";
  } else if (state === "saved") {
    saveStatus.textContent = "已儲存";
  } else {
    saveStatus.textContent = "儲存失敗";
  }
}

// ============================================================
// 新增筆記
// ============================================================
btnNewNote.addEventListener("click", async () => {
  if (!currentUser) return;

  const uid = currentUser.uid;
  const folderId = currentFolderId === "shared_with_me" ? "all" : (currentFolderId || "all");
  const id = genId();
  const now = Date.now();

  const newNote = {
    id,
    folderId,
    title: "",
    content: "",
    isLocked: false,
    createdAt: now,
    updatedAt: now,
    ownerId: uid,
    ownerEmail: currentUser.email?.toLowerCase() ?? "",
    ownerName: currentUser.displayName ?? "",
    sharedWithEmails: []
  };

  try {
    await setDoc(doc(db, "users", uid, "notes", id), newNote);
    // 自動打開新增的筆記
    openNote(newNote);
    // 聚焦標題輸入框
    setTimeout(() => noteTitleInput.focus(), 100);
    showToast("新增筆記成功", "success", "✅");
  } catch (e) {
    showToast("新增失敗：" + e.message, "error", "❌");
  }
});

// ============================================================
// 刪除筆記
// ============================================================
btnDeleteNote.addEventListener("click", () => {
  if (!currentNoteId) return;
  const note = notes.find(n => n.id === currentNoteId)
    || sharedNotes.find(n => n.id === currentNoteId);
  const isShared = sharedNotes.some(n => n.id === currentNoteId)
    && note?.ownerId !== currentUser?.uid;

  $("modal-delete-msg").textContent = isShared
    ? "確定要移除此共享筆記？（不會影響原始擁有者）"
    : "確定要刪除此筆記嗎？此操作無法復原。";

  showModal("modal-confirm-delete");
});

$("btn-cancel-delete").addEventListener("click", () => hideModal("modal-confirm-delete"));

$("btn-confirm-delete").addEventListener("click", async () => {
  hideModal("modal-confirm-delete");
  if (!currentNoteId || !currentUser) return;

  const uid = currentUser.uid;
  const note = notes.find(n => n.id === currentNoteId)
    || sharedNotes.find(n => n.id === currentNoteId);
  const isShared = sharedNotes.some(n => n.id === currentNoteId)
    && note?.ownerId !== uid;

  try {
    if (isShared) {
      await deleteDoc(doc(db, "users", uid, "sharedInbox", currentNoteId));
    } else {
      await deleteDoc(doc(db, "users", uid, "notes", currentNoteId));
    }
    showToast("已刪除筆記", "success", "🗑️");
    currentNoteId = null;
    editorPlaceholder.style.display = "flex";
    editorArea.style.display = "none";
    closeEditorMobile();
  } catch (e) {
    showToast("刪除失敗：" + e.message, "error", "❌");
  }
});

// ============================================================
// 新增資料夾
// ============================================================
btnAddFolder.addEventListener("click", () => showModal("modal-add-folder"));
$("btn-cancel-folder").addEventListener("click", () => {
  hideModal("modal-add-folder");
  $("input-folder-name").value = "";
});

$("btn-confirm-folder").addEventListener("click", addFolder);
$("input-folder-name").addEventListener("keydown", e => { if (e.key === "Enter") addFolder(); });

async function addFolder() {
  const name = $("input-folder-name").value.trim();
  if (!name) { showToast("請輸入資料夾名稱", "error", "⚠️"); return; }
  if (name === "全部" || name === "與我共享") { showToast("此名稱為系統保留名稱", "error", "⚠️"); return; }
  if (!currentUser) return;

  const id = genId();
  try {
    await setDoc(doc(db, "users", currentUser.uid, "folders", id), { id, name });
    hideModal("modal-add-folder");
    $("input-folder-name").value = "";
    showToast(`已新增「${name}」`, "success", "✅");
  } catch (e) {
    showToast("新增失敗：" + e.message, "error", "❌");
  }
}

// ============================================================
// 重新命名資料夾
// ============================================================
function openRenameModal(folderId, currentName) {
  renamingFolderId = folderId;
  $("input-rename-folder").value = currentName;
  showModal("modal-rename-folder");
  setTimeout(() => $("input-rename-folder").select(), 50);
}

$("btn-cancel-rename").addEventListener("click", () => {
  hideModal("modal-rename-folder");
  renamingFolderId = null;
});

$("btn-confirm-rename").addEventListener("click", renameFolder);
$("input-rename-folder").addEventListener("keydown", e => { if (e.key === "Enter") renameFolder(); });

async function renameFolder() {
  const newName = $("input-rename-folder").value.trim();
  if (!newName || !renamingFolderId || !currentUser) return;

  try {
    await setDoc(doc(db, "users", currentUser.uid, "folders", renamingFolderId),
      { name: newName }, { merge: true });
    hideModal("modal-rename-folder");
    showToast(`已重新命名為「${newName}」`, "success", "✅");
    if (currentFolderId === renamingFolderId) {
      currentFolderName.textContent = newName;
    }
    renamingFolderId = null;
  } catch (e) {
    showToast("重新命名失敗：" + e.message, "error", "❌");
  }
}

// ============================================================
// 刪除資料夾
// ============================================================
function openDeleteFolderConfirm(folderId, folderName) {
  $("modal-delete-msg").textContent = `確定要刪除「${folderName}」資料夾嗎？資料夾內的筆記會移至「全部」。`;
  showModal("modal-confirm-delete");

  // 暫時覆寫確認按鈕行為
  const btn = $("btn-confirm-delete");
  const original = btn.onclick;
  btn.onclick = async () => {
    hideModal("modal-confirm-delete");
    btn.onclick = original;
    if (!currentUser) return;

    try {
      await deleteDoc(doc(db, "users", currentUser.uid, "folders", folderId));
      // 將該資料夾內的筆記移至 all
      const affected = notes.filter(n => n.folderId === folderId);
      for (const note of affected) {
        await setDoc(doc(db, "users", currentUser.uid, "notes", note.id),
          { folderId: "all" }, { merge: true });
      }
      showToast(`已刪除資料夾「${folderName}」`, "success", "🗑️");
      if (currentFolderId === folderId) {
        selectFolder("all", "全部");
      }
    } catch (e) {
      showToast("刪除失敗：" + e.message, "error", "❌");
    }
  };
}

// ============================================================
// 搜尋
// ============================================================
searchInput.addEventListener("input", e => {
  searchQuery = e.target.value;
  renderCurrentView();
});

// ============================================================
// 行動版 UI
// ============================================================
btnMenu.addEventListener("click", () => {
  sidebar.classList.toggle("open");
  sidebarOverlay.classList.toggle("open");
});

sidebarOverlay.addEventListener("click", closeSidebar);

function closeSidebar() {
  sidebar.classList.remove("open");
  sidebarOverlay.classList.remove("open");
}

btnBackMobile.addEventListener("click", closeEditorMobile);

function closeEditorMobile() {
  editorPanel.classList.remove("open");
}

// ============================================================
// Modal 通用
// ============================================================
function showModal(id) {
  const el = $(id);
  el.style.display = "flex";
}

function hideModal(id) {
  const el = $(id);
  el.style.display = "none";
}

// 點擊遮罩關閉
document.querySelectorAll(".modal-overlay").forEach(overlay => {
  overlay.addEventListener("click", e => {
    if (e.target === overlay) {
      overlay.style.display = "none";
    }
  });
});

// ESC 關閉 modal
document.addEventListener("keydown", e => {
  if (e.key === "Escape") {
    document.querySelectorAll(".modal-overlay").forEach(m => m.style.display = "none");
  }
});
