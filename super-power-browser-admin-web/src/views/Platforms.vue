<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { Plus, Search } from "@element-plus/icons-vue";
import type { PlatformEntry } from "../api";
import { useConfigEditor } from "../composables/useConfigEditor";
import ConfigStatus from "../components/ConfigStatus.vue";
import ConflictDialog from "../components/ConflictDialog.vue";
const {
  document,
  dirty,
  loading,
  saving,
  loadError,
  conflict,
  serverVersion,
  conflictVisible,
  load,
  refresh,
  save,
  inspectConflict,
  keepDraft,
  useServer,
} = useConfigEditor("platform-sites");
const entries = computed(
  () => (document.value?.entries || []) as PlatformEntry[],
);
const search = ref(""),
  category = ref(""),
  status = ref("all"),
  page = ref(1),
  pageSize = ref(15);
const categories = computed(() =>
  [...new Set(entries.value.map((row) => row.categoryName))].sort(),
);
const platforms = computed(() =>
  [...new Set(entries.value.map((row) => row.platformName))].sort(),
);
const filtered = computed(() =>
  entries.value
    .filter(
      (row) =>
        `${row.platformName} ${row.siteName} ${row.url}`
          .toLowerCase()
          .includes(search.value.toLowerCase()) &&
        (!category.value || row.categoryName === category.value) &&
        (status.value === "all" ||
          row.enabled === (status.value === "enabled")),
    )
    .sort((a, b) => a.sortOrder - b.sortOrder),
);
const paginated = computed(() =>
  filtered.value.slice(
    (page.value - 1) * pageSize.value,
    page.value * pageSize.value,
  ),
);
watch([search, category, status, pageSize], () => {
  page.value = 1;
});
const dialog = ref(false),
  editIndex = ref(-1),
  formError = ref("");
const form = reactive<PlatformEntry>({
  id: "",
  categoryName: "",
  platformName: "",
  siteName: "",
  url: "",
  enabled: true,
  sortOrder: 10,
});
function edit(row?: PlatformEntry) {
  editIndex.value = row ? entries.value.indexOf(row) : -1;
  Object.assign(
    form,
    row || {
      id: "",
      categoryName: "",
      platformName: "",
      siteName: "",
      url: "",
      enabled: true,
      sortOrder: entries.value.length
        ? Math.max(...entries.value.map((item) => item.sortOrder)) + 10
        : 10,
    },
  );
  formError.value = "";
  dialog.value = true;
}
function apply() {
  const next = {
    ...form,
    categoryName: form.categoryName.trim(),
    platformName: form.platformName.trim(),
    siteName: form.siteName.trim(),
    url: form.url.trim(),
  };
  if (!next.categoryName || !next.platformName || !next.siteName || !next.url) {
    formError.value = "请填写分类、平台、站点名称和站点地址。";
    return;
  }
  if (next.categoryName.length > 100 || next.platformName.length > 100) {
    formError.value = "分类和平台名称不能超过 100 个字符。";
    return;
  }
  try {
    const url = new URL(next.url);
    if (
      !["http:", "https:"].includes(url.protocol) ||
      url.username ||
      url.password
    )
      throw new Error();
  } catch {
    formError.value =
      "请输入有效的 http 或 https 网址，地址中不能包含账号密码。";
    return;
  }
  if (
    entries.value.some(
      (row, index) =>
        index !== editIndex.value &&
        row.categoryName === next.categoryName &&
        row.platformName === next.platformName &&
        row.siteName === next.siteName,
    )
  ) {
    formError.value = "该分类、平台下已有同名站点，请编辑已有记录。";
    return;
  }
  if (editIndex.value < 0) entries.value.push(next);
  else entries.value.splice(editIndex.value, 1, next);
  dialog.value = false;
  ElMessage.success("已加入草稿，点击“保存发布”后生效。");
}
onMounted(load);
</script>
<template>
  <section class="page-container">
    <div class="page-heading">
      <div>
        <h1>平台站点</h1>
        <p>
          维护创建店铺时的平台分类、站点和默认访问地址。历史站点保留记录，通过停用控制是否可选。
        </p>
      </div>
    </div>
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
      class="section-alert"
      ><template #default
        ><el-button link type="primary" @click="load"
          >重试加载</el-button
        ></template
      ></el-alert
    >
    <el-card shadow="never" v-loading="loading"
      ><ConfigStatus
        :revision="document?.revision"
        :updated-at="document?.updatedAt"
        :dirty="dirty"
        :saving="saving"
        :loading="loading"
        :conflict="conflict"
        @save="save"
        @refresh="refresh"
        @inspect="inspectConflict"
      />
      <div class="table-toolbar">
        <div class="filters">
          <el-input
            v-model="search"
            :prefix-icon="Search"
            placeholder="搜索平台、站点或地址"
            clearable
          /><el-select v-model="category" placeholder="全部分类" clearable
            ><el-option
              v-for="name in categories"
              :key="name"
              :label="name"
              :value="name" /></el-select
          ><el-select v-model="status"
            ><el-option label="全部状态" value="all" /><el-option
              label="已启用"
              value="enabled" /><el-option label="已停用" value="disabled"
          /></el-select>
        </div>
        <el-button
          type="primary"
          :icon="Plus"
          :disabled="!document || saving"
          @click="edit()"
          >新增站点</el-button
        >
      </div>
      <el-table :data="paginated" stripe empty-text="暂无匹配的站点"
        ><el-table-column
          prop="sortOrder"
          label="排序"
          width="75"
        /><el-table-column
          prop="categoryName"
          label="分类"
          min-width="110"
        /><el-table-column
          prop="platformName"
          label="平台"
          min-width="130"
        /><el-table-column
          prop="siteName"
          label="站点"
          min-width="130"
        /><el-table-column
          prop="url"
          label="站点地址"
          min-width="260"
          show-overflow-tooltip
        /><el-table-column label="状态" width="125"
          ><template #default="{ row }"
            ><el-switch
              v-model="row.enabled"
              :disabled="saving"
              :active-text="row.enabled ? '启用' : '停用'"
              :aria-label="`${row.siteName}启用状态`" /></template></el-table-column
        ><el-table-column label="操作" width="85" fixed="right"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              :disabled="saving"
              @click="edit(row)"
              >编辑</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <div class="table-footer">
        <span class="muted"
          >共 {{ entries.length }} 个站点 · 已启用
          {{ entries.filter((row) => row.enabled).length }} 个</span
        ><el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="filtered.length"
          :page-sizes="[15, 30, 50]"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </el-card>
    <el-dialog
      v-model="dialog"
      :title="editIndex < 0 ? '新增平台站点' : '编辑平台站点'"
      width="560px"
      :close-on-click-modal="false"
      ><el-form :model="form" label-position="top" @submit.prevent="apply"
        ><el-alert
          v-if="formError"
          :title="formError"
          type="error"
          :closable="false"
          class="section-alert" />
        <div class="form-grid">
          <el-form-item label="分类" required
            ><el-select
              v-model="form.categoryName"
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入分类"
              ><el-option
                v-for="name in categories"
                :key="name"
                :label="name"
                :value="name" /></el-select></el-form-item
          ><el-form-item label="平台" required
            ><el-select
              v-model="form.platformName"
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入平台"
              ><el-option
                v-for="name in platforms"
                :key="name"
                :label="name"
                :value="name" /></el-select
          ></el-form-item>
        </div>
        <el-form-item label="站点名称" required
          ><el-input
            v-model="form.siteName"
            maxlength="200"
            placeholder="例如：中国站、美国站" /></el-form-item
        ><el-form-item label="站点地址" required
          ><el-input
            v-model="form.url"
            maxlength="2000"
            placeholder="https://example.com/"
          />
          <div class="field-help">
            用于该站点新建店铺的默认打开地址。
          </div></el-form-item
        >
        <div class="form-grid">
          <el-form-item label="排序"
            ><el-input-number
              v-model="form.sortOrder"
              :min="0"
              :max="1000000"
              :precision="0" /></el-form-item
          ><el-form-item label="状态"
            ><el-switch
              v-model="form.enabled"
              active-text="启用"
              inactive-text="停用"
          /></el-form-item></div></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" @click="apply"
          >保存到草稿</el-button
        ></template
      ></el-dialog
    >
    <ConflictDialog
      v-model="conflictVisible"
      :server="serverVersion"
      :draft="document"
      @keep="keepDraft"
      @use-server="useServer"
    />
  </section>
</template>
