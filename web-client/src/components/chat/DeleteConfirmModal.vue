<template>
  <div v-if="visible" class="modal-mask" @click.self="close">
    <section class="modal-panel">
      <h3>确认删除</h3>
      <p>确定要删除此对话吗？此操作不可撤销。</p>
      <div class="modal-actions">
        <button type="button" class="btn-ghost" @click="close">取消</button>
        <button type="button" class="btn-danger" @click="confirm">删除</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from "vue";

const visible = ref(false);
let resolveFn = null;

function show() {
  visible.value = true;
  return new Promise((resolve) => { resolveFn = resolve; });
}

function close() {
  visible.value = false;
  resolveFn?.(false);
}

function confirm() {
  visible.value = false;
  resolveFn?.(true);
}

defineExpose({ show });
</script>
