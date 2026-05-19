<template>
  <section class="suggestions">
    <div class="suggestions-divider">
      <span class="suggestions-line"></span>
      <span class="suggestions-label">试试这些开场</span>
      <span class="suggestions-line"></span>
    </div>
    <div class="suggestion-cards">
      <article
        v-for="(s, i) in suggestions"
        :key="i"
        class="suggestion-card"
        @click="emit('select', s.prompt)"
      >
        <div class="suggestion-card-icon" aria-hidden="true">{{ s.icon }}</div>
        <h3>{{ s.title }}</h3>
        <p>{{ s.desc }}</p>
      </article>
    </div>
  </section>
</template>

<script setup>
const emit = defineEmits(["select"]);
const suggestions = [
  { icon: "📊", title: "实时数据", desc: "接入外部数据源，回答带引用与可追溯链接。", prompt: "帮我分析当前最新的市场趋势，给出详细报告。" },
  { icon: "🧠", title: "深度分析", desc: "多步推理、知识检索、可展开的思考链条。", prompt: "请深度分析人工智能在医疗领域的主要应用场景和挑战。" },
  { icon: "📝", title: "方案策划", desc: "系统化输出方案报告，含结构化提纲。", prompt: "帮我做一个数字化转型的方案策划，包含目标、步骤和执行计划。" },
];
</script>
