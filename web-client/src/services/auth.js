import { get, post } from "./api";

const BASE = "/api/auth";

export function login(username, password) {
  return post(`${BASE}/login`, { username, password });
}

export function logout(username) {
  return post(`${BASE}/logout?username=${encodeURIComponent(username)}`);
}

export function register(username, password, email, code) {
  return post(`${BASE}/register`, { username, password, email, code });
}

export function applyCode(username, password, email) {
  return post(`${BASE}/applyCode`, { username, password, email });
}

export function verifyUsername(username) {
  return get(`${BASE}/verify_username`, { username });
}
