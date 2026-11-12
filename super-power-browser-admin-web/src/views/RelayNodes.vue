<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { CopyDocument, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { errorMessage, formatDate } from '../api';
import { relayApi, type RelayNodeListItem, type RelayPool } from '../relayApi';
import RelayNodeBindingsDialog from '../components/RelayNodeBindingsDialog.vue';

const router = useRouter();
const defaults = () => ({ query: '', poolId: '', enabled: 'all', protocol: '' });
const filters = reactive(defaults());
let applied = defaults();
const items = ref<RelayNodeListItem[]>([]), total = ref(0), page = ref(1), size = ref(20);
const loading = ref(false), loadError = ref('');
const pools = ref<RelayPool[]>([]), poolsLoading = ref(false), poolsError = ref('');
const addVisible = ref(false), newPoolId = ref('');
const bindingsVisible = ref(false), bindingNode = ref<RelayNodeListItem>();
function showBindings(node: RelayNodeListItem) { bindingNode.value = node; bindingsVisible.value = true; }
let listRequest: AbortController | undefined, poolVersion = 0, disposed = false;
const endpoint = (host: string, port: number) => `${host.includes(':') && !host.startsWith('[') ? `[${host}]` : host}:${port}`;
async function load() {
  listRequest?.abort();
  const controller = new AbortController(); listRequest = controller;
  loading.value = true; loadError.value = ''; items.value = [];
  try {
    const result = await relayApi.allNodes({ ...applied, page: page.value, size: size.value }, controller.signal);
    if (!controller.signal.aborted && !disposed) { items.value = result.items; total.value = result.total; }
  } catch (error) {
    if (!controller.signal.aborted && !disposed) { loadError.value = errorMessage(error); total.value = 0; }
  } finally { if (!controller.signal.aborted && !disposed) loading.value = false; }
}
async function loadPools() {
  const version = ++poolVersion; poolsLoading.value = true; poolsError.value = '';
  try {
    const result = await relayApi.pools();
    if (!disposed && version === poolVersion) pools.value = result.items;
  } catch (error) { if (!disposed && version === poolVersion) poolsError.value = errorMessage(error); }
  finally { if (!disposed && version === poolVersion) poolsLoading.value = false; }
}
function search() { applied = { ...filters, query: filters.query.trim() }; page.value = 1; void load(); }
function reset() { Object.assign(filters, defaults()); search(); }
function setPage(value: number) { page.value = value; void load(); }
function setSize(value: number) { size.value = value; page.value = 1; void load(); }
function manage(poolId: string, nodeId?: string) { void router.push({ path: '/proxy/groups', query: { poolId, ...(nodeId ? { nodeId } : {}) } }); }
function chooseGroup() { newPoolId.value = filters.poolId; addVisible.value = true; void loadPools(); }
function createNode() {
  if (!newPoolId.value || !pools.value.some(pool => pool.id === newPoolId.value)) { ElMessage.warning('请先选择有效的中继分组'); return; }
  addVisible.value = false; void router.push({ path: '/proxy/groups', query: { poolId: newPoolId.value, addNode: '1' } });
}
async function copyAddress(row: RelayNodeListItem) {
  try { await navigator.clipboard.writeText(endpoint(row.host, row.port)); ElMessage.success('已复制节点地址与端口'); }
  catch { ElMessage.error('复制失败，请手动复制显示的地址与端口'); }
}
function refresh() { void load(); void loadPools(); }
onMounted(refresh);
onBeforeUnmount(() => { disposed = true; listRequest?.abort(); poolVersion++; });
</script>

<template>
  <section class="page-container relay-nodes">
    <div class="page-heading"><div><h1>中继池</h1><p>维护平台中继节点。企业授权决定可选范围，客户代理绑定具体节点后由客户端检测选路。</p></div><div class="heading-actions"><el-button :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button><el-button type="primary" :icon="Plus" @click="chooseGroup">新增节点</el-button></div></div>
    <el-card shadow="never" class="filter-card">
      <el-form label-position="top" @submit.prevent="search"><div class="filter-grid">
        <el-form-item label="关键词"><el-input v-model="filters.query" clearable maxlength="200" :prefix-icon="Search" placeholder="名称 / 地址 / 地区 / 分组标识" @keyup.enter="search"/></el-form-item>
        <el-form-item label="所属中继分组"><el-select v-model="filters.poolId" clearable filterable :loading="poolsLoading" placeholder="全部分组" aria-label="筛选中继分组"><el-option v-for="pool in pools" :key="pool.id" :value="pool.id" :label="`${pool.name} · ${pool.code || pool.id}`"/></el-select></el-form-item>
        <el-form-item label="节点状态"><el-select v-model="filters.enabled" aria-label="筛选节点状态"><el-option label="全部" value="all"/><el-option label="启用" value="true"/><el-option label="停用" value="false"/></el-select></el-form-item>
        <el-form-item label="协议"><el-select v-model="filters.protocol" clearable placeholder="全部协议" aria-label="筛选中继协议"><el-option label="HTTP" value="http"/><el-option label="HTTPS" value="https"/><el-option label="SOCKS5" value="socks5"/><el-option label="Shadowsocks (SS)" value="ss"/></el-select></el-form-item>
        <div class="filter-actions"><el-button type="primary" :loading="loading" @click="search">查询</el-button><el-button @click="reset">重置</el-button></div>
      </div></el-form>
      <div v-if="poolsError" class="request-error">分组列表加载失败：{{poolsError}}<el-button link type="primary" @click="loadPools">重试分组列表</el-button></div>
    </el-card>
    <el-card shadow="never">
      <div class="list-note">启用表示配置状态，不代表网络检测成功。绑定代理数为配置数量，可点击查看明细，不代表实时负载。</div>
      <div v-if="loadError" class="request-error" role="alert"><span>{{loadError}}</span><el-button type="primary" link @click="load">重试</el-button></div>
      <el-table :data="items" v-loading="loading" row-key="id" :empty-text="loading ? '正在加载节点…' : loadError ? '节点列表加载失败，请重试' : '暂无符合筛选条件的中继节点'">
        <el-table-column label="节点" min-width="190"><template #default="{row}"><strong>{{row.name}}</strong><div class="muted identifier">ID {{row.id}}</div><div class="muted">{{row.region || '未填写地区'}}</div></template></el-table-column>
        <el-table-column label="所属分组" min-width="200"><template #default="{row}"><el-button link type="primary" @click="manage(row.poolId)">{{row.poolName || row.poolId}}</el-button><div class="muted identifier">{{row.poolCode || row.poolId}}</div><el-tag v-if="!row.poolEnabled" size="small" type="warning">分组已停用</el-tag></template></el-table-column>
        <el-table-column label="节点地址" min-width="220"><template #default="{row}"><div class="address-line"><span class="mono">{{endpoint(row.host, row.port)}}</span><el-button link :icon="CopyDocument" :aria-label="`复制节点 ${row.name} 地址`" @click="copyAddress(row)"/></div><el-tag size="small" type="info" effect="plain">{{row.protocol.toUpperCase()}}</el-tag><div v-if="row.protocol === 'ss'" class="muted">{{row.proxyOptions?.encryption || '未设置加密方式'}}</div></template></el-table-column>
        <el-table-column label="认证信息" min-width="160"><template #default="{row}"><div>{{row.protocol === 'ss' ? '无需账号' : row.username || '未设置账号'}}</div><div class="muted">密码{{row.hasPassword ? '已配置' : '未配置'}}</div></template></el-table-column>
        <el-table-column label="节点状态" width="110"><template #default="{row}"><el-tag :type="row.enabled ? 'success' : 'info'">{{row.enabled ? '启用' : '停用'}}</el-tag></template></el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80"/>
        <el-table-column label="绑定代理数" width="115"><template #default="{row}"><el-button link type="primary" :aria-label="`查看 ${row.name} 绑定代理`" @click="showBindings(row)">{{row.boundProxyCount}}</el-button></template></el-table-column>
        <el-table-column label="更新时间" min-width="180"><template #default="{row}">{{row.updatedAt ? formatDate(row.updatedAt) : '—'}}</template></el-table-column>
        <el-table-column label="操作" width="145" fixed="right"><template #default="{row}"><el-button link type="primary" @click="manage(row.poolId, row.id)">编辑</el-button><el-button link type="primary" @click="manage(row.poolId)">管理分组</el-button></template></el-table-column>
      </el-table>
      <div class="table-footer"><span class="muted">节点可在编辑中启停；企业授权在所属分组中维护。</span><el-pagination :current-page="page" :page-size="size" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @current-change="setPage" @size-change="setSize"/></div>
    </el-card>
    <el-dialog v-model="addVisible" title="新增中继节点" width="min(520px, 92vw)">
      <p class="muted">先选择节点所属的中继分组，再填写连接信息。分组授权决定哪些企业的代理可以绑定该节点。</p>
      <el-form label-position="top"><el-form-item label="所属中继分组" required><el-select v-model="newPoolId" filterable :loading="poolsLoading" placeholder="请选择中继分组" aria-label="新增节点所属分组"><el-option v-for="pool in pools" :key="pool.id" :value="pool.id" :label="`${pool.name} · ${pool.code || pool.id}${pool.enabled ? '' : '（已停用）'}`"/></el-select></el-form-item></el-form>
      <div v-if="poolsError" class="request-error">{{poolsError}}<el-button link type="primary" @click="loadPools">重试分组列表</el-button></div>
      <el-empty v-else-if="!poolsLoading && !pools.length" description="暂无中继分组"><el-button type="primary" @click="router.push('/proxy/groups')">先创建中继分组</el-button></el-empty>
      <template #footer><el-button @click="addVisible = false">取消</el-button><el-button type="primary" :disabled="!newPoolId || poolsLoading || Boolean(poolsError)" @click="createNode">填写节点信息</el-button></template>
    </el-dialog>
    <RelayNodeBindingsDialog v-model="bindingsVisible" :node="bindingNode"/>
  </section>
</template>

<style scoped>
.heading-actions { display: flex; flex-shrink: 0; }
.filter-card { margin-bottom: 16px; }
.filter-grid { display: grid; grid-template-columns: minmax(220px, 1.5fr) minmax(180px, 1.2fr) minmax(110px, .8fr) minmax(110px, .8fr) auto; gap: 16px; }
.filter-grid .el-form-item { margin-bottom: 0; }
.el-select { width: 100%; }
.filter-actions { display: flex; align-items: flex-end; }
.list-note { font-size: 12px; color: #8b95a5; line-height: 1.7; margin-bottom: 16px; }
.request-error { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 16px; margin-bottom: 16px; border-radius: 5px; background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.filter-card .request-error { margin: 14px 0 0; }
.identifier { font-size: 11px; overflow-wrap: anywhere; }
.mono { font-family: Consolas, monospace; overflow-wrap: anywhere; }
.address-line { display: flex; align-items: center; gap: 5px; margin-bottom: 7px; }
.address-line .el-button { flex-shrink: 0; padding: 3px; }
@media (max-width: 1200px) { .filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 580px) { .filter-grid { grid-template-columns: 1fr; } }
</style>
