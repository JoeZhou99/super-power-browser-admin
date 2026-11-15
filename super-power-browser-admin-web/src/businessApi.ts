import { request } from './api';

export interface BusinessPage<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  integrationRequired?: boolean;
  message?: string;
}

export interface MemberSummary {
  id: string;
  accountId: string;
  accountName: string;
  userId: string;
  username: string;
  phone?: string | null;
  role: string;
  status: string;
  permissions?: unknown;
  createdAt?: string | null;
}

export interface AccountQuotaSummary {
  userId: string;
  accountId?: string;
  tenantId?: string;
  accountName?: string;
  username: string;
  displayName?: string | null;
  departmentId?: string | null;
  departmentName?: string | null;
  quota?: number;
  used?: number;
  available?: number;
  limitCount?: number;
  usedCount?: number;
  remainingCount?: number;
  tenantName?: string;
  enabled?: boolean;
  note?: string | null;
  revision?: number;
  updatedAt?: string | null;
}

export interface OrderSummary {
  id: string;
  accountId: string;
  accountName: string;
  orderNo: string;
  businessType: string;
  state: string;
  paymentState: string;
  fulfillmentState: string;
  currency: string;
  grossAmount: string | number;
  dueAmount: string | number;
  expiresAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface SkillSummary {
  id: string;
  name: string;
  version: string;
  categoryName?: string;
  platformName?: string;
  source: string;
  author?: string;
  scope?: string;
  reviewStatus: string;
  reviewReason?: string;
  fileMd5?: string;
  status: string;
  downloadCount: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface BusinessQuery {
  query: string;
  status: string;
  page: number;
  size: number;
}

function params<T extends object>(query: T, extra: Record<string, string> = {}) {
  const values = new URLSearchParams();
  Object.entries({ ...query, ...extra }).forEach(([key, value]) => {
    if (value !== '') values.set(key, String(value));
  });
  return values.toString();
}

export const businessApi = {
  members: (query: BusinessQuery, signal?: AbortSignal) =>
    request<BusinessPage<MemberSummary>>(`/members?${params(query)}`, { signal }),
  accountQuotas: (query: Omit<BusinessQuery, 'status'> & { departmentId?: string; tenantId?: string }, signal?: AbortSignal) =>
    request<BusinessPage<AccountQuotaSummary>>(`/account-quotas?${params(query, { departmentId: query.departmentId || '', tenantId: query.tenantId || '' })}`, { signal }),
  updateAccountQuota: (userId: string, body: { limitCount: number; enabled?: boolean; note?: string; revision?: number }) =>
    request<AccountQuotaSummary>(`/account-quotas/${encodeURIComponent(userId)}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  orders: (query: BusinessQuery & { paymentState?: string; businessType?: string }, signal?: AbortSignal) =>
    request<BusinessPage<OrderSummary>>(`/orders?${params(query, { paymentState: query.paymentState || '', businessType: query.businessType || '' })}`, { signal }),
  skills: (query: BusinessQuery & { reviewStatus?: string; source?: string }, signal?: AbortSignal) =>
    request<BusinessPage<SkillSummary>>(`/skills?${params(query, { reviewStatus: query.reviewStatus || '', source: query.source || '' })}`, { signal }),
};
