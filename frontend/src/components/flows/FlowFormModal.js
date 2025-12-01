import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { CodeOutlined, LayoutOutlined, AppstoreOutlined, FullscreenOutlined, FullscreenExitOutlined } from "@ant-design/icons";
import { Alert, Button, Form, Input, Modal, Select, Space, Switch, Typography, Drawer } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import { DSL_TEMPLATES } from "../../constants/dslTemplates";
import { validateDslStructure } from "../../utils/dsl";
import VisualDslEditor from "../VisualDslEditor";
import { WorkflowEditor } from "../workflow";
import BrowserConfigFields from "./BrowserConfigFields";
const EMPTY_DSL = { version: 1, steps: [] };
const DEFAULT_DSL_OBJECT = {
    version: 1,
    steps: [
        { type: "navigate", url: "https://example.com" },
        { type: "click", selector: "#signin" },
    ],
};
const DEFAULT_DSL = JSON.stringify(DEFAULT_DSL_OBJECT, null, 2);
const FlowFormModal = ({ open, editingFlow, sites, form, loading, onCancel, onSubmit, }) => {
    const [editMode, setEditMode] = useState("visual");
    const [dslErrors, setDslErrors] = useState([]);
    const [stepCount, setStepCount] = useState(0);
    const [workflowDrawerOpen, setWorkflowDrawerOpen] = useState(false);
    const editorRef = useRef(null);
    const selectedSiteId = Form.useWatch("site_id", form);
    const selectedSiteUrl = sites.find((site) => site.id === selectedSiteId)?.url;
    const currentDsl = Form.useWatch("dsl", form);
    // Sync edit mode when DSL format changes
    useEffect(() => {
        if (!open)
            return;
        const currentDsl = form.getFieldValue("dsl");
        if (editMode === "json") {
            if (typeof currentDsl !== "string") {
                const serialized = JSON.stringify(currentDsl ?? DEFAULT_DSL_OBJECT, null, 2);
                form.setFieldValue("dsl", serialized);
                const result = validateDslStructure(serialized);
                setDslErrors(result.errors);
                setStepCount(result.stepCount);
            }
            else {
                const result = validateDslStructure(currentDsl);
                setDslErrors(result.errors);
                setStepCount(result.stepCount);
            }
        }
        else {
            // Visual or Workflow mode - ensure DSL is object
            const allowEmpty = editMode === "workflow"; // Allow empty in workflow mode
            if (typeof currentDsl === "string") {
                try {
                    const parsed = currentDsl ? JSON.parse(currentDsl) : DEFAULT_DSL_OBJECT;
                    form.setFieldValue("dsl", parsed);
                    const result = validateDslStructure(parsed, { allowEmptySteps: allowEmpty });
                    setDslErrors(result.errors);
                    setStepCount(result.stepCount);
                }
                catch {
                    // Keep string if JSON parse fails
                }
            }
            else {
                const result = validateDslStructure(currentDsl ?? DEFAULT_DSL_OBJECT, { allowEmptySteps: allowEmpty });
                setDslErrors(result.errors);
                setStepCount(result.stepCount);
            }
        }
    }, [editMode, form, open]);
    // Handle workflow DSL changes - allow empty steps during editing
    const handleWorkflowChange = useCallback((dsl) => {
        form.setFieldValue("dsl", dsl);
        // Allow empty steps during workflow editing (user may not have connected nodes yet)
        const result = validateDslStructure(dsl, { allowEmptySteps: true });
        setDslErrors(result.errors);
        setStepCount(result.stepCount);
    }, [form]);
    // Open workflow drawer
    const openWorkflowDrawer = useCallback(() => {
        // Ensure DSL is object before opening
        const currentDsl = form.getFieldValue("dsl");
        if (typeof currentDsl === "string") {
            try {
                const parsed = JSON.parse(currentDsl);
                form.setFieldValue("dsl", parsed);
            }
            catch {
                form.setFieldValue("dsl", DEFAULT_DSL_OBJECT);
            }
        }
        setWorkflowDrawerOpen(true);
    }, [form]);
    const handleVisualValidation = (result) => {
        setDslErrors(result.errors);
        setStepCount(result.stepCount);
    };
    const handleTemplateChange = (value) => {
        const template = DSL_TEMPLATES.find((t) => t.value === value);
        if (!template)
            return;
        if (editMode === "json") {
            const pretty = JSON.stringify(template.dsl, null, 2);
            form.setFieldValue("dsl", pretty);
            const result = validateDslStructure(pretty);
            setDslErrors(result.errors);
            setStepCount(result.stepCount);
        }
        else {
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
        }
        else {
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
            let dsl;
            if (editMode === "json") {
                dsl = typeof values.dsl === "string" ? JSON.parse(values.dsl) : values.dsl;
            }
            else {
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
        }
        catch (error) {
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
                let dsl;
                if (editMode === "json") {
                    dsl = typeof values.dsl === "string" ? JSON.parse(values.dsl) : values.dsl;
                }
                else {
                    dsl = values.dsl;
                }
                // Only save if valid
                const validation = validateDslStructure(dsl);
                if (validation.valid && values.name && values.site_id) {
                    onSubmit(values, dsl);
                    return; // onSubmit will close the modal
                }
            }
            catch {
                // Ignore errors, just close
            }
        }
        onCancel();
    };
    return (_jsxs(Modal, { title: editingFlow ? "编辑自动化流程" : "新建自动化流程", open: open, okText: editingFlow ? "保存" : "创建", confirmLoading: loading, onCancel: handleCancel, afterClose: handleAfterClose, onOk: handleSubmit, width: 800, centered: true, maskClosable: true, children: [_jsxs(Form, { layout: "vertical", form: form, initialValues: {
                    dsl: EMPTY_DSL,
                    is_active: true,
                    headless: true,
                    browser_type: "chromium",
                    use_cdp_mode: false,
                    cdp_port: 9222,
                }, style: { marginTop: 24 }, children: [_jsx(Form.Item, { name: "name", label: "\u540D\u79F0", rules: [{ required: true }], children: _jsx(Input, { size: "large", placeholder: "\u8F93\u5165\u6D41\u7A0B\u540D\u79F0" }) }), _jsx(Form.Item, { name: "site_id", label: "\u7AD9\u70B9", rules: [{ required: true }], children: _jsx(Select, { size: "large", placeholder: "\u9009\u62E9\u7AD9\u70B9", options: sites.map((site) => ({ label: site.name, value: site.id })) }) }), _jsx(Form.Item, { name: "cron_expression", label: "Cron \u8868\u8FBE\u5F0F", children: _jsx(Input, { size: "large", placeholder: "0 9 * * * (\u53EF\u9009)" }) }), _jsx(Space, { style: { marginBottom: 12 }, children: _jsxs(Button.Group, { children: [_jsx(Button, { icon: _jsx(LayoutOutlined, {}), type: editMode === "visual" ? "primary" : "default", onClick: () => setEditMode("visual"), children: "\u5217\u8868\u7F16\u8F91" }), _jsx(Button, { icon: _jsx(AppstoreOutlined, {}), type: editMode === "workflow" ? "primary" : "default", onClick: () => setEditMode("workflow"), children: "\u753B\u5E03\u7F16\u8F91" }), _jsx(Button, { icon: _jsx(CodeOutlined, {}), type: editMode === "json" ? "primary" : "default", onClick: () => setEditMode("json"), children: "JSON" })] }) }), _jsx(Form.Item, { name: "dsl", label: editMode === "json"
                            ? "DSL JSON"
                            : editMode === "workflow"
                                ? "工作流画布"
                                : "流程步骤", rules: [{ required: true, message: "请配置流程步骤" }], children: editMode === "workflow" ? (
                        // Workflow mode - show open canvas button
                        _jsxs("div", { style: {
                                border: "1px dashed #d9d9d9",
                                borderRadius: 8,
                                padding: 24,
                                textAlign: "center",
                                background: "#fafafa",
                            }, children: [_jsx(Typography.Text, { type: "secondary", style: { display: "block", marginBottom: 12 }, children: "\u70B9\u51FB\u4E0B\u65B9\u6309\u94AE\u6253\u5F00\u753B\u5E03\u7F16\u8F91\u5668\uFF0C\u62D6\u62FD\u8282\u70B9\u521B\u5EFA\u81EA\u52A8\u5316\u6D41\u7A0B" }), _jsx(Button, { type: "primary", size: "large", icon: _jsx(FullscreenOutlined, {}), onClick: openWorkflowDrawer, children: "\u6253\u5F00\u753B\u5E03\u7F16\u8F91\u5668" }), stepCount > 0 && (_jsxs(Typography.Text, { style: { display: "block", marginTop: 12, color: "#52c41a" }, children: ["\u5F53\u524D\u5DF2\u914D\u7F6E ", stepCount, " \u4E2A\u6B65\u9AA4"] }))] })) : editMode === "visual" ? (
                        // Original visual list editor (commented out can be restored)
                        _jsx(VisualDslEditor, { ref: editorRef, siteUrl: selectedSiteUrl, onValidationChange: handleVisualValidation })) : (
                        // JSON mode
                        _jsx(Input.TextArea, { rows: 10, spellCheck: false, onChange: (e) => {
                                const val = e.target.value;
                                const result = validateDslStructure(val);
                                setDslErrors(result.errors);
                                setStepCount(result.stepCount);
                            } })) }), _jsxs(Space, { style: { marginBottom: 8 }, align: "start", children: [_jsx(Select, { placeholder: "\u9009\u62E9\u793A\u4F8B\u6A21\u677F\u586B\u5145", style: { minWidth: 220 }, options: DSL_TEMPLATES.map((tpl) => ({
                                    value: tpl.value,
                                    label: tpl.label,
                                    description: tpl.description,
                                })), onChange: handleTemplateChange, optionRender: (option) => (_jsxs(Space, { direction: "vertical", size: 0, children: [_jsx(Typography.Text, { children: option.data.label }), option.data.description ? (_jsx(Typography.Text, { type: "secondary", style: { fontSize: 12 }, children: option.data.description })) : null] })) }), _jsx(Button, { onClick: handleResetTemplate, children: "\u91CD\u7F6E\u4E3A\u9ED8\u8BA4\u6A21\u677F" })] }), dslErrors.length > 0 ? (_jsx(Alert, { type: "error", showIcon: true, message: "DSL \u6821\u9A8C\u672A\u901A\u8FC7", description: _jsx("ul", { style: { paddingLeft: 20, margin: 0 }, children: dslErrors.map((err) => (_jsx("li", { children: err }, err))) }) })) : (_jsx(Alert, { type: "success", showIcon: true, message: `DSL 校验通过，步骤数：${stepCount || 0}` })), _jsx(Form.Item, { name: "is_active", label: "\u542F\u7528", valuePropName: "checked", children: _jsx(Switch, {}) }), _jsx(BrowserConfigFields, { form: form })] }), _jsx(Drawer, { title: _jsxs(Space, { children: [_jsx(AppstoreOutlined, {}), _jsx("span", { children: "\u5DE5\u4F5C\u6D41\u753B\u5E03\u7F16\u8F91\u5668" })] }), placement: "right", width: "85vw", open: workflowDrawerOpen, onClose: () => setWorkflowDrawerOpen(false), destroyOnClose: true, extra: _jsx(Button, { type: "primary", icon: _jsx(FullscreenExitOutlined, {}), onClick: () => setWorkflowDrawerOpen(false), children: "\u5B8C\u6210\u7F16\u8F91" }), styles: {
                    body: {
                        padding: 0,
                        height: "calc(100vh - 110px)",
                        overflow: "hidden",
                    },
                }, children: _jsxs("div", { style: { height: "100%", display: "flex", flexDirection: "column" }, children: [_jsx("div", { style: { padding: "8px 16px", borderBottom: "1px solid #f0f0f0", background: "#fff" }, children: dslErrors.length > 0 ? (_jsx(Alert, { type: "error", showIcon: true, banner: true, message: _jsxs("span", { children: [_jsx("strong", { children: "DSL \u6821\u9A8C\u672A\u901A\u8FC7" }), _jsxs("span", { style: { marginLeft: 8, fontWeight: "normal" }, children: ["(", dslErrors.length, " \u4E2A\u9519\u8BEF) ", dslErrors.slice(0, 2).join(" | "), dslErrors.length > 2 ? "..." : ""] })] }), style: { marginBottom: 0 } })) : (_jsx(Alert, { type: "success", showIcon: true, banner: true, message: _jsxs("span", { children: [_jsx("strong", { children: "DSL \u6821\u9A8C\u901A\u8FC7" }), _jsxs("span", { style: { marginLeft: 8, fontWeight: "normal" }, children: ["\u5DF2\u914D\u7F6E ", stepCount || 0, " \u4E2A\u6B65\u9AA4"] })] }), style: { marginBottom: 0 } })) }), _jsx("div", { style: { flex: 1 }, children: _jsx(WorkflowEditor, { value: typeof currentDsl === "object" ? currentDsl : undefined, onChange: handleWorkflowChange, siteUrl: selectedSiteUrl }) })] }) })] }));
};
export default FlowFormModal;
