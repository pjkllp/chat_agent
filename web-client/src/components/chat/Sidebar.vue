<template>
  <aside class="sidebar">
    <div class="sidebar-brand">
      <div class="brand-icon" aria-hidden="true">🤖</div>
      <div class="brand-text">
        <div class="brand-title">RAG 智能问答</div>
        <div class="brand-sub">Powered by AI</div>
      </div>
    </div>
    <button type="button" class="btn-new-chat" @click="newChat">
      <span class="btn-new-chat-icon">+</span>
      <span>新建对话</span>
    </button>
    <a class="link-admin" href="javascript:void(0)" @click="goAdmin">管理后台</a>
    <div class="search-wrap">
      <span class="search-icon" aria-hidden="true">⌕</span>
      <input type="search" class="search-input" placeholder="搜索对话..." v-model="search" @input="filter" />
      <kbd class="search-kbd">Ctrl / ⌘ + K</kbd>
    </div>
    <nav class="history-nav" aria-label="对话历史">
      <template v-for="(items, group) in grouped" :key="group">
        <div v-if="items.length" class="history-group-title">{{ groupLabel(group) }}</div>
        <div v-for="item in items" :key="item.conversationId" class="history-item-row">
          <button
            type="button"
            class="history-item"
            :class="{ active: item.conversationId === store.conversationId }"
            @click="switchConv(item.conversationId)"
          >{{ item.title || "未命名对话" }}</button>
          <button
            type="button"
            class="history-delete-btn"
            title="删除"
            @click.stop="confirmDelete(item.conversationId)"
          >×</button>
        </div>
      </template>
      <div v-if="!filteredList.length" class="history-group-title" style="margin-top:8px;">
        {{ search ? "无匹配结果" : "暂无历史" }}
      </div>
    </nav>
    <div v-if="store.hasMore && !search" class="history-load-more-wrap">
      <button type="button" class="btn-load-more" :disabled="loading" @click="loadMore">
        {{ loading ? "加载中..." : "加载更多" }}
      </button>
    </div>
    <div class="sidebar-footer">
      <div class="user-row">
        <div class="user-avatar">{{ (auth.username || "A").charAt(0).toUpperCase() }}</div>
        <div class="user-meta">
          <span class="user-name">{{ auth.username || "用户" }}</span>
          <span class="user-role">用户</span>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { useRouter } from "vue-router";
import { useChatStore } from "../../stores/chat";
import { useAuthStore } from "../../stores/auth";

const router = useRouter();
const store = useChatStore();
const auth = useAuthStore();
const search = ref("");
const loading = ref(false);

const filteredList = computed(() => {
  const list = store.conversations || [];
  if (!search.value) return list;
  const q = search.value.toLowerCase();
  return list.filter((c) => (c.title || "").toLowerCase().includes(q));
});

const grouped = computed(() => {
  const groups = { today: [], week: [], older: [] };
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  const weekAgo = Date.now() - 7 * 86400000;
  for (const item of filteredList.value) {
    const t = new Date(item.lastTime || item.startTime || Date.now()).getTime();
    if (t >= startOfToday.getTime()) groups.today.push(item);
    else if (t >= weekAgo) groups.week.push(item);
    else groups.older.push(item);
  }
  return groups;
});

function groupLabel(group) {
  return { today: "今天", week: "7 天内", older: "更早" }[group] || group;
}

function newChat() {
  store.newConversation();
}

function switchConv(id) {
  store.loadMessages(id);
}

function confirmDelete(id) {
  store.deleteConversation(id);
}

async function loadMore() {
  loading.value = true;
  try {
    await store.loadConversations(store.currentPage + 1);
  } finally {
    loading.value = false;
  }
}

function filter() { /* computed will react to search.value changes */ }

function goAdmin() {
  router.push("/admin");
}
</script>
