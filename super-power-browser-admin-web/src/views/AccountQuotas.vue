<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Edit, Refresh, Search } from '@element-plus/icons-vue';
import { businessApi, type AccountQuotaSummary } from '../businessApi';
import { errorMessage, formatDate } from '../api';

const filters = reactive({ query: '', tenantId: '', departmentId: '', page: 1, size: 20 });
const items = ref<AccountQuotaSummary[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const integrationRequired = ref(false);
const editVisible = ref(false);
const editing = ref<AccountQuotaSummary | null>(null);
const saving = ref(false);
const form = reactive({ limitCount: 0, enabled: true, note: '' });
let requestController: AbortController | undefined;

function limitOf(row: AccountQuotaSummary) {
  return row.limitCount ?? row.quota ?? 0;
}
function usedOf(row: AccountQuotaSummary) {
  return row.usedCount ?? row.used ?? 0;
}
function remainingOf(row: AccountQuotaSummary) {
  if (row.remainingCount !== undefined) return row.remainingCount === null ? -1 : row.remainingCount;
  if (row.available !== undefined) return row.available === null ? -1 : row.available;
  const limit = limitOf(row);
  return row.enabled === false || limit < 0 ? -1 : Math.max(0, limit - usedOf(row));
}
function unlimited(row: AccountQuotaSummary) { return row.enabled === false || limitOf(row) < 0; }
function unlimitedLabel(row: AccountQuotaSummary) { return row.enabled === false ? '校验停用' : '不限额'; }
function accountLabel(row: AccountQuotaSummary) {
  return row.accountName || row.tenantName || row.tenantId || row.accountId || '未命名企业';
}
function memberLabel(row: AccountQuotaSummary) {
  return row.displayName && row.displayName !== row.username
    ? `${row.displayName}（${row.username}）`
    : row.username;
}
function usagePercent(row: AccountQuotaSummary) {
  const limit = limitOf(row); if (limit <= 0) return 0;
  return Math.min(100, Math.round(usedOf(row) / limit * 100));
}
function usageType(row: AccountQuotaSummary) {
  if (row.enabled === false) return 'warning';
  if (unlimited(row)) return 'success';
  const percent = usagePercent(row);
  return percent >= 100 ? 'danger' : percent >= 80 ? 'warning' : 'success';
}

async function load() {
  requestController?.abort();
  const controller = new AbortController(); requestController = controller;
  loading.value = true; error.value = ''; integrationRequired.value = false;
  try {
    const result = await businessApi.accountQuotas({ ...filters }, controller.signal);
    if (controller.signal.aborted) return;
    items.value = result.items; total.value = result.total;
    integrationRequired.value = result.integrationRequired === true;
    if (result.message) error.value = result.message;
  } catch (reason) {
    if (!controller.signal.aborted) error.value = errorMessage(reason);
  } finally { if (!controller.signal.aborted) loading.value = false; }
}
function search() { filters.page = 1; void load(); }
function reset() { Object.assign(filters, { query: '', tenantId: '', departmentId: '', page: 1, size: 20 }); void load(); }
function openEdit(row: AccountQuotaSummary) {
  editing.value = row;
  form.limitCount = limitOf(row);
  form.enabled = row.enabled !== false;
  form.note = row.note || '';
  editVisible.value = true;
}
async function save() {
  if (!editing.value) return;
  const limit = Number(form.limitCount);
  if (!Number.isInteger(limit) || limit < -1 || limit > 1_000_000) {
    ElMessage.error('额度必须是 -1 至 1000000 的整数，-1 表示不限额'); return;
  }
  saving.value = true;
  try {
    const result = await businessApi.updateAccountQuota(editing.value.userId, {
      limitCount: limit,
      enabled: form.enabled,
      note: form.note.trim(),
      revision: editing.value.revision,
    });
    const index = items.value.findIndex(item => item.userId === editing.value?.userId);
    if (index >= 0) items.value[index] = result;
    editVisible.value = false;
    ElMessage.success('店铺创建额度已更新');
  } catch (reason) { ElMessage.error(errorMessage(reason)); }
  finally { saving.value = false; }
}
watch(() => [filters.page, filters.size], load);
onMounted(load);
</script>

<template>
  <section class="page-container">
      <div class="page-heading">
      <div><h1>店铺创建额度</h1><p>按账号设置店铺创建上限，企业和部门用于归属、筛选和审计；创建时按账号实时校验。</p></div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="integrationRequired" title="请先完成主服务 V31 数据库升级" description="重启主服务执行 Flyway V31 迁移后，再刷新本页面；升级完成后会显示真实账号、使用量和额度配置。" type="warning" show-icon :closable="false" class="section-alert" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="section-alert" />
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="search">
        <el-form-item label="账号/企业"><el-input v-model="filters.query" clearable placeholder="用户名、显示名或企业名称" :prefix-icon="Search" @keyup.enter="search" /></el-form-item>
        <el-form-item label="企业编号"><el-input v-model="filters.tenantId" clearable placeholder="企业编号" @keyup.enter="search" /></el-form-item>
        <el-form-item label="部门"><el-input v-model="filters.departmentId" clearable placeholder="部门编号" @keyup.enter="search" /></el-form-item>
        <el-form-item><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="items" row-key="userId" stripe empty-text="暂无账号额度数据">
        <el-table-column label="账号" min-width="220"><template #default="{ row }"><div class="primary">{{ memberLabel(row) }}</div><div class="muted">{{ row.userId }}</div></template></el-table-column>
        <el-table-column label="所属企业" min-width="170"><template #default="{ row }"><div>{{ accountLabel(row) }}</div><div class="muted">{{ row.tenantId || row.accountId || '—' }}</div></template></el-table-column>
        <el-table-column label="所属部门" min-width="150"><template #default="{ row }"><div>{{ row.departmentName || '未分配部门' }}</div><div class="muted">{{ row.departmentId || '—' }}</div></template></el-table-column>
        <el-table-column label="已用/上限" min-width="175"><template #default="{ row }"><div class="quota-line"><strong>{{ usedOf(row) }}</strong><span>/ {{ unlimited(row) ? '不限' : limitOf(row) }}</span></div><el-progress v-if="!unlimited(row)" :percentage="usagePercent(row)" :status="usageType(row)" :stroke-width="6" :show-text="false" /><el-tag v-else size="small" :type="row.enabled === false ? 'warning' : 'success'">{{ unlimitedLabel(row) }}</el-tag></template></el-table-column>
        <el-table-column label="剩余" width="100"><template #default="{ row }"><el-tag size="small" :type="usageType(row)">{{ row.enabled === false ? '未校验' : remainingOf(row) < 0 ? '不限' : remainingOf(row) }}</el-tag></template></el-table-column>
        <el-table-column label="更新时间" min-width="175"><template #default="{ row }">{{ formatDate(row.updatedAt || undefined) }}</template></el-table-column>
        <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" :icon="Edit" @click="openEdit(row)">配置</el-button></template></el-table-column>
      </el-table>
      <div class="table-footer"><span class="muted">使用量按账号拥有的全部店铺实时统计，永久删除后释放额度；保存额度不会改变历史店铺。</span><el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" :total="total" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" /></div>
    </el-card>
    <el-dialog v-model="editVisible" title="配置店铺创建额度" width="min(520px, 94vw)" destroy-on-close>
      <template v-if="editing">
        <el-descriptions :column="1" border class="edit-summary"><el-descriptions-item label="账号">{{ memberLabel(editing) }}</el-descriptions-item><el-descriptions-item label="企业/部门">{{ accountLabel(editing) }} / {{ editing.departmentName || '未分配部门' }}</el-descriptions-item><el-descriptions-item label="当前使用">{{ usedOf(editing) }} 个店铺</el-descriptions-item></el-descriptions>
        <el-form label-position="top" class="edit-form"><el-form-item label="创建上限"><el-input-number v-model="form.limitCount" :min="-1" :max="1000000" :step="10" controls-position="right" /><div class="field-help">输入 -1 表示不限额。新建店铺时会在事务内重新校验，达到上限会拒绝创建。</div></el-form-item><el-form-item label="策略状态"><el-switch v-model="form.enabled" active-text="启用额度校验" inactive-text="停用额度校验" /></el-form-item><el-form-item label="备注"><el-input v-model="form.note" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="记录调整原因或工单编号" /></el-form-item></el-form>
      </template>
      <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.filter-card { margin-bottom: 16px; }
.primary { color: #253451; font-weight: 600; }
.quota-line { display: flex; align-items: baseline; gap: 4px; margin-bottom: 6px; }
.quota-line strong { color: #253451; font-size: 15px; }
.quota-line span { color: #7b8494; }
.edit-summary { margin-bottom: 20px; }
.edit-form .el-input-number { width: 100%; }
</style>
