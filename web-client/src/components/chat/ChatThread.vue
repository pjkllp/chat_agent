<template>
  <section v-if="store.messages.length" class="chat-thread">
    <ChatMessage
      v-for="(msg, i) in store.messages"
      :key="i"
      :msg="msg"
    />
  </section>
</template>

<script setup>
import { useChatStore } from "../../stores/chat";
import ChatMessage from "./ChatMessage.vue";

const store = useChatStore();
</script>
