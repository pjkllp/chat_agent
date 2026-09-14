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
        </span>
        <span class="thinking-step-name">{{ step.node }}</span>
        <span class="thinking-step-label">
          {{ step.status === 'start' ? '处理中...' : step.status === 'finish' ? '完成' : '失败' }}
        </span>
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
  </div>
</template>

<script setup>
defineProps({ msg: { type: Object, required: true } });
</script>

<style scoped>
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
