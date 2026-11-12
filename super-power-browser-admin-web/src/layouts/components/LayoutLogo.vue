<!-- Adapted from youlaitech/vue3-element-admin LayoutLogo.vue. -->
<template>
  <div class="layout-logo">
    <router-link
      to="/overview"
      :aria-label="collapse ? 'SuperPower 运营后台' : undefined"
      ><span class="brand-mark">S</span
      ><span v-if="!collapse" class="layout-logo__title"
        >SuperPower <small>运营后台</small></span
      ></router-link
    >
  </div>
</template>
<script setup lang="ts">
defineProps<{ collapse: boolean }>();
</script>
<style lang="scss" scoped>
@use "../../styles/variables" as *;
.layout-logo {
  width: 100%;
  height: $navbar-height;
  background-color: $sidebar-logo-background;
  a {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
  }
  &__title {
    flex-shrink: 0;
    margin-left: 10px;
    font-size: 14px;
    font-weight: bold;
    color: $sidebar-logo-text-color;
    small {
      font-size: 10px;
      font-weight: 400;
      margin-left: 3px;
      opacity: 0.75;
    }
  }
}
.brand-mark {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: 800;
  background: var(--el-color-primary);
  border-radius: 6px;
}
</style>
