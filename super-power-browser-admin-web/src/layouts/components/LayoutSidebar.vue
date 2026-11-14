<!-- Simplified from youlaitech/vue3-element-admin LayoutSidebar.vue. -->
<template>
  <el-menu
    :default-active="route.path"
    :collapse="collapse"
    background-color="var(--menu-background)"
    text-color="var(--menu-text)"
    active-text-color="var(--menu-active-text)"
    :unique-opened="false"
    :collapse-transition="false"
    mode="vertical"
    router
  >
    <el-menu-item index="/overview"
      ><el-icon><Odometer /></el-icon
      ><template #title>概览</template></el-menu-item
    >
    <el-sub-menu index="/environment"
      ><template #title
        ><el-icon><Monitor /></el-icon><span>环境选项</span></template
      ><el-menu-item index="/environment/timezones">时区</el-menu-item
      ><el-menu-item index="/environment/languages">语言</el-menu-item
      ><el-menu-item index="/environment/resolutions">窗口尺寸</el-menu-item
      ><el-menu-item index="/environment/defaults"
        >环境默认值</el-menu-item
      ></el-sub-menu
    >
    <el-menu-item index="/kernels"
      ><el-icon><Cpu /></el-icon
      ><template #title>浏览器内核</template></el-menu-item>
    <el-menu-item index="/platform-sites"
      ><el-icon><Connection /></el-icon
      ><template #title>平台站点</template></el-menu-item
    >
    <el-sub-menu index="/proxy">
      <template #title><el-icon><SetUp /></el-icon><span>代理管理</span></template>
      <el-menu-item index="/proxy/list">客户代理</el-menu-item>
      <el-menu-item index="/proxy/relays">中继池</el-menu-item>
      <el-menu-item index="/proxy/groups">中继分组</el-menu-item>
    </el-sub-menu>
    <el-sub-menu index="/business">
      <template #title><el-icon><User /></el-icon><span>业务管理</span></template>
      <el-menu-item index="/business/members">用户与成员</el-menu-item>
      <el-menu-item index="/business/orders">订单与收支</el-menu-item>
    </el-sub-menu>
    <el-sub-menu index="/ai">
      <template #title><el-icon><MagicStick /></el-icon><span>AI 管理</span></template>
      <el-menu-item index="/ai/skills">技能商店</el-menu-item>
    </el-sub-menu>
    <el-sub-menu index="/network">
      <template #title><el-icon><Lock /></el-icon><span>网络与安全</span></template>
      <el-menu-item index="/network/routing">域名分流</el-menu-item>
      <el-menu-item index="/network/access">访问策略</el-menu-item>
      <el-menu-item index="/network/checks">IP 检测</el-menu-item>
      <el-menu-item index="/network/passkey">Passkey 规则</el-menu-item>
    </el-sub-menu>
    <el-menu-item index="/audit"
      ><el-icon><Document /></el-icon
      ><template #title>修改记录</template></el-menu-item
    >
  </el-menu>
</template>
<script setup lang="ts">
import { useRoute } from "vue-router";
import {
  Connection,
  Cpu,
  Document,
  Monitor,
  Odometer,
  Lock,
  SetUp,
  User,
  MagicStick,
} from "@element-plus/icons-vue";
defineProps<{ collapse: boolean }>();
const route = useRoute();
</script>
<style scoped lang="scss">
.el-menu {
  padding-top: 8px;
}
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 48px;
  line-height: 48px;
}
:deep(.el-menu-item.is-active) {
  background-color: var(--menu-active-bg);
}
:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background-color: var(--menu-hover);
}
.el-menu--collapse {
  width: 54px;
}
</style>
