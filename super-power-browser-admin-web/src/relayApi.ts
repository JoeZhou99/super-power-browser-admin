import { request } from './api';
import type { CustomerProxy } from './customerProxyApi';

// AEAD methods supported by the desktop Shadowsocks transport.
export const relaySsMethods = ['aes-128-gcm', 'aes-256-gcm', 'chacha20-poly1305', 'chacha20-ietf-poly1305', 'xchacha20-poly1305', 'xchacha20-ietf-poly1305', '2022-blake3-aes-128-gcm', '2022-blake3-aes-256-gcm', '2022-blake3-chacha20-poly1305'] as const;

export interface RelayPool {
  id: string; name: string; code: string; remark: string; enabled: boolean; candidateLimit: number; revision: number;
  updatedAt?: string; nodeCount: number; grantCount: number;
}
export interface RelayNode {
  id: string; poolId: string; name: string; region: string; protocol: 'http' | 'https' | 'socks5' | 'ss';
  host: string; port: number; username: string; hasPassword: boolean; enabled: boolean;
  sortOrder: number; revision: number; updatedAt?: string; boundProxyCount: number;
  proxyOptions?: { encryption?: string; tls?: { serverName: string; allowInsecure: boolean } };
}
export interface RelayGrant { tenantId: string; tenantName?: string; enabled: boolean; expiresAt?: string | null }
export interface RelayTenant { id: string; name: string }
export interface PoolInput { name: string; code?: string; remark?: string; enabled: boolean; candidateLimit: number; revision?: number }
export interface RelayNodeListItem extends RelayNode { poolName: string; poolCode: string; poolEnabled: boolean }
export type RelayNodeProxyBinding = Pick<CustomerProxy, 'id' | 'name' | 'tenantId' | 'tenantName' | 'host' | 'port' | 'protocol' | 'webType' | 'recycled' | 'expiresAt' | 'relayBindingRevision'> & { sortOrder: number; boundAt: string };
export interface NodeInput {
  name: string; region: string; protocol: RelayNode['protocol']; host: string; port: number;
  username: string; password?: string; enabled: boolean; sortOrder: number; revision?: number;
  proxyOptions?: RelayNode['proxyOptions'];
}
const path = (id: string) => `/relay-pools/${encodeURIComponent(id)}`;
const body = (method: string, value: unknown) => ({ method, body: JSON.stringify(value) });
export const relayApi = {
  pools: () => request<{ items: RelayPool[] }>('/relay-pools'),
  allNodes: (filters: { query: string; poolId: string; enabled: string; protocol: string; page: number; size: number }, signal?: AbortSignal) => {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(filters)) if (value !== '') query.set(key, String(value));
    return request<{ items: RelayNodeListItem[]; total: number }>(`/relay-nodes?${query}`, { signal });
  },
  bindings: (id: string, page: number, size: number, signal?: AbortSignal) => request<{ items: RelayNodeProxyBinding[]; total: number }>(`/relay-nodes/${encodeURIComponent(id)}/bindings?page=${page}&size=${size}`, { signal }),
  createPool: (value: PoolInput) => request<RelayPool>('/relay-pools', body('POST', value)),
  updatePool: (id: string, value: PoolInput) => request<RelayPool>(path(id), body('PUT', value)),
  nodes: (id: string) => request<{ items: RelayNode[] }>(`${path(id)}/nodes`),
  createNode: (id: string, value: NodeInput) => request<RelayNode>(`${path(id)}/nodes`, body('POST', value)),
  updateNode: (id: string, nodeId: string, value: NodeInput) => request<RelayNode>(`${path(id)}/nodes/${encodeURIComponent(nodeId)}`, body('PUT', value)),
  grants: (id: string) => request<{ revision: number; items: RelayGrant[] }>(`${path(id)}/grants`),
  saveGrants: (id: string, revision: number, items: RelayGrant[]) => request<RelayPool>(`${path(id)}/grants`, body('PUT', {
    revision, items: items.map(({ tenantId, enabled, expiresAt }) => ({ tenantId, enabled, expiresAt: expiresAt || null })),
  })),
  tenants: (query: string) => request<{ items: RelayTenant[] }>(`/relay-tenants?query=${encodeURIComponent(query)}`),
};
