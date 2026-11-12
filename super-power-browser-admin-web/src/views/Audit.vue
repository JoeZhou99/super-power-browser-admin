<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { configDifferences } from "../configDiff";
import {
  configTitles,
  errorMessage,
  formatDate,
  request,
  type AuditEntry,
} from "../api";
const loading = ref(false),
  loadError = ref(""),
  page = ref(1),
  size = ref(20),
  total = ref(0),
  items = ref<AuditEntry[]>([]),
  selected = ref<AuditEntry | null>(null),
  detail = ref(false);
async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    const result = await request<{ items: AuditEntry[]; total: number }>(
      `/config-audit?page=${page.value}&size=${size.value}`,
    );
    items.value = result.items;
    total.value = result.total;
  } catch (error) {
    loadError.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}
function inspect(row: AuditEntry) {
  selected.value = row;
  detail.value = true;
}
const differences = computed(() => configDifferences(selected.value?.before, selected.value?.after));
watch(page, load);
watch(size, () => {
  if (page.value !== 1) page.value = 1;
  else void load();
});
onMounted(load);
</script>
<template>
  <section class="page-container">
    <div class="page-heading">
      <div>
        <h1>修改记录</h1>
        <p>查看配置发布人、时间和前后内容，便于追溯运营变更。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新记录</el-button>
    </div>
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      class="section-alert"
      show-icon
    />
    <el-card shadow="never"
      ><el-table
        v-loading="loading"
        :data="items"
        row-key="id"
        stripe
        empty-text="暂无配置修改记录"
        ><el-table-column label="配置模块" min-width="150"
          ><template #default="{ row }">{{
            configTitles[row.key as keyof typeof configTitles] || row.key
          }}</template></el-table-column
        ><el-table-column
          prop="operator"
          label="操作人"
          min-width="150"
        /><el-table-column label="发布时间" min-width="190"
          ><template #default="{ row }">{{
            formatDate(row.createdAt)
          }}</template></el-table-column
        ><el-table-column label="发布版本" width="120"
          ><template #default="{ row }"
            >v{{ row.revision }}</template
          ></el-table-column
        ><el-table-column label="操作" width="110"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="inspect(row)"
              >查看详情</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <div class="table-footer">
        <span class="muted">只记录成功发布的配置变更</span
        ><el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
        /></div
    ></el-card>
    <el-dialog v-model="detail" title="配置修改详情" width="min(1080px, 94vw)"
      ><template v-if="selected"
        ><el-descriptions :column="3" border
          ><el-descriptions-item label="模块">{{
            configTitles[selected.key] || selected.key
          }}</el-descriptions-item
          ><el-descriptions-item label="操作人">{{
            selected.operator
          }}</el-descriptions-item
          ><el-descriptions-item label="时间">{{
            formatDate(selected.createdAt)
          }}</el-descriptions-item></el-descriptions
        >
        <el-table :data="differences" border max-height="480" empty-text="配置内容未改变" class="audit-differences">
          <el-table-column prop="name" label="配置项" min-width="240" />
          <el-table-column prop="server" label="修改前" min-width="260" />
          <el-table-column prop="draft" :label="`修改后 · v${selected.revision}`" min-width="260" />
        </el-table></template
      ><template #footer
        ><el-button @click="detail = false">关闭</el-button></template
      ></el-dialog
    >
  </section>
</template>
<style scoped>
.audit-differences { margin-top: 20px; }
</style>
