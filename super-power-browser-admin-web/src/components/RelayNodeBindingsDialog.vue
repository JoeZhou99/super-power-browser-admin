<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { errorMessage, formatDate } from '../api';
import { relayApi, type RelayNodeProxyBinding } from '../relayApi';

const props = defineProps<{ modelValue: boolean; node?: { id: string; name: string } }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();
const router = useRouter();
const items = ref<RelayNodeProxyBinding[]>([]), total = ref(0), page = ref(1), loading = ref(false), error = ref('');
let controller: AbortController | undefined;
async function load() {
  controller?.abort();
  if (!props.modelValue || !props.node) return;
  const request = new AbortController(); controller = request;
  loading.value = true; error.value = ''; items.value = [];
  try {
    const result = await relayApi.bindings(props.node.id, page.value, 20, request.signal);
    if (!request.signal.aborted) { items.value = result.items; total.value = result.total; }
  } catch (reason) { if (!request.signal.aborted) { error.value = errorMessage(reason); total.value = 0; } }
  finally { if (!request.signal.aborted) loading.value = false; }
}
function openProxy(row: RelayNodeProxyBinding) {
  emit('update:modelValue', false);
  void router.push({ path: '/proxy/list', query: { proxyId: row.id, tab: 'relays' } });
}
watch(() => [props.modelValue, props.node?.id], () => {
  controller?.abort(); page.value = 1; items.value = []; total.value = 0; error.value = '';
  if (props.modelValue) void load();
}, { immediate: true });
onBeforeUnmount(() => controller?.abort());
</script>

<template>
  <el-dialog :model-value="modelValue" :title="`绑定代理 · ${node?.name || ''}`" width="min(1000px, 95vw)" append-to-body @update:model-value="emit('update:modelValue', $event)">
    <el-alert title="这里统计配置上绑定此节点的代理，不代表当前在线连接数或实时负载。" type="info" :closable="false" class="section-alert"/>
    <div v-if="error" class="bindings-error" role="alert">{{error}}<el-button link type="primary" @click="load">重试</el-button></div>
    <el-table :data="items" v-loading="loading" row-key="id" empty-text="暂无绑定代理">
      <el-table-column label="客户代理" min-width="240"><template #default="{row}"><el-button link type="primary" @click="openProxy(row)">{{row.name || row.id}}</el-button><div class="muted">{{row.host.includes(':') ? `[${row.host}]` : row.host}}:{{row.port}} · {{row.protocol.toUpperCase()}}</div></template></el-table-column>
      <el-table-column label="所属企业" min-width="190"><template #default="{row}">{{row.tenantName || row.tenantId}}<div class="muted">{{row.tenantId}}</div></template></el-table-column>
      <el-table-column label="网络类型" width="100"><template #default="{row}">{{row.webType === 2 ? '静态' : row.webType === 1 ? '动态' : '未标明'}}</template></el-table-column>
      <el-table-column label="绑定顺序" width="100"><template #default="{row}">{{row.sortOrder + 1}}</template></el-table-column>
      <el-table-column label="代理状态" width="100"><template #default="{row}"><el-tag :type="row.recycled ? 'warning' : 'info'">{{row.recycled ? '已回收' : '未回收'}}</el-tag></template></el-table-column>
      <el-table-column label="绑定时间" min-width="170"><template #default="{row}">{{row.boundAt ? formatDate(row.boundAt) : '—'}}</template></el-table-column>
    </el-table>
    <div class="table-footer"><span class="muted">共 {{total}} 个绑定代理</span><el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="prev, pager, next" @current-change="load"/></div>
    <template #footer><el-button @click="emit('update:modelValue', false)">关闭</el-button></template>
  </el-dialog>
</template>
<style scoped>.bindings-error{display:flex;justify-content:space-between;gap:16px;color:var(--el-color-danger);margin-bottom:16px}</style>
