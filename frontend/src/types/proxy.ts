export interface ProxyItem {
  id: number;
  ip: string;
  port: number;
  protocol: string | null;
  region: string | null;
  provider: string | null;
  is_active: boolean;
  success_count: number;
  fail_count: number;
  avg_latency_ms: number;
  last_check_success: boolean | null;
  last_checked_at: string | null;
}

export interface ProxyListResponse {
  total: number;
  items: ProxyItem[];
}

export interface ProxyStats {
  total: number;
  active: number;
  inactive: number;
  pool_size: number;
}

export interface ProxyApiConfig {
  id: number;
  name: string;
  base_url: string;
  params_json: string;
  created_at: string;
  updated_at: string;
}

export interface TunnelProxyConfig {
  id: number;
  name: string;
  protocol: "http" | "socks5";
  host: string;
  port: number;
  username?: string;
  created_at: string;
  updated_at: string;
}

export interface BatchImportResult {
  imported: number;
  skipped: number;
}

export interface FetchPreviewItem {
  ip: string;
  port: number;
}

export interface ProxyHealthLog {
  id: number;
  success: boolean;
  latency_ms: number;
  checked_at: string;
  error_message: string | null;
}
