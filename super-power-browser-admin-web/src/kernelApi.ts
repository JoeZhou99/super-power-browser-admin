import { request } from "./api";

export type KernelState = "DRAFT" | "PUBLISHED" | "DISABLED" | string;
export interface KernelRelease {
  id: string;
  version: string;
  platform: "win32" | "darwin" | string;
  arch: "x64" | "arm64" | string;
  fullVersion: string;
  sha256: string;
  archiveBytes: number;
  descriptor: Record<string, unknown>;
  storageType: string;
  storageKey: string;
  objectVersion?: string;
  state: KernelState;
  recommended: boolean;
  revision: number;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}
export interface KernelUploadTicket {
  url: string;
  method?: "PUT";
  headers: Record<string, string>;
  expiresAt: string;
}

export const kernelApi = {
  list: (signal?: AbortSignal) => request<{ items: KernelRelease[] }>("/kernels", { signal }),
  create: (descriptor: Record<string, unknown>) => request<KernelRelease>("/kernels", {
    method: "POST", body: JSON.stringify({ descriptor }),
  }),
  upload: (id: string) => request<KernelUploadTicket>(`/kernels/${encodeURIComponent(id)}/upload`, { method: "POST" }),
  download: (id: string) => request<{ url: string; expiresAt: string }>(`/kernels/${encodeURIComponent(id)}/download`),
  publish: (id: string, revision: number, recommended: boolean) => request<KernelRelease>(`/kernels/${encodeURIComponent(id)}/publish`, {
    method: "POST", body: JSON.stringify({ revision, recommended }),
  }),
  recommend: (id: string) => request<KernelRelease>(`/kernels/${encodeURIComponent(id)}/recommend`, { method: "POST" }),
  disable: (id: string) => request<KernelRelease>(`/kernels/${encodeURIComponent(id)}/disable`, { method: "POST" }),
};

export function kernelPlatform(value: string) {
  return value === "win32" ? "Windows" : value === "darwin" ? "macOS" : value;
}

export function kernelBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return "-";
  const units = ["B", "KB", "MB", "GB"];
  let amount = value, index = 0;
  while (amount >= 1024 && index < units.length - 1) { amount /= 1024; index += 1; }
  return `${amount.toFixed(index ? 2 : 0)} ${units[index]}`;
}
