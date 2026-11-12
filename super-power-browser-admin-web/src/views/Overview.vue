<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import {
  ArrowRight,
  Clock,
  Connection,
  Monitor,
  Operation,
} from "@element-plus/icons-vue";
import {
  auth,
  configTitles,
  errorMessage,
  formatDate,
  request,
  type AuditEntry,
  type ConfigKey,
  type ConfigSummary,
} from "../api";
const loading = ref(false),
  loadError = ref(""),
  configs = ref<ConfigSummary[]>([]),
  recent = ref<AuditEntry[]>([]),
  auditTotal = ref(0);
const links: Record<ConfigKey, string> = {
  timezones: "/environment/timezones",
  languages: "/environment/languages",
  resolutions: "/environment/resolutions",
  "browser-defaults": "/environment/defaults",
  "platform-sites": "/platform-sites",
  "network-routing": "/network/routing",
  "access-policy": "/network/access",
  "network-checks": "/network/checks",
  "relay-pools": "/proxy/groups",
  "passkey-rules": "/network/passkey",
};
const cards = computed(() => [
  {
    title: "配置模块",
    value: configs.value.length,
    icon: Operation,
    color: "blue",
  },
  {
    title: "环境可选项",
    value: configs.value
      .filter((row) =>
        ["timezones", "languages", "resolutions"].includes(row.key),
      )
      .reduce((sum, row) => sum + row.itemCount, 0),
    icon: Monitor,
    color: "green",
  },
  {
    title: "平台站点",
    value:
      configs.value.find((row) => row.key === "platform-sites")?.itemCount || 0,
    icon: Connection,
    color: "orange",
  },
  { title: "累计发布", value: auditTotal.value, icon: Clock, color: "purple" },
]);
async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    const [list, audit] = await Promise.all([
      request<ConfigSummary[]>("/configs"),
      request<{ items: AuditEntry[]; total: number }>(
        "/config-audit?page=1&size=5",
      ),
    ]);
    configs.value = list;
    recent.value = audit.items;
    auditTotal.value = audit.total;
  } catch (error) {
    loadError.value = errorMessage(error);
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="page-container" v-loading="loading">
    <div class="page-heading">
      <div>
        <h1>配置概览</h1>
        <p>
          {{
            auth.user?.username
          }}，欢迎回来。集中维护客户端使用的核心业务配置。
        </p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
      class="section-alert"
    />
    <div class="metric-grid">
      <el-card
        v-for="card in cards"
        :key="card.title"
        shadow="hover"
        class="metric-card"
        ><div :class="['metric-icon', card.color]">
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <div>
          <p>{{ card.title }}</p>
          <strong>{{ loadError ? "—" : card.value }}</strong>
        </div></el-card
      >
    </div>
    <div class="overview-grid">
      <el-card shadow="never"
        ><template #header
          ><div class="card-header">
            <span>配置模块</span
            ><el-tag type="info" size="small">保存发布后生效</el-tag>
          </div></template
        >
        <div v-for="item in configs" :key="item.key" class="module-row">
          <div>
            <strong>{{ configTitles[item.key] || item.title }}</strong>
            <p>
              {{ item.itemCount }} 项 · v{{ item.revision }} ·
              {{ formatDate(item.updatedAt) }}
            </p>
          </div>
          <router-link :to="links[item.key]"
            ><el-button link type="primary"
              >管理<el-icon><ArrowRight /></el-icon></el-button
          ></router-link>
        </div>
        <el-empty v-if="!configs.length && !loading" description="暂无可用配置"
      /></el-card>
      <el-card shadow="never"
        ><template #header
          ><div class="card-header">
            <span>最近发布</span
            ><router-link to="/audit"
              ><el-button link type="primary">全部记录</el-button></router-link
            >
          </div></template
        ><el-timeline v-if="recent.length"
          ><el-timeline-item
            v-for="item in recent"
            :key="item.id"
            :timestamp="formatDate(item.createdAt)"
            placement="top"
            ><strong
              >{{ configTitles[item.key] || item.key }} · v{{
                item.revision
              }}</strong
            >
            <p class="muted">
              {{ item.operator }} 发布了配置
            </p></el-timeline-item
          ></el-timeline
        ><el-empty v-else description="尚无发布记录" :image-size="100"
      /></el-card>
    </div>
    <el-alert
      title="配置的影响范围"
      type="info"
      :closable="false"
      show-icon
      class="section-alert"
      ><template #default
        >环境选项与平台站点用于创建店铺；网络与安全规则用于店铺运行。各模块页面说明具体生效时间和适用范围。</template
      ></el-alert
    >
  </section>
</template>
