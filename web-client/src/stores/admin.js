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
} from "../services/trace";

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
    traceDetail: null,
    activeTab: "kb", // "kb" | "trace"
  }),
  actions: {
    // KB
    async loadKb(page = 1, size = 20, kbName) {
      const data = await pageKb(kbName, null, page, size);
      this.kbList = data?.records || [];
      this.kbTotal = data?.total || 0;
      this.kbCurrent = data?.current || 1;
      this.kbPages = data?.pages || 0;
    },
    async createKb(name, desc) {
      await createKb(name, desc);
      await this.loadKb();
    },
    async loadDocs(page = 1, size = 20, kbId) {
      if (!kbId) return;
      this.selectedKbId = kbId;
      const data = await pageDoc(kbId, null, null, page, size);
      this.docList = data?.records || [];
      this.docTotal = data?.total || 0;
      this.docCurrent = data?.current || 1;
      this.docPages = data?.pages || 0;
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
      this.chunkList = data?.records || [];
      this.chunkTotal = data?.total || 0;
      this.chunkCurrent = data?.current || 1;
      this.chunkPages = data?.pages || 0;
    },
    // Trace
    async loadTraceList(page = 1, size = 10) {
      const data = await fetchTraceConversations(page, size);
      this.traceList = data?.records || [];
      this.traceTotal = data?.total || 0;
      this.traceCurrent = data?.current || 1;
      this.tracePages = data?.pages || 0;
    },
    async loadTraceDetail(conversationId) {
      this.traceDetail = await fetchTraceDetail(conversationId);
    },
  },
});
