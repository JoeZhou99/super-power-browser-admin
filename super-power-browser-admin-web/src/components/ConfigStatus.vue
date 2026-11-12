<script setup lang="ts">
import { formatDate } from "../api";
defineProps<{
  revision?: number;
  updatedAt?: string;
  dirty: boolean;
  saving: boolean;
  loading: boolean;
  conflict: boolean;
}>();
defineEmits<{ save: []; refresh: []; inspect: [] }>();
</script>
<template>
  <div class="config-status">
    <div class="status-details">
      <span>当前版本 v{{ revision ?? "—" }}</span
      ><span>更新于 {{ formatDate(updatedAt) }}</span
      ><el-tag v-if="revision === undefined" type="info" size="small"
        >未加载</el-tag
      ><el-tag v-else-if="dirty" type="warning" size="small"
        >有未发布修改</el-tag
      ><el-tag v-else type="success" size="small">已同步</el-tag>
    </div>
    <div class="status-actions">
      <el-button :disabled="loading || saving" @click="$emit('refresh')"
        >重新载入</el-button
      ><el-button
        type="primary"
        :loading="saving"
        :disabled="!dirty || conflict || loading"
        @click="$emit('save')"
        >保存发布</el-button
      >
    </div>
  </div>
  <el-alert
    v-if="conflict"
    type="warning"
    :closable="false"
    show-icon
    class="section-alert"
    ><template #title>服务器已有新版本，草稿已保留。</template
    ><template #default
      >请核对后继续编辑。<el-button
        link
        type="primary"
        @click="$emit('inspect')"
        >核对服务器版本</el-button
      ></template
    ></el-alert
  >
</template>
