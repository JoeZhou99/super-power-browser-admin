<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { Plus, Search } from "@element-plus/icons-vue";
import { configTitles, type ConfigKey, type DictionaryEntry } from "../api";
import { useConfigEditor } from "../composables/useConfigEditor";
import ConfigStatus from "../components/ConfigStatus.vue";
import ConflictDialog from "../components/ConflictDialog.vue";

const key = useRoute().meta.configKey as ConfigKey;
const title = configTitles[key];
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
} = useConfigEditor(key);
const entries = computed(
  () => (document.value?.entries || []) as DictionaryEntry[],
);
const search = ref(""),
  status = ref("all"),
  page = ref(1),
  pageSize = ref(15);
const filtered = computed(() =>
  entries.value
    .filter(
      (row) =>
        `${row.value} ${row.label}`
          .toLowerCase()
          .includes(search.value.toLowerCase()) &&
        (status.value === "all" ||
          row.enabled === (status.value === "enabled")),
    )
    .sort(
      (a, b) => a.sortOrder - b.sortOrder || a.value.localeCompare(b.value),
    ),
);
const paginated = computed(() =>
  filtered.value.slice(
    (page.value - 1) * pageSize.value,
    page.value * pageSize.value,
  ),
);
watch([search, status, pageSize], () => {
  page.value = 1;
});
const dialog = ref(false),
  editIndex = ref(-1),
  formError = ref("");
const form = reactive<DictionaryEntry>({
  value: "",
  label: "",
  enabled: true,
  sortOrder: 0,
});
const hint = computed(() =>
  key === "timezones"
    ? "使用 IANA 时区标识（如 Asia/Shanghai）、auto（跟随代理 IP）或 __local__（本地时区）。"
    : key === "languages"
      ? "例如 zh-CN、en-US；多个语言可用英文逗号分隔。auto 表示跟随代理 IP。"
      : "例如 1920x1080，使用宽度x高度。",
);
function edit(row?: DictionaryEntry) {
  editIndex.value = row ? entries.value.indexOf(row) : -1;
  Object.assign(
    form,
    row || {
      value: "",
      label: "",
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
  const value = form.value.trim(),
    label = form.label.trim();
  if (!value || !label) {
    formError.value = "请填写配置值和显示名称。";
    return;
  }
  if (
    entries.value.some(
      (row, index) => index !== editIndex.value && row.value === value,
    )
  ) {
    formError.value = "该配置值已存在，请直接编辑已有记录。";
    return;
  }
  if (key === "timezones" && editIndex.value < 0 && !["auto", "__local__"].includes(value)) {
    try {
      new Intl.DateTimeFormat("zh-CN", { timeZone: value });
    } catch {
      formError.value = "请输入有效的 IANA 时区、auto（跟随代理 IP）或 __local__（本地时区）。";
      return;
    }
  }
  if (key === "languages" && value !== "auto") {
    try {
      if (
        !/^[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})?(?:,[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})?)*$/.test(
          value,
        )
      )
        throw new Error();
      value.split(",").forEach((language) => new Intl.Locale(language));
    } catch {
      formError.value = "请输入有效的语言代码（如 zh-CN）或 auto（跟随代理 IP）。";
      return;
    }
  }
  if (
    key === "resolutions" &&
    !/^[1-9][0-9]{2,3}x[1-9][0-9]{2,3}$/.test(value)
  ) {
    formError.value = "窗口尺寸格式应为宽度x高度，例如 1920x1080。";
    return;
  }
  const next = { ...form, value, label };
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
        <h1>{{ title }}配置</h1>
        <p>
          管理创建店铺时可选择的{{
            title
          }}。停用后不再提供给新建店铺，已有店铺保留原值。
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
    <el-card shadow="never" v-loading="loading">
      <ConfigStatus
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
            :placeholder="`搜索${title}或配置值`"
            clearable
            aria-label="搜索配置"
          /><el-select v-model="status" aria-label="配置状态"
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
          >新增{{ title }}</el-button
        >
      </div>
      <el-table
        :data="paginated"
        row-key="value"
        stripe
        empty-text="暂无匹配的配置项"
        ><el-table-column
          prop="sortOrder"
          label="排序"
          width="85"
        /><el-table-column
          prop="value"
          label="配置值"
          min-width="220"
        /><el-table-column
          prop="label"
          label="显示名称"
          min-width="230"
        /><el-table-column label="状态" width="130"
          ><template #default="{ row }"
            ><el-switch
              v-model="row.enabled"
              :disabled="saving"
              :active-text="row.enabled ? '启用' : '停用'"
              :aria-label="`${row.label}启用状态`" /></template></el-table-column
        ><el-table-column label="操作" width="100" fixed="right"
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
          >共 {{ entries.length }} 项 · 已启用
          {{ entries.filter((row) => row.enabled).length }}
          项，排序值越小越靠前</span
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
      :title="`${editIndex < 0 ? '新增' : '编辑'}${title}`"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top" @submit.prevent="apply"
        ><el-alert
          v-if="formError"
          :title="formError"
          type="error"
          :closable="false"
          class="section-alert" /><el-form-item label="配置值" required
          ><el-input
            v-model="form.value"
            :disabled="editIndex >= 0"
            maxlength="100"
            :placeholder="hint"
          />
          <div class="field-help">
            {{
              editIndex >= 0
                ? "已有配置值不可修改；如需替换，请新增一项并停用旧项。"
                : hint
            }}
          </div></el-form-item
        ><el-form-item label="显示名称" required
          ><el-input
            v-model="form.label"
            maxlength="160"
            show-word-limit
            placeholder="客户端下拉框显示的名称" /></el-form-item
        ><el-form-item label="排序"
          ><el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="1000000"
            :precision="0" /></el-form-item
        ><el-form-item label="状态"
          ><el-switch
            v-model="form.enabled"
            active-text="启用"
            inactive-text="停用" /></el-form-item
      ></el-form>
      <template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" @click="apply"
          >保存到草稿</el-button
        ></template
      >
    </el-dialog>
    <ConflictDialog
      v-model="conflictVisible"
      :server="serverVersion"
      :draft="document"
      @keep="keepDraft"
      @use-server="useServer"
    />
  </section>
</template>
