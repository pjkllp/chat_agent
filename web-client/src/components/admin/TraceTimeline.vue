<template>
  <div class="trace-detail-content">
    <div class="trace-timeline">
      <div v-for="(step, i) in steps" :key="i" class="trace-step">
        <div class="trace-step-marker" :class="step.status"></div>
        <div class="trace-step-content">
          <div class="trace-step-header">
            <span class="trace-step-node">{{ nodeLabel(step.nodeName) }}</span>
            <span class="trace-step-time">{{ step.duration ? `${step.duration}ms` : '' }}</span>
            <span class="status-badge" :class="step.status === 'finish' ? 'success' : step.status === 'error' ? 'danger' : 'info'" style="font-size:10px;">{{ step.status }}</span>
          </div>
          <div v-if="step.duration" class="trace-step-time">{{ formatTime(step.startTime) }} → {{ formatTime(step.endTime) }}</div>
          <div v-if="step.errorMessage" class="trace-step-error">{{ step.errorMessage }}</div>
          <details v-if="step.resultData" class="trace-step-detail">
            <summary>查看结果数据</summary>
            <div class="trace-step-result">{{ step.resultData }}</div>
          </details>
        </div>
      </div>
      <div v-if="!steps.length" style="color:var(--text-muted);font-size:13px;">暂无节点数据</div>
    </div>
  </div>
</template>

<script setup>
defineProps({ steps: { type: Array, default: () => [] } });

const nodeLabels = {
  rewrite_node: "问题改写",
  intent_identify_node: "意图识别",
  retrieve_node: "知识检索",
  search_node: "网络搜索",
  fetch_node: "页面抓取",
  answer_node: "生成答案",
  summary_node: "总结",
};

function nodeLabel(name) {
  return nodeLabels[name] || name || "未知节点";
}

function formatTime(t) {
  if (!t) return "-";
  return t.replace("T", " ").slice(0, 19);
}
</script>
