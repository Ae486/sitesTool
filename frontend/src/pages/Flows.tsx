import { CodeOutlined, DeleteOutlined, EditOutlined, PlayCircleOutlined, PlusOutlined, StopOutlined, LayoutOutlined } from "@ant-design/icons";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography, message } from "antd";
import { motion } from "framer-motion";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  createFlow,
  deleteFlow,
  fetchFlows,
  getRunningFlows,
  stopFlow,
  triggerFlow,
  updateFlow,
} from "../api/flows";
import { fetchSites } from "../api/sites";
import type { AutomationFlow, FlowDSL } from "../types/flow";
import { DSL_TEMPLATES } from "../constants/dslTemplates";
import { validateDslStructure } from "../utils/dsl";
import type { DslValidationResult } from "../utils/dsl";
import VisualDslEditor, { type VisualDslEditorRef } from "../components/VisualDslEditor";

const EMPTY_DSL: FlowDSL = { version: 1, steps: [] };
const DEFAULT_DSL_OBJECT: FlowDSL = {
  version: 1,
  steps: [
    { type: "navigate", url: "https://example.com" },
    { type: "click", selector: "#signin" },
  ],
};
const DEFAULT_DSL = JSON.stringify(DEFAULT_DSL_OBJECT, null, 2);

import { containerVariants, itemVariants } from "../constants/animations";

const FlowsPage = () => {
  // ... (keep existing state)
  const [modalOpen, setModalOpen] = useState(false);
  const [editingFlow, setEditingFlow] = useState<AutomationFlow | null>(null);
  const [runningFlows, setRunningFlows] = useState<Set<number>>(new Set());
  const [executingFlows, setExecutingFlows] = useState<Set<number>>(new Set());
  const [dslErrors, setDslErrors] = useState<string[]>([]);
  const [stepCount, setStepCount] = useState<number>(0);
  const [editMode, setEditMode] = useState<'visual' | 'json'>('visual'); // 编辑模式
  const [form] = Form.useForm();
  const editorRef = useRef<VisualDslEditorRef>(null); // ref用于调用flush方法
  const { data: flowData, isLoading, refetch } = useQuery({ queryKey: ["flows"], queryFn: fetchFlows });
  const { data: sites } = useQuery({ queryKey: ["sites"], queryFn: fetchSites });
  const selectedSiteId = Form.useWatch("site_id", form);
  const selectedSiteUrl = useMemo(() => {
    if (!selectedSiteId || !sites?.items) return undefined;
    return sites.items.find((site) => site.id === selectedSiteId)?.url;
  }, [selectedSiteId, sites]);

  // ... (keep existing effects and mutations)
  // Poll for running flows status (only when there are running flows)
  useEffect(() => {
    const checkRunningFlows = async () => {
      try {
        const { running_flows } = await getRunningFlows();
        const newRunningFlows = new Set(running_flows);

        // Only update if changed
        if (
          newRunningFlows.size !== runningFlows.size ||
          ![...newRunningFlows].every((id) => runningFlows.has(id))
        ) {
          setRunningFlows(newRunningFlows);
        }
      } catch (error) {
        // Ignore errors in polling
      }
    };

    // Initial check
    checkRunningFlows();

    // Only poll if there are running flows or we just started one
    if (runningFlows.size > 0 || executingFlows.size > 0) {
      const interval = setInterval(checkRunningFlows, 2000); // Poll every 2 seconds
      return () => clearInterval(interval);
    }
  }, [runningFlows.size, executingFlows.size]);

  const siteNameMap = useMemo(() => {
    const map = new Map<number, string>();
    (sites?.items ?? []).forEach((site) => map.set(site.id, site.name));
    return map;
  }, [sites]);

  const createMutation = useMutation({
    mutationFn: createFlow,
    onSuccess: () => {
      message.success("流程创建成功");
      setModalOpen(false);
      setEditingFlow(null);
      refetch();
    },
    onError: () => message.error("流程创建失败"),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: any }) => updateFlow(id, data),
    onSuccess: () => {
      message.success("流程更新成功");
      setModalOpen(false);
      setEditingFlow(null);
      refetch();
    },
    onError: () => message.error("流程更新失败"),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteFlow,
    onSuccess: () => {
      message.success("流程删除成功");
      refetch();
    },
    onError: () => message.error("流程删除失败"),
  });

  const triggerMutation = useMutation({
    mutationFn: triggerFlow,
    onMutate: (flowId) => {
      // 立即标记为执行中
      setExecutingFlows((prev) => new Set(prev).add(flowId));
    },
    onSuccess: (data, flowId) => {
      if (data.status === "running") {
        message.warning(data.message || "流程正在运行中");
        setExecutingFlows((prev) => {
          const newSet = new Set(prev);
          newSet.delete(flowId);
          return newSet;
        });
      } else {
        message.success("已开始执行");
        setRunningFlows((prev) => new Set(prev).add(flowId));
      }
    },
    onError: (error, flowId) => {
      message.error("触发失败");
      setExecutingFlows((prev) => {
        const newSet = new Set(prev);
        newSet.delete(flowId);
        return newSet;
      });
    },
    onSettled: (data, error, flowId) => {
      setExecutingFlows((prev) => {
        const newSet = new Set(prev);
        newSet.delete(flowId);
        return newSet;
      });
    },
  });

  const stopMutation = useMutation({
    mutationFn: stopFlow,
    onSuccess: (data, flowId) => {
      message.warning({
        content: "已停止执行",
        icon: <StopOutlined style={{ color: "#ff4d4f" }} />,
      });
      setRunningFlows((prev) => {
        const newSet = new Set(prev);
        newSet.delete(flowId);
        return newSet;
      });
    },
    onError: () => message.error("停止失败"),
  });

  const columns = useMemo(
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
        render: (flow: AutomationFlow) => (
          <Tag color="default" style={{ borderRadius: "4px", border: "1px solid #e4e4e7", background: "#fafafa" }}>
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
        render: (flow: AutomationFlow) => (
          <Space>
            <Button
              icon={<EditOutlined />}
              onClick={() => {
                setEditingFlow(flow);
                // 默认使用可视化模式编辑
                setEditMode('visual');
                form.setFieldsValue({
                  name: flow.name,
                  site_id: flow.site_id,
                  cron_expression: flow.cron_expression,
                  dsl: flow.dsl, // 直接传对象，组件会处理
                  is_active: flow.is_active,
                  headless: flow.headless ?? true,
                  browser_type: flow.browser_type ?? "chromium",
                  browser_path: flow.browser_path,
                  use_cdp_mode: flow.use_cdp_mode,
                  cdp_port: flow.cdp_port,
                  cdp_user_data_dir: flow.cdp_user_data_dir,
                });
                setModalOpen(true);
              }}
            >
              编辑
            </Button>
            <Popconfirm
              title="确认删除"
              description="删除后无法恢复，确定要删除这个流程吗？"
              onConfirm={() => deleteMutation.mutate(flow.id)}
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
            >
              <Button icon={<DeleteOutlined />} danger loading={deleteMutation.isPending}>
                删除
              </Button>
            </Popconfirm>
            {runningFlows.has(flow.id) ? (
              <Button
                danger
                icon={<StopOutlined />}
                loading={stopMutation.isPending && stopMutation.variables === flow.id}
                onClick={() => stopMutation.mutate(flow.id)}
              >
                中断
              </Button>
            ) : (
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                loading={executingFlows.has(flow.id)}
                disabled={executingFlows.has(flow.id)}
                onClick={() => triggerMutation.mutate(flow.id)}
              >
                执行
              </Button>
            )}
          </Space>
        ),
      },
    ],
    [
      executingFlows,
      stopMutation.isPending,
      stopMutation.variables,
      deleteMutation.isPending,
      siteNameMap,
      form,
      runningFlows,
    ],
  );

  const handleVisualValidation = (result: DslValidationResult) => {
    setDslErrors(result.errors);
    setStepCount(result.stepCount);
  };

  useEffect(() => {
    if (!modalOpen) return;
    const currentDsl = form.getFieldValue("dsl");

    if (editMode === "json") {
      if (typeof currentDsl !== "string") {
        const serialized = JSON.stringify(currentDsl ?? DEFAULT_DSL_OBJECT, null, 2);
        form.setFieldValue("dsl", serialized);
        const result = validateDslStructure(serialized);
        setDslErrors(result.errors);
        setStepCount(result.stepCount);
      } else {
        const result = validateDslStructure(currentDsl);
        setDslErrors(result.errors);
        setStepCount(result.stepCount);
      }
    } else {
      if (typeof currentDsl === "string") {
        try {
          const parsed = currentDsl ? JSON.parse(currentDsl) : DEFAULT_DSL_OBJECT;
          form.setFieldValue("dsl", parsed);
          const result = validateDslStructure(parsed);
          setDslErrors(result.errors);
          setStepCount(result.stepCount);
        } catch {
          message.error("JSON 解析失败，请检查格式");
        }
      } else {
        const result = validateDslStructure(currentDsl ?? DEFAULT_DSL_OBJECT);
        setDslErrors(result.errors);
        setStepCount(result.stepCount);
      }
    }
  }, [editMode, form, modalOpen]);

  const handleTemplateChange = (value: string) => {
    const template = DSL_TEMPLATES.find((t) => t.value === value);
    if (!template) return;

    if (editMode === "visual") {
      form.setFieldValue("dsl", template.dsl);
      handleVisualValidation(validateDslStructure(template.dsl));
    } else {
      const pretty = JSON.stringify(template.dsl, null, 2);
      form.setFieldValue("dsl", pretty);
      const result = validateDslStructure(pretty);
      setDslErrors(result.errors);
      setStepCount(result.stepCount);
    }
  };

  const handleResetTemplate = () => {
    if (editMode === "visual") {
      form.setFieldValue("dsl", DEFAULT_DSL_OBJECT);
      handleVisualValidation(validateDslStructure(DEFAULT_DSL_OBJECT));
    } else {
      form.setFieldValue("dsl", DEFAULT_DSL);
      const result = validateDslStructure(DEFAULT_DSL);
      setDslErrors(result.errors);
      setStepCount(result.stepCount);
    }
  };

  const handleSubmit = async () => {
    try {
      // 在提交前立即同步所有防抖的更改（关键！）
      if (editMode === 'visual') {
        editorRef.current?.flush();
        // 等待一帧确保 React 状态更新完成
        await new Promise(resolve => requestAnimationFrame(resolve));
      }

      const values = await form.validateFields();

      // 处理 DSL：可视化编辑器返回对象，JSON 编辑器返回字符串
      let dsl: FlowDSL;
      if (editMode === 'visual') {
        dsl = values.dsl; // 已经是对象
      } else {
        dsl = typeof values.dsl === "string" ? JSON.parse(values.dsl) : values.dsl;
      }

      // 验证 DSL 结构
      const validation = validateDslStructure(dsl);
      setDslErrors(validation.errors);
      setStepCount(validation.stepCount);

      if (!validation.valid) {
        message.error(`DSL 校验未通过：${validation.errors[0] || '请检查必填字段'}`);
        return;
      }

      if (editingFlow) {
        updateMutation.mutate({ id: editingFlow.id, data: { ...values, dsl } });
      } else {
        createMutation.mutate({ ...values, dsl });
      }
    } catch (error: any) {
      if (error?.message?.includes("JSON")) {
        message.error("DSL JSON 格式错误");
      } else {
        console.error(error);
      }
    }
  };

  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="show"
    >
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
          自动化流程
        </Typography.Title>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          size="large"
          onClick={() => {
            setEditingFlow(null);
            setEditMode('visual'); // 新建时默认可视化模式
            form.setFieldsValue({
              name: '',
              site_id: undefined,
              cron_expression: '',
              dsl: EMPTY_DSL, // 使用常量
              is_active: true,
              headless: true,
              browser_type: "chromium",
              use_cdp_mode: false,
              cdp_port: 9222,
              cdp_user_data_dir: undefined,
            });
            setModalOpen(true);
          }}
        >
          新建流程
        </Button>
      </motion.div>

      <motion.div variants={itemVariants} className="glass-panel" style={{ padding: 24, borderRadius: 16 }}>
        <Table
          dataSource={flowData?.items ?? []}
          columns={columns}
          rowKey="id"
          loading={isLoading}
          pagination={{
            pageSize: 10,
            showTotal: (total) => `共 ${total} 条`,
          }}
        />
      </motion.div>
      <Modal
        title={editingFlow ? "编辑自动化流程" : "新建自动化流程"}
        open={modalOpen}
        okText={editingFlow ? "保存" : "创建"}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        onCancel={() => {
          setModalOpen(false);
          // Don't reset here - will be done in afterClose to prevent flash
        }}
        afterClose={() => {
          // Reset form after modal animation completes to prevent flash
          setEditingFlow(null);
          setEditMode('visual');
          form.setFieldsValue({
            name: '',
            site_id: undefined,
            cron_expression: '',
            dsl: EMPTY_DSL,
            is_active: true,
            headless: true,
            browser_type: 'chromium',
            use_cdp_mode: false,
            cdp_port: 9222,
            cdp_user_data_dir: undefined,
          });
          setDslErrors([]);
          setStepCount(0);
        }}
        onOk={handleSubmit}
        width={800}
        centered
        maskClosable={true}
      >
        <Form
          layout="vertical"
          form={form}
          initialValues={{ dsl: EMPTY_DSL, is_active: true, headless: true, browser_type: "chromium", use_cdp_mode: false, cdp_port: 9222, cdp_user_data_dir: undefined }}
          style={{ marginTop: 24 }}
        >
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input size="large" placeholder="输入流程名称" />
          </Form.Item>
          <Form.Item name="site_id" label="站点" rules={[{ required: true }]}>
            <Select
              size="large"
              placeholder="选择站点"
              options={(sites?.items ?? []).map((site) => ({ label: site.name, value: site.id }))}
            />
          </Form.Item>
          <Form.Item name="cron_expression" label="Cron 表达式">
            <Input size="large" placeholder="0 9 * * * (可选)" />
          </Form.Item>
          {/* 编辑模式切换 */}
          <Space style={{ marginBottom: 12 }}>
            <Button.Group>
              <Button
                icon={<LayoutOutlined />}
                type={editMode === 'visual' ? 'primary' : 'default'}
                onClick={() => setEditMode('visual')}
              >
                可视化编辑
              </Button>
              <Button
                icon={<CodeOutlined />}
                type={editMode === 'json' ? 'primary' : 'default'}
                onClick={() => setEditMode('json')}
              >
                JSON 编辑
              </Button>
            </Button.Group>
          </Space>

          <Form.Item
            name="dsl"
            label={editMode === "visual" ? "流程步骤" : "DSL JSON"}
            rules={[{ required: true, message: "请配置流程步骤" }]}
          >
            {editMode === "visual" ? (
              <VisualDslEditor 
                ref={editorRef}
                siteUrl={selectedSiteUrl} 
                onValidationChange={handleVisualValidation} 
              />
            ) : (
              <Input.TextArea
                rows={10}
                spellCheck={false}
                onChange={(e) => {
                  const val = e.target.value;
                  const result = validateDslStructure(val);
                  setDslErrors(result.errors);
                  setStepCount(result.stepCount);
                }}
              />
            )}
          </Form.Item>
          <Space style={{ marginBottom: 8 }} align="start">
            <Select
              placeholder="选择示例模板填充"
              style={{ minWidth: 220 }}
              options={DSL_TEMPLATES.map((tpl) => ({
                value: tpl.value,
                label: tpl.label,
                description: tpl.description,
              }))}
              onChange={handleTemplateChange}
              optionRender={(option) => (
                <Space direction="vertical" size={0}>
                  <Typography.Text>{option.data.label}</Typography.Text>
                  {option.data.description ? (
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {option.data.description}
                    </Typography.Text>
                  ) : null}
                </Space>
              )}
            />
            <Button onClick={handleResetTemplate}>重置为默认模板</Button>
          </Space>
          {dslErrors.length > 0 ? (
            <Alert
              type="error"
              showIcon
              message="DSL 校验未通过"
              description={
                <ul style={{ paddingLeft: 20, margin: 0 }}>
                  {dslErrors.map((err) => (
                    <li key={err}>{err}</li>
                  ))}
                </ul>
              }
            />
          ) : (
            <Alert
              type="success"
              showIcon
              message={`DSL 校验通过，步骤数：${stepCount || 0}`}
            />
          )}
          <Form.Item name="is_active" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item
            name="headless"
            label="静默模式"
            valuePropName="checked"
            tooltip="开启后浏览器在后台运行，不显示窗口"
          >
            <Switch />
          </Form.Item>
          <Form.Item
            name="browser_type"
            label="浏览器类型"
            tooltip="选择执行自动化的浏览器"
          >
            <Select
              onChange={(value) => {
                // Clear browser_path when switching away from custom
                if (value !== "custom") {
                  form.setFieldValue("browser_path", null);
                }
              }}
              options={[
                { label: "Chromium（全新浏览器）", value: "chromium" },
                { label: "Chrome（系统浏览器，可复用登录）", value: "chrome" },
                { label: "Edge（系统浏览器，可复用登录）", value: "edge" },
                { label: "Firefox", value: "firefox" },
                { label: "自定义路径", value: "custom" },
              ]}
            />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prevValues, currentValues) =>
              prevValues.browser_type !== currentValues.browser_type
            }
          >
            {({ getFieldValue }) =>
              getFieldValue("browser_type") === "custom" ? (
                <Form.Item
                  name="browser_path"
                  label="浏览器路径"
                  rules={[{ required: true, message: "请输入浏览器可执行文件路径" }]}
                >
                  <Input placeholder="C:\Program Files\Google\Chrome\Application\chrome.exe" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          
          <Form.Item
            name="use_cdp_mode"
            label="CDP模式（独立自动化浏览器）"
            valuePropName="checked"
            tooltip="使用复制的浏览器配置，完全隔离，支持headless静默模式"
            extra={
              <span style={{fontSize: '12px', color: '#52c41a'}}>
                ✅ <strong>首次</strong>：自动复制浏览器配置（包含书签、扩展），需登录一次网站<br/>
                ✅ <strong>后续</strong>：完全自动化，登录状态持久保存，与日常浏览器完全隔离<br/>
                ✅ <strong>支持</strong>：静默模式（headless）+ CDP模式 同时启用
              </span>
            }
          >
            <Switch />
          </Form.Item>
          
          <Form.Item
            noStyle
            shouldUpdate={(prevValues, currentValues) =>
              prevValues.use_cdp_mode !== currentValues.use_cdp_mode
            }
          >
            {({ getFieldValue }) =>
              getFieldValue("use_cdp_mode") ? (
                <>
                  <Form.Item
                    name="cdp_port"
                    label="CDP调试端口"
                    rules={[{ required: true, message: "请输入CDP端口" }]}
                    tooltip="浏览器调试端口，通常为9222"
                  >
                    <Input type="number" placeholder="9222" />
                  </Form.Item>
                  
                  <Form.Item
                    name="cdp_user_data_dir"
                    label="自定义配置目录（高级选项）"
                    tooltip="留空则自动使用复制的专用配置。仅当需要使用特定配置时才填写。"
                    extra={
                      <span style={{fontSize: '12px', color: '#666'}}>
                        ⚠️ 留空 = 自动复制并使用专用配置（推荐，与日常浏览器隔离）<br/>
                        高级：指定特定路径（如 C:\Users\你\AppData\Roaming\autoTool\cdp_browser_profile）
                      </span>
                    }
                  >
                    <Input placeholder="留空使用自动复制的专用配置（推荐）" />
                  </Form.Item>
                </>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>
    </motion.div>
  );
};

export default FlowsPage;
