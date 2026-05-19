import { defineStore } from "pinia";
import { login as apiLogin, logout as apiLogout, register as apiRegister, applyCode as apiApplyCode } from "../services/auth";
import { getToken, setToken, removeToken } from "../services/api";

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: "",
    username: "",
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
  },
  actions: {
    initFromStorage() {
      this.token = getToken();
      this.username = localStorage.getItem("rag_user_id") || "";
    },
    async login(username, password) {
      const token = await apiLogin(username, password);
      setToken(token);
      this.token = token;
      this.username = username;
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
      localStorage.removeItem("rag_user_id");
    },
  },
});
