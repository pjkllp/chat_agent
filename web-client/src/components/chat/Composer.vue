<template>
  <div class="composer-wrap">
    <div class="composer">
      <div v-if="files.length" class="attach-preview">
        <div v-for="(f, i) in files" :key="i" class="attach-chip">
          <img v-if="f.type === 'IMAGE'" :src="f.preview" class="attach-thumb" alt="" />
          <span v-else class="attach-audio-name">🎙 {{ f.file.name }}</span>
          <button type="button" class="attach-remove" @click="removeFile(i)">✕</button>
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
        <button type="button" class="btn-attach" @click="triggerPick" title="上传图片/音频">📎</button>
        <input
          ref="fileInput"
          type="file"
          accept="image/*,audio/*"
          multiple
          style="display:none"
          @change="onPick"
        />
        <button type="button" class="btn-send" :disabled="(!text.trim() && !files.length) || store.streaming || uploading" @click="send">
          <span aria-hidden="true">{{ uploading ? "…" : "➤" }}</span>
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
.attach-preview { display: flex; flex-wrap: wrap; gap: 8px; padding: 8px 0; }
.attach-chip { position: relative; display: flex; align-items: center; gap: 6px; background: rgba(0,0,0,.06); border-radius: 8px; padding: 4px 8px; }
.attach-thumb { width: 48px; height: 48px; object-fit: cover; border-radius: 6px; }
.attach-remove { border: none; background: transparent; cursor: pointer; font-size: 12px; }
.btn-attach { border: none; background: transparent; cursor: pointer; font-size: 18px; }
</style>
