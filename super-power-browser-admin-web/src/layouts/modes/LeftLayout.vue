<!-- Adapted from youlaitech/vue3-element-admin LeftLayout.vue. -->
<template>
  <BaseLayout :mobile="mobile" :opened="opened" @close="opened = false">
    <div class="layout-sidebar" :class="{ 'is-collapsed': !opened }">
      <div class="layout-sidebar__inner has-logo">
        <LayoutLogo :collapse="!opened && !mobile" /><el-scrollbar
          ><LayoutSidebar :collapse="!opened && !mobile"
        /></el-scrollbar>
      </div>
    </div>
    <div class="layout-main" :class="{ 'is-collapsed': !opened }">
      <div class="layout-header">
        <LayoutNavbar
          :opened="opened"
          @toggle="opened = !opened"
        /><LayoutTagsView />
      </div>
      <main class="layout-content">
        <router-view v-slot="{ Component, route: activeRoute }"
          ><component :is="Component" :key="activeRoute.path"
        /></router-view>
      </main>
    </div>
  </BaseLayout>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import BaseLayout from "../BaseLayout.vue";
import LayoutLogo from "../components/LayoutLogo.vue";
import LayoutNavbar from "../components/LayoutNavbar.vue";
import LayoutTagsView from "../components/LayoutTagsView.vue";
import LayoutSidebar from "../components/LayoutSidebar.vue";
const route = useRoute();
const mobile = ref(window.innerWidth < 900),
  opened = ref(!mobile.value);
function resize() {
  const next = window.innerWidth < 900;
  if (mobile.value !== next) opened.value = !next;
  mobile.value = next;
}
window.addEventListener("resize", resize);
onBeforeUnmount(() => window.removeEventListener("resize", resize));
watch(
  () => route.path,
  () => {
    if (mobile.value) opened.value = false;
  },
);
</script>
<style lang="scss" scoped>
@use "../../styles/variables" as *;
.layout-sidebar {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 999;
  width: $sidebar-width;
  background-color: $menu-background;
  transition: width 0.28s;
  &.is-collapsed {
    width: $sidebar-width-collapsed;
  }
  &__inner {
    position: relative;
    height: 100%;
    background-color: var(--menu-background);
    border-right: 1px solid var(--menu-border);
    transition: width 0.28s;
    &.has-logo .el-scrollbar {
      height: calc(100% - #{$navbar-height});
    }
    :deep(.el-menu) {
      border: none;
    }
  }
}
.layout-main {
  position: relative;
  min-height: 100%;
  margin-left: $sidebar-width;
  transition: margin-left 0.28s;
  &.is-collapsed {
    margin-left: $sidebar-width-collapsed;
  }
}
.layout-header {
  position: sticky;
  top: 0;
  z-index: 20;
}
.layout-content {
  min-width: 0;
}
.is-mobile {
  .layout-sidebar {
    width: $sidebar-width !important;
    transition:
      transform 0.28s,
      width 0s;
  }
  &.is-sidebar-collapsed .layout-sidebar {
    transform: translateX(-$sidebar-width);
  }
  &.is-sidebar-open .layout-sidebar {
    transform: translateX(0);
  }
  .layout-main {
    margin-left: 0 !important;
  }
}
</style>
