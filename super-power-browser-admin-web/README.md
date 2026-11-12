# SuperPower 运营管理页面

Vue 3 + Element Plus 前端，包含概览、时区、语言、窗口尺寸、环境默认值、平台站点、域名分流、访问策略、IP 检测、Passkey 规则和修改记录。独立服务位于同级 `../super-power-browser-admin-server`。

```powershell
npm ci
npm run dev
npm run build
```

开发地址 `http://127.0.0.1:3200`，`/api` 默认代理到独立运营服务 `http://127.0.0.1:18082`。复制 `.env.example` 为 `.env.local` 可修改 `VITE_API_PROXY_TARGET`；不要将数据库密码、运营密码放入 `VITE_*` 变量。

保存发布采用组版本号，发生并发修改时提示比较服务器最新数据；切换未保存页面会提醒。默认值只影响新店铺或明确恢复默认，平台站点保留原 ID，既有店铺入口不批量重写。运营登录使用独立运营账号，不能使用客户企业账号。

发布 `dist/` 到静态服务，SPA 路由回退到 `index.html`；按 [Nginx 示例](deploy/nginx.conf.example) 代理 `/api/admin/` 和客户端只读接口 `/api/runtime/config` 到运营后端。前端和服务端无需和原业务服务一起打包。

上游与许可见 [UPSTREAM.md](../UPSTREAM.md)、[LICENSE](../LICENSE)。
