import { get, post, upload } from "./api";

const BASE = "/api/knowledge";

export function createKb(kbName, description) {
  return post(`${BASE}/createKb`, { kbName, description });
}

export function uploadDoc(kbId, file) {
  const formData = new FormData();
  formData.append("kbId", kbId);
  formData.append("file", file);
  return upload(`${BASE}/uploadDoc`, formData);
}

export function parseDoc(docId) {
  return post(`${BASE}/parseDoc`, { docId });
}

export function chuckDoc(kbId, docId, strategy = 0) {
  return post(`${BASE}/chuckDoc`, { kbId, doc_id: docId, chuckStrategy: strategy });
}

export function chuckEmbedding(kbId, docId) {
  return post(`${BASE}/chuckEmbedding`, { kbId, docId: String(docId) });
}

export function pageKb(kbName, enabled, current = 1, size = 20) {
  const params = { current: String(current), size: String(size) };
  if (kbName) params.kbName = kbName;
  if (enabled != null) params.enabled = String(enabled);
  return get(`${BASE}/kb/page`, params);
}

export function pageDoc(kbId, fileName, status, current = 1, size = 20) {
  const params = { current: String(current), size: String(size) };
  if (kbId) params.kbId = kbId;
  if (fileName) params.fileName = fileName;
  if (status != null) params.status = String(status);
  return get(`${BASE}/doc/page`, params);
}

export function pageChunk(kbId, docId, content, enabled, current = 1, size = 20) {
  const params = { current: String(current), size: String(size) };
  if (kbId) params.kbId = kbId;
  if (docId != null) params.docId = String(docId);
  if (content) params.content = content;
  if (enabled != null) params.enabled = String(enabled);
  return get(`${BASE}/chunk/page`, params);
}
