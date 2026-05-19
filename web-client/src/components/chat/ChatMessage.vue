<template>
  <div class="msg" :class="[msg.role, { streaming: msg.streaming }]">
    <span class="msg-label">{{ msg.role === "user" ? "用户" : "AI" }}</span>
    <div class="msg-bubble">{{ msg.content || (msg.streaming ? "思考中..." : "") }}</div>
  </div>
</template>

<script setup>
defineProps({ msg: { type: Object, required: true } });
</script>
