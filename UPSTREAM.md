# 模板来源与精简说明

本项目基于用户最终指定的 [youlaitech/vue3-element-admin](https://github.com/youlaitech/vue3-element-admin) 源码精简，使用 Vue 3、Element Plus、TypeScript 和 Vite。

- 上游基准提交：`301f0ab983f8dda9d451c2c217b065ed613b9062`，上游版本 `4.12.1`。
- `LICENSE` 保留上游 MIT 许可原文。
- 未使用之前讨论的 PanJiaChen Vue 2 模板，以上游 Vue 3 模板为准。

实际参考和派生的上游文件：

下表“本项目对应实现”的路径均相对 `super-power-browser-admin-web/`；本次目录整理保留原前端实现。

| 上游文件                                    | 本项目对应实现        | 保留与调整                                                                               |
| ------------------------------------------- | --------------------- | ---------------------------------------------------------------------------------------- |
| `src/layouts/modes/LeftLayout.vue`          | 同路径                | 保留左侧布局、固定侧栏、折叠和移动端行为；移除多布局与全屏状态依赖                       |
| `src/layouts/BaseLayout.vue`                | 同路径                | 保留基础容器和移动端遮罩；改为显式 props                                                 |
| `src/layouts/components/LayoutSidebar.vue`  | 同路径                | 保留 Element Plus 菜单；仅保留本项目配置路由                                             |
| `src/layouts/components/LayoutNavbar.vue`   | 同路径                | 保留 48px 顶栏、折叠按钮和面包屑，接入运营账号与退出                                     |
| `src/layouts/components/LayoutTagsView.vue` | 同路径                | 保留 38px 页签和上游线型标签样式；精简关闭逻辑，尊重草稿离开确认                         |
| `src/layouts/components/LayoutLogo.vue`     | 同路径                | 保留品牌区结构与尺寸，改为 SuperPower 品牌                                               |
| `src/styles/variables.scss`                 | 同路径                | 原样保留上游布局尺寸与菜单变量                                                           |
| `src/styles/theme.scss`                     | 同路径                | 保留浅色内容区、经典深蓝侧栏两组变量；移除主题设置页面                                   |
| `src/views/login/index.vue`                 | `src/views/Login.vue` | 沿用品牌与表单结构，调整为最大宽度 880px 的居中一体卡片、窄屏单列；移除演示账号、验证码、OIDC、扫码、注册和重置密码 |

环境选项、平台站点、版本冲突处理及修改记录是本项目业务实现。删除上游工作流、动态表单、代码生成、系统租户管理、演示路由、Mock 和关联依赖；接口仅连接本项目 `/api/admin`，不访问上游演示服务。依赖版本与当前 SuperPower 客户端工具链对齐，使用 npm 与 `package-lock.json` 固定实际安装版本。
