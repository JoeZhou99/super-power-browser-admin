export interface ConfigDifference {
  name: string;
  server: string;
  draft: string;
}

const labels: Record<string, string> = {
  timezone: "默认时区", language: "默认语言", resolution: "默认窗口尺寸",
  entries: "配置项", rules: "规则", enabled: "状态", mode: "访问模式",
  label: "名称", name: "名称", value: "配置值", sortOrder: "排序",
  categoryName: "分类", platformName: "平台", siteName: "站点", url: "网址",
  pattern: "匹配规则", domain: "域名", action: "分流方式", target: "目标",
  directDomains: "直连域名", resourceDomains: "资源代理域名", cdnDomains: "CDN 资源域名",
  domains: "网站域名", exceptions: "例外网址", proxyProviders: "代理 IP 检测地址",
  localProviders: "本机 IP 检测地址", loginMatches: "登录页面", registerMatches: "注册页面",
  candidateLimit: "候选线路上限", host: "节点地址", port: "端口", protocol: "协议", region: "地区",
  username: "认证账号", hasPassword: "已设置密码", credentialChanged: "更新认证密码", proxyOptions: "连接参数",
  tls: "TLS", serverName: "服务器名称", allowInsecure: "允许未受信任证书", tenantId: "企业 ID", tenantName: "企业名称",
  expiresAt: "到期时间", items: "企业授权", revision: "版本", updatedAt: "更新时间", poolId: "线路组 ID",
};

function object(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown> : null;
}

function describe(value: unknown) {
  if (value === true) return "启用";
  if (value === false) return "停用";
  if (value === "blacklist") return "禁止名单中的网站";
  if (value === "whitelist") return "只允许名单中的网站";
  return value === null || value === undefined || value === "" ? "空" : String(value);
}

function fields(input: unknown) {
  if (typeof input === "string") {
    try { input = JSON.parse(input); } catch { input = null; }
  }
  const document = object(input);
  const result = new Map<string, { name: string; value: string }>();
  function walk(value: unknown, path: string, name: string) {
    if (Array.isArray(value)) {
      if (!value.length) result.set(path, { name, value: "空列表" });
      value.forEach((item, index) => {
        const row = object(item);
        const identity = row?.id || row?.value || index;
        const title = row?.name || row?.label || row?.domain || row?.pattern || row?.url || `第 ${index + 1} 项`;
        walk(item, `${path}/${identity}`, `${name} · ${title}`);
      });
    } else if (object(value)) {
      for (const [key, child] of Object.entries(object(value)!)) {
        if (key === "id") continue;
        walk(child, `${path}/${key}`, name ? `${name} / ${labels[key] || key}` : labels[key] || key);
      }
    } else result.set(path, { name, value: describe(value) });
  }
  if (document?.values) walk(document.values, "values", "");
  if (document?.entries) walk(document.entries, "entries", "配置项");
  if (document?.value !== undefined) walk(document.value, "value", "线路配置");
  return result;
}

export function configDifferences(before: unknown, after: unknown): ConfigDifference[] {
  const left = fields(before), right = fields(after);
  return [...new Set([...left.keys(), ...right.keys()])]
    .map((key) => ({
      name: right.get(key)?.name || left.get(key)?.name || key,
      server: left.get(key)?.value ?? "不存在",
      draft: right.get(key)?.value ?? "不存在",
    }))
    .filter((row) => row.server !== row.draft);
}
