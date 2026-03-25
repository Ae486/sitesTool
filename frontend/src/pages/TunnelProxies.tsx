import { DeleteOutlined, EditOutlined, PlusOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { motion } from "framer-motion";
import { useState } from "react";
import {
  createTunnelConfig,
  deleteTunnelConfig,
  fetchTunnelConfigs,
  updateTunnelConfig,
} from "../api/proxy";
import { containerVariants, itemVariants } from "../constants/animations";
import type { TunnelProxyConfig } from "../types/proxy";

const TunnelProxiesPage = () => {
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<TunnelProxyConfig | null>(null);
  const [form] = Form.useForm();
  const queryClient = useQueryClient();

  const { data: configs = [], isLoading } = useQuery({
    queryKey: ["tunnelConfigs"],
    queryFn: fetchTunnelConfigs,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["tunnelConfigs"] });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ protocol: "http" });
    setModalOpen(true);
  };

  const openEdit = (config: TunnelProxyConfig) => {
    setEditing(config);
    form.setFieldsValue({
      name: config.name,
      protocol: config.protocol,
      host: config.host,
      port: config.port,
      username: config.username,
    });
    setModalOpen(true);
  };

  const saveMutation = useMutation({
    mutationFn: async (values: {
      name: string;
      protocol: string;
      host: string;
      port: number;
      username?: string;
      password?: string;
    }) => {
      if (editing) {
        return updateTunnelConfig(editing.id, values);
      }
      return createTunnelConfig(values);
    },
    onSuccess: () => {
      message.success(editing ? "配置已更新" : "配置已创建");
      setModalOpen(false);
      form.resetFields();
      setEditing(null);
      invalidate();
    },
    onError: () => message.error("保存失败"),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteTunnelConfig,
    onSuccess: () => { message.success("已删除"); invalidate(); },
    onError: () => message.error("删除失败"),
  });

  const columns: ColumnsType<TunnelProxyConfig> = [
    {
      title: "配置名称",
      dataIndex: "name",
      key: "name",
      render: (v: string) => <Typography.Text strong>{v}</Typography.Text>,
    },
    {
      title: "协议",
      dataIndex: "protocol",
      key: "protocol",
      width: 90,
      render: (v: string) => (
        <Tag style={{ background: "#f4f4f5", color: "#18181b", border: "none" }}>
          {v.toUpperCase()}
        </Tag>
      ),
    },
    {
      title: "服务器地址",
      dataIndex: "host",
      key: "host",
      render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    },
    {
      title: "端口",
      dataIndex: "port",
      key: "port",
      width: 90,
    },
    {
      title: "用户名",
      dataIndex: "username",
      key: "username",
      width: 120,
      render: (v?: string) => v || <span style={{ color: "#d1d5db" }}>-</span>,
    },
    {
      title: "操作",
      key: "actions",
      width: 120,
      render: (_, r) => (
        <Space size="small">
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEdit(r)}
          />
          <Popconfirm
            title="确认删除"
            description="删除后无法恢复"
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
    <motion.div variants={containerVariants} initial="hidden" animate="show">
      <motion.div
        variants={itemVariants}
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 24,
        }}
      >
        <Typography.Title level={4} style={{ margin: 0 }}>
          隧道代理配置
        </Typography.Title>
        <Button icon={<PlusOutlined />} size="large" onClick={openCreate}>
          新建配置
        </Button>
      </motion.div>

      <motion.div
        variants={itemVariants}
        className="glass-panel"
        style={{ padding: 24, borderRadius: 16 }}
      >
        <Table
          dataSource={configs}
          columns={columns}
          rowKey="id"
          loading={isLoading}
          pagination={false}
        />
      </motion.div>

      <Modal
        title={editing ? "编辑隧道配置" : "新建隧道配置"}
        open={modalOpen}
        onCancel={() => { setModalOpen(false); form.resetFields(); setEditing(null); }}
        onOk={() => form.validateFields().then((v) => saveMutation.mutate(v))}
        confirmLoading={saveMutation.isPending}
        centered
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="配置名称" rules={[{ required: true, message: "请输入名称" }]}>
            <Input placeholder="我的隧道代理" />
          </Form.Item>
          <Form.Item name="protocol" label="协议" rules={[{ required: true }]}>
            <Select
              options={[
                { value: "http", label: "HTTP" },
                { value: "socks5", label: "SOCKS5" },
              ]}
            />
          </Form.Item>
          <Form.Item name="host" label="服务器地址" rules={[{ required: true, message: "请输入服务器地址" }]}>
            <Input placeholder="proxy.example.com" />
          </Form.Item>
          <Form.Item name="port" label="端口" rules={[{ required: true, message: "请输入端口" }]}>
            <InputNumber min={1} max={65535} style={{ width: "100%" }} placeholder="8080" />
          </Form.Item>
          <Form.Item name="username" label="用户名（可选）">
            <Input placeholder="username" />
          </Form.Item>
          <Form.Item name="password" label="密码（可选）">
            <Input.Password placeholder={editing ? "不填则保留原密码" : ""} />
          </Form.Item>
        </Form>
      </Modal>
    </motion.div>
  );
};

export default TunnelProxiesPage;
