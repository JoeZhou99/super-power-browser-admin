<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Refresh, Search, Upload, CircleCheck, VideoPause, Plus } from "@element-plus/icons-vue";
import { errorMessage, formatDate } from "../api";
import { kernelApi, kernelBytes, kernelPlatform, type KernelRelease } from "../kernelApi";

const releases = ref<KernelRelease[]>([]);
const loading = ref(false);
const error = ref("");
const search = ref("");
const status = ref("all");
const platform = ref("all");
const importDialog = ref(false);
const manifest = ref("");
const archive = ref<File>();
const recommended = ref(true);
const formError = ref("");
const working = ref(false);
const archiveInput = ref<HTMLInputElement>();
const importArchiveInput = ref<HTMLInputElement>();
const draftRelease = ref<KernelRelease>();

const filtered = computed(() => releases.value.filter((row) => {
  const query = `${row.version} ${row.fullVersion} ${row.platform} ${row.arch} ${row.sha256}`.toLowerCase();
  return query.includes(search.value.trim().toLowerCase())
    && (status.value === "all" || row.state === status.value)
    && (platform.value === "all" || row.platform === platform.value);
}));

async function load() {
  loading.value = true; error.value = "";
  try { releases.value = (await kernelApi.list()).items; }
  catch (reason) { error.value = errorMessage(reason); }
  finally { loading.value = false; }
}

function openImport() {
  manifest.value = ""; archive.value = undefined; recommended.value = true; formError.value = ""; importDialog.value = true;
}

function onArchive(event: Event) {
  archive.value = (event.target as HTMLInputElement).files?.[0];
}

function parseDescriptor() {
  let parsed: any;
  try { parsed = JSON.parse(manifest.value); }
  catch { throw new Error("清单 JSON 格式不正确"); }
  if (Array.isArray(parsed?.kernels)) {
    if (parsed.kernels.length !== 1) throw new Error("请只导入一个内核版本的清单记录");
    parsed = parsed.kernels[0];
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("清单必须是内核记录对象");
  return parsed as Record<string, unknown>;
}

async function importRelease() {
  formError.value = "";
  try {
    const descriptor = parseDescriptor();
    if (!archive.value) throw new Error("请选择对应的 ZIP 内核包");
    const expected = Number(descriptor.archiveBytes);
    if (!Number.isSafeInteger(expected) || expected <= 0 || archive.value.size !== expected) {
      throw new Error(`ZIP 大小与清单不一致，应为 ${kernelBytes(expected)}`);
    }
    working.value = true;
    const created = await kernelApi.create(descriptor);
    const ticket = await kernelApi.upload(created.id);
    const response = await fetch(ticket.url, { method: ticket.method, headers: ticket.headers, body: archive.value });
    if (!response.ok) throw new Error(`内核包上传失败（HTTP ${response.status}）`);
    await kernelApi.publish(created.id, created.revision, recommended.value);
    ElMessage.success("内核已校验并发布"); importDialog.value = false; await load();
  } catch (reason) { formError.value = errorMessage(reason); }
  finally { working.value = false; }
}

async function recommend(row: KernelRelease) {
  try { await kernelApi.recommend(row.id); ElMessage.success("已设为该平台和架构的推荐内核"); await load(); }
  catch (reason) { ElMessage.error(errorMessage(reason)); }
}

async function download(row: KernelRelease) {
  try { const ticket = await kernelApi.download(row.id); window.open(ticket.url, "_blank", "noopener,noreferrer"); }
  catch (reason) { ElMessage.error(errorMessage(reason)); }
}

async function disable(row: KernelRelease) {
  try { await ElMessageBox.confirm(`停用 ${row.version} 后，新店铺将不能再选择它，已绑定店铺不受影响。`, "停用内核", { type: "warning", confirmButtonText: "确认停用", cancelButtonText: "取消" }); }
  catch { return; }
  try { await kernelApi.disable(row.id); ElMessage.success("内核已停用"); await load(); }
  catch (reason) { ElMessage.error(errorMessage(reason)); }
}

function uploadDraft(row: KernelRelease) { draftRelease.value = row; archiveInput.value?.click(); }
async function onDraftArchive(event: Event) {
  const row = draftRelease.value; const file = (event.target as HTMLInputElement).files?.[0];
  (event.target as HTMLInputElement).value = "";
  if (!row || !file) return;
  if (file.size !== row.archiveBytes) { ElMessage.error(`ZIP 大小与清单不一致，应为 ${kernelBytes(row.archiveBytes)}`); return; }
  try {
    working.value = true;
    const ticket = await kernelApi.upload(row.id);
    const response = await fetch(ticket.url, { method: ticket.method, headers: ticket.headers, body: file });
    if (!response.ok) throw new Error(`内核包上传失败（HTTP ${response.status}）`);
    await kernelApi.publish(row.id, row.revision, false);
    ElMessage.success("内核已校验并发布"); await load();
  } catch (reason) { ElMessage.error(errorMessage(reason)); }
  finally { working.value = false; draftRelease.value = undefined; }
}

function stateLabel(value: string) { return value === "PUBLISHED" ? "已发布" : value === "DRAFT" ? "待上传" : value === "DISABLED" ? "已停用" : value; }
function stateType(value: string) { return value === "PUBLISHED" ? "success" : value === "DRAFT" ? "warning" : "info"; }
onMounted(load);
</script>

<template>
  <section class="page-container">
    <div class="page-heading">
      <div>
        <h1>浏览器内核</h1>
        <p>统一维护可供店铺创建和编辑使用的内核版本。发布后客户端按需下载，停用不会影响已绑定店铺。</p>
      </div>
      <div class="heading-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" :disabled="working" @click="openImport">导入内核</el-button>
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="section-alert">
      <template #default><el-button link type="primary" @click="load">重试加载</el-button></template>
    </el-alert>
    <el-card shadow="never" v-loading="loading">
      <div class="table-toolbar">
        <div class="filters">
          <el-input v-model="search" :prefix-icon="Search" placeholder="搜索版本、平台或 SHA-256" clearable />
          <el-select v-model="status" aria-label="发布状态"><el-option label="全部状态" value="all" /><el-option label="已发布" value="PUBLISHED" /><el-option label="待上传" value="DRAFT" /><el-option label="已停用" value="DISABLED" /></el-select>
          <el-select v-model="platform" aria-label="操作系统"><el-option label="全部系统" value="all" /><el-option label="Windows" value="win32" /><el-option label="macOS" value="darwin" /></el-select>
        </div>
        <span class="muted">共 {{ filtered.length }} 个版本</span>
      </div>
      <el-table :data="filtered" stripe empty-text="暂无内核版本">
        <el-table-column label="版本" min-width="150"><template #default="{ row }"><strong>{{ row.version }}</strong><el-tag v-if="row.recommended" size="small" type="warning" class="recommended-tag">推荐</el-tag><div class="muted">{{ row.fullVersion }}</div></template></el-table-column>
        <el-table-column label="系统 / 架构" width="150"><template #default="{ row }">{{ kernelPlatform(row.platform) }} / {{ row.arch }}</template></el-table-column>
        <el-table-column label="资源大小" width="120"><template #default="{ row }">{{ kernelBytes(row.archiveBytes) }}</template></el-table-column>
        <el-table-column label="存储" width="100"><template #default="{ row }">{{ row.storageType }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="stateType(row.state)" effect="light">{{ stateLabel(row.state) }}</el-tag></template></el-table-column>
        <el-table-column label="更新时间" width="175"><template #default="{ row }">{{ formatDate(row.updatedAt || row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="300" fixed="right"><template #default="{ row }"><el-button v-if="row.state === 'PUBLISHED'" link type="primary" :disabled="working" @click="download(row)">下载</el-button><el-button v-if="row.state === 'DRAFT'" link type="primary" :icon="Upload" :disabled="working" @click="uploadDraft(row)">上传并发布</el-button><el-button v-if="row.state === 'PUBLISHED' && !row.recommended" link type="primary" :icon="CircleCheck" :disabled="working" @click="recommend(row)">设为推荐</el-button><el-button v-if="row.state === 'PUBLISHED'" link type="danger" :icon="VideoPause" :disabled="working" @click="disable(row)">停用</el-button><span v-if="row.state === 'DISABLED'" class="muted">已停止提供</span></template></el-table-column>
      </el-table>
    </el-card>

    <input ref="archiveInput" class="hidden-file-input" type="file" accept=".zip,application/zip" @change="onDraftArchive" />
    <el-dialog v-model="importDialog" title="导入内核版本" width="680px" :close-on-click-modal="false">
      <el-alert title="请从构建产物 kernels.json 中复制一个内核记录，ZIP 文件必须与清单中的 archiveBytes 和 sha256 完全一致。" type="info" :closable="false" class="section-alert" />
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" class="section-alert" />
      <el-form label-position="top" :disabled="working">
        <el-form-item label="内核清单 JSON" required><el-input v-model="manifest" type="textarea" :rows="12" spellcheck="false" placeholder="粘贴 kernels.json 中的一条 kernels 记录" /></el-form-item>
        <el-form-item label="ZIP 内核包" required><div class="archive-picker"><el-button :icon="Upload" @click="importArchiveInput?.click()">选择 ZIP 文件</el-button><span v-if="archive" class="muted">{{ archive.name }} · {{ kernelBytes(archive.size) }}</span><span v-else class="muted">尚未选择文件</span></div></el-form-item>
        <el-checkbox v-model="recommended">导入后设为该平台和架构的推荐版本</el-checkbox>
      </el-form>
      <template #footer><el-button :disabled="working" @click="importDialog = false">取消</el-button><el-button type="primary" :loading="working" @click="importRelease">上传、校验并发布</el-button></template>
      <input ref="importArchiveInput" class="hidden-file-input" type="file" accept=".zip,application/zip" @change="onArchive" />
    </el-dialog>
  </section>
</template>

<style scoped>
.heading-actions { display: flex; gap: 10px; }
.recommended-tag { margin-left: 8px; vertical-align: 1px; }
.hidden-file-input { display: none; }
.archive-picker { display: flex; align-items: center; gap: 12px; }
@media (max-width: 600px) { .heading-actions { width: 100%; } }
</style>
