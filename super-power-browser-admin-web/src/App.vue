<template>
  <el-config-provider
    :message="{ grouping: true, duration: 2500, showClose: true }"
  >
    <router-view />
  </el-config-provider>
</template>
