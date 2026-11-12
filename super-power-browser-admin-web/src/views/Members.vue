<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { Refresh, Search, User } from '@element-plus/icons-vue';
import { businessApi, type MemberSummary } from '../businessApi';
import { errorMessage, formatDate } from '../api';

const filters = reactive({ query: '', status: '', page: 1, size: 20 });
const items = ref<MemberSummary[]>([]), total = ref(0), loading = ref(false), error = ref(''), integrationRequired = ref(false), detail = ref<MemberSummary | null>(null), detailVisible = ref(false);
let requestController: AbortController | undefined;

async function load() {
  requestController?.abort();
  const controller = new AbortController(); requestController = controller;
  loading.value = true; error.value = ''; integrationRequired.value = false;
  try {
    const result = await businessApi.members({ ...filters }, controller.signal);
    if (controller.signal.aborted) return;
    items.value = result.items; total.value = result.total; integrationRequired.value = result.integrationRequired === true;
    if (result.message) error.value = result.message;
  } catch (reason) {
    if (!controller.signal.aborted) error.value = errorMessage(reason);
  } finally { if (!controller.signal.aborted) loading.value = false; }
}
function search() { filters.page = 1; void load(); }
function reset() { Object.assign(filters, { query: '', status: '', page: 1, size: 20 }); void load(); }
watch(() => [filters.page, filters.size], load);
onMounted(load);
</script>

<template>
  <section class="page-container">
    <div class="page-heading">
      <div><h1>用户与成员</h1><p>查看新项目账户、成员角色和启用状态，作为旧版用户信息与成员信息的首期迁移。</p></div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="integrationRequired" title="业务用户表尚未接入新后台数据库" description="页面契约已建立，完成业务服务的账户迁移后会显示真实成员。" type="warning" show-icon :closable="false" class="section-alert" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="section-alert" />
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="search">
        <el-form-item label="关键词"><el-input v-model="filters.query" clearable placeholder="用户名、手机号、账户" :prefix-icon="Search" @keyup.enter="search" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部状态" @change="search"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <el-table v-loading="loading" :data="items" row-key="id" stripe empty-text="暂无成员数据">
        <el-table-column label="成员" min-width="210"><template #default="{ row }"><div class="primary">{{ row.username || row.userId }}</div><div class="muted">{{ row.phone || '未填写手机号' }}</div></template></el-table-column>
        <el-table-column label="所属账户" min-width="170"><template #default="{ row }">{{ row.accountName || row.accountId }}</template></el-table-column>
        <el-table-column prop="role" label="角色" width="130" />
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : row.status }}</el-tag></template></el-table-column>
        <el-table-column label="加入时间" min-width="175"><template #default="{ row }">{{ formatDate(row.createdAt || undefined) }}</template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" :icon="User" @click="detail = row; detailVisible = true">查看</el-button></template></el-table-column>
      </el-table>
      <div class="table-footer"><span class="muted">当前只读迁移成员数据，禁用、改密和店铺授权待业务服务接口接入。</span><el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" :total="total" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" /></div>
    </el-card>
    <el-dialog v-model="detailVisible" title="成员详情" width="min(680px, 94vw)">
      <el-descriptions v-if="detail" :column="2" border><el-descriptions-item label="用户名">{{ detail.username || '—' }}</el-descriptions-item><el-descriptions-item label="手机号">{{ detail.phone || '—' }}</el-descriptions-item><el-descriptions-item label="所属账户">{{ detail.accountName || detail.accountId }}</el-descriptions-item><el-descriptions-item label="角色">{{ detail.role || '—' }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.status || '—' }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ formatDate(detail.createdAt || undefined) }}</el-descriptions-item><el-descriptions-item label="权限" :span="2"><pre class="permissions">{{ JSON.stringify(detail.permissions || {}, null, 2) }}</pre></el-descriptions-item></el-descriptions>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.filter-card { margin-bottom: 16px; }
.primary { color: #253451; font-weight: 600; }
.permissions { max-height: 220px; overflow: auto; margin: 0; font-family: Consolas, monospace; font-size: 12px; white-space: pre-wrap; }
</style>
