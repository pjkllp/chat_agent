<template>
  <div>
    <!-- KB List -->
    <div class="card">
      <div class="card-header">
        <h2>知识库</h2>
        <button class="btn-primary-sm" @click="showCreateKb = true">+ 新建</button>
      </div>
      <div class="card-body">
        <div v-if="showCreateKb" class="form-grid" style="margin-bottom:12px;">
          <input class="field-input" placeholder="知识库名称" v-model="newKbName" @keyup.enter="handleCreateKb" />
          <input class="field-input" placeholder="描述（可选）" v-model="newKbDesc" @keyup.enter="handleCreateKb" />
          <button class="btn-primary-sm" @click="handleCreateKb">确认创建</button>
          <button class="btn-sm" @click="showCreateKb = false; newKbName = ''; newKbDesc = ''">取消</button>
        </div>
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>名称</th><th>描述</th><th>状态</th><th>创建时间</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="kb in store.kbList" :key="kb.id">
                <td>{{ kb.kbName }}</td>
                <td>{{ kb.description || '-' }}</td>
                <td><span class="status-badge" :class="kb.enabled ? 'success' : 'default'">{{ kb.enabled ? '启用' : '禁用' }}</span></td>
                <td>{{ formatTime(kb.createdAt) }}</td>
                <td class="cell-actions">
                  <button class="btn-action" @click="selectKb(kb)">📂 文档</button>
                </td>
              </tr>
              <tr v-if="!store.kbList.length"><td colspan="5" style="text-align:center;color:var(--text-muted);">暂无知识库</td></tr>
            </tbody>
          </table>
        </div>
        <div class="pager" v-if="store.kbPages > 1">
          <div class="pager-actions">
            <button class="btn-action" :disabled="store.kbCurrent <= 1" @click="loadKbPage(store.kbCurrent - 1)">上一页</button>
            <button class="btn-action" :disabled="store.kbCurrent >= store.kbPages" @click="loadKbPage(store.kbCurrent + 1)">下一页</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Docs for selected KB -->
    <div class="card" v-if="store.selectedKbId" style="margin-top:14px;">
      <div class="card-header">
        <h2>文档 <span style="font-size:12px;color:var(--text-muted);font-weight:400;">{{ selectedKbName }}</span></h2>
        <button class="btn-primary-sm" @click="showUpload = true">+ 上传</button>
      </div>
      <div class="card-body">
        <div v-if="showUpload" class="upload-inline" style="margin-bottom:12px;">
          <input class="field-input" type="file" @change="handleFileSelect" />
          <button class="btn-primary-sm" :disabled="!uploadFile" @click="handleUpload">上传</button>
          <button class="btn-sm" @click="showUpload = false; uploadFile = null">取消</button>
        </div>
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>文件名</th><th>状态</th><th>版本</th><th>创建时间</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="doc in store.docList" :key="doc.id">
                <td>{{ doc.fileName }}</td>
                <td><span class="status-badge" :class="statusClass(doc.status)">{{ doc.statusDesc || statusText(doc.status) }}</span></td>
                <td>v{{ doc.version || 1 }}</td>
                <td>{{ formatTime(doc.createdAt) }}</td>
                <td class="cell-actions">
                  <button class="btn-action" :disabled="doc.status !== 0" @click="handleParse(doc)">解析</button>
                  <button class="btn-action" :disabled="doc.status !== 1" @click="handleChunk(doc)">分块</button>
                  <button class="btn-action" :disabled="doc.status !== 2" @click="handleEmbed(doc)">向量化</button>
                  <button class="btn-action" @click="viewChunks(doc)">查看块</button>
                </td>
              </tr>
              <tr v-if="!store.docList.length"><td colspan="5" style="text-align:center;color:var(--text-muted);">暂无文档</td></tr>
            </tbody>
          </table>
        </div>
        <div class="pager" v-if="store.docPages > 1">
          <div class="pager-actions">
            <button class="btn-action" :disabled="store.docCurrent <= 1" @click="loadDocPage(store.docCurrent - 1)">上一页</button>
            <button class="btn-action" :disabled="store.docCurrent >= store.docPages" @click="loadDocPage(store.docCurrent + 1)">下一页</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Chunks for selected doc -->
    <div class="card" v-if="store.selectedDocId" style="margin-top:14px;">
      <div class="card-header">
        <h2>文档块</h2>
      </div>
      <div class="card-body">
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>块号</th><th>内容</th><th>启用</th><th>创建时间</th></tr>
            </thead>
            <tbody>
              <tr v-for="chunk in store.chunkList" :key="chunk.id">
                <td>{{ chunk.chunkNo }}</td>
                <td style="max-width:400px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ chunk.content }}</td>
                <td><span class="status-badge" :class="chunk.enabled ? 'success' : 'default'">{{ chunk.enabled ? '是' : '否' }}</span></td>
                <td>{{ formatTime(chunk.createdAt) }}</td>
              </tr>
              <tr v-if="!store.chunkList.length"><td colspan="4" style="text-align:center;color:var(--text-muted);">暂无块</td></tr>
            </tbody>
          </table>
        </div>
        <div class="pager" v-if="store.chunkPages > 1">
          <div class="pager-actions">
            <button class="btn-action" :disabled="store.chunkCurrent <= 1" @click="loadChunkPage(store.chunkCurrent - 1)">上一页</button>
            <button class="btn-action" :disabled="store.chunkCurrent >= store.chunkPages" @click="loadChunkPage(store.chunkCurrent + 1)">下一页</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useAdminStore } from "../../stores/admin";

const store = useAdminStore();
const showCreateKb = ref(false);
const newKbName = ref("");
const newKbDesc = ref("");
const showUpload = ref(false);
const uploadFile = ref(null);
const selectedKbName = ref("");

function formatTime(t) {
  if (!t) return "-";
  return t.replace("T", " ").slice(0, 19);
}

function statusText(status) {
  const m = { 0: "待处理", 1: "已解析", 2: "已分块", 3: "已向量化", "-1": "失败" };
  return m[status] || "未知";
}

function statusClass(status) {
  if (status === -1) return "danger";
  if (status === 0) return "default";
  if (status === 1) return "info";
  if (status === 2) return "warning";
  if (status === 3) return "success";
  return "default";
}

async function handleCreateKb() {
  if (!newKbName.value.trim()) return;
  await store.createKb(newKbName.value.trim(), newKbDesc.value.trim());
  newKbName.value = "";
  newKbDesc.value = "";
  showCreateKb.value = false;
}

function selectKb(kb) {
  selectedKbName.value = kb.kbName;
  store.loadDocs(1, 20, kb.id);
}

function handleFileSelect(e) {
  uploadFile.value = e.target.files[0] || null;
}

async function handleUpload() {
  if (!uploadFile.value || !store.selectedKbId) return;
  await store.uploadDoc(store.selectedKbId, uploadFile.value);
  uploadFile.value = null;
  showUpload.value = false;
}

async function handleParse(doc) {
  await store.parseDoc(doc.id);
}

async function handleChunk(doc) {
  await store.chunkDoc(store.selectedKbId, doc.id);
}

async function handleEmbed(doc) {
  await store.chunkEmbedding(store.selectedKbId, doc.id);
}

function viewChunks(doc) {
  store.loadChunks(1, 20, store.selectedKbId, doc.id);
}

function loadKbPage(page) { store.loadKb(page); }
function loadDocPage(page) { store.loadDocs(page, 20, store.selectedKbId); }
function loadChunkPage(page) { store.loadChunks(page, 20, store.selectedKbId, store.selectedDocId); }
</script>
