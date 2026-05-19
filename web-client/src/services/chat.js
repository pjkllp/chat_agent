import { get, del } from "./api";

const BASE = "/api/chat";

export function fetchConversations(current = 1, size = 20, before) {
  const params = { current: String(current), size: String(size) };
  if (before) params.before = String(before);
  return get(`${BASE}/conversations`, params);
}

export function fetchMessages(conversationId) {
  return get(`${BASE}/messages/${conversationId}`);
}

export function deleteConversation(conversationId) {
  return del(`${BASE}/conversations/${conversationId}`);
}
