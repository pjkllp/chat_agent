<template>
  <form class="auth-form" @submit.prevent="handleLogin">
    <label class="auth-label">用户名</label>
    <input class="auth-input" placeholder="请输入用户名" v-model="username"
     @input="debouncedCheck" />
    <p class="field-hint-row" :class="hintClass">{{ hintText }}</p>
    <label class="auth-label">密码</label>
    <input class="auth-input" type="password" placeholder="请输入密码" v-model="password" />
    <button class="auth-btn-primary" type="submit">登录</button>
  </form>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../../stores/auth";
import { verifyUsername } from "../../services/auth";

const emit = defineEmits(["message"]);
const router = useRouter();
const auth = useAuthStore();

const username = ref("");
const password = ref("");
const hintText = ref("");
const hintClass = ref("info");
let checkTimer = null;

const USERNAME_REGEX = /^[a-zA-Z0-9_]{3,32}$/;

async function debouncedCheck() {
  clearTimeout(checkTimer);
  hintText.value = "";
  const u = username.value.trim();
  if (!u) return;
  if (!USERNAME_REGEX.test(u)) { 
    hintText.value = "格式不正确"; hintClass.value = "error"; return;
   }
  hintText.value = "校验中...";
  hintClass.value = "loading";
  checkTimer = setTimeout(async () => {
    try {
      const exists = await verifyUsername(u);
      hintText.value = exists ? "账号存在" : "账号不存在，请先注册";
      hintClass.value = exists ? "success" : "error";
    } catch(e) {
      console.log('err:',e)
      hintText.value = "校验失败";
      hintClass.value = "error";
    }
  }, 350);
}

async function handleLogin() {
  const u = username.value.trim();
  const p = password.value;
  if (!u || !p) { emit("message", "请填写用户名和密码", "error"); return; }
  if (!USERNAME_REGEX.test(u)) { emit("message", "用户名格式不正确", "error"); return; }
  if (hintText.value === "账号不存在，请先注册") { emit("message", "该账号不存在，请先注册", "error"); return; }
  emit("message", "登录中...");
  try {
    await auth.login(u, p);
    emit("message", "");
    auth.initFromStorage();
    router.push("/");
  } catch (e) {
    emit("message", e?.message || "登录失败", "error");
  }
}
</script>
