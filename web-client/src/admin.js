import "./admin.css";

const STORAGE_THEME = "rag_theme";
const TOKEN_KEY = "rag_access_token";

const themeToggle = document.querySelector("#themeToggle");
const createKbForm = document.querySelector("#createKbForm");
const uploadForm = document.querySelector("#uploadForm");
const kbNameInput = document.querySelector("#kbName");
const kbDescInput = document.querySelector("#kbDesc");
const uploadKbIdInput = document.querySelector("#uploadKbId");
const docFileInput = document.querySelector("#docFile");
const opLog = document.querySelector("#opLog");
const toastEl = document.querySelector("#toast");
const breadcrumb = document.querySelector("#breadcrumb");
const headerDesc = document.querySelector("#headerDesc");
const uploadSection = document.querySelector("#uploadSection");
const listSection = document.querySelector("#listSection");
const listTitle = document.querySelector("#listTitle");
const tableHead = document.querySelector("#tableHead");
const tableBody = document.querySelector("#tableBody");
const refreshBtn = document.querySelector("#refreshBtn");
const searchBtn = document.querySelector("#searchBtn");
const listKeywordInput = document.querySelector("#listKeyword");
const primaryActionBtn = document.querySelector("#primaryActionBtn");
const prevPageBtn = document.querySelector("#prevPageBtn");
const nextPageBtn = document.querySelector("#nextPageBtn");
const pageInfo = document.querySelector("#pageInfo");
const createModal = document.querySelector("#createModal");
const closeCreateModalBtn = document.querySelector("#closeCreateModalBtn");
const rawPayload = document.querySelector("#rawPayload");
const chunkDetailModal = document.querySelector("#chunkDetailModal");
const closeChunkDetailModalBtn = document.querySelector("#closeChunkDetailModalBtn");
const chunkDetailContent = document.querySelector("#chunkDetailContent");

const ViewMode = {
  KB: "kb",
  DOC: "doc",
  CHUNK: "chunk"
};

const state = {
  mode: ViewMode.KB,
  selectedKb: null,
  selectedDoc: null,
  keyword: "",
  page: { current: 1, size: 10, pages: 1, total: 0 },
  actionBusy: false
};

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

function serverErrorFallback(error) {
  if (error?.message && String(error.message).trim()) {
    return String(error.message);
  }
  return "服务器异常";
}

function isPositiveIntegerString(v) {
  const s = String(v ?? "").trim();
  return /^[1-9]\d*$/.test(s);
}

function sleep(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function statusBadgeClass(status) {
  const n = Number(status);
  if (n === 5) return "status-badge success";
  if (n === 6) return "status-badge danger";
  if (n === 2 || n === 4) return "status-badge warning";
  if (n === 1 || n === 3) return "status-badge info";
  return "status-badge default";
}

function truncateText(text, max = 42) {
  const v = String(text ?? "");
  if (v.length <= max) return v;
  return `${v.slice(0, max)}...`;
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

async function vectorizeDoc(docId, kbId) {
  return requestJson("/api/knowledge/chuckEmbedding", {
    method: "POST",
    headers: buildAuthHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ docId: String(docId), kbId: String(kbId) })
  });
}

async function fetchKbPage(current = 1, size = 10) {
  const params = new URLSearchParams({ current: String(current), size: String(size) });
  if (state.keyword) params.set("kbName", state.keyword);
  return requestJson(`/api/knowledge/kb/page?${params.toString()}`, {
    method: "GET",
    headers: buildAuthHeaders()
  });
}

async function fetchDocPage(kbId, current = 1, size = 10) {
  const params = new URLSearchParams({
    current: String(current),
    size: String(size),
    kbId: String(kbId)
  });
  if (state.keyword) params.set("fileName", state.keyword);
  return requestJson(`/api/knowledge/doc/page?${params.toString()}`, {
    method: "GET",
    headers: buildAuthHeaders()
  });
}

async function fetchChunkPage(docId, kbId, current = 1, size = 10) {
  const params = new URLSearchParams({
    current: String(current),
    size: String(size),
    docId: String(docId),
    kbId: String(kbId)
  });
  if (state.keyword) params.set("content", state.keyword);
  return requestJson(`/api/knowledge/chunk/page?${params.toString()}`, {
    method: "GET",
    headers: buildAuthHeaders()
  });
}

function updatePageMeta(page, recordsCount = 0) {
  const current = Number(page?.current || 1);
  let total = Number(page?.total || 0);
  let pages = Number(page?.pages || 0);

  // 后端偶发返回 records 有值但 total/pages 为 0，前端做兜底渲染
  if (recordsCount > 0 && total <= 0) {
    total = recordsCount;
  }
  if (recordsCount > 0 && pages <= 0) {
    pages = 1;
  }
  if (pages <= 0) {
    pages = 1;
  }

  state.page = { ...state.page, current, pages, total };
  pageInfo.textContent = `第 ${current}/${pages} 页 · 共 ${total} 条`;
  prevPageBtn.disabled = current <= 1;
  nextPageBtn.disabled = current >= pages;
}

function renderRawPayload(payload) {
  if (!rawPayload) return;
  rawPayload.textContent = JSON.stringify(payload ?? {}, null, 2);
}

function renderDynamicTable(records, buildActionCell) {
  const safeRecords = Array.isArray(records) ? records : [];
  tableHead.innerHTML = "";
  tableBody.innerHTML = "";

  if (!safeRecords.length) {
    tableHead.innerHTML = `<tr><th>暂无数据</th></tr>`;
    tableBody.innerHTML = `<tr><td>暂无记录</td></tr>`;
    return;
  }

  const keys = Object.keys(safeRecords[0] || {});
  const headCells = [...keys.map((k) => `<th>${k}</th>`), `<th>操作</th>`].join("");
  tableHead.innerHTML = `<tr>${headCells}</tr>`;

  safeRecords.forEach((row) => {
    const tr = document.createElement("tr");
    const dataCells = keys.map((k) => `<td>${row?.[k] ?? "-"}</td>`).join("");
    const actionCell = buildActionCell ? buildActionCell(row) : `<td>-</td>`;
    tr.innerHTML = `${dataCells}${actionCell}`;
    tableBody.appendChild(tr);
  });
}

function statusText(status) {
  const map = {
    0: "INIT",
    1: "PARSED",
    2: "CHUNKING",
    3: "CHUNKED",
    4: "EMBEDDING",
    5: "VECTORIZED",
    6: "FAILED"
  };
  return map[status] || String(status);
}

function renderBreadcrumb() {
  const nodes = [{ key: ViewMode.KB, label: "知识库构建" }];
  if (state.selectedKb) nodes.push({ key: ViewMode.DOC, label: "文档导入" });
  if (state.selectedDoc) nodes.push({ key: ViewMode.CHUNK, label: "分块列表" });
  breadcrumb.innerHTML = "";
  nodes.forEach((node, idx) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = `crumb-btn${state.mode === node.key ? " active" : ""}`;
    btn.textContent = node.label;
    if (state.mode !== node.key) {
      btn.addEventListener("click", () => {
        if (node.key === ViewMode.KB) {
          state.mode = ViewMode.KB;
          state.selectedKb = null;
          state.selectedDoc = null;
        } else if (node.key === ViewMode.DOC) {
          state.mode = ViewMode.DOC;
          state.selectedDoc = null;
        }
        loadPage(1);
      });
    }
    breadcrumb.appendChild(btn);
    if (idx < nodes.length - 1) {
      const sep = document.createElement("span");
      sep.className = "crumb-sep";
      sep.textContent = "->";
      breadcrumb.appendChild(sep);
    }
  });
}

function renderKbTable(records) {
  uploadSection.hidden = true;
  listSection.hidden = false;
  listTitle.textContent = "知识库列表";
  headerDesc.textContent = "点击“查看详情”进入文档导入页面";
  primaryActionBtn.textContent = "新建知识库";
  primaryActionBtn.hidden = false;

  const safeRecords = Array.isArray(records) ? records : [];
  tableHead.innerHTML = `
    <tr>
      <th>知识库ID</th>
      <th>知识库名称</th>
      <th>描述</th>
      <th>启用</th>
      <th>创建时间</th>
      <th>操作</th>
    </tr>
  `;
  tableBody.innerHTML = "";
  if (!safeRecords.length) {
    tableBody.innerHTML = `<tr><td colspan="6">暂无记录</td></tr>`;
    return;
  }
  safeRecords.forEach((row) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${escapeHtml(row.id)}</td>
      <td>${escapeHtml(row.kbName)}</td>
      <td>${escapeHtml(row.description ?? "-")}</td>
      <td>${escapeHtml(String(row.enabled))}</td>
      <td>${escapeHtml(row.createdAt ?? "-")}</td>
      <td class="cell-actions">
        <button class="btn-action" data-action="detail" data-kb-id="${escapeHtml(row.id)}" data-kb-name="${escapeHtml(row.kbName || "")}">查看详情</button>
      </td>
    `;
    tableBody.appendChild(tr);
  });
}

function renderDocTable(records) {
  uploadSection.hidden = false;
  listSection.hidden = false;
  listTitle.textContent = "文档导入";
  headerDesc.textContent = `${state.selectedKb?.kbName || ""}：按文档状态执行解析/分块/向量化`;
  primaryActionBtn.textContent = "上传文档";
  primaryActionBtn.hidden = false;
  uploadKbIdInput.value = String(state.selectedKb?.id || "");

  const safeRecords = Array.isArray(records) ? records : [];
  tableHead.innerHTML = `
    <tr>
      <th>文档ID</th>
      <th>文件名</th>
      <th>状态</th>
      <th>错误信息</th>
      <th>更新时间</th>
      <th>操作</th>
    </tr>
  `;
  tableBody.innerHTML = "";
  if (!safeRecords.length) {
    tableBody.innerHTML = `<tr><td colspan="6">暂无记录</td></tr>`;
    return;
  }

  safeRecords.forEach((row) => {
    const status = Number(row.status);
    const canParse = status === 0 || status === 6;
    const canChunk = status === 1;
    const canVectorize = status === 3;
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${escapeHtml(row.id)}</td>
      <td>${escapeHtml(row.fileName)}</td>
      <td><span class="${statusBadgeClass(status)}">${escapeHtml(row.statusDesc || statusText(status))}</span></td>
      <td class="err-cell" title="${escapeHtml(row.errorMessage || "")}">${escapeHtml(truncateText(row.errorMessage || "-", 28))}</td>
      <td>${escapeHtml(row.updatedAt || "-")}</td>
      <td class="cell-actions">
        <button class="btn-action" data-action="chunks" data-doc-id="${escapeHtml(row.id)}" data-kb-id="${escapeHtml(row.kbId)}">查看分块</button>
        <button class="btn-action" data-action="parse" data-doc-id="${escapeHtml(row.id)}" ${canParse ? "" : "disabled"}>解析</button>
        <button class="btn-action" data-action="chunk" data-doc-id="${escapeHtml(row.id)}" data-kb-id="${escapeHtml(row.kbId)}" ${canChunk ? "" : "disabled"}>分块</button>
        <button class="btn-action" data-action="vectorize" data-doc-id="${escapeHtml(row.id)}" data-kb-id="${escapeHtml(row.kbId)}" ${canVectorize ? "" : "disabled"}>向量化</button>
      </td>
    `;
    tableBody.appendChild(tr);
  });
}

function renderChunkTable(records) {
  uploadSection.hidden = true;
  listSection.hidden = false;
  listTitle.textContent = `分块列表（文档 ${state.selectedDoc?.id || ""}）`;
  headerDesc.textContent = "分块数据默认按 chunkNo 升序展示";
  primaryActionBtn.hidden = true;

  const safeRecords = Array.isArray(records) ? records : [];
  tableHead.innerHTML = `
    <tr>
      <th>分块序号</th>
      <th>分块内容</th>
      <th>启用</th>
      <th>更新时间</th>
    </tr>
  `;
  tableBody.innerHTML = "";
  if (!safeRecords.length) {
    tableBody.innerHTML = `<tr><td colspan="4">暂无记录</td></tr>`;
    return;
  }

  safeRecords.forEach((row) => {
    const tr = document.createElement("tr");
    const fullContent = String(row.content ?? "");
    tr.innerHTML = `
      <td>${escapeHtml(row.chunkNo)}</td>
      <td>
        <button
          type="button"
          class="chunk-preview-btn"
          data-action="chunk-detail"
          data-content="${escapeHtml(fullContent)}"
          title="点击查看分块详情"
        >${escapeHtml(truncateText(fullContent, 56))}</button>
      </td>
      <td>${escapeHtml(String(row.enabled))}</td>
      <td>${escapeHtml(row.updatedAt || "-")}</td>
    `;
    tableBody.appendChild(tr);
  });
}

function openChunkDetailModal(content) {
  if (!chunkDetailModal || !chunkDetailContent) return;
  chunkDetailContent.textContent = String(content ?? "");
  chunkDetailModal.hidden = false;
}

async function loadPage(pageNo = 1) {
  state.page.current = pageNo;
  renderBreadcrumb();
  try {
    if (state.mode === ViewMode.KB) {
      const res = await fetchKbPage(pageNo, state.page.size);
      const page = res.data || {};
      const records = page.records || [];
      renderRawPayload(res);
      updatePageMeta(page, records.length);
      renderKbTable(records);
      return;
    }
    if (state.mode === ViewMode.DOC) {
      const res = await fetchDocPage(state.selectedKb.id, pageNo, state.page.size);
      const page = res.data || {};
      const records = page.records || [];
      renderRawPayload(res);
      updatePageMeta(page, records.length);
      renderDocTable(records);
      return;
    }
    const res = await fetchChunkPage(state.selectedDoc.id, state.selectedKb.id, pageNo, state.page.size);
    const page = res.data || {};
    const records = page.records || [];
    renderRawPayload(res);
    updatePageMeta(page, records.length);
    renderChunkTable(records);
  } catch (error) {
    tableBody.innerHTML = `<tr><td colspan="8">${serverErrorFallback(error)}</td></tr>`;
    renderRawPayload({ error: serverErrorFallback(error) });
    showToast(serverErrorFallback(error));
  }
}

async function pollDocPage(attempts = 5, intervalMs = 1200) {
  if (state.mode !== ViewMode.DOC) return;
  for (let i = 0; i < attempts; i += 1) {
    await sleep(intervalMs);
    await loadPage(state.page.current);
  }
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
    createModal.hidden = true;
    showToast(res.msg || "创建成功");
    if (state.mode === ViewMode.KB) {
      await loadPage(1);
    }
  } catch (error) {
    showToast(serverErrorFallback(error));
  }
});

uploadForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const kbId = String(uploadKbIdInput.value || "").trim();
  const file = docFileInput.files?.[0];
  if (!isPositiveIntegerString(kbId) || !file) {
    showToast("请先选择知识库和文件");
    return;
  }
  try {
    const res = await uploadDoc(file, kbId);
    appendLog(`上传文档：${res.msg || "成功"}（kbId=${kbId}, file=${file.name}）`);
    uploadForm.reset();
    showToast(res.msg || "上传请求已提交");
    await loadPage(state.page.current);
  } catch (error) {
    showToast(serverErrorFallback(error));
  }
});

tableBody.addEventListener("click", async (e) => {
  const previewBtn = e.target.closest("button[data-action='chunk-detail']");
  if (previewBtn) {
    openChunkDetailModal(previewBtn.dataset.content || "");
    return;
  }

  const btn = e.target.closest("button[data-action]");
  if (!btn) return;
  if (state.actionBusy) {
    showToast("操作进行中，请稍候");
    return;
  }
  const action = btn.dataset.action;
  try {
    if (action === "detail") {
      const kbId = String(btn.dataset.kbId || "").trim();
      if (!isPositiveIntegerString(kbId)) {
        showToast("服务器异常");
        return;
      }
      state.selectedKb = { id: kbId, kbName: btn.dataset.kbName || "" };
      state.selectedDoc = null;
      state.mode = ViewMode.DOC;
      await loadPage(1);
      return;
    }
    if (action === "chunks") {
      const docId = String(btn.dataset.docId || "").trim();
      if (!isPositiveIntegerString(docId)) {
        showToast("服务器异常");
        return;
      }
      state.selectedDoc = { id: docId };
      state.mode = ViewMode.CHUNK;
      await loadPage(1);
      return;
    }
    if (action === "parse") {
      const docId = String(btn.dataset.docId || "").trim();
      if (!isPositiveIntegerString(docId)) {
        showToast("服务器异常");
        return;
      }
      state.actionBusy = true;
      const res = await parseDoc(docId);
      appendLog(`解析文档：${res.msg || "成功"}（docId=${docId}）`);
      showToast(res.msg || "解析请求已提交");
      await loadPage(state.page.current);
      await pollDocPage(6, 1200);
      return;
    }
    if (action === "chunk") {
      const docId = String(btn.dataset.docId || "").trim();
      const kbId = String(btn.dataset.kbId || "").trim();
      if (!isPositiveIntegerString(docId) || !isPositiveIntegerString(kbId)) {
        showToast("服务器异常");
        return;
      }
      state.actionBusy = true;
      const res = await chunkDoc(docId, kbId, 0);
      appendLog(`文档分块：${res.msg || "成功"}（docId=${docId}, kbId=${kbId}）`);
      showToast(res.msg || "分块请求已提交");
      await loadPage(state.page.current);
      await pollDocPage(6, 1200);
      return;
    }
    if (action === "vectorize") {
      const docId = String(btn.dataset.docId || "").trim();
      const kbId = String(btn.dataset.kbId || "").trim();
      if (!isPositiveIntegerString(docId) || !isPositiveIntegerString(kbId)) {
        showToast("服务器异常");
        return;
      }
      state.actionBusy = true;
      const res = await vectorizeDoc(docId, kbId);
      appendLog(`文档向量化：${res.msg || "成功"}（docId=${docId}, kbId=${kbId}）`);
      showToast(res.msg || "向量化请求已提交");
      await loadPage(state.page.current);
      await pollDocPage(6, 1200);
      return;
    }
  } catch (error) {
    showToast(serverErrorFallback(error));
  } finally {
    state.actionBusy = false;
  }
});

refreshBtn.addEventListener("click", () => {
  loadPage(state.page.current);
});

searchBtn.addEventListener("click", () => {
  state.keyword = (listKeywordInput.value || "").trim();
  loadPage(1);
});

listKeywordInput.addEventListener("keydown", (e) => {
  if (e.key === "Enter") {
    e.preventDefault();
    state.keyword = (listKeywordInput.value || "").trim();
    loadPage(1);
  }
});

primaryActionBtn.addEventListener("click", () => {
  if (state.mode === ViewMode.KB) {
    createModal.hidden = false;
    kbNameInput.focus();
    return;
  }
  if (state.mode === ViewMode.DOC) {
    uploadSection.scrollIntoView({ behavior: "smooth", block: "start" });
    docFileInput.focus();
  }
});

closeCreateModalBtn.addEventListener("click", () => {
  createModal.hidden = true;
});

createModal.addEventListener("click", (e) => {
  if (e.target === createModal) {
    createModal.hidden = true;
  }
});

if (closeChunkDetailModalBtn) {
  closeChunkDetailModalBtn.addEventListener("click", () => {
    chunkDetailModal.hidden = true;
  });
}

if (chunkDetailModal) {
  chunkDetailModal.addEventListener("click", (e) => {
    if (e.target === chunkDetailModal) {
      chunkDetailModal.hidden = true;
    }
  });
}

prevPageBtn.addEventListener("click", () => {
  if (state.page.current > 1) loadPage(state.page.current - 1);
});

nextPageBtn.addEventListener("click", () => {
  if (state.page.current < state.page.pages) loadPage(state.page.current + 1);
});

themeToggle.addEventListener("click", () => {
  const dark = document.body.classList.contains("theme-dark");
  applyTheme(dark ? "light" : "dark");
});

if (!getAccessToken()) {
  window.location.href = "/auth.html";
}

applyTheme(localStorage.getItem(STORAGE_THEME) || "light");
appendLog("构建模块就绪，已进入知识库列表。");
loadPage(1);
