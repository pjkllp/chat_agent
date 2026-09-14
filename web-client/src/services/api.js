const TOKEN_KEY = "rag_access_token";

export function getToken() {
  return (localStorage.getItem(TOKEN_KEY) || "").trim();
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY);
}

async function request(url, options = {}) {
  const headers = { "Content-Type": "application/json" };
  // 与后端 WebMvcConfig 放行的 /api/auth/** 保持一致：注册、发送验证码等接口
  // 在未登录状态下也必须能调用，否则会在 fetch 之前就被下面的 token 判空挡掉。
  const skipAuth = url.startsWith("/api/auth/");
  if (!skipAuth) {
    const token = getToken();
    if (!token) {
      window.location.hash = "#/auth";
      throw new Error("登录状态已失效");
    }
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(url, { ...options, headers: { ...headers, ...options.headers } });
  if (!response.ok) {
    if (response.status === 401) {
      removeToken();
      window.location.hash = "#/auth";
      throw new Error("登录已过期");
    }
    throw new Error(`请求失败: ${response.status}`);
  }
  const payload = await response.json().catch(() => ({}));
  if (payload && typeof payload === "object" && "code" in payload && payload.code != 1) {
    throw new Error(payload.msg || "请求失败");
  }
  return payload?.data;
}

export function get(url, params) {
  const qs = params ? "?" + new URLSearchParams(params).toString() : "";
  return request(`${url}${qs}`);
}

export function post(url, body) {
  return request(url, { method: "POST", body: JSON.stringify(body) });
}

export function del(url) {
  return request(url, { method: "DELETE" });
}

export function upload(url, formData) {
  const token = getToken();
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  return fetch(url, { method: "POST", body: formData, headers }).then(async (res) => {
    const payload = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(payload?.msg || `上传失败: ${res.status}`);
    // 后端 write-numbers-as-strings=true，code 是字符串 "1"，这里必须宽松比较
    if (payload && typeof payload === "object" && "code" in payload && payload.code != 1) {
      throw new Error(payload.msg || "上传失败");
    }
    return payload?.data;
  });
}
