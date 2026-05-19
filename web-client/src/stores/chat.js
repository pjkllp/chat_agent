import { defineStore } from "pinia";
import { useSSE } from "../composables/useSSE";
import {
  fetchConversations as apiConversations,
  fetchMessages as apiMessages,
  deleteConversation as apiDelete,
} from "../services/chat";

export const useChatStore = defineStore("chat", {
  state: () => ({
    conversationId: crypto.randomUUID(),
    messages: [],
    conversations: [],
    totalPages: 0,
    currentPage: 1,
    hasMore: false,
    streaming: false,
    workflowSteps: [],
    title: "新对话",
  }),
  getters: {
    activeMessages: (state) => state.messages,
  },
  actions: {
    newConversation() {
      this.conversationId = crypto.randomUUID();
      this.messages = [];
      this.workflowSteps = [];
      this.streaming = false;
      this.title = "新对话";
    },
    async loadConversations(page = 1, before) {
      const data = await apiConversations(page, 20, before);
      const records = data?.records || [];
      if (page === 1) {
        this.conversations = records;
      } else {
        const existing = new Map(this.conversations.map((c) => [c.conversationId, c]));
        for (const r of records) existing.set(r.conversationId, r);
        this.conversations = [...existing.values()].sort(
          (a, b) => new Date(b.lastTime || b.startTime) - new Date(a.lastTime || a.startTime)
        );
      }
      this.totalPages = data?.pages || 0;
      this.currentPage = page;
      this.hasMore = page < (data?.pages || 0);
    },
    async loadMessages(conversationId) {
      const data = await apiMessages(conversationId);
      this.messages = (data || []).map((m) => ({
        role: m.messageType === "USER" ? "user" : m.messageType === "ASSISTANT" ? "assistant" : "system",
        content: m.content || "",
      }));
      this.conversationId = conversationId;
      const firstUser = this.messages.find((m) => m.role === "user");
      this.title = firstUser ? firstUser.content.slice(0, 50) : "对话";
    },
    async deleteConversation(conversationId) {
      await apiDelete(conversationId);
      this.conversations = this.conversations.filter((c) => c.conversationId !== conversationId);
      if (this.conversationId === conversationId) {
        this.newConversation();
      }
    },
    sendQuestion(question, deepThink) {
      if (this.streaming) return;
      this.streaming = true;
      this.messages.push({ role: "user", content: question });
      this.messages.push({ role: "assistant", content: "", streaming: true });
      this.workflowSteps = [];
      if (this.title === "新对话") {
        this.title = question.slice(0, 50);
      }

      const sse = useSSE();
      const convId = this.conversationId;

      sse.connect(
        "/api/chat/deepThink",
        { question, conversationId: convId },
        {
          onWorkflow: (data) => {
            this.workflowSteps.push(data);
          },
          onAnswer: (chunk) => {
            const last = this.messages[this.messages.length - 1];
            if (last && last.role === "assistant" && last.streaming) {
              last.content += chunk;
            }
          },
          onError: () => {
            this.streaming = false;
            const last = this.messages[this.messages.length - 1];
            if (last) last.streaming = false;
          },
          onComplete: () => {
            this.streaming = false;
            const last = this.messages[this.messages.length - 1];
            if (last) last.streaming = false;
          },
        }
      );
    },
    cancelStream() {
      this.streaming = false;
      const last = this.messages[this.messages.length - 1];
      if (last) last.streaming = false;
    },
  },
});
