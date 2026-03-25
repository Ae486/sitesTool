import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { message } from "antd";
import {
  batchCheckProxies,
  checkProxy,
  deleteProxy,
  fetchProxies,
  fetchProxyStats,
} from "../../api/proxy";
import type { ProxyFilters } from "../../api/proxy";
import type { ProxyItem, ProxyStats, ProxyListResponse } from "../../types/proxy";

interface ProxyContextValue {
  stats: ProxyStats | undefined;
  proxyData: ProxyListResponse | undefined;
  isLoading: boolean;
  pagination: { skip: number; limit: number };
  setPagination: React.Dispatch<React.SetStateAction<{ skip: number; limit: number }>>;
  filters: ProxyFilters;
  updateFilter: (key: keyof ProxyFilters, value: string | undefined) => void;
  searchTerm: string;
  setSearchTerm: React.Dispatch<React.SetStateAction<string>>;
  checkingIds: Set<number>;
  selectedRowKeys: number[];
  setSelectedRowKeys: React.Dispatch<React.SetStateAction<number[]>>;
  deleteMutation: ReturnType<typeof useMutation<void, Error, number>>;
  handleCheck: (id: number) => Promise<void>;
  handleBatchCheck: () => Promise<void>;
  handleBatchDelete: () => Promise<void>;
  invalidateAll: () => void;
  scheduleRefreshes: () => void;
  getSelectedItems: () => ProxyItem[];
  handleExport: (format: "csv" | "json" | "txt") => void;
  onRowClick?: (id: number) => void;
  setOnRowClick: (fn: ((id: number) => void) | undefined) => void;
}

const ProxyContext = createContext<ProxyContextValue | null>(null);

export const useProxyContext = () => {
  const ctx = useContext(ProxyContext);
  if (!ctx) throw new Error("useProxyContext must be used within ProxyProvider");
  return ctx;
};

export const ProxyProvider = ({ children }: { children: ReactNode }) => {
  const [pagination, setPagination] = useState({ skip: 0, limit: 20 });
  const [filters, setFilters] = useState<ProxyFilters>({});
  const [searchTerm, setSearchTerm] = useState("");
  const [checkingIds, setCheckingIds] = useState<Set<number>>(new Set());
  const checkingSnap = useRef<Map<number, string | null>>(new Map());
  const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
  const [onRowClick, setOnRowClick] = useState<((id: number) => void) | undefined>(undefined);
  const queryClient = useQueryClient();

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ["proxies"] });
    queryClient.invalidateQueries({ queryKey: ["proxyStats"] });
  };

  const scheduleRefreshes = () => {
    [3000, 8000, 15000, 25000].forEach((ms) => setTimeout(invalidateAll, ms));
  };

  const { data: stats } = useQuery({
    queryKey: ["proxyStats"],
    queryFn: fetchProxyStats,
    refetchInterval: 30_000,
  });

  const { data: proxyData, isLoading } = useQuery({
    queryKey: ["proxies", pagination, filters, searchTerm],
    queryFn: () => fetchProxies(pagination.skip, pagination.limit, filters, searchTerm || undefined),
    refetchInterval: 30_000,
  });

  const deleteMutation = useMutation({
    mutationFn: deleteProxy,
    onSuccess: () => { message.success("代理已删除"); invalidateAll(); },
    onError: () => message.error("删除失败"),
  });

  const handleCheck = async (id: number) => {
    const cur = proxyData?.items?.find((i) => i.id === id);
    checkingSnap.current.set(id, cur?.last_checked_at ?? null);
    setCheckingIds((prev) => new Set(prev).add(id));
    try {
      await checkProxy(id);
      message.success("后台检测已触发");
      scheduleRefreshes();
      setTimeout(() => {
        checkingSnap.current.delete(id);
        setCheckingIds((prev) => { const s = new Set(prev); s.delete(id); return s; });
      }, 30000);
    } catch {
      message.error("触发检测失败");
      checkingSnap.current.delete(id);
      setCheckingIds((prev) => { const s = new Set(prev); s.delete(id); return s; });
    }
  };

  const handleBatchCheck = async () => {
    if (!selectedRowKeys.length) { message.warning("请先选择代理"); return; }
    const ids = [...selectedRowKeys];
    const items = proxyData?.items ?? [];
    ids.forEach((id) => {
      const cur = items.find((i) => i.id === id);
      checkingSnap.current.set(id, cur?.last_checked_at ?? null);
      setCheckingIds((prev) => new Set(prev).add(id));
    });
    try {
      await batchCheckProxies(ids);
      message.success(`已触发 ${ids.length} 条代理检测`);
      scheduleRefreshes();
      setTimeout(() => { checkingSnap.current.clear(); setCheckingIds(new Set()); }, 60000);
    } catch {
      message.error("批量检测失败");
      checkingSnap.current.clear();
      setCheckingIds(new Set());
    }
  };

  const handleBatchDelete = async () => {
    if (!selectedRowKeys.length) return;
    try {
      await Promise.all(selectedRowKeys.map((id) => deleteProxy(id)));
      message.success(`已删除 ${selectedRowKeys.length} 条代理`);
      setSelectedRowKeys([]);
      invalidateAll();
    } catch {
      message.error("批量删除失败");
    }
  };

  useEffect(() => {
    if (!proxyData?.items || checkingIds.size === 0) return;
    const done: number[] = [];
    for (const id of checkingIds) {
      const item = proxyData.items.find((i) => i.id === id);
      if (item && item.last_checked_at !== checkingSnap.current.get(id)) done.push(id);
    }
    if (done.length) {
      setCheckingIds((prev) => {
        const next = new Set(prev);
        done.forEach((id) => { next.delete(id); checkingSnap.current.delete(id); });
        return next;
      });
    }
  }, [proxyData, checkingIds]);

  const getSelectedItems = (): ProxyItem[] => {
    const items = proxyData?.items ?? [];
    if (!selectedRowKeys.length) return items;
    const keySet = new Set(selectedRowKeys);
    return items.filter((i) => keySet.has(i.id));
  };

  const handleExport = (format: "csv" | "json" | "txt") => {
    const items = getSelectedItems();
    if (!items.length) { message.warning("无数据可导出"); return; }

    let content: string;
    let mimeType: string;
    let ext: string;

    if (format === "csv") {
      content = ["IP,Port,Protocol,Region,Provider,Status,AvgLatencyMs"]
        .concat(items.map((r) => {
          const status = !r.last_checked_at ? "pending" : r.last_check_success ? "available" : "unavailable";
          return `${r.ip},${r.port},${r.protocol ?? ""},${r.region ?? ""},${r.provider ?? ""},${status},${r.avg_latency_ms}`;
        })).join("\n");
      mimeType = "text/csv";
      ext = "csv";
    } else if (format === "json") {
      content = JSON.stringify(items.map((r) => ({
        ip: r.ip, port: r.port, protocol: r.protocol, region: r.region,
        provider: r.provider, avg_latency_ms: r.avg_latency_ms,
      })), null, 2);
      mimeType = "application/json";
      ext = "json";
    } else {
      content = items.map((r) => {
        const parts = [r.ip, String(r.port)];
        if (r.protocol) parts.push(r.protocol);
        if (r.region) parts.push(r.region);
        return parts.join(":");
      }).join("\n");
      mimeType = "text/plain";
      ext = "txt";
    }

    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url; a.download = `proxies.${ext}`; a.click();
    URL.revokeObjectURL(url);
  };

  const updateFilter = (key: keyof ProxyFilters, value: string | undefined) => {
    setFilters((prev) => ({ ...prev, [key]: value || undefined }));
    setPagination((prev) => ({ ...prev, skip: 0 }));
  };

  return (
    <ProxyContext.Provider value={{
      stats, proxyData, isLoading, pagination, setPagination,
      filters, updateFilter, searchTerm, setSearchTerm,
      checkingIds, selectedRowKeys, setSelectedRowKeys,
      deleteMutation, handleCheck, handleBatchCheck, handleBatchDelete,
      invalidateAll, scheduleRefreshes, getSelectedItems, handleExport,
      onRowClick, setOnRowClick,
    }}>
      {children}
    </ProxyContext.Provider>
  );
};
