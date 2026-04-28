import "./admin.css";

const STORAGE_THEME = "rag_theme";
const TOKEN_KEY = "rag_access_token";

const themeToggle = document.querySelector("#themeToggle");
const createKbForm = document.querySelector("#createKbForm");
const uploadForm = document.querySelector("#uploadForm");
const parseForm = document.querySelector("#parseForm");
const chunkForm = document.querySelector("#chunkForm");
const kbNameInput = document.querySelector("#kbName");
const kbDescInput = document.querySelector("#kbDesc");
const uploadKbIdInput = document.querySelector("#uploadKbId");
const docFileInput = document.querySelector("#docFile");
const parseDocIdInput = document.querySelector("#parseDocId");
const chunkDocIdInput = document.querySelector("#chunkDocId");
const chunkKbIdInput = document.querySelector("#chunkKbId");
const chunkStrategyInput = document.querySelector("#chunkStrategy");
const opLog = document.querySelector("#opLog");
const toastEl = document.querySelector("#toast");

function getAccessToken() {
  return (localStorage.getItem(TOKEN_KEY) || "").trim();
}

function buildAuthHeaders(extra = {}) {
  const token = getAccessToken();
  if (!token) {
    throw new Error("登录状态已失效，请重新登录");
  }
  return {
    ...extra,
    Authorization: `Bearer ${token}`
  };
}

function applyTheme(theme) {
  const dark = theme === "dark";
  document.body.classList.toggle("theme-dark", dark);
  themeToggle.textContent = dark ? "☀" : "☾";
  themeToggle.title = dark ? "切换到浅色" : "切换到暗色";
  localStorage.setItem(STORAGE_THEME, dark ? "dark" : "light");
}

function appendLog(message) {
  if (!opLog) return;
  const text = `[${new Date().toLocaleTimeString()}] ${message}`;
  opLog.textContent = `${opLog.textContent}${opLog.textContent ? "\n" : ""}${text}`;
  opLog.scrollTop = opLog.scrollHeight;
}

function showToast(message) {
  if (!toastEl) return;
  toastEl.textContent = message;
  toastEl.hidden = false;
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => {
    toastEl.hidden = true;
  }, 2600);
}

async function requestJson(url, options) {
  const response = await fetch(url, options);
  let body = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  if (!response.ok) {
    throw new Error(body?.msg || `请求失败: ${response.status}`);
  }
  if (body?.code !== 1) {
    throw new Error(body?.msg || "操作失败");
  }
  return body;
}

async function createKb(name, description) {
  return requestJson("/api/knowledge/createKb", {
    method: "POST",
    headers: buildAuthHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ kbName: name, description })
  });
}

async function uploadDoc(file, kbId) {
  const formData = new FormData();
  formData.set("file", file);
  formData.set("kbId", String(kbId));
  return requestJson("/api/knowledge/uploadDoc", {
    method: "POST",
    headers: buildAuthHeaders(),
    body: formData
  });
}

async function parseDoc(docId) {
  return requestJson("/api/knowledge/parseDoc", {
    method: "POST",
    headers: buildAuthHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ docId })
  });
}

async function chunkDoc(docId, kbId, chuckStrategy) {
  return requestJson("/api/knowledge/chuckDoc", {
    method: "POST",
    headers: buildAuthHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ doc_id: docId, kbId, chuckStrategy })
  });
}

createKbForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const name = (kbNameInput.value || "").trim();
  const description = (kbDescInput.value || "").trim();
  if (!name) {
    showToast("知识库名称不能为空");
    return;
  }
  try {
    const res = await createKb(name, description);
    appendLog(`创建知识库：${res.msg || "成功"}`);
    createKbForm.reset();
    showToast("创建成功");
  } catch (error) {
    showToast(error?.message || "创建知识库失败");
  }
});

uploadForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const kbId = Number(uploadKbIdInput.value);
  const file = docFileInput.files?.[0];
  if (!kbId || kbId <= 0 || !file) {
    showToast("请先选择知识库和文件");
    return;
  }
  try {
    const res = await uploadDoc(file, kbId);
    appendLog(`上传文档：${res.msg || "成功"}（kbId=${kbId}, file=${file.name}）`);
    uploadForm.reset();
    showToast("上传请求已提交");
  } catch (error) {
    showToast(error?.message || "上传失败");
  }
});

parseForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const docId = Number(parseDocIdInput.value);
  if (!docId || docId <= 0) {
    showToast("请输入有效 docId");
    return;
  }
  try {
    const res = await parseDoc(docId);
    appendLog(`解析文档：${res.msg || "成功"}（docId=${docId}）`);
    parseForm.reset();
    showToast("解析请求已提交");
  } catch (error) {
    showToast(error?.message || "解析失败");
  }
});

chunkForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const docId = Number(chunkDocIdInput.value);
  const kbId = Number(chunkKbIdInput.value);
  const chuckStrategy = Number(chunkStrategyInput.value);
  if (!docId || docId <= 0 || !kbId || kbId <= 0 || Number.isNaN(chuckStrategy)) {
    showToast("请填写有效的 docId、kbId、分块策略");
    return;
  }
  try {
    const res = await chunkDoc(docId, kbId, chuckStrategy);
    appendLog(`文档分块：${res.msg || "成功"}（docId=${docId}, kbId=${kbId}, strategy=${chuckStrategy}）`);
    showToast("分块请求已提交");
  } catch (error) {
    showToast(error?.message || "分块失败");
  }
});

themeToggle.addEventListener("click", () => {
  const dark = document.body.classList.contains("theme-dark");
  applyTheme(dark ? "light" : "dark");
});

if (!getAccessToken()) {
  window.location.href = "/auth.html";
}

applyTheme(localStorage.getItem(STORAGE_THEME) || "light");
appendLog("构建模块就绪，按步骤执行接口。");
