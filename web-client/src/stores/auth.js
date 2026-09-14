import { defineStore } from "pinia";
import { login as apiLogin, logout as apiLogout, register as apiRegister, applyCode as apiApplyCode } from "../services/auth";
import { getToken, setToken, removeToken } from "../services/api";

// JWT 的 payload 是 Base64URL 编码的 JSON，解出来用于控制管理入口的显隐。
// 这只是展示层门禁，真正的权限校验在后端 AdminInterceptor。
function decodePayload(token) {
  try {
    const part = String(token || "").split(".")[1];
    if (!part) return null;
    const base64 = part.replace(/-/g, "+").replace(/_/g, "/");
    const json = decodeURIComponent(
      atob(base64)
        .split("")
        .map((c) => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
        .join("")
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function isAdminFromToken(token) {
  return Number(decodePayload(token)?.isAdmin) === 1;
}

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: "",
    username: "",
    isAdmin: false,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
  },
  actions: {
    initFromStorage() {
      this.token = getToken();
      this.username = localStorage.getItem("rag_user_id") || "";
      this.isAdmin = isAdminFromToken(this.token);
    },
    async login(username, password) {
      const token = await apiLogin(username, password);
      setToken(token);
      this.token = token;
      this.username = username;
      this.isAdmin = isAdminFromToken(token);
      localStorage.setItem("rag_user_id", username);
    },
    async register(username, password, email, code) {
      await apiRegister(username, password, email, code);
    },
    async applyCode(username, password, email) {
      await apiApplyCode(username, password, email);
    },
    async logout() {
      try {
        await apiLogout(this.username);
      } catch { /* ignore */ }
      removeToken();
      this.token = "";
      this.username = "";
      this.isAdmin = false;
      localStorage.removeItem("rag_user_id");
    },
  },
});
