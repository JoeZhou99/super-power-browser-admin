<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ApiError, errorMessage } from '../api';
import { customerProxyApi, relayAvailability, type BoundRelayNode, type CustomerProxy, type ProxyRelaySnapshot } from '../customerProxyApi';

const props = defineProps<{ modelValue: boolean; targets: CustomerProxy[]; allocate?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>();
const loading = ref(false), saving = ref(false), error = ref(''), uncertain = ref(false);
const snapshots = ref<Record<string, ProxyRelaySnapshot>>({}), chosen = ref<string[]>([]);
const mode = ref<'append' | 'replace'>('append'), count = ref(2), query = ref('');
let controller: AbortController | undefined;
const single = computed(() => props.targets.length === 1 && !props.allocate);
const title = computed(() => props.allocate ? '自动分配中继' : single.value ? '管理中继绑定' : '批量绑定中继');
const current = computed(() => snapshots.value[props.targets[0]?.id]);
const available = computed(() => {
  if (!props.targets.length || props.targets.some(proxy => !snapshots.value[proxy.id])) return [];
  return current.value.available.filter(node => props.targets.every(proxy => snapshots.value[proxy.id].available.some(item => item.id === node.id)));
});
const options = computed(() => {
  const result = [...available.value];
  if (single.value) for (const node of current.value?.items || []) if (!result.some(item => item.id === node.id)) result.push(node);
  return result;
});
const filtered = computed(() => options.value.filter(node => [node.name, node.host, node.poolName, node.region].some(value => (value || '').toLowerCase().includes(query.value.trim().toLowerCase()))));
const hasUnavailableSelection = computed(() => chosen.value.some(id => !available.value.some(node => node.id === id)));
const ready = computed(() => props.targets.length > 0 && props.targets.length <= 100 && props.targets.every(proxy => snapshots.value[proxy.id]) && !loading.value);
const eligible = (proxy: CustomerProxy) => proxy.webType === 2 && !['ssh', 'ssr', 'socks4', 'socks4a'].includes(proxy.protocol) && !proxy.recycled && (!proxy.expiresAt || Date.parse(proxy.expiresAt) > Date.now());
const ineligible = computed(() => props.targets.filter(proxy => !eligible(proxy)));
const allocationInsufficient = computed(() => props.allocate && ready.value && props.targets.some(proxy => snapshots.value[proxy.id].available.length < count.value));
const appendIssue = computed(() => {
  if (single.value || props.allocate || mode.value !== 'append' || !ready.value) return '';
  if (props.targets.some(proxy => snapshots.value[proxy.id].items.some(node => !node.available))) return '有代理保留了不可用绑定，请改用替换全部绑定，或分别移除不可用项后追加。';
  if (props.targets.some(proxy => new Set([...snapshots.value[proxy.id].items.map(node => node.id), ...chosen.value]).size > 8)) return '追加后有代理将超过 8 个绑定，请减少选择或改用替换全部绑定。';
  return '';
});
const selectionText = computed(() => props.targets.slice(0, 4).map(proxy => proxy.name || proxy.host).join('、') + (props.targets.length > 4 ? ` 等 ${props.targets.length} 个代理` : ''));
const disabled = computed(() => !ready.value || saving.value || uncertain.value ||
  (props.allocate ? ineligible.value.length > 0 || allocationInsufficient.value || !Number.isInteger(count.value) || count.value < 1 || count.value > 4
    : chosen.value.length > 8 || hasUnavailableSelection.value || Boolean(appendIssue.value) || (!single.value && mode.value === 'append' && !chosen.value.length) || (chosen.value.length > 0 && ineligible.value.length > 0)));

async function load() {
  controller?.abort();
  if (!props.modelValue || !props.targets.length) return;
  const request = new AbortController(); controller = request;
  const targets = [...props.targets];
  loading.value = true; error.value = ''; snapshots.value = {}; chosen.value = []; uncertain.value = false;
  let index = 0;
  const result: Record<string, ProxyRelaySnapshot> = {};
  try {
    if (targets.length > 100) throw new Error('一次最多管理 100 个代理，请减少选择');
    await Promise.all(Array.from({ length: Math.min(4, targets.length) }, async () => {
      while (index < targets.length && !request.signal.aborted) {
        const proxy = targets[index++];
        result[proxy.id] = await customerProxyApi.relays(proxy.id, request.signal);
      }
    }));
    if (request.signal.aborted) return;
    snapshots.value = result;
    if (single.value) chosen.value = result[targets[0].id].items.map(node => node.id);
  } catch (reason) {
    if (!request.signal.aborted) { error.value = errorMessage(reason); loading.value = false; request.abort(); }
  } finally { if (!request.signal.aborted) loading.value = false; }
}
function select(node: BoundRelayNode, checked: boolean) {
  if (checked && node.available && chosen.value.length < 8 && !chosen.value.includes(node.id)) chosen.value.push(node.id);
  else if (!checked) chosen.value = chosen.value.filter(id => id !== node.id);
}
function removeUnavailable() { chosen.value = chosen.value.filter(id => available.value.some(node => node.id === id)); }
function move(index: number, direction: number) {
  const next = index + direction;
  if (next < 0 || next >= chosen.value.length) return;
  const values = [...chosen.value]; [values[index], values[next]] = [values[next], values[index]]; chosen.value = values;
}
function nodeName(id: string) { return options.value.find(node => node.id === id)?.name || id; }
async function save() {
  if (disabled.value) return;
  if (!props.allocate && !chosen.value.length) {
    try { await ElMessageBox.confirm(`解除所选 ${props.targets.length} 个代理的全部中继绑定？客户端同步后仅保留直连出口代理的路径，正在使用这些中继的店铺可能重新连接。`, '解除中继绑定', { type: 'warning', confirmButtonText: '确认解除', cancelButtonText: '保留绑定' }); }
    catch { return; }
  }
  saving.value = true; error.value = '';
  try {
    const ids = props.targets.map(proxy => proxy.id);
    const revisions = Object.fromEntries(ids.map(id => [id, snapshots.value[id].revision]));
    if (props.allocate) await customerProxyApi.allocateRelays(ids, count.value, revisions);
    else if (single.value) await customerProxyApi.saveRelays(ids[0], revisions[ids[0]], chosen.value);
    else await customerProxyApi.batchRelays(ids, chosen.value, mode.value, revisions);
    ElMessage.success(props.allocate ? `已为 ${ids.length} 个代理分配中继` : '中继绑定已保存');
    emit('saved'); emit('update:modelValue', false);
  } catch (reason) {
    uncertain.value = !(reason instanceof ApiError) || reason.status === 409;
    error.value = reason instanceof ApiError && reason.status === 409 ? `${errorMessage(reason)}。请重新载入绑定与授权后再调整。`
      : uncertain.value ? '请求结果未确认，请重新载入绑定核对结果后再操作，避免覆盖已保存配置。' : errorMessage(reason);
  } finally { saving.value = false; }
}
function close() { if (!saving.value) emit('update:modelValue', false); }
watch(() => [props.modelValue, props.targets, props.allocate], () => {
  controller?.abort(); query.value = ''; mode.value = 'append'; count.value = 2; error.value = ''; uncertain.value = false;
  if (props.modelValue) void load();
}, { immediate: true });
onBeforeUnmount(() => controller?.abort());
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="min(980px, 95vw)" append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @update:model-value="close">
    <p class="binding-targets">{{selectionText}}<span class="muted"> · {{targets.length}} 个代理</span></p>
    <el-alert title="每个代理最多绑定 8 个授权节点。已打开店铺会同步绑定变更；新增候选保持当前线路，移除或停用正在使用的节点可能触发重新连接。启动优先使用上次成功线路，无记录时先直连出口代理。" type="info" :closable="false" class="section-alert"/>
    <div v-if="error" class="binding-error" role="alert">{{error}}<el-button type="primary" link :disabled="saving" @click="load">重新载入</el-button></div>
    <div v-loading="loading" class="binding-content">
      <el-alert v-if="ready && ineligible.length" :title="`${ineligible.length} 个代理已回收、到期或类型不支持中继；可解除已有绑定，不能新增绑定。`" type="warning" :closable="false" class="section-alert"/>
      <template v-if="allocate">
        <el-form label-position="top"><el-form-item label="每个代理分配的节点数"><el-input-number v-model="count" :min="1" :max="4" :precision="0" :disabled="saving"/></el-form-item></el-form>
        <p>按各企业可用节点的配置绑定数量均衡分配，并替换所选代理的原有中继绑定。</p>
        <p class="muted">分配数量为 1 至 4 个。绑定数量用于分配参考，不代表实时带宽、在线连接数或网络延迟。</p>
        <el-alert v-if="allocationInsufficient" title="部分代理的授权可用节点少于分配数量，请降低数量或先补充分组授权与可用节点。" type="warning" :closable="false" class="section-alert"/>
        <el-table v-if="ready" :data="targets" size="small" max-height="300"><el-table-column prop="name" label="客户代理"/><el-table-column label="当前绑定" width="100"><template #default="{row}">{{snapshots[row.id].items.length}} 个</template></el-table-column><el-table-column label="授权可用节点" width="140"><template #default="{row}">{{snapshots[row.id].available.length}} 个</template></el-table-column></el-table>
      </template>
      <template v-else>
        <div v-if="!single" class="batch-mode"><el-radio-group v-model="mode" :disabled="saving"><el-radio-button value="append">追加绑定</el-radio-button><el-radio-button value="replace">替换全部绑定</el-radio-button></el-radio-group><span class="muted">{{mode === 'append' ? '保留原绑定并追加所选节点，合计不超过 8 个。' : '所选节点将替换每个代理的全部原有绑定。'}}</span></div>
        <p v-if="!single" class="muted">下方仅显示所选代理所属企业均有权使用的节点。不同企业的节点不相同时，可使用自动分配。</p>
        <el-alert v-if="appendIssue" :title="appendIssue" type="warning" :closable="false" class="section-alert"/>
        <div class="binding-toolbar"><el-input v-model="query" clearable placeholder="搜索节点 / 分组 / 地区 / 地址" :disabled="saving"/><span class="muted">已选 {{chosen.length}} / 8</span><el-button :disabled="saving || !chosen.length" @click="chosen = []">清空选择</el-button></div>
        <el-alert v-if="hasUnavailableSelection" type="warning" :closable="false" class="section-alert"><template #title>已选绑定中有不可用节点，请移除后保存。<el-button link type="primary" :disabled="saving" @click="removeUnavailable">移除不可用项</el-button></template></el-alert>
        <el-table :data="filtered" row-key="id" max-height="340" :empty-text="loading ? '正在读取授权节点…' : '没有可选节点，请检查企业分组授权或分别管理代理'">
          <el-table-column width="48"><template #default="{row}"><el-checkbox :model-value="chosen.includes(row.id)" :aria-label="`选择中继 ${row.name}`" :disabled="saving || (!chosen.includes(row.id) && (!row.available || chosen.length >= 8))" @change="select(row, Boolean($event))"/></template></el-table-column>
          <el-table-column label="中继节点" min-width="180"><template #default="{row}">{{row.name}}<div class="muted">{{row.region || '未填写地区'}}</div></template></el-table-column>
          <el-table-column prop="poolName" label="所属分组" min-width="150"/>
          <el-table-column label="地址 / 协议" min-width="200"><template #default="{row}">{{row.host.includes(':') ? `[${row.host}]` : row.host}}:{{row.port}}<div class="muted">{{row.protocol.toUpperCase()}}</div></template></el-table-column>
          <el-table-column label="配置状态" min-width="145"><template #default="{row}"><el-tag :type="row.available ? 'success' : 'warning'">{{relayAvailability(row)}}</el-tag></template></el-table-column>
          <el-table-column prop="boundProxyCount" label="绑定代理数" width="110"/>
        </el-table>
        <div v-if="chosen.length" class="chosen-lines"><span class="muted">尝试顺序</span><div v-for="(id, index) in chosen" :key="id"><span>{{index + 1}}. {{nodeName(id)}}</span><el-button link :disabled="saving || index === 0" :aria-label="`上移 ${nodeName(id)}`" @click="move(index, -1)">上移</el-button><el-button link :disabled="saving || index === chosen.length - 1" :aria-label="`下移 ${nodeName(id)}`" @click="move(index, 1)">下移</el-button></div></div>
      </template>
    </div>
    <template #footer><el-button :disabled="saving" @click="close">取消</el-button><el-button type="primary" :loading="saving" :disabled="disabled" @click="save">{{allocate ? '确认分配并替换' : '保存中继绑定'}}</el-button></template>
  </el-dialog>
</template>

<style scoped>
.binding-targets{line-height:1.6}.binding-content{min-height:130px}.binding-error{display:flex;align-items:center;justify-content:space-between;gap:16px;color:var(--el-color-danger);margin:14px 0}.binding-toolbar,.batch-mode{display:flex;align-items:center;gap:14px;margin:16px 0}.binding-toolbar .el-input{flex:1}.binding-toolbar>span{white-space:nowrap}.chosen-lines{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-top:16px}.chosen-lines>div{display:flex;gap:5px;align-items:center;padding:4px 8px;border:1px solid var(--el-border-color);border-radius:5px}.chosen-lines .el-button+.el-button{margin-left:0}.batch-mode{flex-wrap:wrap}
</style>
