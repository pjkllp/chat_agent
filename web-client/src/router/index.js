import { createRouter, createWebHashHistory } from "vue-router";
import { useAuthStore } from "../stores/auth";
import AuthView from "../views/AuthView.vue";
import ChatView from "../views/ChatView.vue";
import AdminView from "../views/AdminView.vue";

const routes = [
  { path: "/auth", name: "auth", component: AuthView, meta: { guest: true } },
  { path: "/", name: "chat", component: ChatView, meta: { auth: true } },
  { path: "/admin", name: "admin", component: AdminView, meta: { auth: true } },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore();
  auth.initFromStorage();
  if (to.meta.auth && !auth.isLoggedIn) {
    next("/auth");
  } else if (to.meta.guest && auth.isLoggedIn) {
    next("/");
  } else {
    next();
  }
});

export default router;
