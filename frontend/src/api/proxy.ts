import api from "./client";
import type {
  BatchImportResult,
  FetchPreviewItem,
  ProxyApiConfig,
  ProxyHealthLog,
  ProxyItem,
  ProxyListResponse,
  ProxyStats,
  TunnelProxyConfig,
} from "../types/proxy";

export interface ProxyFilters {
  protocol?: string;
  provider?: string;
  status?: string;
}

export const fetchProxies = async (
  skip = 0,
  limit = 20,
  filters?: ProxyFilters,
  search?: string,
): Promise<ProxyListResponse> => {
  const params: Record<string, string | number> = { skip, limit };
  if (filters?.protocol) params.protocol = filters.protocol;
  if (filters?.provider) params.provider = filters.provider;
  if (filters?.status) params.status = filters.status;
  if (search) params.search = search;
  const { data } = await api.get<ProxyListResponse>("/proxy", { params });
  return data;
};

export const fetchProxyStats = async (): Promise<ProxyStats> => {
  const { data } = await api.get<ProxyStats>("/proxy/stats");
  return data;
};

export const addProxy = async (payload: {
  ip: string;
  port: number;
  protocol: string;
  region?: string;
}): Promise<ProxyItem> => {
  const { data } = await api.post<ProxyItem>("/proxy", payload);
  return data;
};

export const deleteProxy = async (id: number): Promise<void> => {
  await api.delete(`/proxy/${id}`);
};

export const toggleProxy = async (id: number): Promise<ProxyItem> => {
  const { data } = await api.patch<ProxyItem>(`/proxy/${id}/toggle`);
  return data;
};

export const refreshProxyPool = async (
  protocol = "https",
  count = 5,
): Promise<{ status: string; protocol: string; count: number }> => {
  const { data } = await api.post<{
    status: string;
    protocol: string;
    count: number;
  }>("/proxy/refresh", null, { params: { protocol, count } });
  return data;
};

export const batchImportProxies = async (
  lines: string[],
  providerName: string,
): Promise<BatchImportResult> => {
  const { data } = await api.post<BatchImportResult>("/proxy/batch", {
    lines,
    provider_name: providerName,
  });
  return data;
};

export const checkProxy = async (id: number): Promise<void> => {
  await api.post(`/proxy/${id}/check`);
};

export const batchCheckProxies = async (ids: number[]): Promise<void> => {
  await api.post("/proxy/batch-check", ids);
};

export const fetchProxyPreview = async (
  baseUrl: string,
  params: { key: string; value: string }[],
): Promise<{ items: FetchPreviewItem[]; url: string }> => {
  const { data } = await api.post<{ items: FetchPreviewItem[]; url: string }>(
    "/proxy/fetch-preview",
    { base_url: baseUrl, params },
  );
  return data;
};

// ── API Config CRUD ──────────────────────────────────────────

export const fetchApiConfigs = async (): Promise<ProxyApiConfig[]> => {
  const { data } = await api.get<ProxyApiConfig[]>("/proxy/api-configs");
  return data;
};

export const createApiConfig = async (payload: {
  name: string;
  base_url: string;
  params_json: string;
}): Promise<ProxyApiConfig> => {
  const { data } = await api.post<ProxyApiConfig>("/proxy/api-configs", payload);
  return data;
};

export const updateApiConfig = async (
  id: number,
  payload: { name: string; base_url: string; params_json: string },
): Promise<ProxyApiConfig> => {
  const { data } = await api.put<ProxyApiConfig>(`/proxy/api-configs/${id}`, payload);
  return data;
};

export const deleteApiConfig = async (id: number): Promise<void> => {
  await api.delete(`/proxy/api-configs/${id}`);
};

export const fetchProxyLogs = async (
  proxyId: number,
  skip = 0,
  limit = 20,
): Promise<{ total: number; items: ProxyHealthLog[] }> => {
  const { data } = await api.get<{ total: number; items: ProxyHealthLog[] }>(
    `/proxy/${proxyId}/logs`,
    { params: { skip, limit } },
  );
  return data;
};

// ── Tunnel Config CRUD ───────────────────────────────────────

export const fetchTunnelConfigs = async (): Promise<TunnelProxyConfig[]> => {
  const { data } = await api.get<TunnelProxyConfig[]>("/proxy/tunnel-configs");
  return data;
};

export const createTunnelConfig = async (payload: {
  name: string;
  protocol: string;
  host: string;
  port: number;
  username?: string;
  password?: string;
}): Promise<TunnelProxyConfig> => {
  const { data } = await api.post<TunnelProxyConfig>("/proxy/tunnel-configs", payload);
  return data;
};

export const updateTunnelConfig = async (
  id: number,
  payload: {
    name: string;
    protocol: string;
    host: string;
    port: number;
    username?: string;
    password?: string;
  },
): Promise<TunnelProxyConfig> => {
  const { data } = await api.put<TunnelProxyConfig>(`/proxy/tunnel-configs/${id}`, payload);
  return data;
};

export const deleteTunnelConfig = async (id: number): Promise<void> => {
  await api.delete(`/proxy/tunnel-configs/${id}`);
};

