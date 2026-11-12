# SuperPower 运营配置服务

本服务位于 `super-power-browser-admin/super-power-browser-admin-server`，与同仓库的 `super-power-browser-admin-web` 配套。使用 Java 21、Spring Boot 4、MyBatis，独立构建为可执行 JAR；不依赖业务服务的源码、Maven 父项目或 JAR。

## 数据与职责

服务与 `super-power-browser-server` 连接同一数据库。原有五组配置（时区、语言、窗口尺寸、环境默认值和平台站点）仍由原业务服务直接读取。新增网络分流、访问控制、网络检测和 Passkey 规则四组配置，由更新后的桌面客户端通过本服务的运行时接口读取；原业务服务无需更改或重新构建。

数据库结构继续由业务服务的 Flyway 迁移管理。本服务不运行 Flyway、不执行 DDL、不创建另一张迁移历史表。首次启动只读检查配置表、客户会话表、全局版本、中继表、code/remark 字段、设备节点绑定表与绑定版本，并要求业务 Flyway 历史存在 V28 的成功记录。V28 新增具体节点绑定，已有代理默认没有绑定，不把企业授权转成绑定。空库必须先由业务服务初始化。检查通过后仅补缺失配置组，不覆盖已有配置、平台站点 ID、版本、账号或审计记录。后续启动本服务不要求业务服务进程正在运行，只要求共享数据库可连接且已完成所需迁移。

管理前端将 `/api/admin` 指向本服务。原有通用配置写入实现继续使用同一全局锁、组版本和审计表。中继池管理仅在本服务提供写入接口，业务服务负责租户授权校验和运行连接下发。

## 构建与启动

```powershell
cd super-power-browser-admin-server
mvn clean verify
Copy-Item .env.example .env
# 编辑 .env，填写与业务服务相同的 DB_URL / DB_USER / DB_PASSWORD。
./scripts/start.ps1
```

默认端口 `18082`，避免与官方后台服务的 `18081` 冲突。环境变量 `OPS_PORT` 或脚本 `-Port` 可覆盖。生产也可以直接使用 `java -jar target/super-power-browser-admin-server-0.1.0.jar`；直接启动时由进程环境提供配置，只有 PowerShell 脚本会读取 `.env`。

健康检查为 `GET /api/health`，返回 `{"status":"ok","service":"operations"}`。`/api/admin/health` 同样可用。建议通过同站点反向代理 `/api/admin`，不要在前端变量中放数据库或管理员密码。

本地若需要连接 H2，驱动已包含在 JAR 中；必须显式提供可共享的 JDBC URL，例如现有 H2 TCP 数据库。普通嵌入式 H2 file 不能由两个 JVM 同时打开；本服务不会修改原项目启动脚本或数据库连接方式。生产使用同一 MySQL 数据库。

## 运营身份与配置发布

运营账号与客户账号分离。设置 `OPS_ADMIN_USERNAME` 和 `OPS_ADMIN_PASSWORD` 仅用于数据库尚无运营账号时初始化；现有账号不会因环境变量改变而重置。密码要求 12–72 UTF-8 字节，并包含字母、数字和符号。已有账号可以直接登录，token 仍保存为散列，八小时失效且支持退出撤销。

保持原有接口协议：`POST /api/admin/auth/login`、`GET /auth/me`、`POST /auth/logout`、`GET /configs`、`GET/PUT /configs/{key}`、`GET /config-audit`（后面各路径均带 `/api/admin` 前缀）。客户 token 无管理权限，运营 token 不能在此服务访问客户接口。

保存必须带读取时的组 `revision`，冲突返回 409。配置更新、全局版本和审计同事务提交。已作为默认值的选项不能停用；现有平台站点通过停用下架，不能删除或改 ID。配置变更不批量重写历史店铺的指纹或入口网址。

## 桌面运行时配置

`GET /api/runtime/config` 使用原业务登录返回的客户 Bearer token。本服务只读查询共享 `auth_session` 与 `app_user`，沿用原服务的 SHA-256 token 散列、会话有效期和用户启用/未删除条件。原客户退出、会话撤销、账号停用或删除后，此接口立即拒绝对应 token；不创建第二套客户登录。运营 token 不能访问该接口，客户 token 也不能获得 `/api/admin` 权限。

返回 `schemaVersion: 1`、全局 `revision` 和四个对象 `networkRouting / accessPolicy / networkChecks / passkeyRules`，每个对象对应以下 `values`。读取使用同一只读事务，不返回账号、密码或审计，响应带 `Cache-Control: no-store`。桌面仅应用 `enabled=true` 的组；桌面客户端自身的本地缓存与降级逻辑由桌面项目管理。生产反向代理须将 `/api/runtime/config` 同样路由到本服务。

| 配置 key | values 字段 | 发布约束 |
| --- | --- | --- |
| `network-routing` | `enabled, directDomains, resourceDomains, cdnDomains` | 每类 0–100 条公共纯域名，可用 `*.example.com` |
| `access-policy` | `enabled, mode, domains, exceptions` | mode 为 blacklist/whitelist；域名每组最多 100 条，不使用 `*`，客户端自动涵盖子域；启用白名单时域名非空；例外为 HTTPS URL 前缀 |
| `network-checks` | `enabled, proxyProviders, localProviders` | 每类最多 10 条 HTTPS 公共 DNS 地址；启用时两类都非空；默认沿用原 NetworkInfo 地址 |
| `passkey-rules` | `enabled, loginMatches, registerMatches` | 每类最多 100 条 Chrome HTTPS match pattern；启用时两类都非空，如 `https://*.amazon.com/ap/signin*` |

所有新增组必须明确提供布尔 `enabled`；拒绝未知字段、错误类型、重复值及超限列表。域名最长 253 字符、每段最多 63 字符，不接受 IP 字面量或本地域名。网络检测地址允许查询参数，拒绝凭据、片段、非标准端口；例外 URL 不允许查询参数，保存时规范化域名、默认端口和路径。Passkey 不允许 `<all_urls>`、全 host 通配符、HTTP、凭据、显式端口或片段。

公网主机校验只验证 DNS 主机名格式，不在保存时发起 DNS 或外部 HTTP 请求；它不保证由店铺代理解析的目标地址。客户端检测请求不携带运营/客户 token 或 Cookie，且不跟随重定向；客户 Bearer token 仅发送到显式配置的本运行时接口。

## 平台中继池

中继池通过 `/api/admin/relay-pools` 管理，与租户的自有出口代理分开保存。上线前，先发布业务服务并完成截至 `V28__device_relay_bindings` 的迁移，再发布运营服务；运营服务不执行数据库迁移，也不修改旧迁移历史。两服务 `.env` 中的 `APP_DATA_KEY` 必须相同（32 字节随机密钥的 Base64），用于 AES-GCM 中继密码加密。缺少密钥时读取公共节点信息不受影响，但保存新密码返回 503。

`GET/POST /relay-pools`、`GET/PUT /relay-pools/{id}` 管理组；`GET/POST /relay-pools/{id}/nodes`、`PUT /relay-pools/{id}/nodes/{nodeId}` 管理节点；`GET/PUT /relay-pools/{id}/grants` 管理企业授权；`GET /relay-tenants?query=...` 查找企业，以上路径均带 `/api/admin` 前缀。组和节点采用停用方式下架，不提供删除。组修改带组 `revision`，节点修改带节点 `revision`，授权整体保存带组 `revision`；节点和授权变更也递增组版本。

节点支持 HTTP、HTTPS、SOCKS5、SS。HTTP/HTTPS/SOCKS5 必须配置账号密码，SS 使用密码与 `proxyOptions.encryption`。节点列表仅返回 `hasPassword`，不返回明文或密文。编辑省略 `password` 保留原值，空值不允许清除。密码最多 600 UTF-8 字节，SOCKS5 最多 255 字节；切换到 SOCKS5 时需要重填密码。HTTPS 可提供 `proxyOptions.tls.serverName/allowInsecure`，其他任意 Xray 配置不接受。审计只记录脱敏的公开节点视图和凭据是否变更。服务只保存连接配置，不负责安装 VPS 代理软件，也不从 API 服务器探测节点。

客户新增/编辑/导入不接收 `relayPoolId`、`relayNodeIds` 或 `nodeIds`。企业授权只决定可绑定范围，运营后台为每台自有代理绑定最多 8 个具体节点。业务后端保留直连原代理作为第 0 条候选，再按绑定顺序下发仍获授权且节点、分组都启用的中继。静态 HTTP/HTTPS/SOCKS5/SS/VMess/VLESS 出口支持绑定；动态、不支持协议或没有可用绑定时只有直连。历史 `candidateLimit` 字段不再限制显式绑定。已开店通过业务 `GET /api/environments/{id}/connection` 获取最新授权快照，不重复记录开店；客户端移除撤销线路后仍需验证出口才能恢复业务访问。

`GET/PUT /api/admin/customer-proxies/{id}/relays` 读取或替换绑定，响应 `{revision,items,available}`，写入 `{revision,nodeIds}`；版本冲突返回 409。已失效的绑定保留在 `items` 供移除，但不能再次提交为有效绑定。`POST /api/admin/customer-proxies/relays/batch` 支持 `{deviceIds,nodeIds,mode:'replace'|'append',revisions?}`，`POST /api/admin/customer-proxies/relays/allocate` 支持 `{deviceIds,count:1..4,revisions?}`，两者最多 100 台并以事务原子提交。分配按当前绑定数量均衡挑选具体节点并替换原绑定。传入 `revisions` 时必须覆盖全部设备；响应 `{items:[{deviceId,revision,nodeIds}]}`。节点列表返回 `boundProxyCount`，`GET /api/admin/relay-nodes/{id}/bindings?page=1&size=20` 返回绑定代理公共信息，不包含密码。

节点认证仍为共享凭据。授权撤销会阻止后续下发，客户端轮询可撤销运行候选，但不能从网关层使已复制的凭据即时失效；后续可使用独立企业网关凭据或短期令牌。

新增只读 `GET /api/admin/customer-proxies` 与 `/{id}` 展示客户代理、绑定店铺和公共候选；只允许运营身份访问，不读取密码、不探测客户地址。`GET /api/admin/relay-nodes` 支持节点平铺分页筛选。分组原接口增加 `code`（唯一）和 `remark`；旧请求缺少 code 时创建默认用 ID，更新省略字段时保留原值。前端入口分别是 `/proxy/list`、`/proxy/relays`、`/proxy/groups`。
