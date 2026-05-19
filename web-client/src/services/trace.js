import { get } from "./api";

const BASE = "/api/admin/trace";

export function fetchTraceConversations(current = 1, size = 10) {
  return get(`${BASE}/conversations`, { current: String(current), size: String(size) });
}

export function fetchTraceDetail(conversationId) {
  return get(`${BASE}/${conversationId}`);
}
