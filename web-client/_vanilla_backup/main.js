import "./style.css";

const STORAGE_HISTORY = "rag_chat_history_v1";
const STORAGE_USER = "rag_user_id";
const STORAGE_THEME = "rag_theme";
const TOKEN_KEY = "rag_access_token";
const AUTH_API_BASE = "/api/auth";
const CHAT_API_BASE = "/api/chat";
const msgKey = (id) => `rag_msgs_${id}`;

const newChatBtn = document.querySelector("#newChatBtn");
const historySearch = document.querySelector("#historySearch");
const historyNav = document.querySelector("#historyNav");
const sidebarUserName = document.querySelector("#sidebarUserName");
const mainTitle = document.querySelector("#mainTitle");
const mainInner = document.querySelector("#mainInner");
const heroSection = document.querySelector("#heroSection");
const suggestionsSection = document.querySelector("#suggestionsSection");
const chatThread = document.querySelector("#chatThread");
const composerInput = document.querySelector("#composerInput");
const sendBtn = document.querySelector("#sendBtn");
const deepThinkToggle = document.querySelector("#deepThinkToggle");
const suggestionCards = document.querySelector("#suggestionCards");
const adminLink = document.querySelector("#adminLink");
const starBtn = document.querySelector("#starBtn");
const logoutBtn = document.querySelector("#logoutBtn");
const themeToggle = document.querySelector("#themeToggle");
const particleLayer = document.querySelector("#particleLayer");
const historyLoadMoreWrap = document.querySelector("#historyLoadMoreWrap");
const historyLoadMoreBtn = document.querySelector("#historyLoadMoreBtn");
const deleteConfirmModal = document.querySelector("#deleteConfirmModal");
const cancelDeleteBtn = document.querySelector("#cancelDeleteBtn");
const confirmDeleteBtn = document.querySelector("#confirmDeleteBtn");

let activeStreamController = null;
let streaming = false;
let streamActive = false;
let conversationId = crypto.randomUUID();
let messages = [];
let latestWorkflowTrace = "";
let historyPage = 1;
let historyHasMore = false;
let historyLoading = false;
let pendingDeleteConvId = null;

const SUGGESTIONS = [
  {
    icon: "📊",
    title: "实时数据",
    desc: "接入外部数据源，回答带引用与可追溯链接。",
    prompt: "请对比近三个月行业公开数据，总结趋势并给出结论依据。"
  },
  {
    icon: "✓",
    title: "系统交互",
    desc: "把自然语言转成可执行步骤，适合排障与流程梳理。",
    prompt: "用户支付失败，请列出排查步骤和需要收集的日志字段。"
  },
  {
    icon: "🛡",
    title: "业务系统",
    desc: "结合知识库与检索，输出可落地的业务方案。",
    prompt: "基于现有知识库，给出上线前检查清单与风险点。"
  }
];

function nowTime() {
  return new Date().toLocaleTimeString();
}

function applyTheme(theme) {
  const dark = theme === "dark";
  document.body.classList.toggle("theme-dark", dark);
  themeToggle.textContent = dark ? "☀" : "☾";
  themeToggle.title = dark ? "切换到浅色" : "切换到暗色";
  localStorage.setItem(STORAGE_THEME, dark ? "dark" : "light");
  ensureParticles(dark);
}

function ensureParticles(enabled) {
  if (!particleLayer) return;
  if (!enabled) {
    particleLayer.innerHTML = "";
    return;
  }
  if (particleLayer.childElementCount > 0) return;
  const count = 26;
  for (let i = 0; i < count; i++) {
    const dot = document.createElement("span");
    dot.className = "particle";
    const left = Math.random() * 100;
    const size = 4 + Math.random() * 8;
    const duration = 10 + Math.random() * 12;
    const delay = -Math.random() * duration;
    const drift = -40 + Math.random() * 80;
    dot.style.left = `${left}%`;
    dot.style.width = `${size}px`;
    dot.style.height = `${size}px`;
    dot.style.animationDuration = `${duration}s`;
    dot.style.animationDelay = `${delay}s`;
    dot.style.setProperty("--drift", `${drift}px`);
    particleLayer.appendChild(dot);
  }
}

function appendLog(message) {
  console.debug(`[${nowTime()}] ${message}`);
}

function safeMessage(message, fallback) {
  if (typeof message !== "string") return fallback;
  const trimmed = message.trim();
  return trimmed || fallback;
}

function loadHistoryMeta() {
  try {
    const raw = localStorage.getItem(STORAGE_HISTORY);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}

function saveHistoryMeta(list) {
  localStorage.setItem(STORAGE_HISTORY, JSON.stringify(list));
}

function loadMessages(id) {
  try {
    const raw = localStorage.getItem(msgKey(id));
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}

function saveMessages(id, msgs) {
  localStorage.setItem(msgKey(id), JSON.stringify(msgs));
}

function upsertHistoryTitle(id, title) {
  const list = loadHistoryMeta();
  const idx = list.findIndex((x) => x.id === id);
  const item = { id, title: title.slice(0, 48), updatedAt: Date.now() };
  if (idx >= 0) {
    list[idx] = { ...list[idx], ...item };
  } else {
    list.unshift(item);
  }
  list.sort((a, b) => b.updatedAt - a.updatedAt);
  saveHistoryMeta(list.slice(0, 80));
}

async function fetchConversationsApi(page = 1, beforeTimestamp = null) {
  const params = new URLSearchParams({ current: String(page), size: "20" });
  if (beforeTimestamp) params.set("before", String(beforeTimestamp));
  const res = await fetch(`${CHAT_API_BASE}/conversations?${params.toString()}`, {
    headers: buildAuthHeaders()
  });
  if (!res.ok) throw new Error(`请求失败: ${res.status}`);
  const body = await res.json();
  if (body?.code !== 1) throw new Error(body?.msg || "获取会话列表失败");
  return body.data;
}

async function fetchMessagesApi(conversationId) {
  const res = await fetch(`${CHAT_API_BASE}/messages/${encodeURIComponent(conversationId)}`, {
    headers: buildAuthHeaders()
  });
  if (!res.ok) throw new Error(`请求失败: ${res.status}`);
  const body = await res.json();
  if (body?.code !== 1) throw new Error(body?.msg || "获取消息失败");
  return body.data;
}

async function deleteConversationApi(conversationId) {
  const res = await fetch(`${CHAT_API_BASE}/conversations/${encodeURIComponent(conversationId)}`, {
    method: "DELETE",
    headers: buildAuthHeaders()
  });
  if (!res.ok) throw new Error(`请求失败: ${res.status}`);
  const body = await res.json();
  if (body?.code !== 1) throw new Error(body?.msg || "删除失败");
}

function apiConversationsToLocalMeta(apiData) {
  const records = Array.isArray(apiData?.records) ? apiData.records : [];
  return records.map((c) => ({
    id: c.conversationId,
    title: c.title || "未命名对话",
    updatedAt: c.lastTime ? new Date(c.lastTime).getTime() : Date.now()
  }));
}

async function renderHistory() {
  const q = (historySearch.value || "").trim().toLowerCase();
  let list = [];

  try {
    const apiData = await fetchConversationsApi(1);
    list = apiConversationsToLocalMeta(apiData);
    historyPage = 1;
    historyHasMore = apiData?.pages > 1;
    const merged = mergeLocalWithApi(loadHistoryMeta(), list);
    saveHistoryMeta(merged);
  } catch {
    list = loadHistoryMeta();
    historyHasMore = false;
  }

  if (q) {
    list = list.filter((x) => !q || (x.title || "").toLowerCase().includes(q));
  }

  const groups = { today: [], week: [], older: [] };
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  const weekAgo = Date.now() - 7 * 86400000;

  for (const item of list) {
    if (item.updatedAt >= startOfToday.getTime()) groups.today.push(item);
    else if (item.updatedAt >= weekAgo) groups.week.push(item);
    else groups.older.push(item);
  }

  historyNav.innerHTML = "";
  const frag = (title, items) => {
    if (!items.length) return;
    const gt = document.createElement("div");
    gt.className = "history-group-title";
    gt.textContent = title;
    historyNav.appendChild(gt);
    for (const it of items) {
      const row = document.createElement("div");
      row.className = "history-item-row";

      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "history-item";
      if (it.id === conversationId) btn.classList.add("active");
      btn.textContent = it.title || "未命名对话";
      btn.addEventListener("click", () => switchConversation(it.id));

      const delBtn = document.createElement("button");
      delBtn.type = "button";
      delBtn.className = "history-delete-btn";
      delBtn.title = "删除对话";
      delBtn.textContent = "×";
      delBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        openDeleteConfirm(it.id);
      });

      row.appendChild(btn);
      row.appendChild(delBtn);
      historyNav.appendChild(row);
    }
  };

  frag("今天", groups.today);
  frag("7 天内", groups.week);
  frag("更早", groups.older);

  if (!list.length) {
    const empty = document.createElement("div");
    empty.className = "history-group-title";
    empty.style.marginTop = "8px";
    empty.textContent = "暂无历史，发送第一条消息开始";
    historyNav.appendChild(empty);
  }

  if (historyLoadMoreWrap) {
    historyLoadMoreWrap.hidden = !historyHasMore || !!q;
  }
}

function mergeLocalWithApi(localList, apiList) {
  const apiIds = new Set(apiList.map((x) => x.id));
  const merged = [...apiList];
  for (const item of localList) {
    if (!apiIds.has(item.id)) {
      merged.push(item);
    }
  }
  merged.sort((a, b) => b.updatedAt - a.updatedAt);
  return merged.slice(0, 80);
}

async function switchConversation(id) {
  streamActive = false;
  closeConnection();
  streaming = false;
  sendBtn.disabled = false;
  const tail = messages[messages.length - 1];
  if (tail && tail.role === "assistant" && tail.streaming) {
    tail.streaming = false;
    saveMessages(conversationId, messages);
  }
  conversationId = id;

  let loaded = false;
  try {
    const apiMsgs = await fetchMessagesApi(id);
    const localMsgs = apiMsgs.map((m) => ({
      role: m.messageType === "USER" ? "user" : m.messageType === "ASSISTANT" ? "assistant" : "system",
      content: m.content || ""
    }));
    messages = localMsgs;
    saveMessages(id, localMsgs);
    loaded = true;
  } catch {
    messages = loadMessages(id);
  }

  mainTitle.textContent = loadHistoryMeta().find((x) => x.id === id)?.title || "对话";
  renderHistory();
  renderChatFromMessages();
  updateLayoutMode();
}

function newConversation() {
  streamActive = false;
  closeConnection();
  streaming = false;
  sendBtn.disabled = false;
  conversationId = crypto.randomUUID();
  messages = [];
  mainTitle.textContent = "新对话";
  composerInput.value = "";
  renderHistory();
  renderChatFromMessages();
  updateLayoutMode();
}

function updateLayoutMode() {
  const hasMessages = messages.length > 0;
  heroSection.hidden = hasMessages;
  suggestionsSection.hidden = hasMessages;
  chatThread.hidden = !hasMessages;
}

function scrollToLatest() {
  requestAnimationFrame(() => {
    if (mainInner) {
      mainInner.scrollTop = mainInner.scrollHeight;
    }
    chatThread.scrollTop = chatThread.scrollHeight;
  });
}

function renderChatFromMessages() {
  chatThread.innerHTML = "";
  for (let i = 0; i < messages.length; i++) {
    const m = messages[i];
    const wrap = document.createElement("div");
    wrap.className = `msg ${m.role}${m.streaming ? " streaming" : ""}`;
    const label = document.createElement("div");
    label.className = "msg-label";
    label.textContent = m.role === "user" ? "你" : "助手";
    const bubble = document.createElement("div");
    bubble.className = "msg-bubble";
    bubble.textContent = m.content || (m.streaming ? "…" : "");
    wrap.appendChild(label);
    wrap.appendChild(bubble);
    chatThread.appendChild(wrap);
  }
  scrollToLatest();
}

function getAssistantBubbleEl() {
  const nodes = chatThread.querySelectorAll(".msg.assistant");
  const last = nodes[nodes.length - 1];
  return last?.querySelector(".msg-bubble") || null;
}

function setStreamingChip(visible, text = "正在生成…") {
  let chip = chatThread.querySelector(".status-chip");
  if (!visible) {
    chip?.remove();
    return;
  }
  if (!chip) {
    chip = document.createElement("div");
    chip.className = "status-chip";
    chip.innerHTML = `<span class="status-chip-dot"></span><span class="chip-text"></span>`;
    chatThread.appendChild(chip);
  }
  chip.querySelector(".chip-text").textContent = text;
  latestWorkflowTrace = text || latestWorkflowTrace;
}

function closeConnection() {
  if (activeStreamController) {
    activeStreamController.abort();
    activeStreamController = null;
    appendLog("SSE 请求已中止");
  }
}

function buildDeepThinkPayload(question) {
  return {
    question,
    conversationId
  };
}

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

function parseSseBlock(block) {
  const lines = block.split(/\r?\n/);
  let event = "message";
  const dataParts = [];
  for (const line of lines) {
    if (!line || line.startsWith(":")) continue;
    if (line.startsWith("event:")) {
      event = line.slice(6).trim() || "message";
      continue;
    }
    if (line.startsWith("data:")) {
      dataParts.push(line.slice(5).trimStart());
    }
  }
  return { event, data: dataParts.join("\n") };
}

async function startStream(question) {
  closeConnection();
  streamActive = true;
  appendLog("准备发起 SSE 请求");

  const url = "/api/chat/deepThink";
  appendLog(`请求: POST ${url}`);
  const controller = new AbortController();
  activeStreamController = controller;

  try {
    const payload = buildDeepThinkPayload(question);
    const response = await fetch(url, {
      method: "POST",
      headers: buildAuthHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify(payload),
      signal: controller.signal
    });
    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`);
    }
    if (!response.body) {
      throw new Error("未收到流式响应");
    }
    appendLog("SSE 已建立");

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";

    while (streamActive) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split(/\r?\n\r?\n/);
      buffer = blocks.pop() || "";

      for (const block of blocks) {
        const payload = parseSseBlock(block);
        if (!payload.data) continue;
        if (payload.event === "answer") {
          const last = messages[messages.length - 1];
          if (last && last.role === "assistant") {
            last.content += payload.data;
            const el = getAssistantBubbleEl();
            if (el) el.textContent = last.content;
            saveMessages(conversationId, messages);
            scrollToLatest();
          }
          continue;
        }
        if (payload.event === "workflow") {
          try {
            const workflowPayload = JSON.parse(payload.data);
            const stepText = `${workflowPayload.node || "流程"} ${workflowPayload.status || ""} ${workflowPayload.message || ""}`.trim();
            setStreamingChip(true, stepText || "深度思考中…");
            if (workflowPayload.status === "done" || workflowPayload.status === "error") {
              const statusMsg = safeMessage(
                workflowPayload.message,
                workflowPayload.status === "error" ? "系统异常" : "处理完成"
              );
              appendLog(`workflow: ${workflowPayload.status} - ${statusMsg}`);
              finishStream();
            }
          } catch {
            setStreamingChip(true, "深度思考中…");
          }
          continue;
        }
        appendLog(`默认 message: ${payload.data}`);
      }
    }
    if (streamActive) {
      finishStream();
    }
  } catch (error) {
    if (controller.signal.aborted) {
      appendLog("SSE 已手动中止");
    } else {
      appendLog(`SSE 异常: ${error?.message || "未知错误"}`);
      finishStream();
    }
  } finally {
    if (activeStreamController === controller) {
      activeStreamController = null;
    }
  }
}

function finishStream() {
  if (!streamActive) {
    return;
  }
  streamActive = false;
  streaming = false;
  sendBtn.disabled = false;
  const last = messages[messages.length - 1];
  if (last && last.role === "assistant") {
    if (!last.content.trim()) {
      last.content = latestWorkflowTrace || "本次未产出可展示的回答";
      const el = getAssistantBubbleEl();
      if (el) el.textContent = last.content;
    }
    last.streaming = false;
    const wrap = chatThread.querySelector(".msg.assistant:last-of-type");
    wrap?.classList.remove("streaming");
    saveMessages(conversationId, messages);
  }
  setStreamingChip(false);
  closeConnection();
}

async function logout() {
  const username = (localStorage.getItem(STORAGE_USER) || "").trim();
  if (!username) {
    localStorage.removeItem(TOKEN_KEY);
    window.location.href = "/auth.html";
    return;
  }
  try {
    await fetch(`${AUTH_API_BASE}/logout?username=${encodeURIComponent(username)}`, {
      method: "POST",
      headers: buildAuthHeaders()
    });
  } catch (error) {
    appendLog(`退出登录请求失败: ${error?.message || "未知错误"}`);
  } finally {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(STORAGE_USER);
    closeConnection();
    window.location.href = "/auth.html";
  }
}

function sendQuestion(text) {
  const q = text.trim();
  if (!q || streaming) return;

  streaming = true;
  sendBtn.disabled = true;

  messages.push({ role: "user", content: q });
  messages.push({ role: "assistant", content: "", streaming: true });
  latestWorkflowTrace = "";
  upsertHistoryTitle(conversationId, q);
  mainTitle.textContent = q.length > 24 ? `${q.slice(0, 24)}…` : q;

  saveMessages(conversationId, messages);
  renderHistory();
  updateLayoutMode();
  renderChatFromMessages();
  setStreamingChip(true, "深度思考中…");

  startStream(q);
  scrollToLatest();
}

function initSuggestions() {
  suggestionCards.innerHTML = "";
  for (const s of SUGGESTIONS) {
    const card = document.createElement("article");
    card.className = "suggestion-card";
    card.innerHTML = `
      <div class="suggestion-card-icon" aria-hidden="true">${s.icon}</div>
      <h3>${s.title}</h3>
      <p>${s.desc}</p>
      <a href="#" class="suggestion-prompt">${s.prompt.slice(0, 28)}… →</a>
    `;
    const link = card.querySelector(".suggestion-prompt");
    link.addEventListener("click", (e) => {
      e.preventDefault();
      composerInput.value = s.prompt;
      composerInput.focus();
    });
    suggestionCards.appendChild(card);
  }
}

function syncUserLabel() {
  const u = (localStorage.getItem(STORAGE_USER) || "").trim() || "admin";
  sidebarUserName.textContent = u.length > 12 ? `${u.slice(0, 12)}…` : u;
}

newChatBtn.addEventListener("click", newConversation);

historySearch.addEventListener("input", renderHistory);

adminLink.addEventListener("click", (e) => {
  e.preventDefault();
  window.location.href = "/admin.html";
});

starBtn.addEventListener("click", () => {
  starBtn.textContent = starBtn.textContent === "☆" ? "★" : "☆";
});

logoutBtn?.addEventListener("click", () => {
  logout();
});

sendBtn.addEventListener("click", () => {
  const text = composerInput.value;
  sendQuestion(text);
  composerInput.value = "";
});

composerInput.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    sendBtn.click();
  }
});

deepThinkToggle.addEventListener("click", () => {
  const on = deepThinkToggle.getAttribute("aria-pressed") !== "true";
  deepThinkToggle.setAttribute("aria-pressed", String(on));
});

themeToggle.addEventListener("click", () => {
  const dark = document.body.classList.contains("theme-dark");
  applyTheme(dark ? "light" : "dark");
});

document.addEventListener("keydown", (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
    e.preventDefault();
    historySearch.focus();
  }
});

syncUserLabel();
applyTheme(localStorage.getItem(STORAGE_THEME) || "light");

function openDeleteConfirm(convId) {
  pendingDeleteConvId = convId;
  if (deleteConfirmModal) deleteConfirmModal.hidden = false;
}

function closeDeleteConfirm() {
  pendingDeleteConvId = null;
  if (deleteConfirmModal) deleteConfirmModal.hidden = true;
}

cancelDeleteBtn?.addEventListener("click", closeDeleteConfirm);

deleteConfirmModal?.addEventListener("click", (e) => {
  if (e.target === deleteConfirmModal) closeDeleteConfirm();
});

confirmDeleteBtn?.addEventListener("click", async () => {
  if (!pendingDeleteConvId) return;
  const convId = pendingDeleteConvId;
  closeDeleteConfirm();
  try {
    await deleteConversationApi(convId);
  } catch {
    // deletion on server failed, still clean up locally
  }
  const list = loadHistoryMeta().filter((x) => x.id !== convId);
  saveHistoryMeta(list);
  localStorage.removeItem(msgKey(convId));
  if (conversationId === convId) {
    newConversation();
  } else {
    renderHistory();
  }
});

historyLoadMoreBtn?.addEventListener("click", async () => {
  if (historyLoading) return;
  historyLoading = true;
  historyLoadMoreBtn.disabled = true;
  historyLoadMoreBtn.textContent = "加载中...";
  try {
    const lastItem = loadHistoryMeta().slice(-1)[0];
    const beforeTimestamp = lastItem ? lastItem.updatedAt : null;
    const apiData = await fetchConversationsApi(historyPage + 1, beforeTimestamp);
    const apiList = apiConversationsToLocalMeta(apiData);
    historyPage += 1;
    historyHasMore = historyPage < (apiData?.pages || 1);
    const merged = mergeLocalWithApi(loadHistoryMeta(), apiList);
    saveHistoryMeta(merged);
    renderHistory();
  } catch {
    historyHasMore = false;
    renderHistory();
  } finally {
    historyLoading = false;
    historyLoadMoreBtn.disabled = false;
    historyLoadMoreBtn.textContent = "加载更多";
  }
});

if (!getAccessToken()) {
  window.location.href = "/auth.html";
}

initSuggestions();
renderHistory();
updateLayoutMode();
scrollToLatest();
appendLog("界面已就绪，输入问题后发送即可连接 SSE");
