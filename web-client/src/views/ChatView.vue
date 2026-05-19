<template>
  <div @keydown="handleKeydown">
    <div class="bg-fx" aria-hidden="true">
      <div class="bg-glow"></div>
    </div>
    <div class="app-shell">
      <Sidebar />
      <main class="main">
        <header class="main-header">
          <span class="main-title">{{ store.title }}</span>
          <div class="main-header-actions">
            <button type="button" class="btn-icon" @click="toggleTheme">◐</button>
            <button type="button" class="btn-icon" @click="toggleAdmin">⚙️</button>
            <button type="button" class="btn-icon btn-logout" @click="handleLogout">退出登录</button>
          </div>
        </header>
        <div class="main-inner" ref="mainInner">
          <HeroSection v-if="!store.messages.length" />
          <SuggestionCards v-if="!store.messages.length" @select="handleSend" />
          <ChatThread />
          <Composer @send="handleSend" />
        </div>
      </main>
    </div>
    <DeleteConfirmModal />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from "vue";
import { useRouter } from "vue-router";
import { useChatStore } from "../stores/chat";
import { useAuthStore } from "../stores/auth";
import Sidebar from "../components/chat/Sidebar.vue";
import HeroSection from "../components/chat/HeroSection.vue";
import SuggestionCards from "../components/chat/SuggestionCards.vue";
import ChatThread from "../components/chat/ChatThread.vue";
import Composer from "../components/chat/Composer.vue";
import DeleteConfirmModal from "../components/chat/DeleteConfirmModal.vue";

const router = useRouter();
const store = useChatStore();
const auth = useAuthStore();
const mainInner = ref(null);
const theme = ref(localStorage.getItem("rag_theme") || "light");

// Prevent default Ctrl+K to avoid browser search dialog
function handleKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === "k") {
    e.preventDefault();
  }
}

function handleSend(text) {
  if (!text.trim() || store.streaming) return;
  store.sendQuestion(text.trim(), true);
  nextTick(() => scrollToBottom());
}

function toggleTheme() {
  theme.value = theme.value === "dark" ? "light" : "dark";
  localStorage.setItem("rag_theme", theme.value);
  document.body.classList.toggle("theme-dark", theme.value === "dark");
}

function toggleAdmin() {
  router.push("/admin");
}

async function handleLogout() {
  await auth.logout();
  router.push("/auth");
}

function scrollToBottom() {
  if (mainInner.value) {
    mainInner.value.scrollTop = mainInner.value.scrollHeight;
  }
}

// Auto scroll on new messages
watch(
  () => store.messages.length,
  () => nextTick(() => scrollToBottom())
);

onMounted(() => {
  if (theme.value === "dark") document.body.classList.add("theme-dark");
  store.loadConversations();
});
</script>
