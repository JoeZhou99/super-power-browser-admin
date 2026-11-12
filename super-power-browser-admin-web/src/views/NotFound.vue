<template>
  <section class="page-container">
    <el-result
      icon="warning"
      title="404 · 页面不存在"
      sub-title="该页面可能已移除，或访问地址有误。"
      ><template #extra
        ><router-link to="/overview"
          ><el-button type="primary">返回概览</el-button></router-link
        ></template
      ></el-result
    >
  </section>
</template>
