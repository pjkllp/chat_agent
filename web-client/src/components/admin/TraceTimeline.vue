<template>
  <div class="trace-detail-content">
    <div v-if="!turns.length" style="color:var(--text-muted);font-size:13px;">暂无节点数据</div>
    <div v-for="(turn, ti) in turns" :key="ti" class="trace-turn">
      <div class="trace-turn-head">
        <span class="trace-turn-index">{{ turn.messageId ? `第 ${ti + 1} 轮` : '历史数据' }}</span>
        <span class="trace-turn-meta">{{ turn.nodeCount }} 个节点</span>
        <span class="trace-turn-meta" v-if="turn.totalDuration != null">{{ turn.totalDuration }}ms</span>
        <span class="status-badge" :class="statusClass(turn.status)" style="font-size:10px;">{{ statusLabel(turn.status) }}</span>
        <span class="trace-turn-id" :title="turn.messageId || '无 message_id（历史数据）'">
          {{ turn.messageId || '历史数据（无 message_id）' }}
        </span>
      </div>
      <div class="trace-timeline">
        <div v-for="(step, i) in turn.steps" :key="i" class="trace-step">
          <div class="trace-step-marker" :class="statusClass(step.status)"></div>
          <div class="trace-step-content">
            <div class="trace-step-header">
              <span class="trace-step-node">{{ nodeLabel(step.nodeName) }}</span>
              <span class="trace-step-time">{{ step.duration != null ? `${step.duration}ms` : '' }}</span>
              <span class="status-badge" :class="statusClass(step.status)" style="font-size:10px;">{{ statusLabel(step.status) }}</span>
            </div>
            <div v-if="step.duration != null" class="trace-step-time">{{ formatTime(step.startTime) }} → {{ formatTime(step.endTime) }}</div>
            <div v-if="step.errorMessage" class="trace-step-error">{{ step.errorMessage }}</div>
            <details v-if="step.resultData" class="trace-step-detail">
              <summary>查看结果数据</summary>
              <div class="trace-step-result">{{ step.resultData }}</div>
            </details>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({ turns: { type: Array, default: () => [] } });

const nodeLabels = {
  rewrite_node: "问题改写",
  intent_identify_node: "意图识别",
  retrieve_node: "知识检索",
  search_node: "网络搜索",
  fetch_node: "页面抓取",
  answer_node: "生成答案",
  summary_node: "总结",
};

// 后端落库的 status 是大写（START/FINISH/ERROR），这里统一归一化后再做样式和文案映射
const STATUS_LABELS = { START: "进行中", RUNNING: "进行中", FINISH: "完成", ERROR: "失败" };

function nodeLabel(name) {
  return nodeLabels[name] || name || "未知节点";
}

function statusClass(status) {
  const key = (status || "").toUpperCase();
  if (key === "FINISH") return "finish";
  if (key === "ERROR") return "error";
  return "start";
}

function statusLabel(status) {
  return STATUS_LABELS[(status || "").toUpperCase()] || status || "-";
}

function formatTime(t) {
  if (!t) return "-";
  return t.replace("T", " ").slice(0, 19);
}
</script>
