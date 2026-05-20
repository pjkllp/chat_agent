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
