<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Delete, Plus, Refresh, Upload } from "@element-plus/icons-vue";
import { errorMessage, getConfig, saveConfig, type ConfigDocument } from "../api";

type FreeVps = { id: string; name: string; protocol: string; host: string; port: number; username: string; password: string; region: string; provider: string; stock: number; enabled: boolean; description: string };
const doc = ref<ConfigDocument<unknown, FreeVps>>({ key: "free-proxies", revision: 1, entries: [] });
const rows = ref<FreeVps[]>([]);
const loading = ref(false), saving = ref(false), dialog = ref(false), editing = ref<FreeVps>();
const fileInput = ref<HTMLInputElement>();
const form = reactive<FreeVps>({ id: "", name: "", protocol: "http", host: "", port: 8080, username: "", password: "", region: "", provider: "", stock: 1, enabled: true, description: "" });
const blank = () => Object.assign(form, { id: `vps-${Date.now()}`, name: "", protocol: "http", host: "", port: 8080, username: "", password: "", region: "", provider: "", stock: 1, enabled: true, description: "" });
async function load() { loading.value = true; try { const result = await getConfig<unknown, FreeVps>("free-proxies"); doc.value = result; rows.value = (result.entries || []).map(row => ({ ...row })); } catch (error) { ElMessage.error(errorMessage(error)); } finally { loading.value = false; } }
function add() { blank(); editing.value = undefined; dialog.value = true; }
function edit(row: FreeVps) { Object.assign(form, row); editing.value = row; dialog.value = true; }
function submitRow() { const value = { ...form, id: form.id.trim(), name: form.name.trim(), host: form.host.trim(), username: form.username.trim(), password: form.password, region: form.region.trim(), provider: form.provider.trim(), description: form.description.trim(), port: Number(form.port), stock: Number(form.stock) }; if (!value.id || !value.name || !value.host || !value.port) { ElMessage.warning("请填写完整的代理信息"); return; } const index = rows.value.findIndex(row => row.id === value.id); if (index < 0) rows.value.push(value); else rows.value[index] = value; dialog.value = false; }
async function importFile(event: Event) { const input = event.target as HTMLInputElement; const file = input.files?.[0]; input.value = ""; if (!file) return; const text = await file.text(); const parsed: FreeVps[] = []; for (const [index, line] of text.split(/\r?\n/).map(value => value.trim()).filter(Boolean).entries()) { if (index === 0 && line.toLowerCase().startsWith("id,")) continue; const fields = line.split(",").map(value => value.trim()); if (fields.length < 10) continue; const [id, name, protocol, host, port, username, password, region, provider, stock, description = ""] = fields; parsed.push({ id: id || `vps-${Date.now()}-${index}`, name, protocol: protocol || "http", host, port: Number(port) || 8080, username, password, region, provider, stock: Number(stock) || 0, enabled: true, description }); } if (!parsed.length) { ElMessage.warning("没有识别到可导入的代理行"); return; } const ids = new Set(rows.value.map(row => row.id)); for (const row of parsed) { const index = rows.value.findIndex(item => item.id === row.id); if (index >= 0) rows.value[index] = row; else if (!ids.has(row.id)) { rows.value.push(row); ids.add(row.id); } } ElMessage.success(`已导入 ${parsed.length} 条代理，请检查后发布`); }
function openFilePicker() { fileInput.value?.click(); }
async function remove(row: FreeVps) { try { await ElMessageBox.confirm(`确定删除“${row.name}”吗？已领取的客户不受影响。`, "删除 VPS 代理", { type: "warning" }); } catch { return; } rows.value = rows.value.filter(item => item.id !== row.id); await persist(); }
async function persist() { saving.value = true; try { const result = await saveConfig({ key: "free-proxies", revision: doc.value.revision, entries: rows.value }); doc.value = result; rows.value = (result.entries || []).map(row => ({ ...row })); ElMessage.success("免费 VPS 代理配置已发布"); } catch (error) { ElMessage.error(errorMessage(error)); await load(); } finally { saving.value = false; } }
onMounted(load);
</script>

<template>
  <section class="page-container free-vps-page">
    <div class="page-heading"><div><h1>免费 VPS 代理</h1><p>维护新用户可领取的代理库存。发布后，企业所有者可以在客户端领取一条并直接使用。</p></div><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button></div>
    <el-alert title="代理账号密码会写入共享业务配置，请仅录入已获授权、允许分发的资源。" type="warning" :closable="false" show-icon />
    <div class="toolbar"><el-button type="primary" :icon="Plus" @click="add">新增 VPS 代理</el-button><el-button :icon="Upload" @click="openFilePicker">批量上传 CSV</el-button><input ref="fileInput" class="file-input" type="file" accept=".csv,.txt" @change="importFile"><span class="muted">配置版本 {{ doc.revision }} · 共 {{ rows.length }} 条</span><el-button class="publish" type="primary" :loading="saving" @click="persist">发布配置</el-button></div>
    <el-table :data="rows" v-loading="loading" border>
      <el-table-column label="名称" min-width="160"><template #default="{ row }"><strong>{{ row.name }}</strong><div class="muted">{{ row.id }}</div></template></el-table-column>
      <el-table-column label="代理地址" min-width="210"><template #default="{ row }"><span class="mono">{{ row.protocol.toUpperCase() }}://{{ row.host }}:{{ row.port }}</span><div class="muted">{{ row.region || '未填写地区' }}</div></template></el-table-column>
      <el-table-column label="供应商" prop="provider" min-width="130" />
      <el-table-column label="库存" width="95"><template #default="{ row }">{{ row.stock }} 条</template></el-table-column>
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '已发布' : '已停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="160" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button></template></el-table-column>
      <template #empty><el-empty description="暂无免费 VPS 代理，请先录入资源"><el-button type="primary" @click="add">新增代理</el-button></el-empty></template>
    </el-table>
    <el-dialog v-model="dialog" :title="editing ? '编辑 VPS 代理' : '新增 VPS 代理'" width="640px" :close-on-click-modal="false">
      <el-form label-position="top"><div class="form-grid"><el-form-item label="代理 ID"><el-input v-model="form.id" :disabled="Boolean(editing)" /></el-form-item><el-form-item label="显示名称"><el-input v-model="form.name" placeholder="例如：香港 VPS 01" /></el-form-item><el-form-item label="协议"><el-select v-model="form.protocol"><el-option v-for="value in ['http','https','socks5','socks4','ssh','ss','ssr','vmess','vless']" :key="value" :label="value.toUpperCase()" :value="value" /></el-select></el-form-item><el-form-item label="主机地址"><el-input v-model="form.host" placeholder="域名或 IP，不带协议头" /></el-form-item><el-form-item label="端口"><el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" /></el-form-item><el-form-item label="库存数量"><el-input-number v-model="form.stock" :min="0" :max="1000000" controls-position="right" /></el-form-item><el-form-item label="账号"><el-input v-model="form.username" /></el-form-item><el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item><el-form-item label="地区"><el-input v-model="form.region" placeholder="例如：香港 / 美国洛杉矶" /></el-form-item><el-form-item label="供应商"><el-input v-model="form.provider" /></el-form-item><el-form-item label="说明" class="wide"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" /></el-form-item><el-form-item label="发布状态" class="wide"><el-switch v-model="form.enabled" active-text="允许领取" inactive-text="暂停领取" /></el-form-item></div></el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="submitRow">保存条目</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.page-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:18px}.page-heading h1{margin:0}.page-heading p{margin:8px 0 0;color:#7b8494;line-height:1.7}.toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin:18px 0}.toolbar .muted{margin-left:4px}.toolbar .publish{margin-left:auto}.file-input{display:none}.mono{font-family:Consolas,monospace;font-size:12px;overflow-wrap:anywhere}.muted{color:#8994a5;font-size:12px;line-height:1.6}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 18px}.form-grid .wide{grid-column:1/-1}.form-grid .el-input-number,.form-grid .el-select{width:100%}@media(max-width:700px){.page-heading{flex-direction:column}.toolbar .publish{margin-left:0}.form-grid{grid-template-columns:1fr}.form-grid .wide{grid-column:auto}}
</style>
