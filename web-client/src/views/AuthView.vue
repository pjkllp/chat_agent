<template>
  <div>
    <div class="bg-fx" aria-hidden="true">
      <div class="bg-glow"></div>
      <div class="particle-layer" ref="particleLayer"></div>
    </div>
    <div class="auth-page">
      <aside class="auth-side">
        <div class="auth-brand">
          <div class="auth-brand-icon">🤖</div>
          <div>
            <div class="auth-brand-title">RAG 智能问答</div>
            <div class="auth-brand-sub">Powered by AI</div>
          </div>
        </div>
        <h1 class="auth-side-title">把问题变成<span style="color:var(--primary)">清晰答案</span></h1>
        <p class="auth-side-desc">登录后可使用深度思考、知识检索与会话历史功能。</p>
      </aside>
      <main class="auth-main">
        <section class="auth-card">
          <div class="auth-card-top">
            <button class="auth-theme-btn" type="button" @click="toggleTheme">{{ themeIcon }}</button>
          </div>
          <div class="auth-tabs" role="tablist">
            <button class="auth-tab" :class="{ active: tab === 'login' }" type="button" @click="tab = 'login'">登录</button>
            <button class="auth-tab" :class="{ active: tab === 'register' }" type="button" @click="tab = 'register'">注册</button>
          </div>
          <LoginForm v-if="tab === 'login'" @message="showMessage" />
          <RegisterForm v-if="tab === 'register'" @message="showMessage" @login="switchToLogin" />
          <p class="auth-message" :class="msgType">{{ msgText }}</p>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import LoginForm from "../components/auth/LoginForm.vue";
import RegisterForm from "../components/auth/RegisterForm.vue";

const tab = ref("login");
const msgText = ref("");
const msgType = ref("");
const theme = ref(localStorage.getItem("rag_theme") || "light");
const particleLayer = ref(null);

const themeIcon = ref(theme.value === "dark" ? "☀" : "☾");

function showMessage(text, type) {
  msgText.value = text;
  msgType.value = type || "";
}

function switchToLogin(username) {
  tab.value = "login";
  msgText.value = "注册成功，请登录";
  msgType.value = "success";
}

function toggleTheme() {
  const next = theme.value === "dark" ? "light" : "dark";
  theme.value = next;
  themeIcon.value = next === "dark" ? "☀" : "☾";
  localStorage.setItem("rag_theme", next);
  document.body.classList.toggle("theme-dark", next === "dark");
}

onMounted(() => {
  if (theme.value === "dark") document.body.classList.add("theme-dark");
  if (particleLayer.value && theme.value === "dark") {
    for (let i = 0; i < 24; i++) {
      const dot = document.createElement("span");
      dot.className = "particle";
      dot.style.left = `${Math.random() * 100}%`;
      dot.style.width = `${4 + Math.random() * 8}px`;
      dot.style.height = dot.style.width;
      dot.style.animationDuration = `${10 + Math.random() * 12}s`;
      dot.style.animationDelay = `${-Math.random() * 22}s`;
      dot.style.setProperty("--drift", `${-40 + Math.random() * 80}px`);
      particleLayer.value.appendChild(dot);
    }
  }
});
</script>
