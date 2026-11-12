<!-- Adapted from youlaitech/vue3-element-admin LayoutTagsView.vue (line style). -->
<template>
  <div class="layout-tabs layout-tabs--line">
    <el-scrollbar
      ref="scrollbar"
      class="layout-tabs__scroll"
      :view-style="{ height: '100%' }"
      ><div class="layout-tabs__list">
        <div
          v-for="tag in tags"
          :key="tag.path"
          class="layout-tabs__item"
          :class="{
            'is-active': route.path === tag.path,
            'is-affix': tag.path === '/overview',
          }"
        >
          <router-link :to="tag.path" class="layout-tabs__item-text">{{
            tag.title
          }}</router-link
          ><button
            v-if="tag.path !== '/overview'"
            class="layout-tabs__item-close"
            :aria-label="`关闭${tag.title}标签`"
            @click.stop="close(tag.path)"
          >
            <el-icon><Close /></el-icon>
          </button>
        </div></div
    ></el-scrollbar>
  </div>
</template>
<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Close } from "@element-plus/icons-vue";
const route = useRoute(),
  router = useRouter();
const tags = ref([{ path: "/overview", title: "概览" }]),
  scrollbar = ref();
watch(
  () => route.path,
  async () => {
    if (!tags.value.some((tag) => tag.path === route.path))
      tags.value.push({
        path: route.path,
        title: String(route.meta.title || "配置管理"),
      });
    await nextTick();
    scrollbar.value?.$el
      ?.querySelector(".is-active")
      ?.scrollIntoView({ block: "nearest", inline: "nearest" });
  },
  { immediate: true },
);
async function close(path: string) {
  if (route.path === path) {
    const index = tags.value.findIndex((tag) => tag.path === path);
    await router.push(tags.value[Math.max(index - 1, 0)].path);
    if (route.path === path) return;
  }
  tags.value = tags.value.filter((tag) => tag.path !== path);
}
</script>
<style lang="scss" scoped>
@use "../../styles/variables" as *;
.layout-tabs {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  width: 100%;
  height: $tags-view-height;
  padding: 0 12px;
  background-color: var(--content-bg);
  border-bottom: 1px solid var(--card-border);
}
.layout-tabs__scroll {
  flex: 1;
  min-width: 0;
  height: 100%;
  :deep(.el-scrollbar__wrap) {
    overflow-y: hidden;
  }
}
.layout-tabs__list {
  display: flex;
  gap: 8px;
  align-items: center;
  height: 100%;
}
.layout-tabs__item {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  height: 100%;
  padding: 0 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  user-select: none;
  background: transparent;
  border: 0;
  border-radius: 0;
  &::after {
    position: absolute;
    right: 6px;
    bottom: 0;
    left: 6px;
    height: 2px;
    content: "";
    background: var(--el-color-primary);
    transform: scaleX(0);
    transform-origin: center;
    transition: transform 0.16s ease;
  }
  &-text {
    display: inline-flex;
    align-items: center;
    height: 100%;
    white-space: nowrap;
    color: inherit;
  }
  &-close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    padding: 0;
    margin-left: 1px;
    font-size: 12px;
    border: 0;
    border-radius: 4px;
    opacity: 0.5;
    background: transparent;
    color: inherit;
    cursor: pointer;
    &:hover {
      background: var(--el-color-primary-light-7);
      opacity: 1;
    }
  }
  &:hover {
    color: var(--el-text-color-primary);
    background: var(--el-fill-color-lighter);
    border-radius: 4px 4px 0 0;
  }
  &.is-active {
    color: var(--el-color-primary);
    background: transparent;
    &::after {
      transform: scaleX(1);
    }
  }
  &.is-affix &-text {
    font-weight: 500;
  }
}
</style>
