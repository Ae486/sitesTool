import { DeleteOutlined, EditOutlined, PlusOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from "antd";
import { motion } from "framer-motion";
import { useState } from "react";
import { fetchTags, createTag as apiCreateTag, deleteTag as apiDeleteTag } from "../api/catalog";
import { createSite, deleteSite, fetchSites, updateSite } from "../api/sites";
import TagManager from "../components/TagManager";
import { containerVariants, itemVariants } from "../constants/animations";
import type { Site } from "../types/site";

const { Title, Text, Paragraph, Link } = Typography;

const tagPairColors = [
  { bg: "#E0F7FA", text: "#006064" },
  { bg: "#F1F8E9", text: "#33691E" },
  { bg: "#FFF8E1", text: "#F57F17" },
  { bg: "#F3E5F5", text: "#4A148C" },
  { bg: "#E8EAF6", text: "#1A237E" },
  { bg: "#FBE9E7", text: "#BF360C" },
  { bg: "#EDE7F6", text: "#311B92" },
  { bg: "#E1F5FE", text: "#01579B" },
];

const getTagTextColor = (bgColor?: string | null) => {
  if (!bgColor) return "#262626";
  const pair = tagPairColors.find((pair) => pair.bg === bgColor);
  return pair ? pair.text : "#262626";
};

const SitesPage = () => {
  const [modalOpen, setModalOpen] = useState(false);
  const [editingSite, setEditingSite] = useState<Site | null>(null);
  const [form] = Form.useForm();
  const queryClient = useQueryClient();

  const { data: sitesData, isLoading } = useQuery({ queryKey: ["sites"], queryFn: fetchSites });
  const { data: allTags = [], isLoading: tagsLoading } = useQuery({ queryKey: ["tags"], queryFn: fetchTags });

  const createSiteMutation = useMutation({
    mutationFn: createSite,
    onSuccess: () => {
      message.success("站点创建成功");
      handleCloseModal();
      queryClient.invalidateQueries({ queryKey: ["sites"] });
    },
    onError: () => message.error("创建站点失败，请重试。"),
  });

  const updateSiteMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: any }) => updateSite(id, payload),
    onSuccess: () => {
      message.success("站点更新成功");
      handleCloseModal();
      queryClient.invalidateQueries({ queryKey: ["sites"] });
    },
    onError: () => message.error("更新站点失败，请重试。"),
  });

  const deleteSiteMutation = useMutation({
    mutationFn: deleteSite,
    onSuccess: () => {
      message.success("站点已删除");
      queryClient.invalidateQueries({ queryKey: ["sites"] });
    },
    onError: () => message.error("删除站点失败，请重试。"),
  });

  const createTagMutation = useMutation({
    mutationFn: apiCreateTag,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["tags"] });
      // 刷新站点数据以确保标签关联正确显示
      queryClient.invalidateQueries({ queryKey: ["sites"] });
    },
    onError: (error: any) => {
      message.error("创建标签失败");
    },
  });

  const deleteTagMutation = useMutation({
    mutationFn: apiDeleteTag,
    onSuccess: () => {
      message.success("标签已删除");
      queryClient.invalidateQueries({ queryKey: ["tags"] });
      queryClient.invalidateQueries({ queryKey: ["sites"] });
    },
    onError: (error: any) => {
      message.error("删除标签失败");
    },
  });

  const columns = [
    {
      title: "名称",
      dataIndex: "name",
      key: "name",
      width: 180,
      render: (text: string) => <span style={{ fontWeight: 600 }}>{text}</span>,
    },
    {
      title: "URL",
      dataIndex: "url",
      key: "url",
      width: 220,
      render: (value: string) => (
        <Link href={value} target="_blank" rel="noreferrer">
          {value}
        </Link>
      ),
    },
    {
      title: "描述",
      dataIndex: "description",
      key: "description",
      width: 260,
      render: (value?: string) =>
        value ? (
          <Tooltip title={value}>
            <Paragraph ellipsis={{ rows: 2 }} style={{ marginBottom: 0 }}>
              {value}
            </Paragraph>
          </Tooltip>
        ) : (
          <Text type="secondary">-</Text>
        ),
    },
    {
      title: "标签",
      dataIndex: "tags",
      key: "tags",
      width: 220,
      render: (tags: Site["tags"]) => (
        <Space size={[4, 8]} wrap>
          {tags.length ? (
            tags.map((tag) => (
              <Tag
                key={tag.id}
                style={{
                  backgroundColor: tag.color || "#f0f0f0",
                  color: getTagTextColor(tag.color),
                  border: "none",
                }}
              >
                {tag.name}
              </Tag>
            ))
          ) : (
            <Text type="secondary">无</Text>
          )}
        </Space>
      ),
    },
    {
      title: "状态",
      dataIndex: "is_active",
      key: "is_active",
      width: 120,
      render: (value: boolean) => (
        <Tag color={value ? "success" : "error"} style={{ borderRadius: 12 }}>
          {value ? "正常" : "禁用"}
        </Tag>
      ),
    },
    {
      title: "操作",
      key: "actions",
      width: 220,
      render: (record: Site) => (
        <Space>
          <Button icon={<EditOutlined />} onClick={() => handleOpenModal(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除此站点？"
            description="此操作不可撤销。"
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => deleteSiteMutation.mutate(record.id)}
          >
            <Button icon={<DeleteOutlined />} danger loading={deleteSiteMutation.isPending}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const handleOpenModal = (site?: Site) => {
    setModalOpen(true);
    if (site) {
      setEditingSite(site);
      const tagIds = site.tags.map((tag) => tag.id);
      console.log("Opening modal for site:", site.name, "with tags:", site.tags, "tag_ids:", tagIds);
      form.setFieldsValue({
        name: site.name,
        url: site.url,
        description: site.description,
        tag_ids: tagIds,
        is_active: site.is_active,
      });
    } else {
      setEditingSite(null);
      form.resetFields();
      form.setFieldsValue({ tag_ids: [], is_active: true });
    }
  };

  const handleCloseModal = () => {
    setModalOpen(false);
    // Don't reset here - will be done in afterClose to prevent flash
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const payload = {
        ...values,
        tag_ids: values.tag_ids || [],
        description: values.description?.trim() || null,
      };
      console.log("Submitting site with payload:", payload);
      if (editingSite) {
        updateSiteMutation.mutate({ id: editingSite.id, payload });
      } else {
        createSiteMutation.mutate(payload);
      }
    } catch (error) {
      console.error("Form validation error:", error);
    }
  };

  return (
    <motion.div variants={containerVariants} initial="hidden" animate="show">
      <motion.div
        variants={itemVariants}
        style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}
      >
        <Title level={4} style={{ margin: 0 }}>
          站点管理
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => handleOpenModal()} size="large">
          新建站点
        </Button>
      </motion.div>

      <motion.div variants={itemVariants} className="glass-panel" style={{ padding: 24, borderRadius: 16 }}>
        <Table
          loading={isLoading}
          dataSource={sitesData?.items ?? []}
          columns={columns}
          rowKey="id"
          pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条` }}
        />
      </motion.div>

      <Modal
        title={editingSite ? "编辑站点" : "新建站点"}
        open={modalOpen}
        onOk={handleSubmit}
        confirmLoading={createSiteMutation.isPending || updateSiteMutation.isPending}
        onCancel={handleCloseModal}
        afterClose={() => {
          // Reset form after modal animation completes to prevent flash
          setEditingSite(null);
          form.resetFields();
        }}
        centered
        maskClosable={true}
        width={720}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 12 }} initialValues={{ is_active: true, tag_ids: [] }}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
            <Input placeholder="示例站点" size="large" />
          </Form.Item>
          <Form.Item
            name="url"
            label="URL"
            rules={[{ required: true, type: "url", message: "请输入有效的 URL" }]}
          >
            <Input placeholder="https://example.com" size="large" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="备注信息" />
          </Form.Item>

          <Form.Item name="tag_ids" style={{ display: "none" }}>
            <Input />
          </Form.Item>

          <Form.Item label="标签" shouldUpdate>
            {() =>
              tagsLoading ? (
                <Text type="secondary">加载中...</Text>
              ) : (
                <TagManager
                  value={form.getFieldValue("tag_ids") || []}
                  onChange={(ids) => form.setFieldsValue({ tag_ids: ids })}
                  availableTags={allTags}
                  onCreateTag={(payload) => createTagMutation.mutateAsync(payload)}
                  onDeleteTag={(tagId) => deleteTagMutation.mutateAsync(tagId)}
                  creating={createTagMutation.isPending}
                  deleting={deleteTagMutation.isPending}
                />
              )
            }
          </Form.Item>

          <Form.Item name="is_active" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </motion.div>
  );
};

export default SitesPage;
