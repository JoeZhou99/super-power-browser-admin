<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Search, ArrowUp, ArrowDown } from "@element-plus/icons-vue";
import { configTitles } from "../api";
import { useConfigEditor } from "../composables/useConfigEditor";
import ConfigStatus from "../components/ConfigStatus.vue";
import ConflictDialog from "../components/ConflictDialog.vue";
import {
  normalizedRule, rulePages, validateRule,
  type RuleConfigKey, type RuleGroup, type RuleListKey, type RuleValues,
} from "../ruleConfig";

const key = useRoute().meta.configKey as RuleConfigKey;
const title = configTitles[key], policy = rulePages[key];
const {
  document, dirty, loading, saving, loadError, conflict, serverVersion,
  conflictVisible, load, refresh, save, inspectConflict, keepDraft, useServer,
} = useConfigEditor<RuleValues>(key);
const values = computed(() => document.value?.values);
const active = ref<RuleListKey>(policy.groups[0].key);
const group = computed(() => policy.groups.find((item) => item.key === active.value)!);
const search = ref(""), page = ref(1), pageSize = ref(15);
const list = (item: RuleGroup) => values.value?.[item.key] || [];
const rows = computed(() => list(group.value).map((value, index) => ({ value, index })));
const filtered = computed(() => rows.value.filter((row) => row.value.toLowerCase().includes(search.value.trim().toLowerCase())));
const paginated = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value));
const total = computed(() => policy.groups.reduce((sum, item) => sum + list(item).length, 0));
watch([search, active, pageSize], () => { page.value = 1; });
watch(() => filtered.value.length, (count) => {
  page.value = Math.min(page.value, Math.max(1, Math.ceil(count / pageSize.value)));
});

const dialog = ref(false), editIndex = ref(-1), formValue = ref(""), formError = ref("");
const editGroup = ref<RuleGroup>(policy.groups[0]);
function edit(row?: { value: string; index: number }) {
  editGroup.value = group.value;
  editIndex.value = row?.index ?? -1;
  formValue.value = row?.value || "";
  formError.value = "";
  dialog.value = true;
}
function apply() {
  if (!values.value || saving.value) return;
  const target = editGroup.value;
  formError.value = validateRule(target, formValue.value);
  if (formError.value) return;
  const value = normalizedRule(target, formValue.value), entries = list(target);
  if (entries.some((entry, index) => index !== editIndex.value && normalizedRule(target, entry) === value)) {
    formError.value = "这条规则已存在，请编辑已有记录。";
    return;
  }
  if (editIndex.value < 0 && entries.length >= target.max) {
    formError.value = `本组最多允许 ${target.max} 条规则。`;
    return;
  }
  const next = [...entries];
  if (editIndex.value < 0) next.push(value);
  else next.splice(editIndex.value, 1, value);
  values.value[target.key] = next;
  dialog.value = false;
  ElMessage.success("已加入草稿，保存发布后生效。");
}
async function remove(row: { value: string; index: number }) {
  try {
    await ElMessageBox.confirm(`从草稿中删除“${row.value}”？保存发布后生效。`, "删除规则", {
      type: "warning", confirmButtonText: "删除", cancelButtonText: "取消",
    });
    if (!values.value || saving.value) return;
    values.value[active.value] = list(group.value).filter((_, index) => index !== row.index);
  } catch { /* Keep the draft when cancelled. */ }
}
function move(index: number, offset: number) {
  if (!values.value || saving.value) return;
  const next = [...list(group.value)], other = index + offset;
  if (other < 0 || other >= next.length) return;
  [next[index], next[other]] = [next[other], next[index]];
  values.value[active.value] = next;
}
async function publish() {
  if (!values.value) return;
  for (const item of policy.groups) {
    const entries = list(item);
    let error = entries.length > item.max ? `最多允许 ${item.max} 条规则。` : "";
    if (!error && values.value.enabled && !entries.length) {
      if (key === "network-checks") error = "启用 IP 检测配置时，本组至少需要一个地址。";
      if (key === "passkey-rules") error = "启用 Passkey 规则时，本组至少需要一条页面规则。";
      if (key === "access-policy" && values.value.mode === "whitelist" && item.key === "domains") error = "启用白名单时至少需要一个网站域名。";
    }
    if (!error) error = entries.map((value) => validateRule(item, value)).find(Boolean) || "";
    if (!error && new Set(entries.map((value) => normalizedRule(item, value))).size !== entries.length) error = "规则重复，请删除重复项。";
    if (error) {
      active.value = item.key;
      ElMessage.warning(`${item.title}：${error}`);
      return;
    }
  }
  await save();
}
onMounted(load);
</script>

<template>
  <section class="page-container">
    <div class="page-heading"><div><h1>{{ title }}</h1><p>{{ policy.description }}</p></div></div>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon class="section-alert">
      <template #default><el-button link type="primary" @click="refresh">重试加载</el-button></template>
    </el-alert>
    <el-card shadow="never" v-loading="loading">
      <ConfigStatus :revision="document?.revision" :updated-at="document?.updatedAt" :dirty="dirty"
        :saving="saving" :loading="loading" :conflict="conflict" @save="publish" @refresh="refresh" @inspect="inspectConflict" />
      <template v-if="values">
        <div class="policy-settings">
          <div class="policy-enabled">
            <span class="setting-label">启用此配置</span>
            <el-switch v-model="values.enabled" :disabled="saving" active-text="启用" inactive-text="停用" :aria-label="`启用${title}`" />
            <span class="muted">{{ values.enabled ? '发布后使用下方规则' : '当前停用，发布后沿用现有配置；可先维护规则' }}</span>
          </div>
          <el-form v-if="key === 'access-policy'" label-position="left" class="access-mode" :disabled="saving">
            <el-form-item label="访问模式">
              <el-radio-group v-model="values.mode" aria-label="访问模式">
                <el-radio-button value="blacklist">禁止名单中的网站</el-radio-button>
                <el-radio-button value="whitelist">只允许名单中的网站</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-form>
        </div>
        <el-alert :title="policy.effect" type="info" :closable="false" show-icon class="section-alert" />
        <el-tabs v-model="active" class="rule-tabs">
          <el-tab-pane v-for="item in policy.groups" :key="item.key" :name="item.key" :label="`${item.title}（${list(item).length}）`" />
        </el-tabs>
        <p class="rule-hint">{{ group.hint }}</p>
        <div class="table-toolbar">
          <div class="filters"><el-input v-model="search" :prefix-icon="Search" clearable placeholder="搜索当前分组规则" aria-label="搜索规则" /></div>
          <el-button type="primary" :icon="Plus" :disabled="saving || list(group).length >= group.max" @click="edit()">新增规则</el-button>
        </div>
        <el-table :data="paginated" stripe empty-text="暂无规则，可点击“新增规则”添加" row-key="index">
          <el-table-column label="顺序" width="80"><template #default="{ row }">{{ row.index + 1 }}</template></el-table-column>
          <el-table-column prop="value" :label="group.title" min-width="350" show-overflow-tooltip />
          <el-table-column v-if="key === 'network-checks'" label="调整顺序" width="125">
            <template #default="{ row }">
              <el-button link :icon="ArrowUp" :disabled="saving || row.index === 0" aria-label="上移检测地址" @click="move(row.index, -1)" />
              <el-button link :icon="ArrowDown" :disabled="saving || row.index === rows.length - 1" aria-label="下移检测地址" @click="move(row.index, 1)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }">
            <el-button link type="primary" :disabled="saving" @click="edit(row)">编辑</el-button>
            <el-button link type="danger" :disabled="saving" @click="remove(row)">删除</el-button>
          </template></el-table-column>
        </el-table>
        <div class="table-footer">
          <span class="muted">本组 {{ rows.length }} / {{ group.max }} 条 · 全部 {{ total }} 条，规则随本模块统一启停</span>
          <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="filtered.length" :page-sizes="[15, 30, 50]" layout="total, sizes, prev, pager, next" />
        </div>
      </template>
    </el-card>
    <el-dialog v-model="dialog" :title="`${editIndex < 0 ? '新增' : '编辑'}${editGroup.title}`" width="min(620px, 94vw)" :close-on-click-modal="false">
      <el-form label-position="top" :disabled="saving" @submit.prevent="apply">
        <el-alert v-if="formError" :title="formError" type="error" :closable="false" class="section-alert" />
        <el-form-item :label="editGroup.title" required>
          <el-input v-model="formValue" type="textarea" :rows="3" :maxlength="2048" :placeholder="editGroup.placeholder" show-word-limit :aria-label="editGroup.title" />
          <div class="field-help">{{ editGroup.hint }}</div>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :disabled="saving" @click="apply">保存到草稿</el-button></template>
    </el-dialog>
    <ConflictDialog v-model="conflictVisible" :server="serverVersion" :draft="document" @keep="keepDraft" @use-server="useServer" />
  </section>
</template>

<style scoped>
.policy-settings { margin-bottom: 18px; }
.policy-enabled { display: flex; align-items: center; flex-wrap: wrap; gap: 18px; }
.setting-label { font-weight: 500; }
.access-mode { margin-top: 22px; }
.access-mode :deep(.el-form-item) { margin-bottom: 0; }
.rule-hint { color: #7b8494; line-height: 1.7; margin: 2px 0 20px; }
.rule-tabs { margin-bottom: 12px; }
@media (max-width: 600px) {
  .policy-enabled { gap: 10px; }
  .access-mode :deep(.el-form-item__content) { width: 100%; }
}
</style>
