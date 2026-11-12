import { reactive } from "vue";

export type ConfigKey =
  | "timezones"
  | "languages"
  | "resolutions"
  | "browser-defaults"
  | "platform-sites"
  | "network-routing"
  | "access-policy"
  | "network-checks"
  | "relay-pools"
  | "passkey-rules";
export interface AdminUser {
  id: string;
  username: string;
}
export interface DictionaryEntry {
  value: string;
  label: string;
  enabled: boolean;
  sortOrder: number;
}
export interface PlatformEntry {
  id: string;
  categoryName: string;
  platformName: string;
  siteName: string;
  url: string;
  enabled: boolean;
  sortOrder: number;
}
export interface Defaults {
  timezone: string;
  language: string;
  resolution: string;
}
export interface ConfigDocument<TValues = Defaults, TEntry = DictionaryEntry | PlatformEntry> {
  key: ConfigKey;
  title?: string;
  revision: number;
  updatedAt?: string;
  entries?: TEntry[];
  values?: TValues;
}
export interface ConfigSummary {
  key: ConfigKey;
  title: string;
  revision: number;
  updatedAt?: string;
  itemCount: number;
}
export interface AuditEntry {
  id: string;
  key: ConfigKey;
  operator: string;
  createdAt: string;
  revision: number;
  before: unknown;
  after: unknown;
}
export const configTitles: Record<ConfigKey, string> = {
  timezones: "时区",
  languages: "语言",
  resolutions: "窗口尺寸",
  "browser-defaults": "环境默认值",
  "platform-sites": "平台站点",
  "network-routing": "域名分流",
  "access-policy": "访问策略",
  "network-checks": "IP 检测",
  "relay-pools": "中继分组",
  "passkey-rules": "Passkey 规则",
};
const tokenKey = "super-power-operations-token";
export const auth = reactive<{ user: AdminUser | null }>({ user: null });
export const hasToken = () => Boolean(sessionStorage.getItem(tokenKey));
export function clearSession() {
  sessionStorage.removeItem(tokenKey);
  auth.user = null;
}
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

export async function request<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body) headers.set("Content-Type", "application/json");
  const token = sessionStorage.getItem(tokenKey);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`/api/admin${path}`, {
    ...init,
    headers,
    credentials: "omit",
  });
  if (response.status === 401 && path !== "/auth/login") {
    clearSession();
    window.dispatchEvent(new Event("admin-session-expired"));
  }
  const text = await response.text();
  let data: any;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    throw new ApiError(
      response.status,
      "接口返回内容无法识别，请检查服务和反向代理。",
    );
  }
  if (!response.ok) {
    throw new ApiError(
      response.status,
      data?.message || `请求失败（${response.status}）`,
    );
  }
  return data as T;
}
export async function login(username: string, password: string) {
  const result = await request<{
    token: string;
    expiresAt: string;
    user: AdminUser;
  }>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
  sessionStorage.setItem(tokenKey, result.token);
  auth.user = result.user;
}
export async function loadMe() {
  auth.user = await request<AdminUser>("/auth/me");
  return auth.user;
}
export async function logout() {
  try {
    await request("/auth/logout", { method: "POST" });
  } finally {
    clearSession();
  }
}
export const getConfig = <TValues = Defaults, TEntry = DictionaryEntry | PlatformEntry>(key: ConfigKey) =>
  request<ConfigDocument<TValues, TEntry>>(`/configs/${key}`);
export const saveConfig = <TValues, TEntry>(document: ConfigDocument<TValues, TEntry>) =>
  request<ConfigDocument<TValues, TEntry>>(`/configs/${document.key}`, {
    method: "PUT",
    body: JSON.stringify(
      document.values
        ? { revision: document.revision, values: document.values }
        : { revision: document.revision, entries: document.entries },
    ),
  });
export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "操作失败，请稍后重试。";
}
export function formatDate(value?: string) {
  if (!value) return "尚未修改";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString("zh-CN", { hour12: false });
}
