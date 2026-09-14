<template>
  <div class="build-shell">
    <div class="layout-shell">
      <aside class="admin-sidebar">
        <div class="admin-brand">
          <div class="admin-brand-dot">R</div>
          <span class="admin-brand-title">RAG 管理</span>
        </div>
        <div class="admin-menu">
          <div class="menu-group-title">导航</div>
          <button v-if="auth.isAdmin" class="menu-item" :class="{ active: store.activeTab === 'kb' }" @click="store.activeTab = 'kb'">📚 知识库</button>
          <button class="menu-item" :class="{ active: store.activeTab === 'trace' }" @click="store.activeTab = 'trace'">🔗 链路追踪</button>
        </div>
        <div style="margin-top: auto;">
          <button class="menu-item" @click="goBack">← 返回聊天</button>
        </div>
      </aside>
      <div class="admin-content">
        <div class="admin-topbar">
          <div class="admin-topbar-left">{{ store.activeTab === 'kb' ? '知识库管理' : 'Agent 链路追踪' }}</div>
          <button class="btn-icon" @click="toggleTheme">◐</button>
        </div>
        <KbList v-if="auth.isAdmin && store.activeTab === 'kb'" />
        <TraceList v-if="store.activeTab === 'trace'" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import { useAdminStore } from "../stores/admin";
import { useAuthStore } from "../stores/auth";
import KbList from "../components/admin/KbList.vue";
import TraceList from "../components/admin/TraceList.vue";
import "../styles/admin.css";

const router = useRouter();
const store = useAdminStore();
const auth = useAuthStore();

function goBack() { router.push("/"); }

function toggleTheme() {
  const theme = localStorage.getItem("rag_theme");
  const next = theme === "dark" ? "light" : "dark";
  localStorage.setItem("rag_theme", next);
  document.body.classList.toggle("theme-dark", next === "dark");
}

onMounted(() => {
  // 知识库为管理员专属，普通用户停留在默认的 kb 页签会请求到 403。
  if (!auth.isAdmin) store.activeTab = "trace";
  if (store.activeTab === "kb") store.loadKb();
  if (store.activeTab === "trace") store.loadTraceList();
});
</script>
