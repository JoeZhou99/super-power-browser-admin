import { createRouter, createWebHistory } from "vue-router";
import { auth, clearSession, hasToken, loadMe } from "./api";
import Layout from "./layouts/modes/LeftLayout.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      component: () => import("./views/Login.vue"),
      meta: { title: "登录", public: true },
    },
    {
      path: "/",
      component: Layout,
      redirect: "/overview",
      children: [
        {
          path: "overview",
          component: () => import("./views/Overview.vue"),
          meta: { title: "概览" },
        },
        { path: "environment", redirect: "/environment/timezones" },
        {
          path: "environment/timezones",
          component: () => import("./views/Dictionary.vue"),
          meta: { title: "时区", group: "环境选项", configKey: "timezones" },
        },
        {
          path: "environment/languages",
          component: () => import("./views/Dictionary.vue"),
          meta: { title: "语言", group: "环境选项", configKey: "languages" },
        },
        {
          path: "environment/resolutions",
          component: () => import("./views/Dictionary.vue"),
          meta: {
            title: "窗口尺寸",
            group: "环境选项",
            configKey: "resolutions",
          },
        },
        {
          path: "environment/defaults",
          component: () => import("./views/Defaults.vue"),
          meta: { title: "环境默认值", group: "环境选项" },
        },
        {
          path: "platform-sites",
          component: () => import("./views/Platforms.vue"),
          meta: { title: "平台站点" },
        },
        { path: "proxy", redirect: "/proxy/list" },
        {
          path: "proxy/list",
          component: () => import("./views/CustomerProxies.vue"),
          meta: { title: "客户代理", group: "代理管理" },
        },
        {
          path: "business/members",
          component: () => import("./views/Members.vue"),
          meta: { title: "用户与成员", group: "业务管理" },
        },
        {
          path: "business/orders",
          component: () => import("./views/Orders.vue"),
          meta: { title: "订单与收支", group: "业务管理" },
        },
        {
          path: "ai/skills",
          component: () => import("./views/Skills.vue"),
          meta: { title: "AI 技能商店", group: "AI 管理" },
        },
        {
          path: "proxy/relays",
          component: () => import("./views/RelayNodes.vue"),
          meta: { title: "中继池", group: "代理管理" },
        },
        {
          path: "proxy/groups",
          component: () => import("./views/RelayPools.vue"),
          meta: { title: "中继分组", group: "代理管理" },
        },
        { path: "network/relays", redirect: to => ({ path: "/proxy/groups", query: to.query }) },
        { path: "network", redirect: "/network/routing" },
        {
          path: "network/routing",
          component: () => import("./views/Rules.vue"),
          meta: { title: "域名分流", group: "网络与安全", configKey: "network-routing" },
        },
        {
          path: "network/access",
          component: () => import("./views/Rules.vue"),
          meta: { title: "访问策略", group: "网络与安全", configKey: "access-policy" },
        },
        {
          path: "network/checks",
          component: () => import("./views/Rules.vue"),
          meta: { title: "IP 检测", group: "网络与安全", configKey: "network-checks" },
        },
        {
          path: "network/passkey",
          component: () => import("./views/Rules.vue"),
          meta: { title: "Passkey 规则", group: "网络与安全", configKey: "passkey-rules" },
        },
        {
          path: "audit",
          component: () => import("./views/Audit.vue"),
          meta: { title: "修改记录" },
        },
        {
          path: ":pathMatch(.*)*",
          component: () => import("./views/NotFound.vue"),
          meta: { title: "页面不存在" },
        },
      ],
    },
  ],
});
router.beforeEach(async (to) => {
  document.title = `${String(to.meta.title || "配置管理")} · SuperPower 运营后台`;
  if (to.meta.public) return true;
  if (!hasToken()) return { path: "/login", query: { redirect: to.fullPath } };
  if (!auth.user) {
    try {
      await loadMe();
    } catch {
      clearSession();
      return { path: "/login", query: { redirect: to.fullPath } };
    }
  }
});
window.addEventListener("admin-session-expired", () => {
  if (router.currentRoute.value.path !== "/login")
    void router.replace({
      path: "/login",
      query: { redirect: router.currentRoute.value.fullPath, expired: "1" },
    });
});
export default router;
