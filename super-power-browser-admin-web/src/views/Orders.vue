<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { Refresh, Search, Tickets } from '@element-plus/icons-vue';
import { businessApi, type OrderSummary } from '../businessApi';
import { errorMessage, formatDate } from '../api';

const filters = reactive({ query: '', status: '', paymentState: '', businessType: '', page: 1, size: 20 });
const items = ref<OrderSummary[]>([]), total = ref(0), loading = ref(false), error = ref(''), integrationRequired = ref(false), detail = ref<OrderSummary | null>(null), detailVisible = ref(false);
let requestController: AbortController | undefined;
async function load() {
  requestController?.abort(); const controller = new AbortController(); requestController = controller;
  loading.value = true; error.value = ''; integrationRequired.value = false;
  try { const result = await businessApi.orders({ ...filters }, controller.signal); if (controller.signal.aborted) return; items.value = result.items; total.value = result.total; integrationRequired.value = result.integrationRequired === true; if (result.message) error.value = result.message; }
  catch (reason) { if (!controller.signal.aborted) error.value = errorMessage(reason); }
  finally { if (!controller.signal.aborted) loading.value = false; }
}
function search() { filters.page = 1; void load(); }
function reset() { Object.assign(filters, { query: '', status: '', paymentState: '', businessType: '', page: 1, size: 20 }); void load(); }
watch(() => [filters.page, filters.size], load); onMounted(load);
function amount(value: string | number, currency: string) { return `${value ?? '0'} ${currency || ''}`.trim(); }
function stateType(value: string) { return ['PAID', 'FULFILLED', 'SUCCEEDED'].includes(value) ? 'success' : ['FAILED', 'CANCELLED', 'REFUNDED'].includes(value) ? 'danger' : 'info'; }
</script>

<template>
  <section class="page-container">
    <div class="page-heading"><div><h1>订单与收支</h1><p>优先迁移旧版购买记录、设备订单和收支明细，先提供支付与履约状态的只读视图。</p></div><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button></div>
    <el-alert v-if="integrationRequired" title="订单业务表尚未接入新后台数据库" description="页面契约已建立，完成订单/账本迁移后会显示真实数据。" type="warning" show-icon :closable="false" class="section-alert" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="section-alert" />
    <el-card shadow="never" class="filter-card"><el-form inline @submit.prevent="search"><el-form-item label="订单号/账户"><el-input v-model="filters.query" clearable placeholder="订单号、用户名或账户" :prefix-icon="Search" @keyup.enter="search" /></el-form-item><el-form-item label="支付状态"><el-select v-model="filters.paymentState" clearable placeholder="全部" @change="search"><el-option label="未支付" value="UNPAID" /><el-option label="已支付" value="PAID" /><el-option label="失败" value="FAILED" /></el-select></el-form-item><el-form-item label="业务类型"><el-input v-model="filters.businessType" clearable placeholder="设备、续费、充值" @keyup.enter="search" /></el-form-item><el-form-item><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item></el-form></el-card>
    <el-card shadow="never"><el-table v-loading="loading" :data="items" row-key="id" stripe empty-text="暂无订单数据"><el-table-column label="订单号" min-width="205"><template #default="{ row }"><div class="primary">{{ row.orderNo }}</div><div class="muted">{{ row.businessType || '未分类' }}</div></template></el-table-column><el-table-column label="账户" min-width="165"><template #default="{ row }">{{ row.accountName || row.accountId }}</template></el-table-column><el-table-column label="订单状态" width="120"><template #default="{ row }"><el-tag size="small" :type="stateType(row.state)">{{ row.state || '—' }}</el-tag></template></el-table-column><el-table-column label="支付/履约" min-width="150"><template #default="{ row }"><div>{{ row.paymentState || '—' }}</div><div class="muted">{{ row.fulfillmentState || '—' }}</div></template></el-table-column><el-table-column label="应付金额" min-width="125"><template #default="{ row }">{{ amount(row.dueAmount, row.currency) }}</template></el-table-column><el-table-column label="创建时间" min-width="175"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column><el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" :icon="Tickets" @click="detail = row; detailVisible = true">查看</el-button></template></el-table-column></el-table><div class="table-footer"><span class="muted">支付、取消、退款和设备履约动作暂不在运营后台伪造。</span><el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" :total="total" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" /></div></el-card>
    <el-dialog v-model="detailVisible" title="订单详情" width="min(760px, 94vw)"><el-descriptions v-if="detail" :column="2" border><el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item><el-descriptions-item label="账户">{{ detail.accountName || detail.accountId }}</el-descriptions-item><el-descriptions-item label="业务类型">{{ detail.businessType || '—' }}</el-descriptions-item><el-descriptions-item label="订单状态">{{ detail.state || '—' }}</el-descriptions-item><el-descriptions-item label="支付状态">{{ detail.paymentState || '—' }}</el-descriptions-item><el-descriptions-item label="履约状态">{{ detail.fulfillmentState || '—' }}</el-descriptions-item><el-descriptions-item label="订单金额">{{ amount(detail.grossAmount, detail.currency) }}</el-descriptions-item><el-descriptions-item label="应付金额">{{ amount(detail.dueAmount, detail.currency) }}</el-descriptions-item><el-descriptions-item label="过期时间">{{ formatDate(detail.expiresAt || undefined) }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ formatDate(detail.createdAt || undefined) }}</el-descriptions-item></el-descriptions><template #footer><el-button @click="detailVisible = false">关闭</el-button></template></el-dialog>
  </section>
</template>
<style scoped>.filter-card { margin-bottom: 16px; }.primary { color: #253451; font-weight: 600; }</style>
