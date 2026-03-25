import { useQuery } from "@tanstack/react-query";
import { Descriptions, Drawer, Table, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useState } from "react";
import { fetchProxyLogs } from "../../../api/proxy";
import type { ProxyHealthLog, ProxyItem } from "../../../types/proxy";
import { useProxyContext } from "../context";

interface DetailDrawerProps {
  proxyId: number | null;
  open: boolean;
  onClose: () => void;
}

const getLatencyColor = (ms: number) => {
  if (ms < 200) return "#16a34a";
  if (ms < 500) return "#ca8a04";
  return "#dc2626";
};

const formatTime = (dateStr: string | null | undefined) => {
  if (!dateStr) return "-";
  const d = new Date(dateStr);
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, "0")}`;
};

const formatRelativeTime = (dateStr: string | null) => {
  if (!dateStr) return "-";
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "刚刚";
  if (mins < 60) return `${mins} 分钟前`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} 小时前`;
  return `${Math.floor(hours / 24)} 天前`;
};

const getStatusTag = (r: ProxyItem) => {
  if (!r.last_checked_at) return <Tag color="default">待检测</Tag>;
  if (r.last_check_success === true) return <Tag color="success">可用</Tag>;
  if (r.last_check_success === false) return <Tag color="error">不可用</Tag>;
  if (r.success_count > 0) return <Tag color="success">可用</Tag>;
  return <Tag color="error">不可用</Tag>;
};

const LatencyChart = ({ logs }: { logs: ProxyHealthLog[] }) => {
  const points = logs
    .filter((l) => l.success)
    .slice(0, 20)
    .reverse();
  if (points.length < 2) return <span style={{ color: "#9ca3af" }}>数据不足</span>;

  const maxLatency = Math.max(...points.map((p) => p.latency_ms), 1);
  const w = 440;
  const h = 120;
  const coords = points
    .map((p, i) => {
      const x = (i / (points.length - 1)) * w;
      const y = h - (p.latency_ms / maxLatency) * (h - 10) - 5;
      return `${x},${y}`;
    })
    .join(" ");

  return (
    <div style={{ padding: "16px 0" }}>
      <svg width={w} height={h} style={{ overflow: "visible" }}>
        <polyline
          points={coords}
          fill="none"
          stroke="#3b82f6"
          strokeWidth={2}
          strokeLinejoin="round"
        />
        {points.map((p, i) => {
          const x = (i / (points.length - 1)) * w;
          const y = h - (p.latency_ms / maxLatency) * (h - 10) - 5;
          return <circle key={i} cx={x} cy={y} r={3} fill="#3b82f6" />;
        })}
      </svg>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          fontSize: 11,
          color: "#9ca3af",
          marginTop: 4,
        }}
      >
        <span>{formatTime(points[0]?.checked_at)}</span>
        <span>{formatTime(points[points.length - 1]?.checked_at)}</span>
      </div>
    </div>
  );
};

const logColumns: ColumnsType<ProxyHealthLog> = [
  {
    title: "时间",
    dataIndex: "checked_at",
    key: "checked_at",
    width: 110,
    render: (v: string) => (
      <span style={{ color: "#71717a", fontSize: 13 }}>{formatRelativeTime(v)}</span>
    ),
  },
  {
    title: "状态",
    dataIndex: "success",
    key: "success",
    width: 70,
    render: (v: boolean) =>
      v ? <Tag color="success">成功</Tag> : <Tag color="error">失败</Tag>,
  },
  {
    title: "延迟",
    dataIndex: "latency_ms",
    key: "latency_ms",
    width: 80,
    render: (v: number, r: ProxyHealthLog) =>
      r.success ? (
        <span style={{ color: getLatencyColor(v), fontWeight: 500 }}>{v}ms</span>
      ) : (
        <span style={{ color: "#d1d5db" }}>-</span>
      ),
  },
  {
    title: "错误信息",
    dataIndex: "error_message",
    key: "error_message",
    ellipsis: true,
    render: (v: string | null) =>
      v ? (
        <Typography.Text type="danger" ellipsis={{ tooltip: v }} style={{ fontSize: 13 }}>
          {v}
        </Typography.Text>
      ) : (
        <span style={{ color: "#d1d5db" }}>-</span>
      ),
  },
];

const DetailDrawer = ({ proxyId, open, onClose }: DetailDrawerProps) => {
  const { proxyData } = useProxyContext();
  const [logPagination, setLogPagination] = useState({ skip: 0, limit: 10 });

  const proxy = proxyData?.items?.find((i) => i.id === proxyId) ?? null;

  const { data: logsData, isLoading: logsLoading } = useQuery({
    queryKey: ["proxyLogs", proxyId, logPagination],
    queryFn: () => fetchProxyLogs(proxyId!, logPagination.skip, logPagination.limit),
    enabled: open && proxyId !== null,
  });

  return (
    <Drawer
      title="代理详情"
      open={open}
      onClose={onClose}
      width={520}
      destroyOnClose
    >
      {proxy && (
        <>
          <Descriptions column={2} size="small" bordered style={{ marginBottom: 20 }}>
            <Descriptions.Item label="地址" span={2}>
              <Typography.Text code>
                {proxy.ip}:{proxy.port}
              </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="协议">
              {proxy.protocol?.toUpperCase() || "-"}
            </Descriptions.Item>
            <Descriptions.Item label="地区">{proxy.region || "-"}</Descriptions.Item>
            <Descriptions.Item label="来源">{proxy.provider || "-"}</Descriptions.Item>
            <Descriptions.Item label="状态">{getStatusTag(proxy)}</Descriptions.Item>
            <Descriptions.Item label="平均延迟">
              <span style={{ color: getLatencyColor(proxy.avg_latency_ms), fontWeight: 500 }}>
                {proxy.avg_latency_ms}ms
              </span>
            </Descriptions.Item>
            <Descriptions.Item label="成功/失败">
              {proxy.success_count} / {proxy.fail_count}
            </Descriptions.Item>
          </Descriptions>

          <Tabs
            defaultActiveKey="logs"
            items={[
              {
                key: "logs",
                label: "检测日志",
                children: (
                  <Table
                    dataSource={logsData?.items ?? []}
                    columns={logColumns}
                    rowKey="id"
                    loading={logsLoading}
                    size="small"
                    pagination={{
                      total: logsData?.total ?? 0,
                      pageSize: logPagination.limit,
                      current: Math.floor(logPagination.skip / logPagination.limit) + 1,
                      size: "small",
                      showTotal: (total) => `共 ${total} 条`,
                      onChange: (page, pageSize) =>
                        setLogPagination({ skip: (page - 1) * pageSize, limit: pageSize }),
                    }}
                  />
                ),
              },
              {
                key: "chart",
                label: "延迟趋势",
                children: <LatencyChart logs={logsData?.items ?? []} />,
              },
            ]}
          />
        </>
      )}
    </Drawer>
  );
};

export default DetailDrawer;
