<!-- Adapted from youlaitech/vue3-element-admin LayoutNavbar.vue. -->
<template>
  <div class="layout-navbar">
    <div class="navbar-left">
      <button
        class="hamburger-wrapper"
        :aria-label="opened ? '折叠菜单' : '展开菜单'"
        @click="$emit('toggle')"
      >
        <el-icon :size="18"
          ><Fold v-if="opened" /><Expand v-else
        /></el-icon></button
      ><el-breadcrumb separator="/"
        ><el-breadcrumb-item v-if="route.meta.group">{{
          route.meta.group
        }}</el-breadcrumb-item
        ><el-breadcrumb-item>{{
          route.meta.title
        }}</el-breadcrumb-item></el-breadcrumb
      >
    </div>
    <div class="layout-navbar__actions">
      <el-tag size="small" type="info" class="admin-label">运营配置</el-tag
      ><el-dropdown trigger="click" @command="exit"
        ><button class="user-menu">
          <el-avatar :size="28">{{
            auth.user?.username?.slice(0, 1).toUpperCase()
          }}</el-avatar
          ><span>{{ auth.user?.username }}</span
          ><el-icon><ArrowDown /></el-icon></button
        ><template #dropdown
          ><el-dropdown-menu
            ><el-dropdown-item command="logout"
              >退出登录</el-dropdown-item
            ></el-dropdown-menu
          ></template
        ></el-dropdown
      >
    </div>
  </div>
</template>
<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import { ArrowDown, Expand, Fold } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { auth, errorMessage, logout } from "../../api";
defineProps<{ opened: boolean }>();
defineEmits<{ toggle: [] }>();
const route = useRoute(),
  router = useRouter();
async function exit() {
  try {
    await ElMessageBox.confirm(
      "确定退出运营后台吗？尚未发布的修改将被丢弃。",
      "退出登录",
      { confirmButtonText: "退出登录", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
  try {
    await logout();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    await router.replace("/login");
  }
}
</script>
<style lang="scss" scoped>
@use "../../styles/variables" as *;
.layout-navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: $navbar-height;
  padding: 0 14px 0 6px;
  background-color: var(--content-bg);
  border-bottom: 1px solid var(--card-border);
  box-shadow: 0 1px 0 rgb(15 23 42 / 3%);
  &__actions {
    display: flex;
    gap: 18px;
    align-items: center;
    height: 100%;
  }
}
.navbar-left {
  display: flex;
  align-items: center;
  min-width: 0;
  height: 100%;
}
.hamburger-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 100%;
  padding: 0;
  color: var(--el-text-color-regular);
  cursor: pointer;
  border: 0;
  background: transparent;
  &:hover {
    color: var(--el-color-primary);
  }
}
.user-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  background: transparent;
  border: 0;
  padding: 4px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  .el-avatar {
    background: #e8f3ff;
    color: var(--el-color-primary);
  }
}
@media (max-width: 600px) {
  .admin-label {
    display: none;
  }
  .user-menu > span:not(.el-avatar) {
    max-width: 90px;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
</style>
