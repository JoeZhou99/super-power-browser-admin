<!-- Adapted from youlaitech/vue3-element-admin; see UPSTREAM.md and LICENSE. -->
<template>
  <div
    class="layout-root layout--left"
    :class="{
      'is-mobile': mobile,
      'is-sidebar-open': opened,
      'is-sidebar-collapsed': !opened,
    }"
  >
    <div
      v-if="mobile && opened"
      class="layout-root__overlay"
      @click="$emit('close')"
    />
    <slot />
  </div>
</template>
<script setup lang="ts">
defineProps<{ mobile: boolean; opened: boolean }>();
defineEmits<{ close: [] }>();
</script>
<style scoped lang="scss">
.layout-root {
  width: 100%;
  height: 100%;
  &__overlay {
    position: fixed;
    top: 0;
    left: 0;
    z-index: 998;
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, 0.3);
  }
}
</style>
