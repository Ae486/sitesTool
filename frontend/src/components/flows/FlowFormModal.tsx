import { CodeOutlined, LayoutOutlined, AppstoreOutlined, FullscreenOutlined, FullscreenExitOutlined } from "@ant-design/icons";
import { Alert, Button, Form, Input, Modal, Select, Space, Switch, Typography, Drawer } from "antd";
import type { FormInstance } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import type { AutomationFlow, FlowDSL } from "../../types/flow";
import type { Site } from "../../types/site";
import { DSL_TEMPLATES } from "../../constants/dslTemplates";
import { validateDslStructure } from "../../utils/dsl";
import type { DslValidationResult } from "../../utils/dsl";
import VisualDslEditor, { type VisualDslEditorRef } from "../VisualDslEditor";
import { WorkflowEditor } from "../workflow";
import BrowserConfigFields from "./BrowserConfigFields";

const EMPTY_DSL: FlowDSL = { version: 1, steps: [] };
const DEFAULT_DSL_OBJECT: FlowDSL = {
  version: 1,
  steps: [
    { type: "navigate", url: "https://example.com" },
    { type: "click", selector: "#signin" },
  ],
};
const DEFAULT_DSL = JSON.stringify(DEFAULT_DSL_OBJECT, null, 2);

interface FlowFormModalProps {
  open: boolean;
  editingFlow: AutomationFlow | null;
  sites: Site[];
  form: FormInstance;
  loading: boolean;
  onCancel: () => void;
  onSubmit: (values: any, dsl: FlowDSL) => void;
}

const FlowFormModal = ({
  open,
  editingFlow,
  sites,
  form,
  loading,
  onCancel,
  onSubmit,
}: FlowFormModalProps) => {
  const [editMode, setEditMode] = useState<"visual" | "json" | "workflow">("visual");
  const [dslErrors, setDslErrors] = useState<string[]>([]);
  const [stepCount, setStepCount] = useState<number>(0);
  const [workflowDrawerOpen, setWorkflowDrawerOpen] = useState(false);
  const editorRef = useRef<VisualDslEditorRef>(null);

  const selectedSiteId = Form.useWatch("site_id", form);
  const selectedSiteUrl = sites.find((site) => site.id === selectedSiteId)?.url;
  const currentDsl = Form.useWatch("dsl", form);

  // Sync edit mode when DSL format changes
  useEffect(() => {
    if (!open) return;
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
      // Visual or Workflow mode - ensure DSL is object
      const allowEmpty = editMode === "workflow"; // Allow empty in workflow mode
      if (typeof currentDsl === "string") {
        try {
          const parsed = currentDsl ? JSON.parse(currentDsl) : DEFAULT_DSL_OBJECT;
          form.setFieldValue("dsl", parsed);
          const result = validateDslStructure(parsed, { allowEmptySteps: allowEmpty });
          setDslErrors(result.errors);
          setStepCount(result.stepCount);
        } catch {
          // Keep string if JSON parse fails
        }
      } else {
        const result = validateDslStructure(currentDsl ?? DEFAULT_DSL_OBJECT, { allowEmptySteps: allowEmpty });
        setDslErrors(result.errors);
        setStepCount(result.stepCount);
      }
    }
  }, [editMode, form, open]);

  // Handle workflow DSL changes - allow empty steps during editing
  const handleWorkflowChange = useCallback(
    (dsl: FlowDSL) => {
      form.setFieldValue("dsl", dsl);
      // Allow empty steps during workflow editing (user may not have connected nodes yet)
      const result = validateDslStructure(dsl, { allowEmptySteps: true });
      setDslErrors(result.errors);
      setStepCount(result.stepCount);
    },
    [form]
  );

  // Open workflow drawer
  const openWorkflowDrawer = useCallback(() => {
    // Ensure DSL is object before opening
    const currentDsl = form.getFieldValue("dsl");
    if (typeof currentDsl === "string") {
      try {
        const parsed = JSON.parse(currentDsl);
        form.setFieldValue("dsl", parsed);
      } catch {
        form.setFieldValue("dsl", DEFAULT_DSL_OBJECT);
      }
    }
    setWorkflowDrawerOpen(true);
  }, [form]);

  const handleVisualValidation = (result: DslValidationResult) => {
    setDslErrors(result.errors);
    setStepCount(result.stepCount);
  };

  const handleTemplateChange = (value: string) => {
    const template = DSL_TEMPLATES.find((t) => t.value === value);
    if (!template) return;

    if (editMode === "json") {
      const pretty = JSON.stringify(template.dsl, null, 2);
      form.setFieldValue("dsl", pretty);
      const result = validateDslStructure(pretty);
      setDslErrors(result.errors);
      setStepCount(result.stepCount);
    } else {
      // Visual or Workflow mode
      const allowEmpty = editMode === "workflow";
      form.setFieldValue("dsl", template.dsl);
      handleVisualValidation(validateDslStructure(template.dsl, { allowEmptySteps: allowEmpty }));
    }
  };

  const handleResetTemplate = () => {
    if (editMode === "json") {
      form.setFieldValue("dsl", DEFAULT_DSL);
      const result = validateDslStructure(DEFAULT_DSL);
      setDslErrors(result.errors);
      setStepCount(result.stepCount);
    } else {
      // Visual or Workflow mode
      const allowEmpty = editMode === "workflow";
      form.setFieldValue("dsl", DEFAULT_DSL_OBJECT);
      handleVisualValidation(validateDslStructure(DEFAULT_DSL_OBJECT, { allowEmptySteps: allowEmpty }));
    }
  };

  const handleSubmit = async () => {
    try {
      // Flush visual editor changes
      if (editMode === "visual") {
        editorRef.current?.flush();
        await new Promise((resolve) => requestAnimationFrame(resolve));
      }

      const values = await form.validateFields();

      // Parse DSL - workflow mode uses object directly
      let dsl: FlowDSL;
      if (editMode === "json") {
        dsl = typeof values.dsl === "string" ? JSON.parse(values.dsl) : values.dsl;
      } else {
        // Visual or Workflow mode - already object
        dsl = values.dsl;
      }

      // Validate DSL
      const validation = validateDslStructure(dsl);
      setDslErrors(validation.errors);
      setStepCount(validation.stepCount);

      if (!validation.valid) {
        return;
      }

      onSubmit(values, dsl);
    } catch (error: any) {
      // Form validation or JSON parse error - handled by antd
    }
  };

  const handleAfterClose = () => {
    setEditMode("visual");
    setDslErrors([]);
    setStepCount(0);
    setWorkflowDrawerOpen(false);
  };

  // Auto-save when closing (for editing existing flows)
  const handleCancel = async () => {
    // Only auto-save for existing flows with valid data
    if (editingFlow) {
      try {
        // Flush visual editor changes
        if (editMode === "visual") {
          editorRef.current?.flush();
          await new Promise((resolve) => requestAnimationFrame(resolve));
        }

        const values = form.getFieldsValue();
        
        // Parse DSL
        let dsl: FlowDSL;
        if (editMode === "json") {
          dsl = typeof values.dsl === "string" ? JSON.parse(values.dsl) : values.dsl;
        } else {
          dsl = values.dsl;
        }

        // Only save if valid
        const validation = validateDslStructure(dsl);
        if (validation.valid && values.name && values.site_id) {
          onSubmit(values, dsl);
          return; // onSubmit will close the modal
        }
      } catch {
        // Ignore errors, just close
      }
    }
    onCancel();
  };

  return (
    <Modal
      title={editingFlow ? "编辑自动化流程" : "新建自动化流程"}
      open={open}
      okText={editingFlow ? "保存" : "创建"}
      confirmLoading={loading}
      onCancel={handleCancel}
      afterClose={handleAfterClose}
      onOk={handleSubmit}
      width={800}
      centered
      maskClosable={true}
    >
      <Form
        layout="vertical"
        form={form}
        initialValues={{
          dsl: EMPTY_DSL,
          is_active: true,
          headless: true,
          browser_type: "chromium",
          use_cdp_mode: false,
          cdp_port: 9222,
        }}
        style={{ marginTop: 24 }}
      >
        <Form.Item name="name" label="名称" rules={[{ required: true }]}>
          <Input size="large" placeholder="输入流程名称" />
        </Form.Item>

        <Form.Item name="site_id" label="站点" rules={[{ required: true }]}>
          <Select
            size="large"
            placeholder="选择站点"
            options={sites.map((site) => ({ label: site.name, value: site.id }))}
          />
        </Form.Item>

        <Form.Item name="cron_expression" label="Cron 表达式">
          <Input size="large" placeholder="0 9 * * * (可选)" />
        </Form.Item>

        {/* Edit mode toggle */}
        <Space style={{ marginBottom: 12 }}>
          <Button.Group>
            <Button
              icon={<LayoutOutlined />}
              type={editMode === "visual" ? "primary" : "default"}
              onClick={() => setEditMode("visual")}
            >
              列表编辑
            </Button>
            <Button
              icon={<AppstoreOutlined />}
              type={editMode === "workflow" ? "primary" : "default"}
              onClick={() => setEditMode("workflow")}
            >
              画布编辑
            </Button>
            <Button
              icon={<CodeOutlined />}
              type={editMode === "json" ? "primary" : "default"}
              onClick={() => setEditMode("json")}
            >
              JSON
            </Button>
          </Button.Group>
        </Space>

        <Form.Item
          name="dsl"
          label={
            editMode === "json" 
              ? "DSL JSON" 
              : editMode === "workflow" 
                ? "工作流画布" 
                : "流程步骤"
          }
          rules={[{ required: true, message: "请配置流程步骤" }]}
        >
          {editMode === "workflow" ? (
            // Workflow mode - show open canvas button
            <div
              style={{
                border: "1px dashed #d9d9d9",
                borderRadius: 8,
                padding: 24,
                textAlign: "center",
                background: "#fafafa",
              }}
            >
              <Typography.Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
                点击下方按钮打开画布编辑器，拖拽节点创建自动化流程
              </Typography.Text>
              <Button
                type="primary"
                size="large"
                icon={<FullscreenOutlined />}
                onClick={openWorkflowDrawer}
              >
                打开画布编辑器
              </Button>
              {stepCount > 0 && (
                <Typography.Text style={{ display: "block", marginTop: 12, color: "#52c41a" }}>
                  当前已配置 {stepCount} 个步骤
                </Typography.Text>
              )}
            </div>
          ) : editMode === "visual" ? (
            // Original visual list editor (commented out can be restored)
            <VisualDslEditor
              ref={editorRef}
              siteUrl={selectedSiteUrl}
              onValidationChange={handleVisualValidation}
            />
          ) : (
            // JSON mode
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
          <Alert type="success" showIcon message={`DSL 校验通过，步骤数：${stepCount || 0}`} />
        )}

        <Form.Item name="is_active" label="启用" valuePropName="checked">
          <Switch />
        </Form.Item>

        <BrowserConfigFields form={form} />
      </Form>

      {/* Workflow Canvas Drawer */}
      <Drawer
        title={
          <Space>
            <AppstoreOutlined />
            <span>工作流画布编辑器</span>
          </Space>
        }
        placement="right"
        width="85vw"
        open={workflowDrawerOpen}
        onClose={() => setWorkflowDrawerOpen(false)}
        destroyOnClose={true}
        extra={
          <Button 
            type="primary" 
            icon={<FullscreenExitOutlined />}
            onClick={() => setWorkflowDrawerOpen(false)}
          >
            完成编辑
          </Button>
        }
        styles={{
          body: { 
            padding: 0, 
            height: "calc(100vh - 110px)",
            overflow: "hidden",
          },
        }}
      >
        <div style={{ height: "100%", display: "flex", flexDirection: "column" }}>
          {/* Validation status bar */}
          <div style={{ padding: "8px 16px", borderBottom: "1px solid #f0f0f0", background: "#fff" }}>
            {dslErrors.length > 0 ? (
              <Alert
                type="error"
                showIcon
                banner
                message={
                  <span>
                    <strong>DSL 校验未通过</strong>
                    <span style={{ marginLeft: 8, fontWeight: "normal" }}>
                      ({dslErrors.length} 个错误) {dslErrors.slice(0, 2).join(" | ")}{dslErrors.length > 2 ? "..." : ""}
                    </span>
                  </span>
                }
                style={{ marginBottom: 0 }}
              />
            ) : (
              <Alert
                type="success"
                showIcon
                banner
                message={
                  <span>
                    <strong>DSL 校验通过</strong>
                    <span style={{ marginLeft: 8, fontWeight: "normal" }}>
                      已配置 {stepCount || 0} 个步骤
                    </span>
                  </span>
                }
                style={{ marginBottom: 0 }}
              />
            )}
          </div>
          
          {/* Workflow Editor */}
          <div style={{ flex: 1 }}>
            <WorkflowEditor
              value={typeof currentDsl === "object" ? currentDsl : undefined}
              onChange={handleWorkflowChange}
              siteUrl={selectedSiteUrl}
            />
          </div>
        </div>
      </Drawer>
    </Modal>
  );
};

export default FlowFormModal;
