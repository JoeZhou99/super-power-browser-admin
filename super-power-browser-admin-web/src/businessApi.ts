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

function params(query: BusinessQuery, extra: Record<string, string> = {}) {
  const values = new URLSearchParams();
  Object.entries({ ...query, ...extra }).forEach(([key, value]) => {
    if (value !== '') values.set(key, String(value));
  });
  return values.toString();
}

export const businessApi = {
  members: (query: BusinessQuery, signal?: AbortSignal) =>
    request<BusinessPage<MemberSummary>>(`/members?${params(query)}`, { signal }),
  orders: (query: BusinessQuery & { paymentState?: string; businessType?: string }, signal?: AbortSignal) =>
    request<BusinessPage<OrderSummary>>(`/orders?${params(query, { paymentState: query.paymentState || '', businessType: query.businessType || '' })}`, { signal }),
  skills: (query: BusinessQuery & { reviewStatus?: string; source?: string }, signal?: AbortSignal) =>
    request<BusinessPage<SkillSummary>>(`/skills?${params(query, { reviewStatus: query.reviewStatus || '', source: query.source || '' })}`, { signal }),
};
