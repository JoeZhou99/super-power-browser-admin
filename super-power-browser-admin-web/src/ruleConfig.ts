export type RuleConfigKey = "network-routing" | "access-policy" | "network-checks" | "passkey-rules";
export type RuleListKey = "directDomains" | "resourceDomains" | "cdnDomains" | "domains" | "exceptions"
  | "proxyProviders" | "localProviders" | "loginMatches" | "registerMatches";
export type RuleValues = { enabled: boolean; mode?: "blacklist" | "whitelist" } & Partial<Record<RuleListKey, string[]>>;
export interface RuleGroup {
  key: RuleListKey;
  title: string;
  hint: string;
  placeholder: string;
  format: "domain" | "wildcard-domain" | "https-prefix" | "https-provider" | "passkey";
  max: number;
}
export interface RulePage {
  description: string;
  effect: string;
  groups: RuleGroup[];
}

export const rulePages: Record<RuleConfigKey, RulePage> = {
  "network-routing": {
    description: "对应旧版域名走本地网络、域名加速设置，统一维护店铺的域名分流。",
    effect: "下次打开店铺时读取。代理已绑定平台中继的店铺不应用本页分流，所有网站均使用绑定的代理出口。其他店铺没有专用资源代理时，资源与 CDN 域名继续使用主代理出口。",
    groups: [
      { key: "directDomains", title: "直连域名", hint: "代理未绑定平台中继的店铺访问这些域名时使用本地网络；请确认域名适合使用本机出口。可用 *.example.com 匹配子域名。", placeholder: "例如 assets.example.com", format: "wildcard-domain", max: 100 },
      { key: "resourceDomains", title: "资源代理域名", hint: "保留已有资源分流规则。未提供专用资源代理时使用店铺主代理出口；代理绑定平台中继时统一使用绑定出口。", placeholder: "例如 *.example.com", format: "wildcard-domain", max: 100 },
      { key: "cdnDomains", title: "CDN 资源域名", hint: "不改变资源网址。未提供专用资源代理时使用店铺主代理出口；代理绑定平台中继时统一使用绑定出口。", placeholder: "例如 cdn.example.com", format: "wildcard-domain", max: 100 },
    ],
  },
  "access-policy": {
    description: "对应旧版黑白名单管理，维护网站访问范围和例外网址。",
    effect: "作为缺省策略在下次打开店铺时读取，已打开的窗口不变。专属非空配置优先；启用白名单时至少填写一个域名，黑名单为空时不限制访问。",
    groups: [
      { key: "domains", title: "网站域名", hint: "填写纯域名，会同时匹配该域名及其子域名，不需要添加 *.。", placeholder: "例如 example.com", format: "domain", max: 100 },
      { key: "exceptions", title: "例外网址", hint: "匹配此 HTTPS 网址前缀的页面可访问。请填写完整网址，不使用通配符。", placeholder: "例如 https://example.com/help/", format: "https-prefix", max: 100 },
    ],
  },
  "network-checks": {
    description: "维护系统常规配置中的 IP 检测地址，用于检查店铺代理和本机网络。",
    effect: "客户端下次进行 IP 检测时读取。启用时两组都需至少一个地址，停用时沿用现有检测地址。",
    groups: [
      { key: "proxyProviders", title: "代理 IP 检测", hint: "使用店铺代理请求这些地址。服务需返回客户端可识别的 IP 信息，可调整尝试顺序。", placeholder: "例如 https://ifconfig.me/ip", format: "https-provider", max: 10 },
      { key: "localProviders", title: "本机 IP 检测", hint: "通过本机网络请求这些地址。请使用公开可访问、可信的 HTTPS IP 检测服务。", placeholder: "例如 https://ipwho.is/", format: "https-provider", max: 10 },
    ],
  },
  "passkey-rules": {
    description: "维护现有 Passkey 功能适用的登录页面和注册页面。",
    effect: "作为缺省规则在下次打开店铺时读取，已打开的窗口不变，专属非空配置优先。启用时登录、注册两组都需填写；增加网址不会自动增加平台支持。",
    groups: [
      { key: "loginMatches", title: "登录页面", hint: "填写 HTTPS 页面匹配规则。域名可使用 *.example.com，路径可使用 *；无需填写正则表达式。", placeholder: "例如 https://*.amazon.com/ap/signin*", format: "passkey", max: 100 },
      { key: "registerMatches", title: "注册页面", hint: "填写注册 Passkey 时使用的页面匹配规则，需包含域名和以 / 开始的路径。", placeholder: "例如 https://*.amazon.com/ax/claim/webauthn*", format: "passkey", max: 100 },
    ],
  },
};

function publicDomain(value: string, wildcard: boolean) {
  const input = wildcard && value.startsWith("*.") ? value.slice(2) : value;
  if (/[/:@\\?#%*]/.test(input)) return false;
  let host: string;
  try { host = new URL(`https://${input}/`).hostname; } catch { return false; }
  const labels = host.split(".");
  return host.length <= 253 && labels.length >= 2
    && labels.every((part) => /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/i.test(part))
    && /^[a-z][a-z0-9-]+$/i.test(labels.at(-1)!)
    && !["localhost", "local", "localdomain", "lan", "internal", "test", "invalid", "onion"].includes(labels.at(-1)!.toLowerCase())
    && host !== "home.arpa" && !host.endsWith(".home.arpa");
}

export function validateRule(group: RuleGroup, input: string): string {
  const value = input.trim();
  if (!value || value.length > 2048 || /[\\\s\u0000-\u001f\u007f-\u009f]/.test(value)) return "请填写有效规则，不能含空白、反斜杠或控制字符，长度不能超过 2048 个字符。";
  if (group.format === "domain" || group.format === "wildcard-domain") {
    return publicDomain(value, group.format === "wildcard-domain") ? ""
      : group.format === "domain" ? "请输入公共域名，不含协议、端口、路径或通配符。" : "请输入公共域名，可在开头使用 *.，不含协议、端口或路径。";
  }
  if (group.format === "passkey") {
    const match = /^https:\/\/(\*\.)?([^/:?#]+)(\/[^#]*)$/.exec(value);
    return match && publicDomain(match[2], false) ? "" : "请使用 https://域名/路径，可用 *. 匹配子域名、* 匹配路径，不能包含端口或片段。";
  }
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password || value.includes("#") || url.port || !publicDomain(url.hostname, false)) throw new Error();
    if (group.format === "https-prefix" && (value.includes("?") || value.includes("*"))) throw new Error();
    return "";
  } catch {
    return group.format === "https-prefix" ? "请输入公共 HTTPS 网址前缀，不含账号密码、查询参数、片段、通配符或非 443 端口。"
      : "请输入公共域名的 HTTPS 检测地址，不含账号密码、片段或非 443 端口。";
  }
}

export function normalizedRule(group: RuleGroup, input: string) {
  const value = input.trim();
  if (group.format === "domain" || group.format === "wildcard-domain") {
    const prefix = value.startsWith("*.") ? "*." : "";
    try { return prefix + new URL(`https://${prefix ? value.slice(2) : value}/`).hostname; } catch { return value.toLowerCase(); }
  }
  if (group.format === "passkey") {
    return value.replace(/^(https:\/\/)(\*\.)?([^/]+)/, (_, scheme: string, wildcard: string | undefined, host: string) => {
      try { return scheme + (wildcard || "") + new URL(`https://${host}/`).hostname; } catch { return scheme + (wildcard || "") + host.toLowerCase(); }
    });
  }
  try { return new URL(value).href; } catch { return value; }
}
