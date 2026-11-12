<script setup lang="ts">
import { computed } from "vue";
import type { ConfigDocument } from "../api";
import { configDifferences } from "../configDiff";
const props = defineProps<{
  modelValue: boolean;
  server: ConfigDocument<unknown, unknown> | null;
  draft: ConfigDocument<unknown, unknown> | null;
}>();
defineEmits<{
  "update:modelValue": [value: boolean];
  keep: [];
  useServer: [];
}>();
const rows = computed(() => configDifferences(props.server, props.draft));
</script>
<template>
  <el-dialog
    :model-value="modelValue"
    title="核对配置版本"
    width="min(960px, 94vw)"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <p class="muted">
      下方列出两个版本的差异。当前草稿不会自动覆盖服务器，继续编辑后仍需手动保存发布。
    </p>
    <el-table :data="rows" max-height="430" border empty-text="两个版本的配置内容一致"
      ><el-table-column
        prop="name"
        label="配置项"
        min-width="160" /><el-table-column
        prop="server"
        :label="`服务器 v${server?.revision ?? ''}`"
        min-width="260" /><el-table-column
        prop="draft"
        label="当前草稿"
        min-width="260"
    /></el-table>
    <template #footer
      ><el-button @click="$emit('update:modelValue', false)">稍后处理</el-button
      ><el-button @click="$emit('useServer')">采用服务器版本</el-button
      ><el-button type="primary" @click="$emit('keep')"
        >保留草稿继续编辑</el-button
      ></template
    >
  </el-dialog>
</template>
