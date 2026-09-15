<template>
  <div class="msg" :class="[msg.role, { streaming: msg.streaming }]">
    <span class="msg-label">{{ msg.role === "user" ? "用户" : "AI" }}</span>
    <!-- Thinking steps (deepThink mode) -->
    <div v-if="msg.thinkingSteps && msg.thinkingSteps.length" class="thinking-wrap">
      <div class="thinking-header">思考过程</div>
      <div
        v-for="(step, i) in msg.thinkingSteps"
        :key="i"
        class="thinking-step"
        :class="step.status"
      >
        <span class="thinking-step-icon">
          <template v-if="step.status === 'start'">⟳</template>
          <template v-else-if="step.status === 'finish'">✓</template>
          <template v-else-if="step.status === 'error'">✗</template>
          <template v-else-if="step.status === 'cancel'">⊘</template>
          <template v-else>·</template>
        </span>
        <span class="thinking-step-name">{{ step.node }}</span>
        <span class="thinking-step-label">{{ stepLabel(step.status) }}</span>
      </div>
      <div v-if="msg.streaming" class="thinking-loading">...</div>
    </div>
    <!-- Attachments (user messages) -->
    <div v-if="msg.attachments && msg.attachments.length" class="msg-attachments">
      <template v-for="(a, i) in msg.attachments" :key="i">
        <a v-if="a.type === 'IMAGE'" :href="a.url" target="_blank" rel="noopener">
          <img :src="a.url" class="msg-attach-image" alt="" />
        </a>
        <audio v-else-if="a.type === 'AUDIO'" :src="a.url" controls class="msg-attach-audio"></audio>
      </template>
    </div>
    <!-- Answer content -->
    <div class="msg-bubble" :class="{ 'bubble-empty': msg.streaming && !msg.content }">
      <template v-if="msg.content">{{ msg.content }}</template>
      <template v-else-if="msg.streaming"><span class="streaming-cursor">▍</span></template>
    </div>
    <div v-if="msg.cancelled && !msg.streaming" class="msg-cancelled">已停止生成</div>
  </div>
</template>

<script setup>
defineProps({ msg: { type: Object, required: true } });

// 后端节点状态：start/finish/error/cancel 是节点生命周期，hit/progress/done 是过程性事件
const STATUS_TEXT = {
  start: "处理中...",
  finish: "完成",
  error: "失败",
  cancel: "已取消",
  done: "完成",
  hit: "命中",
  progress: "进行中",
};

function stepLabel(status) {
  return STATUS_TEXT[status] || status || "";
}
</script>

<style scoped>
.msg-cancelled {
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.08em;
  color: var(--text-faint);
}

.msg-attach-image {
  display: block;
  max-width: 220px;
  max-height: 220px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  transition: transform 0.2s var(--ease), border-color 0.2s;
}

a:hover > .msg-attach-image {
  transform: translateY(-2px);
  border-color: var(--accent-strong);
}

.msg-attach-audio {
  width: 260px;
  height: 36px;
}
</style>
