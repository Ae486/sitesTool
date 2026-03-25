import { DeleteOutlined, SyncOutlined } from "@ant-design/icons";
import { Button, Empty, Popconfirm, Space, Switch, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toggleProxy } from "../../../api/proxy";
import type { ProxyItem } from "../../../types/proxy";
import { useProxyContext } from "../context";

const getLatencyColor = (ms: number) => {
  if (ms < 200) return "#16a34a";
  if (ms < 500) return "#ca8a04";
  return "#dc2626";
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

const getLatencyDisplay = (r: ProxyItem) => {
  if (!r.last_checked_at) return <span style={{ color: "#d1d5db" }}>-</span>;
  if (r.last_check_success === false || (r.last_check_success == null && r.success_count === 0))
    return <span style={{ color: "#dc2626", fontWeight: 500 }}>超时</span>;
  if (r.avg_latency_ms === 0)
    return <span style={{ color: "#dc2626", fontWeight: 500 }}>超时</span>;
  return (
    <span style={{ color: getLatencyColor(r.avg_latency_ms), fontWeight: 500 }}>
      {r.avg_latency_ms}ms
    </span>
  );
};

const DataTable = () => {
  const {
    proxyData, isLoading, checkingIds, selectedRowKeys, setSelectedRowKeys,
    pagination, setPagination, deleteMutation, handleCheck, onRowClick,
  } = useProxyContext();

  const queryClient = useQueryClient();
  const toggleMutation = useMutation({
    mutationFn: toggleProxy,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["proxies"] });
      queryClient.invalidateQueries({ queryKey: ["proxyStats"] });
    },
  });

  const columns: ColumnsType<ProxyItem> = [
    {
      title: "IP:端口",
      key: "address",
      width: 180,
      render: (_, r) => (
        <Typography.Text code style={{ fontSize: 13 }}>
          {r.ip}:{r.port}
        </Typography.Text>
      ),
    },
    {
      title: "协议",
      dataIndex: "protocol",
      key: "protocol",
      width: 80,
      responsive: ["md"],
      render: (v: string | null) => (
        <Tag style={{ background: "#f4f4f5", color: "#18181b", border: "none" }}>
          {v?.toUpperCase() || "-"}
        </Tag>
      ),
    },
    {
      title: "地区",
      dataIndex: "region",
      key: "region",
      width: 90,
      responsive: ["md"],
      sorter: (a, b) => (a.region ?? "").localeCompare(b.region ?? ""),
      render: (v: string | null) => v || <span style={{ color: "#d1d5db" }}>-</span>,
    },
    {
      title: "来源",
      dataIndex: "provider",
      key: "provider",
      width: 90,
      responsive: ["md"],
      render: (v: string | null) =>
        !v || v === "manual" ? (
          <Tag style={{ background: "#f4f4f5", color: "#71717a", border: "none" }}>手动</Tag>
        ) : (
          <Tag style={{ background: "#dbeafe", color: "#1d4ed8", border: "none" }}>{v}</Tag>
        ),
    },
    {
      title: "状态",
      key: "status",
      width: 80,
      render: (_, r) => checkingIds.has(r.id)
        ? <Tag color="processing">检测中</Tag>
        : getStatusTag(r),
    },
    {
      title: "延迟",
      key: "latency",
      width: 80,
      responsive: ["sm"],
      sorter: (a, b) => a.avg_latency_ms - b.avg_latency_ms,
      render: (_, r) => getLatencyDisplay(r),
    },
    {
      title: "最后检测",
      dataIndex: "last_checked_at",
      key: "last_checked",
      width: 100,
      responsive: ["lg"],
      sorter: (a, b) => {
        const ta = a.last_checked_at ? new Date(a.last_checked_at).getTime() : 0;
        const tb = b.last_checked_at ? new Date(b.last_checked_at).getTime() : 0;
        return ta - tb;
      },
      render: (v: string | null) => (
        <span style={{ color: "#71717a", fontSize: 13 }}>{formatRelativeTime(v)}</span>
      ),
    },
    {
      title: "操作",
      key: "actions",
      width: 120,
      render: (_, r) => (
        <Space size="small">
          <Switch
            size="small"
            checked={r.is_active}
            loading={toggleMutation.isPending && toggleMutation.variables === r.id}
            onChange={() => toggleMutation.mutate(r.id)}
          />
          <Button
            size="small"
            icon={<SyncOutlined />}
            loading={checkingIds.has(r.id)}
            onClick={() => handleCheck(r.id)}
            title="触发检测"
          />
          <Popconfirm
            title="确认删除"
            description="删除后无法恢复，确定要删除该代理吗？"
            onConfirm={() => deleteMutation.mutate(r.id)}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              loading={deleteMutation.isPending && deleteMutation.variables === r.id}
            />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Table
      dataSource={proxyData?.items ?? []}
      columns={columns}
      rowKey="id"
      loading={isLoading}
      rowClassName={(record) => checkingIds.has(record.id) ? "proxy-row-checking" : ""}
      onRow={(record) => ({
        onClick: () => onRowClick?.(record.id),
        style: { cursor: onRowClick ? "pointer" : undefined },
      })}
      rowSelection={{
        selectedRowKeys,
        onChange: (keys) => setSelectedRowKeys(keys as number[]),
      }}
      locale={{
        emptyText: (
          <Empty
            description="暂无代理数据"
            style={{ padding: "40px 0" }}
          >
            <span style={{ fontSize: 12, color: "#9ca3af" }}>
              点击右上角"添加代理"按钮开始，支持手动录入或 API 订阅导入
            </span>
          </Empty>
        ),
      }}
      pagination={{
        total: proxyData?.total ?? 0,
        pageSize: pagination.limit,
        current: Math.floor(pagination.skip / pagination.limit) + 1,
        showSizeChanger: true,
        showTotal: (total) => `共 ${total} 条`,
        onChange: (page, pageSize) =>
          setPagination({ skip: (page - 1) * pageSize, limit: pageSize }),
      }}
    />
  );
};

export default DataTable;
