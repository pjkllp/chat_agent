<template>
  <div>
    <div class="card">
      <div class="card-header">
        <h2>Agent 链路追踪</h2>
        <button class="btn-sm" @click="store.loadTraceList()">刷新</button>
      </div>
      <div class="card-body">
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>对话 ID</th>
                <th>节点数</th>
                <th>开始时间</th>
                <th>持续时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in store.traceList" :key="t.conversationId">
                <td style="max-width:160px;overflow:hidden;text-overflow:ellipsis;">{{ t.conversationId }}</td>
                <td>{{ t.nodeCount }}</td>
                <td>{{ formatTime(t.startTime) }}</td>
                <td>{{ formatDuration(t.totalDuration) }}</td>
                <td><button class="btn-action" @click="viewDetail(t.conversationId)">查看</button></td>
              </tr>
              <tr v-if="!store.traceList.length"><td colspan="5" style="text-align:center;color:var(--text-muted);">暂无追踪数据</td></tr>
            </tbody>
          </table>
        </div>
        <div class="pager" v-if="store.tracePages > 1">
          <div class="pager-actions">
            <button class="btn-action" :disabled="store.traceCurrent <= 1" @click="loadPage(store.traceCurrent - 1)">上一页</button>
            <button class="btn-action" :disabled="store.traceCurrent >= store.tracePages" @click="loadPage(store.traceCurrent + 1)">下一页</button>
          </div>
        </div>
      </div>
    </div>
    <!-- Detail modal -->
    <div v-if="detailVisible" class="modal-mask" @click.self="closeDetail">
      <div class="modal-panel modal-panel-wide">
        <div class="modal-head">
          <h3>链路详情</h3>
          <button class="btn-icon" @click="closeDetail" style="border:none;background:transparent;cursor:pointer;">✕</button>
        </div>
        <div style="margin-bottom:8px;font-size:12px;color:var(--text-muted);">对话 ID: {{ store.traceDetail?.conversationId }}</div>
        <TraceTimeline :steps="store.traceDetail?.steps || []" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useAdminStore } from "../../stores/admin";
import TraceTimeline from "./TraceTimeline.vue";

const store = useAdminStore();
const detailVisible = ref(false);

function formatTime(t) {
  if (!t) return "-";
  return t.replace("T", " ").slice(0, 19);
}

function formatDuration(ms) {
  if (ms == null) return "-";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

async function viewDetail(conversationId) {
  await store.loadTraceDetail(conversationId);
  detailVisible.value = true;
}

function closeDetail() {
  detailVisible.value = false;
  store.traceDetail = null;
}

function loadPage(page) { store.loadTraceList(page); }
</script>
