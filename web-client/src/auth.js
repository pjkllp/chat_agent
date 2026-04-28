import "./auth.css";

const API_BASE = "/api/auth";
const TOKEN_KEY = "rag_access_token";
const USER_KEY = "rag_user_id";
const STORAGE_THEME = "rag_theme";

const tabLogin = document.querySelector("#tabLogin");
const tabRegister = document.querySelector("#tabRegister");
const loginForm = document.querySelector("#loginForm");
const registerForm = document.querySelector("#registerForm");
const authMessage = document.querySelector("#authMessage");

const loginUsername = document.querySelector("#loginUsername");
const loginPassword = document.querySelector("#loginPassword");

const registerUsername = document.querySelector("#registerUsername");
const registerEmail = document.querySelector("#registerEmail");
const registerPassword = document.querySelector("#registerPassword");
const registerCode = document.querySelector("#registerCode");
const sendCodeBtn = document.querySelector("#sendCodeBtn");
const themeToggle = document.querySelector("#themeToggle");
const particleLayer = document.querySelector("#particleLayer");
const loginUsernameHint = document.querySelector("#loginUsernameHint");
const registerUsernameHint = document.querySelector("#registerUsernameHint");
const mascotWrap = document.querySelector("#mascotWrap");
const mascot = document.querySelector("#mascot");
const mascotText = document.querySelector("#mascotText");
const pupilLeft = document.querySelector("#pupilLeft");
const pupilRight = document.querySelector("#pupilRight");

const USERNAME_REGEX = /^[a-zA-Z0-9_]{3,32}$/;

let loginNameExists = null;
let registerNameExists = null;
let loginCheckTimer = null;
let registerCheckTimer = null;

function showMessage(text, type = "") {
  authMessage.textContent = text || "";
  authMessage.className = `auth-message ${type}`.trim();
}

function setHint(el, text, cls = "info") {
  if (!el) return;
  el.textContent = text || "";
  el.className = `field-hint-row ${cls}`.trim();
}

function buildAuthHeaders(url) {
  const headers = { "Content-Type": "application/json" };
  if (url.includes("/login") || url.includes("/register")) {
    return headers;
  }
  const token = (localStorage.getItem(TOKEN_KEY) || "").trim();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

async function verifyUsernameExists(username) {
  const url = `${API_BASE}/verify_username?username=${encodeURIComponent(username)}`;
  const resp = await fetch(url, { headers: buildAuthHeaders(url) });
  let payload = {};
  try {
    payload = await resp.json();
  } catch {
    payload = {};
  }
  if (!resp.ok) {
    throw new Error(resolveBackendMessage(payload, `请求失败: ${resp.status}`));
  }
  if (payload && typeof payload === "object" && "code" in payload && payload.code !== 1) {
    throw new Error(resolveBackendMessage(payload, "校验失败"));
  }
  return Boolean(payload?.data);
}

function validateUsernameFormat(username) {
  if (!username) return { ok: false, text: "请输入用户名" };
  if (!USERNAME_REGEX.test(username)) {
    return { ok: false, text: "用户名须为 3–32 位字母、数字或下划线" };
  }
  return { ok: true, text: "格式正确" };
}

function resolveBackendMessage(payload, fallback) {
  if (payload && typeof payload === "object") {
    const msg = typeof payload.msg === "string" ? payload.msg.trim() : "";
    const message = typeof payload.message === "string" ? payload.message.trim() : "";
    if (msg) return msg;
    if (message) return message;
  }
  return fallback;
}

function ensureParticles(enabled) {
  if (!particleLayer) return;
  if (!enabled) {
    particleLayer.innerHTML = "";
    return;
  }
  if (particleLayer.childElementCount > 0) return;
  for (let i = 0; i < 24; i++) {
    const dot = document.createElement("span");
    dot.className = "particle";
    const left = Math.random() * 100;
    const size = 4 + Math.random() * 8;
    const duration = 10 + Math.random() * 12;
    const delay = -Math.random() * duration;
    const drift = -40 + Math.random() * 80;
    dot.style.left = `${left}%`;
    dot.style.width = `${size}px`;
    dot.style.height = `${size}px`;
    dot.style.animationDuration = `${duration}s`;
    dot.style.animationDelay = `${delay}s`;
    dot.style.setProperty("--drift", `${drift}px`);
    particleLayer.appendChild(dot);
  }
}

function applyTheme(theme) {
  const dark = theme === "dark";
  document.body.classList.toggle("theme-dark", dark);
  themeToggle.textContent = dark ? "☀" : "☾";
  themeToggle.title = dark ? "切换到浅色" : "切换到暗色";
  localStorage.setItem(STORAGE_THEME, dark ? "dark" : "light");
  ensureParticles(dark);
}

function mascotSay(text) {
  if (!mascotText) return;
  mascotText.textContent = text;
}

function waveMascot() {
  if (!mascot) return;
  mascot.classList.remove("waving");
  requestAnimationFrame(() => mascot.classList.add("waving"));
  setTimeout(() => mascot.classList.remove("waving"), 1200);
}

function movePupilsByPointer(clientX, clientY) {
  if (!mascotWrap || !pupilLeft || !pupilRight) return;
  const rect = mascotWrap.getBoundingClientRect();
  const cx = rect.left + rect.width / 2;
  const cy = rect.top + rect.height / 2;
  const dx = Math.max(-1, Math.min(1, (clientX - cx) / 80));
  const dy = Math.max(-1, Math.min(1, (clientY - cy) / 80));
  const tx = (dx * 2.8).toFixed(2);
  const ty = (dy * 2.8).toFixed(2);
  pupilLeft.style.transform = `translate(${tx}px, ${ty}px)`;
  pupilRight.style.transform = `translate(${tx}px, ${ty}px)`;
}

function resetPupils() {
  if (!pupilLeft || !pupilRight) return;
  pupilLeft.style.transform = "translate(0, 0)";
  pupilRight.style.transform = "translate(0, 0)";
}

function switchTab(mode) {
  const isLogin = mode === "login";
  tabLogin.classList.toggle("active", isLogin);
  tabRegister.classList.toggle("active", !isLogin);
  loginForm.hidden = !isLogin;
  registerForm.hidden = isLogin;
  showMessage("");
}

async function requestJson(url, options) {
  const response = await fetch(url, {
    headers: buildAuthHeaders(url),
    ...options
  });
  let payload = null;
  try {
    payload = await response.json();
  } catch {
    payload = {};
  }
  if (!response.ok) {
    throw new Error(resolveBackendMessage(payload, `请求失败: ${response.status}`));
  }
  if (payload && typeof payload === "object" && "code" in payload && payload.code !== 1) {
    throw new Error(resolveBackendMessage(payload, "请求失败"));
  }
  return payload;
}

function extractToken(payload) {
  if (!payload) return "";
  if (typeof payload.data === "string") return payload.data;
  if (typeof payload.accessToken === "string") return payload.accessToken;
  if (payload.data && typeof payload.data.accessToken === "string") return payload.data.accessToken;
  return "";
}

async function handleLogin(evt) {
  evt.preventDefault();
  const username = loginUsername.value.trim();
  const password = loginPassword.value;
  if (!username || !password) {
    showMessage("请填写用户名和密码", "error");
    return;
  }
  const format = validateUsernameFormat(username);
  if (!format.ok) {
    setHint(loginUsernameHint, format.text, "error");
    showMessage("请先修正用户名格式", "error");
    return;
  }
  if (loginNameExists === false) {
    showMessage("该账号不存在，请先注册", "error");
    return;
  }
  try {
    showMessage("登录中...");
    const payload = await requestJson(`${API_BASE}/login`, {
      method: "POST",
      body: JSON.stringify({ username, password })
    });
    const token = extractToken(payload);
    if (!token) {
      throw new Error("登录成功但未返回 token");
    }
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, username);
    showMessage("登录成功，正在跳转...", "success");
    setTimeout(() => {
      window.location.href = "/";
    }, 350);
  } catch (error) {
    showMessage(error?.message || "登录失败", "error");
  }
}

async function handleSendCode() {
  const username = registerUsername.value.trim();
  const email = registerEmail.value.trim();
  const password = registerPassword.value;
  if (!username || !email || !password) {
    showMessage("请先填写用户名、邮箱和密码", "error");
    return;
  }
  const format = validateUsernameFormat(username);
  if (!format.ok) {
    setHint(registerUsernameHint, format.text, "error");
    showMessage("请先修正用户名格式", "error");
    return;
  }
  if (registerNameExists === true) {
    showMessage("该账号已存在，请更换用户名", "error");
    return;
  }
  sendCodeBtn.disabled = true;
  try {
    showMessage("验证码发送中...");
    const payload = await requestJson(`${API_BASE}/applyCode`, {
      method: "POST",
      body: JSON.stringify({ username, email, password })
    });
    showMessage(payload.msg || "验证码发送成功", "success");
  } catch (error) {
    showMessage(error?.message || "验证码发送失败", "error");
  } finally {
    setTimeout(() => {
      sendCodeBtn.disabled = false;
    }, 800);
  }
}

async function handleRegister(evt) {
  evt.preventDefault();
  const username = registerUsername.value.trim();
  const email = registerEmail.value.trim();
  const password = registerPassword.value;
  const code = registerCode.value.trim();
  if (!username || !email || !password || !code) {
    showMessage("请完整填写注册信息", "error");
    return;
  }
  const format = validateUsernameFormat(username);
  if (!format.ok) {
    setHint(registerUsernameHint, format.text, "error");
    showMessage("请先修正用户名格式", "error");
    return;
  }
  if (registerNameExists === true) {
    showMessage("该账号已存在，请更换用户名", "error");
    return;
  }
  try {
    showMessage("注册中...");
    await requestJson(`${API_BASE}/register`, {
      method: "POST",
      body: JSON.stringify({ username, email, password, code })
    });
    // 注册和登录流程分离：注册成功后只跳到登录表单，不做自动登录
    registerEmail.value = "";
    registerPassword.value = "";
    registerCode.value = "";
    setHint(registerUsernameHint, "注册成功，可直接去登录", "success");
    loginUsername.value = username;
    loginPassword.value = "";
    switchTab("login");
    setHint(loginUsernameHint, "已为你填好用户名，请输入密码登录", "success");
    showMessage("注册成功，请登录", "success");
    loginPassword.focus();
  } catch (error) {
    showMessage(error?.message || "注册失败", "error");
  }
}

async function checkLoginUsernameNow() {
  const username = loginUsername.value.trim();
  const format = validateUsernameFormat(username);
  if (!format.ok) {
    loginNameExists = null;
    setHint(loginUsernameHint, format.text, "error");
    return;
  }
  setHint(loginUsernameHint, "正在校验账号...", "loading");
  try {
    const exists = await verifyUsernameExists(username);
    loginNameExists = exists;
    if (exists) {
      setHint(loginUsernameHint, "账号存在，可直接登录", "success");
    } else {
      setHint(loginUsernameHint, "账号不存在，请先注册", "error");
    }
  } catch (e) {
    loginNameExists = null;
    setHint(loginUsernameHint, e?.message || "账号校验失败", "error");
  }
}

async function checkRegisterUsernameNow() {
  const username = registerUsername.value.trim();
  const format = validateUsernameFormat(username);
  if (!format.ok) {
    registerNameExists = null;
    setHint(registerUsernameHint, format.text, "error");
    return;
  }
  setHint(registerUsernameHint, "正在校验账号...", "loading");
  try {
    const exists = await verifyUsernameExists(username);
    registerNameExists = exists;
    if (exists) {
      setHint(registerUsernameHint, "账号已存在，请更换用户名", "error");
    } else {
      setHint(registerUsernameHint, "账号可用", "success");
    }
  } catch (e) {
    registerNameExists = null;
    setHint(registerUsernameHint, e?.message || "账号校验失败", "error");
  }
}

tabLogin.addEventListener("click", () => switchTab("login"));
tabRegister.addEventListener("click", () => switchTab("register"));
loginForm.addEventListener("submit", handleLogin);
registerForm.addEventListener("submit", handleRegister);
sendCodeBtn.addEventListener("click", handleSendCode);
themeToggle.addEventListener("click", () => {
  const dark = document.body.classList.contains("theme-dark");
  applyTheme(dark ? "light" : "dark");
});

loginUsername.addEventListener("input", () => {
  loginNameExists = null;
  clearTimeout(loginCheckTimer);
  const username = loginUsername.value.trim();
  if (!username) {
    setHint(loginUsernameHint, "");
    return;
  }
  loginCheckTimer = setTimeout(checkLoginUsernameNow, 350);
});

registerUsername.addEventListener("input", () => {
  registerNameExists = null;
  clearTimeout(registerCheckTimer);
  const username = registerUsername.value.trim();
  if (!username) {
    setHint(registerUsernameHint, "");
    return;
  }
  registerCheckTimer = setTimeout(checkRegisterUsernameNow, 350);
});

if (mascotWrap) {
  const tips = [
    "你好呀，我来陪你完成登录～",
    "点击我可以换提示语哦",
    "输入用户名和密码，我帮你看着~",
    "注册时先发验证码，再提交注册",
    "有报错就看下面提示，我会提醒你"
  ];
  let tipIndex = 0;
  mascotWrap.addEventListener("click", () => {
    tipIndex = (tipIndex + 1) % tips.length;
    mascotSay(tips[tipIndex]);
    waveMascot();
  });
  mascotWrap.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      mascotWrap.click();
    }
  });
  mascotWrap.addEventListener("mousemove", (e) => {
    movePupilsByPointer(e.clientX, e.clientY);
  });
  mascotWrap.addEventListener("mouseleave", resetPupils);
}

loginUsername?.addEventListener("focus", () => mascotSay("用户名建议 3-32 位字母数字下划线"));
loginPassword?.addEventListener("focus", () => mascotSay("密码输入时我会帮你守住秘密~"));
registerCode?.addEventListener("focus", () => mascotSay("验证码一般几分钟内有效，注意及时输入"));

switchTab("login");
applyTheme(localStorage.getItem(STORAGE_THEME) || "light");
setHint(loginUsernameHint, "支持字母、数字、下划线，实时校验账号是否存在", "info");
setHint(registerUsernameHint, "支持字母、数字、下划线，实时校验账号是否可用", "info");

const existingToken = localStorage.getItem(TOKEN_KEY);
if (existingToken) {
  showMessage("检测到已登录状态，可直接进入系统", "success");
}
