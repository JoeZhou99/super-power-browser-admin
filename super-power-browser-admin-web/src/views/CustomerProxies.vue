<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowDown, CopyDocument, Refresh, Search } from '@element-plus/icons-vue';
import { errorMessage, formatDate } from '../api';
import { relayApi, type RelayTenant } from '../relayApi';
import { customerProxyApi, relayAvailability, type CustomerProxy, type CustomerProxyDetail, type CustomerProxyQuery, type ProxyRelaySnapshot } from '../customerProxyApi';
import ProxyRelayBindingDialog from '../components/ProxyRelayBindingDialog.vue';

const router = useRouter(), route = useRoute();
const defaults = () => ({ query: '', tenantId: '', protocol: '', webType: 0, recycled: 'false', connectionStatus: 'all', accelerationStatus: 'all' });
const filters = reactive(defaults());
let applied = defaults();
const protocols = ['http', 'https', 'socks5', 'socks4', 'socks4a', 'ssh', 'ss', 'ssr', 'vmess', 'vless'];
const items = ref<CustomerProxy[]>([]), total = ref(0), page = ref(1), size = ref(20);
const loading = ref(false), loadError = ref('');
const tenants = ref<RelayTenant[]>([]), tenantLoading = ref(false), tenantError = ref('');
const detailVisible = ref(false), detailLoading = ref(false), detailError = ref(''), detailTab = ref('exit');
const selected = ref<CustomerProxyDetail>(), detailId = ref('');
const selection = ref<CustomerProxy[]>([]), bindingTargets = ref<CustomerProxy[]>([]), bindingVisible = ref(false), allocate = ref(false);
const relaySnapshot = ref<ProxyRelaySnapshot>(), relayLoading = ref(false), relayError = ref('');
let listRequest: AbortController | undefined, detailRequest: AbortController | undefined, relayRequest: AbortController | undefined;
let tenantVersion = 0, disposed = false;

const accelerationLabels: Record<string, string> = { auto: '中继可用', direct: '直连代理', unsupported: '暂不支持', unavailable: '线路不可用', inactive: '设备不可用' };
const accelerationTypes: Record<string, 'success' | 'info' | 'warning'> = { auto: 'success', direct: 'info', unsupported: 'info', unavailable: 'warning', inactive: 'warning' };
const reasons: Record<string, string> = {
  available: '已绑定可用中继；启动优先上次成功线路，无记录时先直连代理', no_authorization: '所属企业尚未获授可用线路组',
  unsupported_proxy: '此代理类型暂不支持平台加速', inactive_device: '代理已回收或已到期',
  no_available_nodes: '已绑定中继暂无可用节点', no_available_bound_nodes: '已绑定中继暂无可用节点',
  no_bindings: '尚未绑定中继，直接连接客户出口代理',
};
function accelerationReason(row: CustomerProxy) {
  return reasons[row.acceleration.reason] || ({ auto: '已绑定可用中继，客户端可检测和切换线路', direct: '直接连接客户绑定的代理', unsupported: '此代理暂不支持平台加速', unavailable: '请检查线路组、节点与企业授权', inactive: '请检查设备回收状态和到期时间' }[row.acceleration.status] || '暂无加速配置');
}
const text = (value?: string | null) => value || '—';
const date = (value?: string | null, fallback = '—') => value ? formatDate(value) : fallback;
const endpoint = (host: string, port?: number | null) => `${host.includes(':') && !host.startsWith('[') ? `[${host}]` : host}${port ? `:${port}` : ''}`;
const networkType = (row: CustomerProxy) => row.webType === 2 ? '静态' : row.webType === 1 ? '动态' : '未标明';
const connectionLabel = (row: CustomerProxy) => row.checkConnected === true ? '检测成功' : row.checkConnected === false ? '检测失败' : '未检测';
const connectionType = (row: CustomerProxy) => row.checkConnected === true ? 'success' : row.checkConnected === false ? 'danger' : 'info';
const latency = (row: CustomerProxy) => typeof row.checkLatencyMs === 'number' && row.checkLatencyMs >= 0 ? `${row.checkLatencyMs} ms` : '无延迟记录';

async function load() {
  listRequest?.abort();
  const controller = new AbortController(); listRequest = controller;
  loading.value = true; loadError.value = ''; items.value = []; selection.value = [];
  try {
    const query: CustomerProxyQuery = { ...applied, page: page.value, size: size.value };
    const result = await customerProxyApi.list(query, controller.signal);
    if (controller.signal.aborted || disposed) return;
    items.value = result.items; total.value = result.total;
  } catch (error) {
    if (!controller.signal.aborted && !disposed) { loadError.value = errorMessage(error); total.value = 0; }
  } finally { if (!controller.signal.aborted && !disposed) loading.value = false; }
}
function search() { applied = { ...filters, query: filters.query.trim() }; page.value = 1; void load(); }
function reset() { Object.assign(filters, defaults()); search(); }
function setPage(value: number) { page.value = value; void load(); }
function setSize(value: number) { size.value = value; page.value = 1; void load(); }
async function searchTenants(query: string) {
  const version = ++tenantVersion; tenantLoading.value = true; tenantError.value = '';
  try {
    const result = await relayApi.tenants(query.trim());
    if (!disposed && version === tenantVersion) tenants.value = result.items;
  } catch (error) { if (!disposed && version === tenantVersion) tenantError.value = errorMessage(error); }
  finally { if (!disposed && version === tenantVersion) tenantLoading.value = false; }
}
function showTenants(visible: boolean) { if (visible) void searchTenants(''); }
async function loadDetail() {
  detailRequest?.abort();
  const controller = new AbortController(); detailRequest = controller;
  detailLoading.value = true; detailError.value = ''; selected.value = undefined;
  try {
    const result = await customerProxyApi.detail(detailId.value, controller.signal);
    if (!controller.signal.aborted && !disposed) selected.value = result;
  } catch (error) { if (!controller.signal.aborted && !disposed) detailError.value = errorMessage(error); }
  finally { if (!controller.signal.aborted && !disposed) detailLoading.value = false; }
}
async function loadRelays() {
  relayRequest?.abort();
  const controller = new AbortController(); relayRequest = controller;
  relayLoading.value = true; relayError.value = ''; relaySnapshot.value = undefined;
  try {
    const result = await customerProxyApi.relays(detailId.value, controller.signal);
    if (!controller.signal.aborted && !disposed) relaySnapshot.value = result;
  } catch (error) { if (!controller.signal.aborted && !disposed) relayError.value = errorMessage(error); }
  finally { if (!controller.signal.aborted && !disposed) relayLoading.value = false; }
}
function inspect(row: Pick<CustomerProxy, 'id'>, tab = 'exit') {
  detailId.value = row.id; detailTab.value = tab; detailVisible.value = true; void loadDetail(); void loadRelays();
}
function closeDetail() { if (detailVisible.value) return; detailRequest?.abort(); relayRequest?.abort(); selected.value = undefined; relaySnapshot.value = undefined; detailId.value = ''; }
function bind(targets: CustomerProxy[], automatic = false) {
  bindingTargets.value = [...targets]; allocate.value = automatic; bindingVisible.value = true;
}
function bindingsSaved() { void load(); if (detailVisible.value) { void loadDetail(); void loadRelays(); } }
function managePool(poolId: string) { detailVisible.value = false; void router.push({ path: '/proxy/groups', query: { poolId } }); }
function action(command: string, row: CustomerProxy) {
  if (command === 'bind') bind([row]);
  else if (command === 'manage') {
    if (row.acceleration.pools.length === 1) managePool(row.acceleration.pools[0].id);
    else inspect(row, 'relays');
  } else inspect(row, command);
}
async function copyEndpoint(host: string, port?: number | null) {
  try { await navigator.clipboard.writeText(endpoint(host, port)); ElMessage.success('已复制地址与端口'); }
  catch { ElMessage.error('复制失败，请手动复制显示的地址与端口'); }
}
watch(() => [route.query.proxyId, route.query.tab], () => {
  if (typeof route.query.proxyId === 'string' && route.query.proxyId) inspect({ id: route.query.proxyId }, route.query.tab === 'relays' ? 'relays' : 'exit');
}, { immediate: true });
onMounted(load);
onBeforeUnmount(() => { disposed = true; listRequest?.abort(); detailRequest?.abort(); relayRequest?.abort(); tenantVersion++; });
</script>

<template>
  <section class="page-container customer-proxies">
    <div class="page-heading">
      <div><h1>客户代理</h1><p>查看客户出口代理与检测记录，为代理绑定企业授权范围内的具体中继。</p></div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form label-position="top" @submit.prevent="search">
        <div class="filter-grid">
          <el-form-item label="关键词"><el-input v-model="filters.query" clearable maxlength="200" :prefix-icon="Search" placeholder="代理名称 / 地址 / ID / 账号" @keyup.enter="search"/></el-form-item>
          <el-form-item label="所属企业"><el-select v-model="filters.tenantId" clearable filterable remote :remote-method="searchTenants" :loading="tenantLoading" placeholder="全部企业，输入名称搜索" aria-label="筛选所属企业" @visible-change="showTenants"><el-option v-for="tenant in tenants" :key="tenant.id" :value="tenant.id" :label="`${tenant.name} · ${tenant.id}`"/></el-select><span v-if="tenantError" class="field-error">{{tenantError}}</span></el-form-item>
          <el-form-item label="代理协议"><el-select v-model="filters.protocol" clearable placeholder="全部协议" aria-label="筛选代理协议"><el-option v-for="protocol in protocols" :key="protocol" :value="protocol" :label="protocol.toUpperCase()"/></el-select></el-form-item>
          <el-form-item label="网络类型"><el-select v-model="filters.webType" aria-label="筛选网络类型"><el-option label="全部" :value="0"/><el-option label="静态" :value="2"/><el-option label="动态" :value="1"/></el-select></el-form-item>
          <el-form-item label="最近检测"><el-select v-model="filters.connectionStatus" aria-label="筛选检测状态"><el-option label="全部" value="all"/><el-option label="检测成功" value="connected"/><el-option label="检测失败" value="failed"/><el-option label="未检测" value="unchecked"/></el-select></el-form-item>
          <el-form-item label="加速配置"><el-select v-model="filters.accelerationStatus" aria-label="筛选加速状态"><el-option label="全部" value="all"/><el-option v-for="(label, value) in accelerationLabels" :key="value" :label="label" :value="value"/></el-select></el-form-item>
          <el-form-item label="回收状态"><el-select v-model="filters.recycled" aria-label="筛选回收状态" :empty-values="[null, undefined]"><el-option label="全部" value=""/><el-option label="未回收" value="false"/><el-option label="已回收" value="true"/></el-select></el-form-item>
          <div class="filter-actions"><el-button type="primary" :loading="loading" @click="search">查询</el-button><el-button @click="reset">重置</el-button></div>
        </div>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <div class="list-note"><span>检测结果是最近一次记录；绑定数量不表示当前使用线路。客户端会同步绑定变更，新增候选保持当前线路，移除正在使用的节点可能重连。</span></div>
      <div class="batch-toolbar"><span class="muted">已选 {{selection.length}} 个代理（当前页，最多 100 个）</span><el-button :disabled="!selection.length || loading" @click="bind(selection)">批量绑定中继</el-button><el-button type="primary" plain :disabled="!selection.length || loading" @click="bind(selection, true)">自动分配中继</el-button></div>
      <div v-if="loadError" class="request-error" role="alert"><span>{{loadError}}</span><el-button type="primary" link @click="load">重试</el-button></div>
      <el-table :data="items" v-loading="loading" row-key="id" class="proxy-table" :empty-text="loading ? '正在加载客户代理…' : loadError ? '数据加载失败，请重试' : '暂无符合筛选条件的客户代理'" @selection-change="selection = $event">
        <el-table-column type="selection" width="45"/>
        <el-table-column label="出口代理" min-width="235"><template #default="{row}">
          <el-button link type="primary" class="proxy-name" @click="inspect(row)">{{row.name || endpoint(row.host, row.port)}}</el-button>
          <div class="address-line"><el-button link type="primary" class="mono" @click="inspect(row)">{{endpoint(row.host, row.port)}}</el-button><el-button link :icon="CopyDocument" :aria-label="`复制 ${endpoint(row.host, row.port)}`" @click="copyEndpoint(row.host, row.port)"/></div>
          <div class="row-tags"><el-tag size="small" type="info" effect="plain">{{row.protocol.toUpperCase()}}</el-tag><el-tag size="small" type="info" effect="plain">{{networkType(row)}}</el-tag><el-tag v-if="row.recycled" size="small" type="warning">已回收</el-tag></div>
          <div class="muted identifier">ID {{row.id}}</div>
        </template></el-table-column>
        <el-table-column label="归属与填报信息" min-width="210"><template #default="{row}">
          <div class="cell-title">{{row.tenantName || row.tenantId}}</div><div class="muted identifier">企业 ID {{row.tenantId}}</div>
          <dl class="cell-facts"><dt>创建者</dt><dd>{{row.creatorName || row.createdBy || '—'}}</dd><dt>地区</dt><dd>{{text(row.region)}}</dd><dt>供应商</dt><dd>{{text(row.provider)}}</dd></dl>
          <div class="muted">地区、供应商为客户填报</div>
        </template></el-table-column>
        <el-table-column label="最近检测" min-width="205"><template #default="{row}">
          <div class="row-tags"><el-tag size="small" :type="connectionType(row)">{{connectionLabel(row)}}</el-tag><span class="latency">{{latency(row)}}</span></div>
          <dl class="cell-facts"><dt>出口 IP</dt><dd class="mono">{{text(row.checkIp)}}</dd><dt>地区</dt><dd>{{text(row.checkCountryName)}}</dd></dl>
          <div class="muted">{{date(row.checkedAt, '尚无检测时间')}}</div>
          <el-tooltip v-if="row.checkMessage" :content="row.checkMessage" placement="top"><div class="check-message">{{row.checkMessage}}</div></el-tooltip>
        </template></el-table-column>
        <el-table-column label="绑定中继" min-width="240"><template #default="{row}">
          <div class="row-tags"><el-tag size="small" :type="accelerationTypes[row.acceleration.status] || 'info'">{{accelerationLabels[row.acceleration.status] || '暂无配置'}}</el-tag><el-button link type="primary" @click="inspect(row, 'relays')">{{row.relayCount}} 个绑定 · {{row.relayAvailableCount}} 个可用</el-button></div>
          <div v-for="candidate in row.acceleration.candidates.slice(0, 2)" :key="candidate.id" class="candidate-line"><span>{{candidate.name || endpoint(candidate.host, candidate.port)}}</span><span class="muted">{{candidate.region}}</span></div>
          <el-button link type="primary" @click="bind([row])">管理中继绑定</el-button>
          <div class="muted reason">{{accelerationReason(row)}}</div>
        </template></el-table-column>
        <el-table-column label="时间与绑定" min-width="190"><template #default="{row}">
          <dl class="time-facts"><dt>创建时间</dt><dd>{{date(row.createdAt)}}</dd><dt>到期时间</dt><dd>{{date(row.expiresAt, '未设置')}}</dd></dl>
          <el-button link type="primary" @click="inspect(row, 'bindings')">{{row.boundCount}} 个绑定店铺</el-button>
        </template></el-table-column>
        <el-table-column label="操作" width="105" fixed="right"><template #default="{row}">
          <el-dropdown trigger="click" @command="(command: string) => action(command, row)"><el-button size="small">操作<el-icon class="action-icon"><ArrowDown/></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="exit">查看详情</el-dropdown-item><el-dropdown-item command="bindings">查看绑定店铺</el-dropdown-item><el-dropdown-item command="relays">查看绑定中继</el-dropdown-item><el-dropdown-item command="bind" divided>管理中继绑定</el-dropdown-item><el-dropdown-item command="manage" :disabled="!row.acceleration.pools.length">管理线路组</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        </template></el-table-column>
      </el-table>
      <div class="table-footer"><span class="muted">绑定数包含已回收店铺，不表示正在运行。</span><el-pagination :current-page="page" :page-size="size" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @current-change="setPage" @size-change="setSize"/></div>
    </el-card>

    <el-dialog v-model="detailVisible" title="客户代理详情" width="min(1100px, 95vw)" destroy-on-close @closed="closeDetail">
      <div v-loading="detailLoading" class="detail-body">
        <div v-if="detailError" class="request-error" role="alert"><span>{{detailError}}</span><el-button type="primary" link @click="loadDetail">重新加载详情</el-button></div>
        <template v-if="selected">
          <div class="detail-heading"><div><h2>{{selected.name || endpoint(selected.host, selected.port)}}</h2><p class="mono">{{endpoint(selected.host, selected.port)}} <el-button link :icon="CopyDocument" aria-label="复制出口地址与端口" @click="copyEndpoint(selected.host, selected.port)"/></p></div><el-tag :type="selected.recycled ? 'warning' : 'info'">{{selected.recycled ? '已回收' : '未回收'}}</el-tag></div>
          <el-tabs v-model="detailTab">
            <el-tab-pane label="出口信息" name="exit">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="代理 ID">{{selected.id}}</el-descriptions-item><el-descriptions-item label="协议 / 类型">{{selected.protocol.toUpperCase()}} / {{networkType(selected)}}</el-descriptions-item>
                <el-descriptions-item label="所属企业">{{selected.tenantName || selected.tenantId}}<div class="muted">{{selected.tenantId}}</div></el-descriptions-item><el-descriptions-item label="创建者">{{selected.creatorName || selected.createdBy || '—'}}<div v-if="selected.creatorName" class="muted">{{selected.createdBy}}</div></el-descriptions-item>
                <el-descriptions-item label="客户填报地区">{{text(selected.region)}}</el-descriptions-item><el-descriptions-item label="客户填报供应商">{{text(selected.provider)}}</el-descriptions-item>
                <el-descriptions-item label="代理账号">{{text(selected.username)}}</el-descriptions-item><el-descriptions-item label="代理密码">{{selected.hasPassword ? '已配置' : '未配置'}}</el-descriptions-item>
                <el-descriptions-item label="映射出口">{{selected.mapIp ? endpoint(selected.mapIp, selected.mapPort) : '未设置'}}</el-descriptions-item><el-descriptions-item label="到期时间">{{date(selected.expiresAt, '未设置')}}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{date(selected.createdAt)}}</el-descriptions-item><el-descriptions-item label="绑定店铺">{{selected.boundCount}} 个（含已回收）</el-descriptions-item>
              </el-descriptions>
              <h3>最近检测记录</h3>
              <el-descriptions :column="2" border><el-descriptions-item label="检测结果"><el-tag size="small" :type="connectionType(selected)">{{connectionLabel(selected)}}</el-tag></el-descriptions-item><el-descriptions-item label="耗时">{{latency(selected)}}</el-descriptions-item><el-descriptions-item label="检测出口 IP">{{text(selected.checkIp)}}</el-descriptions-item><el-descriptions-item label="检测地区">{{text(selected.checkCountryName)}}</el-descriptions-item><el-descriptions-item label="检测时间">{{date(selected.checkedAt, '尚无检测记录')}}</el-descriptions-item><el-descriptions-item label="检测说明">{{text(selected.checkMessage)}}</el-descriptions-item></el-descriptions>
              <p class="muted detail-note">检测记录不代表当前连通状态；此页面不展示代理密码。</p>
            </el-tab-pane>
            <el-tab-pane label="绑定中继" name="relays">
              <el-alert title="这里只列出已绑定中继及配置状态，不代表当前使用线路。客户端会同步变更：新增候选保持当前线路，解绑或停用正在使用的节点可能重连；失效绑定保留显示以便解除。" type="info" show-icon :closable="false" class="section-alert"/>
              <div class="pool-summary"><el-tag :type="accelerationTypes[selected.acceleration.status] || 'info'">{{accelerationLabels[selected.acceleration.status] || '暂无配置'}}</el-tag><span>{{accelerationReason(selected)}}</span></div>
              <div class="pool-links"><el-button type="primary" @click="bind([selected])">管理中继绑定</el-button><el-button @click="bind([selected], true)">自动分配</el-button><el-button :loading="relayLoading" @click="loadRelays">刷新绑定</el-button></div>
              <div v-if="relayError" class="request-error" role="alert">{{relayError}}<el-button link type="primary" @click="loadRelays">重新加载绑定</el-button></div>
              <el-table :data="relaySnapshot?.items || []" v-loading="relayLoading" row-key="id" :empty-text="relayError ? '绑定加载失败，请重试' : '尚未绑定中继，将直接连接客户出口代理'" border><el-table-column type="index" label="顺序" width="65"/><el-table-column prop="name" label="节点名称" min-width="145"/><el-table-column label="所属分组" min-width="145"><template #default="{row}"><el-button link type="primary" @click="managePool(row.poolId)">{{row.poolName}}</el-button></template></el-table-column><el-table-column label="配置状态" min-width="150"><template #default="{row}"><el-tag :type="row.available ? 'success' : 'warning'">{{relayAvailability(row)}}</el-tag></template></el-table-column><el-table-column label="节点地址" min-width="205"><template #default="{row}"><div class="address-line"><span class="mono">{{endpoint(row.host, row.port)}}</span><el-button link :icon="CopyDocument" aria-label="复制节点地址与端口" @click="copyEndpoint(row.host, row.port)"/></div><div class="muted">{{row.protocol.toUpperCase()}} · {{row.region || '未填写地区'}}</div></template></el-table-column></el-table>
            </el-tab-pane>
            <el-tab-pane label="绑定店铺" name="bindings">
              <p class="muted binding-note">包含已回收店铺。“最近打开”是历史记录，不代表店铺当前正在运行。</p>
              <el-table :data="selected.bindings" row-key="id" empty-text="该代理暂无绑定店铺" border><el-table-column label="店铺" min-width="190"><template #default="{row}"><div>{{row.name || row.id}}</div><div class="muted identifier">{{row.id}}</div></template></el-table-column><el-table-column prop="platform" label="平台" min-width="120"/><el-table-column label="所属成员" min-width="140"><template #default="{row}">{{row.ownerName || row.ownerId || '—'}}</template></el-table-column><el-table-column label="状态" width="100"><template #default="{row}"><el-tag size="small" :type="row.recycled ? 'warning' : 'info'">{{row.recycled ? '已回收' : '正常'}}</el-tag></template></el-table-column><el-table-column label="最近打开" min-width="175"><template #default="{row}">{{date(row.lastOpenedAt, '无记录')}}</template></el-table-column><el-table-column label="创建时间" min-width="175"><template #default="{row}">{{date(row.createdAt)}}</template></el-table-column></el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>
    <ProxyRelayBindingDialog v-model="bindingVisible" :targets="bindingTargets" :allocate="allocate" @saved="bindingsSaved"/>
  </section>
</template>

<style scoped lang="scss">
.filter-card { margin-bottom: 16px; }
.batch-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.batch-toolbar .el-button + .el-button { margin-left: 0; }
.filter-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0 18px; }
.filter-grid .el-form-item { margin-bottom: 14px; }
.filter-grid .el-select { width: 100%; }
.filter-actions { display: flex; align-items: flex-end; padding-bottom: 14px; }
.field-error { color: var(--el-color-danger); font-size: 12px; line-height: 1.5; padding-top: 4px; }
.list-note { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; color: #7b8494; font-size: 12px; line-height: 1.7; }
.request-error { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 16px; margin-bottom: 16px; border-radius: 5px; background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.mono { font-family: Consolas, monospace; overflow-wrap: anywhere; }
.address-line { display: flex; align-items: center; gap: 5px; font-size: 13px; margin: 4px 0; }
.address-line .el-button { padding: 3px; flex-shrink: 0; }
.proxy-name { font-size: 14px; font-weight: 600; white-space: normal; text-align: left; line-height: 1.5; }
.identifier { overflow-wrap: anywhere; font-size: 11px; }
.row-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-bottom: 7px; }
.cell-title { color: #354057; font-weight: 500; }
.cell-facts { display: grid; grid-template-columns: 48px minmax(0, 1fr); gap: 3px 7px; margin: 8px 0; font-size: 12px; line-height: 1.6; }
.cell-facts dt, .time-facts dt { color: #8b95a5; }
.cell-facts dd, .time-facts dd { margin: 0; overflow-wrap: anywhere; }
.time-facts { font-size: 12px; margin: 0 0 8px; line-height: 1.6; }
.time-facts dd { margin-bottom: 5px; }
.latency { font-size: 12px; white-space: nowrap; }
.check-message { color: #8b95a5; max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.candidate-line { display: flex; flex-wrap: wrap; column-gap: 6px; margin: 3px 0; font-size: 12px; }
.reason { margin-top: 6px; }
.action-icon { margin-left: 5px; }
.detail-body { min-height: 180px; }
.detail-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.detail-heading h2 { font-size: 18px; margin: 0 0 4px; }
.detail-heading p { margin: 0 0 14px; color: #7b8494; }
.detail-body h3 { font-size: 15px; margin: 22px 0 12px; }
.detail-body :deep(.el-descriptions__content) { overflow-wrap: anywhere; }
.pool-summary, .pool-links { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 16px; }
.pool-summary { font-size: 13px; color: #7b8494; }
.binding-note { margin: 0 0 14px; }
.detail-note { margin: 14px 0 0; }
@media (max-width: 1050px) { .filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 580px) { .filter-grid { grid-template-columns: 1fr; } .list-note { align-items: flex-start; } }
</style>
