import { request } from './api';

export type AccelerationStatus = 'auto' | 'direct' | 'unsupported' | 'unavailable' | 'inactive';
export interface AccelerationPool { id: string; name: string }
export interface RelayCandidate {
  id: string; poolId: string; poolName: string; name: string; region: string;
  protocol: string; host: string; port: number;
}
export interface CustomerProxy {
  id: string; name: string; tenantId: string; tenantName: string; createdBy: string; creatorName: string;
  host: string; port: number; protocol: string; username: string; hasPassword: boolean;
  region: string; provider: string; mapIp: string; mapPort?: number | null; webType: number;
  expiresAt?: string | null; recycled: boolean; createdAt?: string | null; boundCount: number;
  checkConnected?: boolean | null; checkIp?: string | null; checkCountryName?: string | null;
  checkLatencyMs?: number | null; checkMessage?: string | null; checkedAt?: string | null;
  relayBindingRevision: number; relayCount: number; relayAvailableCount: number;
  acceleration: { status: AccelerationStatus; reason: string; pools: AccelerationPool[]; candidates: RelayCandidate[] };
}
export interface ProxyBinding {
  id: string; name: string; platform: string; ownerId: string; ownerName: string;
  recycled: boolean; lastOpenedAt?: string | null; createdAt?: string | null;
}
export interface CustomerProxyDetail extends CustomerProxy { bindings: ProxyBinding[] }
export interface CustomerProxyQuery {
  query: string; tenantId: string; protocol: string; webType: number; recycled: string;
  connectionStatus: string; accelerationStatus: string; page: number; size: number;
}
export interface BoundRelayNode extends RelayCandidate {
  enabled: boolean; poolEnabled: boolean; authorized: boolean; available: boolean;
  status?: string; reason?: string; boundProxyCount: number;
}
export interface ProxyRelaySnapshot { revision: number; items: BoundRelayNode[]; available: BoundRelayNode[] }
export interface RelayBatchResult { items: { deviceId: string; revision: number; nodeIds: string[] }[] }
const relayPath = (id: string) => `/customer-proxies/${encodeURIComponent(id)}/relays`;
const write = (method: string, body: unknown) => ({ method, body: JSON.stringify(body) });
export const customerProxyApi = {
  list: (filters: CustomerProxyQuery, signal?: AbortSignal) => {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(filters)) if (value !== '') query.set(key, String(value));
    return request<{ items: CustomerProxy[]; total: number }>(`/customer-proxies?${query}`, { signal });
  },
  detail: (id: string, signal?: AbortSignal) => request<CustomerProxyDetail>(`/customer-proxies/${encodeURIComponent(id)}`, { signal }),
  relays: (id: string, signal?: AbortSignal) => request<ProxyRelaySnapshot>(relayPath(id), { signal }),
  saveRelays: (id: string, revision: number, nodeIds: string[]) => request<ProxyRelaySnapshot>(relayPath(id), write('PUT', { revision, nodeIds })),
  batchRelays: (deviceIds: string[], nodeIds: string[], mode: 'replace' | 'append', revisions: Record<string, number>) => request<RelayBatchResult>('/customer-proxies/relays/batch', write('POST', { deviceIds, nodeIds, mode, revisions })),
  allocateRelays: (deviceIds: string[], count: number, revisions: Record<string, number>) => request<RelayBatchResult>('/customer-proxies/relays/allocate', write('POST', { deviceIds, count, revisions })),
};

export function relayAvailability(node: BoundRelayNode) {
  if (node.available) return '可用';
  if (node.authorized === false) return '企业授权不可用';
  if (node.poolEnabled === false) return '所属分组已停用';
  if (node.enabled === false) return '节点已停用';
  return '当前不可用';
}
