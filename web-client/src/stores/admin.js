import { defineStore } from "pinia";
import {
  createKb,
  uploadDoc,
  parseDoc,
  chuckDoc,
  chuckEmbedding,
  pageKb,
  pageDoc,
  pageChunk,
} from "../services/knowledge";
import {
  fetchTraceConversations,
  fetchTraceDetail,
  fetchTraceStats,
} from "../services/trace";

// 后端 write-numbers-as-strings=true，数字字段都会以字符串形式返回，
// 这里统一转回数字，避免前端 === / >= 等严格比较失效。
function toNum(v, fallback = 0) {
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
}

function toNullableNum(v) {
  return v == null || v === "" ? v : Number(v);
}

export const useAdminStore = defineStore("admin", {
  state: () => ({
    // Knowledge base
    kbList: [],
    kbTotal: 0,
    kbCurrent: 1,
    kbPages: 0,
    docList: [],
    docTotal: 0,
    docCurrent: 1,
    docPages: 0,
    chunkList: [],
    chunkTotal: 0,
    chunkCurrent: 1,
    chunkPages: 0,
    selectedKbId: null,
    selectedDocId: null,
    // Trace
    traceList: [],
    traceTotal: 0,
    traceCurrent: 1,
    tracePages: 0,
    traceStats: null,
    traceDetail: null,
    activeTab: "kb", // "kb" | "trace"
  }),
  actions: {
    // KB
    async loadKb(page = 1, size = 20, kbName) {
      const data = await pageKb(kbName, null, page, size);
      this.kbList = data?.records || [];
      this.kbTotal = toNum(data?.total);
      this.kbCurrent = toNum(data?.current, 1);
      this.kbPages = toNum(data?.pages);
    },
    async createKb(name, desc) {
      await createKb(name, desc);
      await this.loadKb();
    },
    async loadDocs(page = 1, size = 20, kbId) {
      if (!kbId) return;
      this.selectedKbId = kbId;
      const data = await pageDoc(kbId, null, null, page, size);
      this.docList = (data?.records || []).map((d) => ({
        ...d,
        status: toNullableNum(d.status),
        version: toNullableNum(d.version),
      }));
      this.docTotal = toNum(data?.total);
      this.docCurrent = toNum(data?.current, 1);
      this.docPages = toNum(data?.pages);
    },
    async uploadDoc(kbId, file) {
      await uploadDoc(kbId, file);
      await this.loadDocs(1, 20, kbId);
    },
    async parseDoc(docId) {
      await parseDoc(docId);
      if (this.selectedKbId) await this.loadDocs(this.docCurrent, 20, this.selectedKbId);
    },
    async chunkDoc(kbId, docId) {
      await chuckDoc(kbId, docId);
      if (this.selectedKbId) await this.loadDocs(this.docCurrent, 20, this.selectedKbId);
    },
    async chunkEmbedding(kbId, docId) {
      await chuckEmbedding(kbId, docId);
      if (this.selectedKbId) await this.loadDocs(this.docCurrent, 20, this.selectedKbId);
    },
    async loadChunks(page = 1, size = 20, kbId, docId) {
      this.selectedDocId = docId;
      const data = await pageChunk(kbId, docId, null, null, page, size);
      this.chunkList = (data?.records || []).map((c) => ({
        ...c,
        chunkNo: toNullableNum(c.chunkNo),
      }));
      this.chunkTotal = toNum(data?.total);
      this.chunkCurrent = toNum(data?.current, 1);
      this.chunkPages = toNum(data?.pages);
    },
    // Trace
    async loadTraceStats() {
      this.traceStats = await fetchTraceStats();
    },
    async loadTraceList(page = 1, size = 10) {
      const data = await fetchTraceConversations(page, size);
      this.traceList = data?.records || [];
      this.traceTotal = toNum(data?.total);
      this.traceCurrent = toNum(data?.current, 1);
      this.tracePages = toNum(data?.pages);
    },
    async loadTraceDetail(conversationId) {
      this.traceDetail = await fetchTraceDetail(conversationId);
    },
  },
});
