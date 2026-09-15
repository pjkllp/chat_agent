<template>
  <div class="composer-wrap">
    <div class="composer">
      <div v-if="files.length" class="attach-preview">
        <div v-for="(f, i) in files" :key="i" class="attach-chip">
          <img v-if="f.type === 'IMAGE'" :src="f.preview" class="attach-thumb" alt="" />
          <span v-else class="attach-audio-name">
            <span class="attach-audio-tag">AUDIO</span>
            {{ f.file.name }}
          </span>
          <button type="button" class="attach-remove" @click="removeFile(i)" aria-label="移除附件">✕</button>
        </div>
      </div>
      <textarea
        class="composer-textarea"
        rows="3"
        placeholder="输入需要深度分析的问题..."
        v-model="text"
        @keydown="handleKeydown"
      ></textarea>
      <div class="composer-toolbar">
        <button type="button" class="pill-deep" :aria-pressed="deepThink" @click="deepThink = !deepThink">
          <span class="pill-dot" aria-hidden="true"></span>
          <span class="pill-icon" aria-hidden="true">◎</span>
          深度思考
        </button>
        <button type="button" class="btn-attach" @click="triggerPick" title="上传图片/音频">
          <svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
            <path
              d="M13.4 6.2 7.1 12.5a2.1 2.1 0 0 0 3 3l6.6-6.6a3.8 3.8 0 0 0-5.4-5.4L4.5 10.3a5.4 5.4 0 0 0 7.7 7.7l5.6-5.6"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
        <input
          ref="fileInput"
          type="file"
          accept="image/*,audio/*"
          multiple
          style="display:none"
          @change="onPick"
        />
        <button
          v-if="store.streaming"
          type="button"
          class="btn-stop"
          :disabled="store.cancelling"
          @click="store.cancelCurrent()"
          :aria-label="store.cancelling ? '正在取消' : '停止生成'"
          :title="store.cancelling ? '正在取消…' : '停止生成'"
        >
          <span v-if="store.cancelling" class="send-pending" aria-hidden="true">…</span>
          <svg v-else viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
            <rect x="6" y="6" width="8" height="8" rx="1.4" fill="currentColor" />
          </svg>
        </button>
        <button v-else type="button" class="btn-send" :disabled="(!text.trim() && !files.length) || uploading" @click="send" aria-label="发送">
          <span v-if="uploading" class="send-pending" aria-hidden="true">…</span>
          <svg v-else viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
            <path
              d="M4 10h11m0 0-4.4-4.4M15 10l-4.4 4.4"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
      </div>
    </div>
    <p class="composer-hint">Enter 发送 · Shift + Enter 换行 · 支持图片/音频（单文件≤20MB，最多4个）</p>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useChatStore } from "../../stores/chat";
import { uploadAttachment } from "../../services/chat";

const emit = defineEmits(["send"]);
const store = useChatStore();
const text = ref("");
const deepThink = ref(true);
const files = ref([]);          // { file, type, preview }
const uploading = ref(false);
const fileInput = ref(null);

const MAX_SIZE = 20 * 1024 * 1024;
const MAX_COUNT = 4;
const IMAGE_RE = /^image\/(jpeg|png|webp|gif)$/;
const AUDIO_RE = /^audio\/(mpeg|wav|x-wav|mp4|webm|ogg)$/;

function triggerPick() {
  fileInput.value?.click();
}

function onPick(e) {
  const picked = Array.from(e.target.files || []);
  e.target.value = "";
  for (const file of picked) {
    if (files.value.length >= MAX_COUNT) {
      alert(`最多 ${MAX_COUNT} 个附件`);
      break;
    }
    const type = IMAGE_RE.test(file.type) ? "IMAGE" : AUDIO_RE.test(file.type) ? "AUDIO" : null;
    if (!type) {
      alert(`不支持的文件类型: ${file.name}`);
      continue;
    }
    if (file.size > MAX_SIZE) {
      alert(`文件过大（>20MB）: ${file.name}`);
      continue;
    }
    files.value.push({ file, type, preview: type === "IMAGE" ? URL.createObjectURL(file) : "" });
  }
}

function removeFile(i) {
  const f = files.value[i];
  if (f?.preview) URL.revokeObjectURL(f.preview);
  files.value.splice(i, 1);
}

function handleKeydown(e) {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    send();
  }
}

async function send() {
  const q = text.value.trim();
  if ((!q && !files.value.length) || store.streaming || uploading.value) return;
  uploading.value = true;
  try {
    const attachments = await Promise.all(
      files.value.map((f) => uploadAttachment(f.file, store.conversationId))
    );
    emit("send", q, deepThink.value, attachments);
    files.value.forEach((f) => f.preview && URL.revokeObjectURL(f.preview));
    files.value = [];
    text.value = "";
  } catch (err) {
    alert(err?.message || "附件上传失败，请重试");
  } finally {
    uploading.value = false;
  }
}
</script>

<style scoped>
.attach-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 14px 4px;
}

.attach-chip {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 9px;
  background: var(--surface-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

.attach-thumb {
  width: 44px;
  height: 44px;
  object-fit: cover;
  border-radius: var(--radius-xs);
  display: block;
}

.attach-audio-name {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-muted);
}

.attach-audio-tag {
  flex-shrink: 0;
  padding: 2px 6px;
  border-radius: var(--radius-xs);
  background: var(--accent-surface);
  border: 1px solid var(--accent-strong);
  color: var(--accent-ink);
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.1em;
}

.attach-remove {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border: none;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-faint);
  font-size: 11px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.attach-remove:hover {
  background: var(--accent-surface);
  color: var(--danger);
}

.btn-attach {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.btn-attach:hover {
  border-color: var(--accent-strong);
  background: var(--accent-surface);
  color: var(--accent-ink);
}

.btn-attach svg {
  width: 17px;
  height: 17px;
  display: block;
}

.send-pending {
  font-family: var(--font-mono);
  font-size: 15px;
  line-height: 1;
}
</style>
