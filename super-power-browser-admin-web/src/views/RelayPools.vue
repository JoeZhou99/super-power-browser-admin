<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Refresh, Search } from '@element-plus/icons-vue';
import { ApiError, errorMessage, formatDate } from '../api';
import { relayApi, relaySsMethods, type NodeInput, type RelayGrant, type RelayNode, type RelayPool, type RelayTenant } from '../relayApi';
import RelayNodeBindingsDialog from '../components/RelayNodeBindingsDialog.vue';

const route = useRoute();
const pools = ref<RelayPool[]>([]), loading = ref(false), loadError = ref(''), query = ref('');
const selectedPool = ref<RelayPool>(), drawer = ref(false), tab = ref('nodes');
const nodes = ref<RelayNode[]>([]), grants = ref<RelayGrant[]>([]), detailLoading = ref(false), detailError = ref('');
const bindingsVisible = ref(false), bindingNode = ref<RelayNode>();
function showBindings(node: RelayNode) { bindingNode.value = node; bindingsVisible.value = true; }
const saving = ref(false), grantOriginal = ref(''), grantRevision = ref(0);
const grantDirty = computed(() => JSON.stringify(grants.value) !== grantOriginal.value);
const filtered = computed(() => pools.value.filter(pool => [pool.id, pool.name, pool.code, pool.remark].some(value => (value || '').toLowerCase().includes(query.value.trim().toLowerCase()))));
let listVersion = 0, detailVersion = 0, tenantVersion = 0, disposed = false;
let poolsLoaded = false, handledRoute = '';
let pendingNodeAction: { poolId: string; nodeId: string; add: boolean } | undefined;
function openRouteSelection() {
  const poolId = typeof route.query.poolId === 'string' ? route.query.poolId : '';
  if (!poolId) return;
  const nodeId = typeof route.query.nodeId === 'string' ? route.query.nodeId : '';
  const add = route.query.addNode === '1';
  const signature = JSON.stringify([poolId, nodeId, add]);
  if (handledRoute === signature) return;
  if (saving.value || (drawer.value && grantDirty.value) || nodeDialog.value) {
    loadError.value = '当前分组有未完成的编辑，请先保存或关闭，再刷新以查看指定分组。'; return;
  }
  const pool = pools.value.find(item => item.id === poolId);
  if (!pool) { loadError.value = '指定的中继分组不存在或当前不可访问。'; return; }
  handledRoute = signature;
  pendingNodeAction = nodeId || add ? { poolId, nodeId, add } : undefined;
  manage(pool);
}
function failure(error: unknown) {
  return error instanceof ApiError && error.status === 409
    ? '配置已被其他管理员修改。当前内容已保留，请关闭后重新载入最新配置再修改。'
    : errorMessage(error);
}
async function loadPools() {
  const version = ++listVersion;
  loading.value = true; loadError.value = '';
  try {
    const result = await relayApi.pools();
    if (disposed || version !== listVersion) return;
    pools.value = result.items;
    if (selectedPool.value) selectedPool.value = pools.value.find(pool => pool.id === selectedPool.value?.id);
    poolsLoaded = true; openRouteSelection();
  } catch (error) { if (!disposed && version === listVersion) loadError.value = failure(error); }
  finally { if (!disposed && version === listVersion) loading.value = false; }
}
async function loadDetails() {
  const id = selectedPool.value?.id;
  if (!id) return;
  const version = ++detailVersion;
  detailLoading.value = true; detailError.value = '';
  const results = await Promise.allSettled([relayApi.nodes(id), relayApi.grants(id)]);
  if (disposed || version !== detailVersion || selectedPool.value?.id !== id) return;
  const [nodeResult, grantResult] = results;
  if (nodeResult.status === 'fulfilled') nodes.value = nodeResult.value.items.sort((a, b) => a.sortOrder - b.sortOrder);
  if (grantResult.status === 'fulfilled') {
    grants.value = grantResult.value.items;
    grantOriginal.value = JSON.stringify(grants.value);
    grantRevision.value = grantResult.value.revision;
  }
  const rejected = results.find(result => result.status === 'rejected');
  if (rejected?.status === 'rejected') detailError.value = failure(rejected.reason);
  detailLoading.value = false;
  if (pendingNodeAction?.poolId === id && nodeResult.status === 'fulfilled') {
    const action = pendingNodeAction; pendingNodeAction = undefined;
    if (action.add) editNode();
    else {
      const node = nodes.value.find(item => item.id === action.nodeId);
      if (node) editNode(node);
      else detailError.value = '指定节点不在此分组中或已不可访问。';
    }
  }
}
function manage(pool: RelayPool) {
  selectedPool.value = pool; tab.value = 'nodes'; nodes.value = []; grants.value = []; grantOriginal.value = '[]';
  drawer.value = true; void loadDetails();
}
async function closeDetails(done: () => void) {
  if (saving.value) return;
  if (grantDirty.value) {
    try { await ElMessageBox.confirm('企业授权有未保存的修改，关闭后将丢弃。', '未保存的授权', { confirmButtonText: '丢弃并关闭', cancelButtonText: '继续编辑', type: 'warning' }); }
    catch { return; }
  }
  detailVersion++; tenantVersion++; tenants.value = []; tenantId.value = ''; done();
}
const poolDialog = ref(false), poolError = ref(''), editingPool = ref<RelayPool>();
const poolForm = reactive({ name: '', code: '', remark: '', enabled: true, candidateLimit: 4 });
function editPool(pool?: RelayPool) {
  editingPool.value = pool; poolError.value = '';
  Object.assign(poolForm, { name: pool?.name || '', code: pool?.code || '', remark: pool?.remark || '', enabled: pool?.enabled ?? true, candidateLimit: pool?.candidateLimit || 4 });
  poolDialog.value = true;
}
async function savePool() {
  if (saving.value) return;
  poolError.value = '';
  if (!poolForm.name.trim()) { poolError.value = '请填写分组名称'; return; }
  if (poolForm.code.trim() && !/^[a-zA-Z0-9_-]{1,64}$/.test(poolForm.code.trim())) { poolError.value = '分组标识仅支持字母、数字、下划线、短横线，最长 64 位'; return; }
  saving.value = true;
  try {
    const input = { name: poolForm.name.trim(), remark: poolForm.remark.trim(), enabled: poolForm.enabled, candidateLimit: poolForm.candidateLimit, ...(poolForm.code.trim() ? { code: poolForm.code.trim() } : {}) };
    if (editingPool.value) await relayApi.updatePool(editingPool.value.id, { ...input, revision: editingPool.value.revision });
    else await relayApi.createPool(input);
    poolDialog.value = false; await loadPools(); ElMessage.success('中继分组已保存');
  } catch (error) { poolError.value = failure(error); }
  finally { saving.value = false; }
}
const nodeDialog = ref(false), nodeError = ref(''), editingNode = ref<RelayNode>();
const nodeForm = reactive({ name: '', region: '', protocol: 'socks5' as RelayNode['protocol'], host: '', port: 1080,
  username: '', password: '', enabled: true, sortOrder: 0, encryption: '', serverName: '', allowInsecure: false });
function editNode(node?: RelayNode) {
  editingNode.value = node; nodeError.value = '';
  Object.assign(nodeForm, { name: node?.name || '', region: node?.region || '', protocol: node?.protocol || 'socks5',
    host: node?.host || '', port: node?.port || 1080, username: node?.username || '', password: '', enabled: node?.enabled ?? true,
    sortOrder: node?.sortOrder ?? nodes.value.length, encryption: node?.proxyOptions?.encryption || '', serverName: node?.proxyOptions?.tls?.serverName || '', allowInsecure: node?.proxyOptions?.tls?.allowInsecure ?? false });
  nodeDialog.value = true;
}
function resetPassword() { nodeForm.password = ''; }
function nodeInput(): NodeInput {
  if (!nodeForm.name.trim() || !nodeForm.host.trim()) throw new Error('请填写节点名称和地址');
  if (nodeForm.protocol !== 'ss' && !nodeForm.username.trim()) throw new Error('请填写节点认证账号');
  if (nodeForm.protocol === 'ss' && !relaySsMethods.some(method => method === nodeForm.encryption)) throw new Error('请选择节点实际使用的 Shadowsocks 加密方式');
  if (!editingNode.value && !nodeForm.password) throw new Error('请填写节点认证密码');
  if (editingNode.value && editingNode.value.protocol !== 'socks5' && nodeForm.protocol === 'socks5' && !nodeForm.password) throw new Error('改为 SOCKS5 时请重新填写认证密码');
  if (!Number.isInteger(nodeForm.port) || nodeForm.port < 1 || nodeForm.port > 65535) throw new Error('端口范围为 1 至 65535');
  const host = nodeForm.host.trim().replace(/^\[|\]$/g, '');
  if (/[\s/@?#\\]/.test(host) || host.includes('://')) throw new Error('节点地址只填写 IP 或域名，端口单独填写');
  return {
    name: nodeForm.name.trim(), region: nodeForm.region.trim(), protocol: nodeForm.protocol, host, port: nodeForm.port,
    username: nodeForm.protocol === 'ss' ? '' : nodeForm.username, ...(nodeForm.password ? { password: nodeForm.password } : {}), enabled: nodeForm.enabled,
    sortOrder: nodeForm.sortOrder, ...(editingNode.value ? { revision: editingNode.value.revision } : {}),
    proxyOptions: nodeForm.protocol === 'ss' ? { encryption: nodeForm.encryption } : nodeForm.protocol === 'https' ? { tls: { serverName: nodeForm.serverName.trim(), allowInsecure: nodeForm.allowInsecure } } : {},
  };
}
async function saveNode() {
  const pool = selectedPool.value;
  if (!pool || saving.value) return;
  nodeError.value = '';
  let input: NodeInput;
  try { input = nodeInput(); } catch (error) { nodeError.value = failure(error); return; }
  saving.value = true;
  try {
    if (editingNode.value) await relayApi.updateNode(pool.id, editingNode.value.id, input);
    else await relayApi.createNode(pool.id, input);
    nodeDialog.value = false; resetPassword();
    // Keep the grant draft and its original revision; concurrent changes must still conflict.
    const result = await relayApi.nodes(pool.id);
    nodes.value = result.items.sort((a, b) => a.sortOrder - b.sortOrder);
    await loadPools();
    if (!grantDirty.value) {
      const snapshot = await relayApi.grants(pool.id);
      grants.value = snapshot.items; grantOriginal.value = JSON.stringify(snapshot.items); grantRevision.value = snapshot.revision;
    }
    ElMessage.success('节点已保存');
  } catch (error) { nodeError.value = failure(error); if (!nodeDialog.value) detailError.value = nodeError.value; }
  finally { saving.value = false; }
}
const tenants = ref<RelayTenant[]>([]), tenantId = ref(''), tenantLoading = ref(false), tenantError = ref('');
async function searchTenants(value: string) {
  const version = ++tenantVersion;
  tenantLoading.value = true; tenantError.value = '';
  try {
    const result = await relayApi.tenants(value.trim());
    if (!disposed && version === tenantVersion) tenants.value = result.items;
  } catch (error) { if (!disposed && version === tenantVersion) tenantError.value = failure(error); }
  finally { if (!disposed && version === tenantVersion) tenantLoading.value = false; }
}
function addGrant() {
  const tenant = tenants.value.find(item => item.id === tenantId.value);
  if (!tenant) return;
  if (grants.value.some(grant => grant.tenantId === tenant.id)) { ElMessage.info('该企业已在授权列表中'); return; }
  grants.value.push({ tenantId: tenant.id, tenantName: tenant.name, enabled: true, expiresAt: null });
  tenantId.value = '';
}
function showTenantSearch(visible: boolean) { if (visible) void searchTenants(''); }
async function saveGrants() {
  if (!selectedPool.value || saving.value || !grantDirty.value) return;
  saving.value = true; detailError.value = '';
  try {
    const pool = await relayApi.saveGrants(selectedPool.value.id, grantRevision.value, grants.value);
    selectedPool.value = pool; grantRevision.value = pool.revision; grantOriginal.value = JSON.stringify(grants.value);
    await loadPools(); ElMessage.success('企业授权已保存');
  } catch (error) { detailError.value = failure(error); }
  finally { saving.value = false; }
}
async function reloadDetails() {
  if (grantDirty.value) {
    try { await ElMessageBox.confirm('重新载入会丢弃尚未保存的企业授权修改。', '重新载入', { confirmButtonText: '丢弃并载入', cancelButtonText: '保留修改', type: 'warning' }); }
    catch { return; }
  }
  await loadPools(); await loadDetails();
}
watch(() => [route.query.poolId, route.query.nodeId, route.query.addNode], () => { handledRoute = ''; if (poolsLoaded && !loading.value) openRouteSelection(); });
onMounted(loadPools);
onBeforeUnmount(() => { disposed = true; listVersion++; detailVersion++; tenantVersion++; resetPassword(); });
</script>

<template>
  <section class="page-container">
    <div class="page-heading"><div><h1>中继分组</h1><p>按组维护节点与企业授权，再为客户代理绑定具体节点或自动分配。客户端启动优先上次成功线路，无记录时先直连代理。</p></div><el-button type="primary" :icon="Plus" :disabled="saving" @click="editPool()">新增分组</el-button></div>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon class="section-alert"/>
    <el-card shadow="never">
      <div class="table-toolbar"><div class="filters"><el-input v-model="query" clearable :prefix-icon="Search" placeholder="搜索 ID / 标识 / 名称 / 备注" aria-label="搜索中继分组"/></div><el-button :icon="Refresh" :loading="loading" :disabled="saving" @click="loadPools">刷新</el-button></div>
      <el-table :data="filtered" v-loading="loading" row-key="id" stripe empty-text="暂无中继分组，请新增分组并配置节点与企业授权">
        <el-table-column label="分组" min-width="205"><template #default="{row}"><strong>{{row.name}}</strong><div class="muted">ID {{row.id}}</div></template></el-table-column>
        <el-table-column prop="code" label="标识" min-width="135"/>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip/>
        <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.enabled ? 'success' : 'info'">{{row.enabled ? '启用' : '停用'}}</el-tag></template></el-table-column>
        <el-table-column prop="nodeCount" label="节点数" width="100"/>
        <el-table-column prop="grantCount" label="授权企业数" width="115"/>
        <el-table-column label="更新时间" min-width="185"><template #default="{row}">{{formatDate(row.updatedAt)}}</template></el-table-column>
        <el-table-column label="操作" width="175" fixed="right"><template #default="{row}"><el-button link type="primary" :disabled="saving" @click="manage(row)">节点与授权</el-button><el-button link type="primary" :disabled="saving" @click="editPool(row)">编辑</el-button></template></el-table-column>
      </el-table>
      <div class="table-footer"><span class="muted">配置的启用状态不代表实测网络状态。连接耗时由客户端从用户电脑检测。</span><span class="muted">共 {{filtered.length}} 个分组</span></div>
    </el-card>

    <el-dialog v-model="poolDialog" :title="editingPool ? '编辑中继分组' : '新增中继分组'" width="min(560px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <el-alert v-if="poolError" :title="poolError" type="error" :closable="false" class="section-alert"/>
      <el-form label-position="top" :disabled="saving" @submit.prevent="savePool">
        <el-form-item label="分组名称" required><el-input v-model="poolForm.name" maxlength="100" placeholder="例如：香港加速线路"/></el-form-item>
        <el-form-item label="分组标识"><el-input v-model="poolForm.code" maxlength="64" placeholder="例如：hong-kong；新建留空自动生成"/><div class="field-help">唯一标识，支持字母、数字、下划线、短横线；编辑留空保留已有标识。</div></el-form-item>
        <el-form-item label="备注"><el-input v-model="poolForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="记录用途或维护说明"/></el-form-item>
        <el-form-item label="状态"><el-switch v-model="poolForm.enabled" active-text="启用" inactive-text="停用"/></el-form-item>
        <div class="field-help">停用后不再提供新连接配置。正在运行的店铺按现有连接继续工作，不会改用本机出口。</div>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="poolDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="savePool">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="drawer" :title="selectedPool?.name || '中继分组'" size="min(1080px, 96vw)" :before-close="closeDetails" destroy-on-close>
      <template v-if="selectedPool">
        <div class="detail-toolbar"><el-tag :type="selectedPool.enabled ? 'success' : 'info'">{{selectedPool.enabled ? '启用' : '停用'}}</el-tag><span class="muted">配置版本 {{selectedPool.revision}}</span><el-button :icon="Refresh" :disabled="saving" :loading="detailLoading" @click="reloadDetails">重新载入</el-button></div>
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon class="section-alert"/>
        <el-tabs v-model="tab" v-loading="detailLoading">
          <el-tab-pane label="加速节点" name="nodes">
            <div class="table-toolbar"><span class="muted">暂时不用的节点可编辑为停用。绑定代理数为配置数量，不代表实时负载。</span><el-button type="primary" :icon="Plus" :disabled="saving || detailLoading" @click="editNode()">新增节点</el-button></div>
            <el-table :data="nodes" row-key="id" empty-text="暂无加速节点，请添加已部署并启用认证的代理服务">
              <el-table-column prop="sortOrder" label="排序" width="65"/>
              <el-table-column label="节点" min-width="170"><template #default="{row}">{{row.name}}<div class="muted">{{row.region || '未填写地区'}}</div></template></el-table-column>
              <el-table-column label="连接地址" min-width="200"><template #default="{row}">{{row.host}}:{{row.port}}<div class="muted">{{row.protocol.toUpperCase()}}</div><div v-if="row.protocol === 'ss'" class="muted">{{row.proxyOptions?.encryption || '未设置加密方式'}}</div></template></el-table-column>
              <el-table-column label="认证" min-width="130"><template #default="{row}">{{row.protocol === 'ss' ? '无需账号' : row.username}}<div class="muted">{{row.hasPassword ? '已设置密码' : '未设置密码'}}</div></template></el-table-column>
              <el-table-column label="状态" width="95"><template #default="{row}"><el-tag :type="row.enabled ? 'success' : 'info'">{{row.enabled ? '启用' : '停用'}}</el-tag></template></el-table-column>
              <el-table-column label="绑定代理数" width="115"><template #default="{row}"><el-button link type="primary" :aria-label="`查看 ${row.name} 绑定代理`" @click="showBindings(row)">{{row.boundProxyCount}}</el-button></template></el-table-column>
              <el-table-column label="操作" width="75"><template #default="{row}"><el-button link type="primary" :disabled="saving || detailLoading" @click="editNode(row)">编辑</el-button></template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="企业授权" name="grants">
            <p class="muted">保存授权后，可在客户代理页面为该企业的代理绑定本组具体节点，也可批量自动分配。授权本身不会新增代理绑定。客户端会同步节点与授权变更，新增候选保持当前线路；停用或撤销正在使用的节点可能触发重新连接，出口仍为客户绑定的代理。</p>
            <div class="grant-toolbar"><el-select v-model="tenantId" filterable remote clearable :remote-method="searchTenants" :loading="tenantLoading" :disabled="saving || detailLoading" placeholder="搜索企业名称或 ID" aria-label="搜索授权企业" @visible-change="showTenantSearch"><el-option v-for="tenant in tenants" :key="tenant.id" :value="tenant.id" :label="`${tenant.name} · ${tenant.id}`"/></el-select><el-button :disabled="!tenantId || saving || detailLoading" @click="addGrant">添加授权</el-button></div>
            <el-alert v-if="tenantError" :title="tenantError" type="error" :closable="false" class="section-alert"/>
            <el-table :data="grants" row-key="tenantId" empty-text="尚未授权任何企业，客户代理暂不能绑定本组节点">
              <el-table-column label="企业" min-width="200"><template #default="{row}">{{row.tenantName || row.tenantId}}<div class="muted">{{row.tenantId}}</div></template></el-table-column>
              <el-table-column label="授权状态" width="120"><template #default="{row}"><el-switch v-model="row.enabled" :disabled="saving || detailLoading" :aria-label="`${row.tenantName || row.tenantId} 授权状态`"/></template></el-table-column>
              <el-table-column label="到期时间" min-width="240"><template #default="{row}"><el-date-picker v-model="row.expiresAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" clearable placeholder="不设到期时间" :disabled="saving || detailLoading"/></template></el-table-column>
              <el-table-column label="操作" width="75"><template #default="{row}"><el-button link type="danger" :disabled="saving || detailLoading" @click="grants = grants.filter(item => item.tenantId !== row.tenantId)">移除</el-button></template></el-table-column>
            </el-table>
            <div class="grant-footer"><span class="muted">{{grantDirty ? '有未保存的授权修改' : '授权已同步'}}</span><el-button type="primary" :loading="saving" :disabled="!grantDirty || detailLoading || Boolean(detailError)" @click="saveGrants">保存企业授权</el-button></div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <el-dialog v-model="nodeDialog" :title="editingNode ? '编辑加速节点' : '新增加速节点'" width="min(700px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @closed="resetPassword">
      <p class="muted">所属中继分组：{{selectedPool?.name}}（{{selectedPool?.code || selectedPool?.id}}）</p>
      <el-alert v-if="nodeError" :title="nodeError" type="error" :closable="false" class="section-alert"/>
      <el-form label-position="top" :disabled="saving" class="node-form" @submit.prevent="saveNode">
        <el-form-item label="节点名称" required><el-input v-model="nodeForm.name" maxlength="100" placeholder="例如：香港线路 A"/></el-form-item>
        <el-form-item label="地区"><el-input v-model="nodeForm.region" maxlength="100" placeholder="例如：香港"/></el-form-item>
        <el-form-item label="协议" required><el-select v-model="nodeForm.protocol"><el-option label="SOCKS5" value="socks5"/><el-option label="HTTP" value="http"/><el-option label="HTTPS" value="https"/><el-option label="Shadowsocks (SS)" value="ss"/></el-select><div class="field-help">须与服务器部署的协议一致；端口可达不代表代理协议正确。</div></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="nodeForm.sortOrder" :min="0" :max="100000" :precision="0"/></el-form-item>
        <el-form-item label="节点地址" required><el-input v-model="nodeForm.host" maxlength="255" placeholder="IP 或域名"/></el-form-item>
        <el-form-item label="节点端口" required><el-input-number v-model="nodeForm.port" :min="1" :max="65535" :precision="0"/></el-form-item>
        <el-form-item v-if="nodeForm.protocol !== 'ss'" label="认证账号" required><el-input v-model="nodeForm.username" autocomplete="off" maxlength="255"/></el-form-item>
        <el-form-item v-else label="加密方式" required><el-select v-model="nodeForm.encryption" placeholder="选择节点实际使用的加密方式" aria-label="Shadowsocks 加密方式"><el-option v-for="method in relaySsMethods" :key="method" :value="method" :label="method"/></el-select><div class="field-help">Shadowsocks 使用密码与加密方式连接，无需认证账号。</div></el-form-item>
        <el-form-item label="认证密码" :required="!editingNode"><el-input v-model="nodeForm.password" type="password" show-password autocomplete="new-password" :placeholder="editingNode ? '留空保留原密码' : '填写已部署节点的认证密码'"/></el-form-item>
        <template v-if="nodeForm.protocol === 'https'"><el-form-item label="TLS 服务器名称"><el-input v-model="nodeForm.serverName" placeholder="证书对应的域名，留空使用节点地址"/></el-form-item><el-form-item label="证书校验"><el-checkbox v-model="nodeForm.allowInsecure">允许未受信任证书</el-checkbox></el-form-item></template>
        <el-form-item label="节点状态"><el-switch v-model="nodeForm.enabled" active-text="启用" inactive-text="停用"/></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="nodeDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveNode">保存节点</el-button></template>
    </el-dialog>
    <RelayNodeBindingsDialog v-model="bindingsVisible" :node="bindingNode"/>
  </section>
</template>

<style scoped>
.detail-toolbar{display:flex;align-items:center;gap:14px;margin-bottom:20px}.detail-toolbar .el-button{margin-left:auto}.grant-toolbar{display:flex;gap:12px;margin:18px 0}.grant-toolbar .el-select{flex:1;max-width:500px}.grant-footer{display:flex;justify-content:space-between;align-items:center;margin-top:20px}.node-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 22px}.node-form :deep(.el-select),.node-form :deep(.el-input-number){width:100%}@media(max-width:600px){.page-heading{align-items:flex-start}.node-form{grid-template-columns:1fr}.detail-toolbar{flex-wrap:wrap}}
</style>
