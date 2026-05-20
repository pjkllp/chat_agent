<template>
  <div>
    <!-- Stats dashboard -->
    <div class="trace-stats" v-if="store.traceStats">
      <div class="stat-card" v-if="store.traceStats.totalConversations != null">
        <div class="stat-value">{{ store.traceStats.totalConversations }}</div>
        <div class="stat-label">总对话数</div>
      </div>
      <div class="stat-card" :class="{ warn: errorRateNum > 10 }">
        <div class="stat-value">{{ store.traceStats.errorRate }}</div>
        <div class="stat-label">异常率</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ formatDuration(store.traceStats.avgDuration) }}</div>
        <div class="stat-label">平均响应时间</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ store.traceStats.totalNodes }}</div>
        <div class="stat-label">总节点数</div>
      </div>
    </div>

    <!-- Trace table -->
    <div class="card">
      <div class="card-header">
        <h2>Agent 链路追踪</h2>
        <button class="btn-sm" @click="refresh">刷新</button>
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
        <!-- Pagination -->
        <div class="pager" v-if="store.tracePages > 0">
          <span>共 {{ store.traceTotal }} 条</span>
          <div class="pager-actions">
            <button class="btn-action" :disabled="store.traceCurrent <= 1" @click="loadPage(store.traceCurrent - 1)">上一页</button>
            <span style="font-size:12px;color:var(--text-muted);">{{ store.traceCurrent }} / {{ store.tracePages }}</span>
            <button class="btn-action" :disabled="store.traceCurrent >= store.tracePages" @click="loadPage(store.traceCurrent + 1)">下一页</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Detail modal - landscape / full-screen -->
    <div v-if="detailVisible" class="modal-mask" @click.self="closeDetail">
      <div class="modal-panel modal-panel-landscape">
        <div class="modal-head">
          <h3>链路详情</h3>
          <div style="font-size:12px;color:var(--text-muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1;margin-left:12px;">{{ store.traceDetail?.conversationId }}</div>
          <button class="btn-icon" @click="closeDetail" style="border:none;background:transparent;cursor:pointer;font-size:18px;">✕</button>
        </div>
        <div class="modal-body-landscape">
          <TraceTimeline :steps="store.traceDetail?.steps || []" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useAdminStore } from "../../stores/admin";
import TraceTimeline from "./TraceTimeline.vue";

const store = useAdminStore();
const detailVisible = ref(false);

const errorRateNum = computed(() => {
  const s = store.traceStats;
  if (!s || !s.errorRate) return 0;
  return parseFloat(s.errorRate) || 0;
});

function formatTime(t) {
  if (!t) return "-";
  return t.replace("T", " ").slice(0, 19);
}

function formatDuration(ms) {
  if (ms == null) return "-";
  if (ms < 1000) return `${Math.round(ms)}ms`;
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

async function refresh() {
  await Promise.all([store.loadTraceStats(), store.loadTraceList()]);
}

onMounted(() => {
  if (store.activeTab === "trace") {
    store.loadTraceStats();
  }
});
</script>
