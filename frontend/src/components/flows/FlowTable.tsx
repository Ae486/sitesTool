import { DeleteOutlined, EditOutlined, PlayCircleOutlined, StopOutlined } from "@ant-design/icons";
import { Button, Popconfirm, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo } from "react";
import type { AutomationFlow } from "../../types/flow";

interface FlowTableProps {
  flows: AutomationFlow[];
  loading: boolean;
  siteNameMap: Map<number, string>;
  runningFlows: Set<number>;
  executingFlows: Set<number>;
  onEdit: (flow: AutomationFlow) => void;
  onDelete: (flowId: number) => void;
  onTrigger: (flowId: number) => void;
  onStop: (flowId: number) => void;
  deleteLoading: boolean;
  stopLoading: boolean;
  stoppingFlowId?: number;
}

const FlowTable = ({
  flows,
  loading,
  siteNameMap,
  runningFlows,
  executingFlows,
  onEdit,
  onDelete,
  onTrigger,
  onStop,
  deleteLoading,
  stopLoading,
  stoppingFlowId,
}: FlowTableProps) => {
  const columns: ColumnsType<AutomationFlow> = useMemo(
    () => [
      {
        title: "名称",
        dataIndex: "name",
        key: "name",
        width: 250,
        render: (text: string) => <span style={{ fontWeight: 600 }}>{text}</span>,
      },
      {
        title: "站点",
        key: "site",
        width: 200,
        render: (_, flow) => (
          <Tag
            color="default"
            style={{ borderRadius: "4px", border: "1px solid #e4e4e7", background: "#fafafa" }}
          >
            {siteNameMap.get(flow.site_id) ?? flow.site_id}
          </Tag>
        ),
      },
      {
        title: "状态",
        dataIndex: "last_status",
        key: "status",
        width: 120,
        render: (status: string) => (
          <Tag
            color={status === "success" ? "success" : status === "failed" ? "error" : "default"}
            style={{ borderRadius: "12px" }}
          >
            {status || "未执行"}
          </Tag>
        ),
      },
      {
        title: "调度",
        dataIndex: "cron_expression",
        key: "cron",
        width: 150,
        render: (value: string | null) => (
          <span style={{ color: "var(--text-secondary)" }}>{value || "手动触发"}</span>
        ),
      },
      {
        title: "操作",
        key: "actions",
        width: 280,
        render: (_, flow) => (
          <Space>
            <Button icon={<EditOutlined />} onClick={() => onEdit(flow)}>
              编辑
            </Button>
            <Popconfirm
              title="确认删除"
              description="删除后无法恢复，确定要删除这个流程吗？"
              onConfirm={() => onDelete(flow.id)}
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button icon={<DeleteOutlined />} danger loading={deleteLoading}>
                删除
              </Button>
            </Popconfirm>
            {runningFlows.has(flow.id) ? (
              <Button
                danger
                icon={<StopOutlined />}
                loading={stopLoading && stoppingFlowId === flow.id}
                onClick={() => onStop(flow.id)}
              >
                中断
              </Button>
            ) : (
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                loading={executingFlows.has(flow.id)}
                disabled={executingFlows.has(flow.id)}
                onClick={() => onTrigger(flow.id)}
              >
                执行
              </Button>
            )}
          </Space>
        ),
      },
    ],
    [siteNameMap, runningFlows, executingFlows, onEdit, onDelete, onTrigger, onStop, deleteLoading, stopLoading, stoppingFlowId]
  );

  return (
    <Table
      dataSource={flows}
      columns={columns}
      rowKey="id"
      loading={loading}
      pagination={{
        pageSize: 10,
        showTotal: (total) => `共 ${total} 条`,
      }}
    />
  );
};

export default FlowTable;
