import { CheckCircleOutlined, CloseCircleOutlined, DeleteOutlined, EyeOutlined, WarningOutlined } from "@ant-design/icons";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Image,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Timeline,
  Typography,
  message,
} from "antd";
import { motion } from "framer-motion";
import { useMemo, useState } from "react";
import { deleteHistory, fetchHistory, type HistoryRecord } from "../api/history";
import { fetchFlows } from "../api/flows";

import { containerVariants, itemVariants } from "../constants/animations";

const { Text } = Typography;

const HistoryPage = () => {
  // ... state hooks ...
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [selectedHistory, setSelectedHistory] = useState<HistoryRecord | null>(null);
  const [errorTypeFilter, setErrorTypeFilter] = useState<string | undefined>(undefined);

  // ... queries and mutations ...
  const {
    data: historyData,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ["history", errorTypeFilter],
    queryFn: () => fetchHistory(0, 50, errorTypeFilter),
  });

  const { data: flowsData } = useQuery({
    queryKey: ["flows"],
    queryFn: () => import("../api/flows").then((m) => m.fetchFlows()),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteHistory,
    onSuccess: () => {
      message.success("历史记录已删除");
      refetch();
    },
    onError: () => message.error("删除失败"),
  });

  // ... helpers ...
  const historyItems = historyData?.items ?? [];
  const errorTypeOptions = useMemo(() => {
    const values = new Set<string>();
    historyItems.forEach((record) => {
      record.error_types?.forEach((type) => values.add(type));
    });
    return Array.from(values).map((type) => ({ label: type, value: type }));
  }, [historyItems]);

  const buildScreenshotUrl = (path: string) => {
    const normalized = path.replace(/\\/g, "/");
    const segments = normalized.split("/");
    const filename = segments[segments.length - 1] ?? "";
    return filename ? `/screenshots/${filename}` : "";
  };

  const getFlowName = (flowId: number) => {
    const flow = flowsData?.items.find((f) => f.id === flowId);
    return flow?.name || `流程 #${flowId}`;
  };

  const getStatusColor = (status: string) => {
    switch (status.toLowerCase()) {
      case "success":
        return "success";
      case "failed":
        return "error";
      case "running":
        return "processing";
      default:
        return "default";
    }
  };

  const getStatusText = (status: string) => {
    switch (status.toLowerCase()) {
      case "success":
        return "成功";
      case "failed":
        return "失败";
      case "running":
        return "运行中";
      case "idle":
        return "空闲";
      default:
        return status;
    }
  };

  const formatDuration = (ms: number | null) => {
    if (!ms) return "-";
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(2)}s`;
  };

  const formatDateTime = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  };

  const columns = [
    {
      title: "执行时间",
      dataIndex: "started_at",
      key: "started_at",
      render: (text: string) => (
        <span style={{ fontWeight: 500 }}>{formatDateTime(text)}</span>
      ),
      width: 180,
    },
    {
      title: "流程名称",
      dataIndex: "flow_id",
      key: "flow_name",
      render: (flowId: number) => (
        <Tag color="blue" style={{ borderRadius: "4px" }}>
          {getFlowName(flowId)}
        </Tag>
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: string) => (
        <Tag color={getStatusColor(status)} style={{ borderRadius: "12px" }}>
          {getStatusText(status)}
        </Tag>
      ),
      width: 100,
    },
    {
      title: "耗时",
      dataIndex: "duration_ms",
      key: "duration",
      render: (ms: number | null) => (
        <span style={{ color: "var(--text-secondary)" }}>{formatDuration(ms)}</span>
      ),
      width: 100,
    },
    {
      title: "错误类型",
      dataIndex: "error_types",
      key: "error_types",
      render: (types: string[] = []) =>
        types.length ? (
          <Space size={4}>
            {types.map((type) => (
              <Tag color="volcano" key={type} style={{ borderRadius: "4px" }}>
                {type}
              </Tag>
            ))}
          </Space>
        ) : (
          <Text type="secondary">-</Text>
        ),
      width: 200,
    },
    {
      title: "操作",
      key: "actions",
      render: (record: HistoryRecord) => (
        <Space>
          <Button
            icon={<EyeOutlined />}
            size="small"
            onClick={() => {
              setSelectedHistory(record);
              setDetailModalOpen(true);
            }}
          >
            详情
          </Button>
          <Popconfirm
            title="确认删除"
            description="删除后无法恢复"
            onConfirm={() => deleteMutation.mutate(record.id)}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button
              icon={<DeleteOutlined />}
              size="small"
              danger
              loading={deleteMutation.isPending && deleteMutation.variables === record.id}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
      width: 150,
    },
  ];

  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="show"
    >
      <motion.div
        variants={itemVariants}
        style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}
      >
        <Typography.Title level={4} style={{ margin: 0 }}>
          执行历史
        </Typography.Title>
        <Space>
          <Select
            allowClear
            placeholder="按错误类型筛选"
            style={{ minWidth: 200 }}
            options={errorTypeOptions}
            value={errorTypeFilter}
            onChange={(value) => setErrorTypeFilter(value)}
          />
          <Button onClick={() => refetch()} disabled={isLoading}>
            刷新
          </Button>
        </Space>
      </motion.div>

      <motion.div variants={itemVariants} className="glass-panel" style={{ padding: 24, borderRadius: 16 }}>
        {historyItems.length === 0 ? (
          <Empty description="暂无执行记录" />
        ) : (
          <Table
            dataSource={historyItems}
            columns={columns}
            rowKey="id"
            loading={isLoading}
            pagination={{
              total: historyData?.total,
              pageSize: 50,
              showSizeChanger: false,
              showTotal: (total) => `共 ${total} 条`,
            }}
          />
        )}
      </motion.div>

      <Modal
        title="执行详情"
        open={detailModalOpen}
        onCancel={() => setDetailModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setDetailModalOpen(false)}>
            关闭
          </Button>,
        ]}
        width={900}
        centered
      >
        {selectedHistory && (
          <Space direction="vertical" style={{ width: "100%" }} size="large">
            {/* 基本信息 */}
            <Card size="small" title="基本信息" className="glass-card">
              <Descriptions column={2} size="small">
                <Descriptions.Item label="流程名称">
                  {getFlowName(selectedHistory.flow_id)}
                </Descriptions.Item>
                <Descriptions.Item label="执行状态">
                  <Tag color={getStatusColor(selectedHistory.status)}>
                    {getStatusText(selectedHistory.status)}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="错误类型" span={2}>
                  {selectedHistory.error_types?.length ? (
                    <Space size={4}>
                      {selectedHistory.error_types.map((type) => (
                        <Tag color="volcano" key={type}>
                          {type}
                        </Tag>
                      ))}
                    </Space>
                  ) : (
                    <Text type="secondary">-</Text>
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="开始时间">
                  {formatDateTime(selectedHistory.started_at)}
                </Descriptions.Item>
                <Descriptions.Item label="结束时间">
                  {selectedHistory.finished_at ? formatDateTime(selectedHistory.finished_at) : "-"}
                </Descriptions.Item>
                <Descriptions.Item label="执行耗时">
                  {formatDuration(selectedHistory.duration_ms)}
                </Descriptions.Item>
              </Descriptions>
            </Card>

            {selectedHistory.screenshot_files?.length ? (
              <Card size="small" title="截图汇总" className="glass-card">
                <Image.PreviewGroup>
                  <Space size="middle" wrap>
                    {selectedHistory.screenshot_files.map((file) => (
                      <Image
                        key={file}
                        src={buildScreenshotUrl(file)}
                        alt={file}
                        width={220}
                        style={{ borderRadius: 4 }}
                      />
                    ))}
                  </Space>
                </Image.PreviewGroup>
              </Card>
            ) : null}

            {/* 步骤执行详情 */}
            {selectedHistory.result_payload && (() => {
              try {
                const result = JSON.parse(selectedHistory.result_payload);
                const stepResults = result.step_results || [];

                if (stepResults.length > 0) {
                  return (
                    <Card size="small" title="步骤执行详情" className="glass-card">
                      <Timeline
                        items={stepResults.map((step: any, index: number) => {
                          const isSuccess = step.success;
                          const hasError = step.error;

                          return {
                            color: isSuccess ? "green" : "red",
                            dot: isSuccess ? (
                              <CheckCircleOutlined style={{ fontSize: "16px" }} />
                            ) : (
                              <CloseCircleOutlined style={{ fontSize: "16px" }} />
                            ),
                            children: (
                              <div>
                                <Space direction="vertical" style={{ width: "100%" }} size="small">
                                  {/* 步骤标题 */}
                                  <div>
                                    <Text strong style={{ fontSize: "14px" }}>
                                      步骤 {index + 1}: {step.step_type}
                                    </Text>
                                    <Tag
                                      color={isSuccess ? "success" : "error"}
                                      style={{ marginLeft: "8px" }}
                                    >
                                      {isSuccess ? "成功" : "失败"}
                                    </Tag>
                                    <Text type="secondary" style={{ marginLeft: "8px" }}>
                                      耗时: {formatDuration(step.duration_ms)}
                                    </Text>
                                  </div>

                                  {/* 成功消息 */}
                                  {isSuccess && step.message && (
                                    <Text type="secondary">{step.message}</Text>
                                  )}

                                  {/* 错误详情 */}
                                  {hasError && (
                                    <Alert
                                      message="错误详情"
                                      description={
                                        <Space direction="vertical" style={{ width: "100%" }}>
                                          <Text>{step.error}</Text>
                                          {step.screenshot_path && (
                                            <div>
                                              <Text strong>错误截图：</Text>
                                              <div style={{ marginTop: "8px" }}>
                                                <Image
                                                  src={buildScreenshotUrl(step.screenshot_path)}
                                                  alt="错误截图"
                                                  style={{ maxWidth: "100%" }}
                                                  placeholder={
                                                    <div style={{
                                                      background: "#f0f0f0",
                                                      padding: "20px",
                                                      textAlign: "center"
                                                    }}>
                                                      加载中...
                                                    </div>
                                                  }
                                                />
                                              </div>
                                            </div>
                                          )}
                                        </Space>
                                      }
                                      type="error"
                                      showIcon
                                      icon={<WarningOutlined />}
                                    />
                                  )}

                                  {/* 提取的数据 */}
                                  {step.extracted_data && (
                                    <div>
                                      <Text type="secondary">
                                        提取数据: {JSON.stringify(step.extracted_data)}
                                      </Text>
                                    </div>
                                  )}
                                </Space>
                              </div>
                            ),
                          };
                        })}
                      />
                    </Card>
                  );
                }
              } catch (e) {
                console.error("Failed to parse result_payload:", e);
              }
              return null;
            })()}

            {/* 总体错误信息 */}
            {selectedHistory.error_message && (
              <Alert
                message="执行失败"
                description={selectedHistory.error_message}
                type="error"
                showIcon
              />
            )}

            {/* 原始执行日志（折叠） */}
            {selectedHistory.result_payload && (
              <Card
                size="small"
                title="原始执行数据"
                style={{ marginTop: "16px" }}
                bodyStyle={{ maxHeight: "200px", overflow: "auto" }}
                className="glass-card"
              >
                <pre
                  style={{
                    background: "rgba(0,0,0,0.02)",
                    padding: "12px",
                    borderRadius: "4px",
                    fontSize: "12px",
                    margin: 0,
                  }}
                >
                  {JSON.stringify(JSON.parse(selectedHistory.result_payload), null, 2)}
                </pre>
              </Card>
            )}
          </Space>
        )}
      </Modal>
    </motion.div>
  );
};

export default HistoryPage;
