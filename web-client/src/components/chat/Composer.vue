<template>
  <div class="composer-wrap">
    <div class="composer">
      <textarea
        class="composer-textarea"
        rows="3"
        placeholder="输入需要深度分析的问题..."
        v-model="text"
        @keydown="handleKeydown"
      ></textarea>
      <div class="composer-toolbar">
        <button type="button" class="pill-deep" :aria-pressed="deepThink" @click="deepThink = !deepThink">
          <span class="pill-dot" aria-hidden="true"></span>
          <span class="pill-icon" aria-hidden="true">◎</span>
          深度思考
        </button>
        <button type="button" class="btn-send" :disabled="!text.trim() || store.streaming" @click="send">
          <span aria-hidden="true">➤</span>
        </button>
      </div>
    </div>
    <p class="composer-hint">Enter 发送 · Shift + Enter 换行</p>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useChatStore } from "../../stores/chat";

const emit = defineEmits(["send"]);
const store = useChatStore();
const text = ref("");
const deepThink = ref(true);

function handleKeydown(e) {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    send();
  }
}

function send() {
  const q = text.value.trim();
  if (!q || store.streaming) return;
  text.value = "";
  emit("send", q, deepThink.value);
}
</script>
