<template>
  <form class="auth-form" @submit.prevent="handleRegister">
    <label class="auth-label">用户名</label>
    <input class="auth-input" placeholder="3-32位字母数字下划线" v-model="username" @input="debouncedCheck" />
    <p class="field-hint-row" :class="hintClass">{{ hintText }}</p>
    <label class="auth-label">邮箱</label>
    <input class="auth-input" type="email" placeholder="请输入邮箱" v-model="email" />
    <label class="auth-label">密码</label>
    <input class="auth-input" type="password" placeholder="至少6位" v-model="password" />
    <label class="auth-label">验证码</label>
    <div class="auth-inline">
      <input class="auth-input" placeholder="请输入验证码" v-model="code" />
      <button class="auth-btn-secondary" type="button" :disabled="sending" @click="sendCode">{{ sending ? "发送中" : "发送验证码" }}</button>
    </div>
    <button class="auth-btn-primary" type="submit">注册</button>
  </form>
</template>

<script setup>
import { ref } from "vue";
import { useAuthStore } from "../../stores/auth";
import { verifyUsername } from "../../services/auth";

const emit = defineEmits(["message", "login"]);
const auth = useAuthStore();

const username = ref("");
const password = ref("");
const email = ref("");
const code = ref("");
const hintText = ref("");
const hintClass = ref("info");
const sending = ref(false);
let checkTimer = null;

const USERNAME_REGEX = /^[a-zA-Z0-9_]{3,32}$/;

function debouncedCheck() {
  clearTimeout(checkTimer);
  hintText.value = "";
  const u = username.value.trim();
  if (!u) return;
  if (!USERNAME_REGEX.test(u)) { hintText.value = "格式不正确"; hintClass.value = "error"; return; }
  hintText.value = "校验中...";
  hintClass.value = "loading";
  checkTimer = setTimeout(async () => {
    try {
      const exists = await verifyUsername(u);
      hintText.value = exists ? "账号已存在" : "账号可用";
      hintClass.value = exists ? "error" : "success";
    } catch {
      hintText.value = "校验失败";
      hintClass.value = "error";
    }
  }, 350);
}

async function sendCode() {
  const u = username.value.trim();
  const e = email.value.trim();
  const p = password.value;
  if (!u || !e || !p) { emit("message", "请先填写用户名、邮箱和密码", "error"); return; }
  if (!USERNAME_REGEX.test(u)) { emit("message", "用户名格式不正确", "error"); return; }
  if (hintText.value === "账号已存在") { emit("message", "该账号已存在", "error"); return; }
  sending.value = true;
  try {
    await auth.applyCode(u, p, e);
    emit("message", "验证码发送成功", "success");
  } catch (e) {
    emit("message", e?.message || "发送失败", "error");
  } finally {
    sending.value = false;
  }
}

async function handleRegister() {
  const u = username.value.trim();
  const e = email.value.trim();
  const p = password.value;
  const c = code.value.trim();
  if (!u || !e || !p || !c) { emit("message", "请完整填写注册信息", "error"); return; }
  if (hintText.value === "账号已存在") { emit("message", "该账号已存在", "error"); return; }
  try {
    await auth.register(u, p, e, c);
    emit("login", u);
  } catch (e) {
    emit("message", e?.message || "注册失败", "error");
  }
}
</script>
