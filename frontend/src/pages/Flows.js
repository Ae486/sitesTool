import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { CodeOutlined, DeleteOutlined, EditOutlined, PlayCircleOutlined, PlusOutlined, StopOutlined, LayoutOutlined } from "@ant-design/icons";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography, message } from "antd";
import { motion } from "framer-motion";
import { useEffect, useMemo, useRef, useState } from "react";
import { createFlow, deleteFlow, fetchFlows, getRunningFlows, stopFlow, triggerFlow, updateFlow, } from "../api/flows";
import { fetchSites } from "../api/sites";
import { DSL_TEMPLATES } from "../constants/dslTemplates";
import { validateDslStructure } from "../utils/dsl";
import VisualDslEditor from "../components/VisualDslEditor";
const EMPTY_DSL = { version: 1, steps: [] };
const DEFAULT_DSL_OBJECT = {
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
    const [editingFlow, setEditingFlow] = useState(null);
    const [runningFlows, setRunningFlows] = useState(new Set());
    const [executingFlows, setExecutingFlows] = useState(new Set());
    const [dslErrors, setDslErrors] = useState([]);
    const [stepCount, setStepCount] = useState(0);
    const [editMode, setEditMode] = useState('visual'); // 编辑模式
    const [form] = Form.useForm();
    const editorRef = useRef(null); // ref用于调用flush方法
    const { data: flowData, isLoading, refetch } = useQuery({ queryKey: ["flows"], queryFn: fetchFlows });
    const { data: sites } = useQuery({ queryKey: ["sites"], queryFn: fetchSites });
    const selectedSiteId = Form.useWatch("site_id", form);
    const selectedSiteUrl = useMemo(() => {
        if (!selectedSiteId || !sites?.items)
            return undefined;
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
                if (newRunningFlows.size !== runningFlows.size ||
                    ![...newRunningFlows].every((id) => runningFlows.has(id))) {
                    setRunningFlows(newRunningFlows);
                }
            }
            catch (error) {
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
        const map = new Map();
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
        mutationFn: ({ id, data }) => updateFlow(id, data),
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
            }
            else {
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
                icon: _jsx(StopOutlined, { style: { color: "#ff4d4f" } }),
            });
            setRunningFlows((prev) => {
                const newSet = new Set(prev);
                newSet.delete(flowId);
                return newSet;
            });
        },
        onError: () => message.error("停止失败"),
    });
    const columns = useMemo(() => [
        {
            title: "名称",
            dataIndex: "name",
            key: "name",
            width: 250,
            render: (text) => _jsx("span", { style: { fontWeight: 600 }, children: text }),
        },
        {
            title: "站点",
            key: "site",
            width: 200,
            render: (flow) => (_jsx(Tag, { color: "default", style: { borderRadius: "4px", border: "1px solid #e4e4e7", background: "#fafafa" }, children: siteNameMap.get(flow.site_id) ?? flow.site_id })),
        },
        {
            title: "状态",
            dataIndex: "last_status",
            key: "status",
            width: 120,
            render: (status) => (_jsx(Tag, { color: status === "success" ? "success" : status === "failed" ? "error" : "default", style: { borderRadius: "12px" }, children: status || "未执行" })),
        },
        {
            title: "调度",
            dataIndex: "cron_expression",
            key: "cron",
            width: 150,
            render: (value) => (_jsx("span", { style: { color: "var(--text-secondary)" }, children: value || "手动触发" })),
        },
        {
            title: "操作",
            key: "actions",
            width: 280,
            render: (flow) => (_jsxs(Space, { children: [_jsx(Button, { icon: _jsx(EditOutlined, {}), onClick: () => {
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
                        }, children: "\u7F16\u8F91" }), _jsx(Popconfirm, { title: "\u786E\u8BA4\u5220\u9664", description: "\u5220\u9664\u540E\u65E0\u6CD5\u6062\u590D\uFF0C\u786E\u5B9A\u8981\u5220\u9664\u8FD9\u4E2A\u6D41\u7A0B\u5417\uFF1F", onConfirm: () => deleteMutation.mutate(flow.id), okText: "\u5220\u9664", cancelText: "\u53D6\u6D88", okButtonProps: { danger: true }, children: _jsx(Button, { icon: _jsx(DeleteOutlined, {}), danger: true, loading: deleteMutation.isPending, children: "\u5220\u9664" }) }), runningFlows.has(flow.id) ? (_jsx(Button, { danger: true, icon: _jsx(StopOutlined, {}), loading: stopMutation.isPending && stopMutation.variables === flow.id, onClick: () => stopMutation.mutate(flow.id), children: "\u4E2D\u65AD" })) : (_jsx(Button, { type: "primary", icon: _jsx(PlayCircleOutlined, {}), loading: executingFlows.has(flow.id), disabled: executingFlows.has(flow.id), onClick: () => triggerMutation.mutate(flow.id), children: "\u6267\u884C" }))] })),
        },
    ], [
        executingFlows,
        stopMutation.isPending,
        stopMutation.variables,
        deleteMutation.isPending,
        siteNameMap,
        form,
        runningFlows,
    ]);
    const handleVisualValidation = (result) => {
        setDslErrors(result.errors);
        setStepCount(result.stepCount);
    };
    useEffect(() => {
        if (!modalOpen)
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
            if (typeof currentDsl === "string") {
                try {
                    const parsed = currentDsl ? JSON.parse(currentDsl) : DEFAULT_DSL_OBJECT;
                    form.setFieldValue("dsl", parsed);
                    const result = validateDslStructure(parsed);
                    setDslErrors(result.errors);
                    setStepCount(result.stepCount);
                }
                catch {
                    message.error("JSON 解析失败，请检查格式");
                }
            }
            else {
                const result = validateDslStructure(currentDsl ?? DEFAULT_DSL_OBJECT);
                setDslErrors(result.errors);
                setStepCount(result.stepCount);
            }
        }
    }, [editMode, form, modalOpen]);
    const handleTemplateChange = (value) => {
        const template = DSL_TEMPLATES.find((t) => t.value === value);
        if (!template)
            return;
        if (editMode === "visual") {
            form.setFieldValue("dsl", template.dsl);
            handleVisualValidation(validateDslStructure(template.dsl));
        }
        else {
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
        }
        else {
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
            let dsl;
            if (editMode === 'visual') {
                dsl = values.dsl; // 已经是对象
            }
            else {
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
            }
            else {
                createMutation.mutate({ ...values, dsl });
            }
        }
        catch (error) {
            if (error?.message?.includes("JSON")) {
                message.error("DSL JSON 格式错误");
            }
            else {
                console.error(error);
            }
        }
    };
    return (_jsxs(motion.div, { variants: containerVariants, initial: "hidden", animate: "show", children: [_jsxs(motion.div, { variants: itemVariants, style: {
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    marginBottom: 24,
                }, children: [_jsx(Typography.Title, { level: 4, style: { margin: 0 }, children: "\u81EA\u52A8\u5316\u6D41\u7A0B" }), _jsx(Button, { type: "primary", icon: _jsx(PlusOutlined, {}), size: "large", onClick: () => {
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
                        }, children: "\u65B0\u5EFA\u6D41\u7A0B" })] }), _jsx(motion.div, { variants: itemVariants, className: "glass-panel", style: { padding: 24, borderRadius: 16 }, children: _jsx(Table, { dataSource: flowData?.items ?? [], columns: columns, rowKey: "id", loading: isLoading, pagination: {
                        pageSize: 10,
                        showTotal: (total) => `共 ${total} 条`,
                    } }) }), _jsx(Modal, { title: editingFlow ? "编辑自动化流程" : "新建自动化流程", open: modalOpen, okText: editingFlow ? "保存" : "创建", confirmLoading: createMutation.isPending || updateMutation.isPending, onCancel: () => {
                    setModalOpen(false);
                    // Don't reset here - will be done in afterClose to prevent flash
                }, afterClose: () => {
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
                }, onOk: handleSubmit, width: 800, centered: true, maskClosable: true, children: _jsxs(Form, { layout: "vertical", form: form, initialValues: { dsl: EMPTY_DSL, is_active: true, headless: true, browser_type: "chromium", use_cdp_mode: false, cdp_port: 9222, cdp_user_data_dir: undefined }, style: { marginTop: 24 }, children: [_jsx(Form.Item, { name: "name", label: "\u540D\u79F0", rules: [{ required: true }], children: _jsx(Input, { size: "large", placeholder: "\u8F93\u5165\u6D41\u7A0B\u540D\u79F0" }) }), _jsx(Form.Item, { name: "site_id", label: "\u7AD9\u70B9", rules: [{ required: true }], children: _jsx(Select, { size: "large", placeholder: "\u9009\u62E9\u7AD9\u70B9", options: (sites?.items ?? []).map((site) => ({ label: site.name, value: site.id })) }) }), _jsx(Form.Item, { name: "cron_expression", label: "Cron \u8868\u8FBE\u5F0F", children: _jsx(Input, { size: "large", placeholder: "0 9 * * * (\u53EF\u9009)" }) }), _jsx(Space, { style: { marginBottom: 12 }, children: _jsxs(Button.Group, { children: [_jsx(Button, { icon: _jsx(LayoutOutlined, {}), type: editMode === 'visual' ? 'primary' : 'default', onClick: () => setEditMode('visual'), children: "\u53EF\u89C6\u5316\u7F16\u8F91" }), _jsx(Button, { icon: _jsx(CodeOutlined, {}), type: editMode === 'json' ? 'primary' : 'default', onClick: () => setEditMode('json'), children: "JSON \u7F16\u8F91" })] }) }), _jsx(Form.Item, { name: "dsl", label: editMode === "visual" ? "流程步骤" : "DSL JSON", rules: [{ required: true, message: "请配置流程步骤" }], children: editMode === "visual" ? (_jsx(VisualDslEditor, { ref: editorRef, siteUrl: selectedSiteUrl, onValidationChange: handleVisualValidation })) : (_jsx(Input.TextArea, { rows: 10, spellCheck: false, onChange: (e) => {
                                    const val = e.target.value;
                                    const result = validateDslStructure(val);
                                    setDslErrors(result.errors);
                                    setStepCount(result.stepCount);
                                } })) }), _jsxs(Space, { style: { marginBottom: 8 }, align: "start", children: [_jsx(Select, { placeholder: "\u9009\u62E9\u793A\u4F8B\u6A21\u677F\u586B\u5145", style: { minWidth: 220 }, options: DSL_TEMPLATES.map((tpl) => ({
                                        value: tpl.value,
                                        label: tpl.label,
                                        description: tpl.description,
                                    })), onChange: handleTemplateChange, optionRender: (option) => (_jsxs(Space, { direction: "vertical", size: 0, children: [_jsx(Typography.Text, { children: option.data.label }), option.data.description ? (_jsx(Typography.Text, { type: "secondary", style: { fontSize: 12 }, children: option.data.description })) : null] })) }), _jsx(Button, { onClick: handleResetTemplate, children: "\u91CD\u7F6E\u4E3A\u9ED8\u8BA4\u6A21\u677F" })] }), dslErrors.length > 0 ? (_jsx(Alert, { type: "error", showIcon: true, message: "DSL \u6821\u9A8C\u672A\u901A\u8FC7", description: _jsx("ul", { style: { paddingLeft: 20, margin: 0 }, children: dslErrors.map((err) => (_jsx("li", { children: err }, err))) }) })) : (_jsx(Alert, { type: "success", showIcon: true, message: `DSL 校验通过，步骤数：${stepCount || 0}` })), _jsx(Form.Item, { name: "is_active", label: "\u542F\u7528", valuePropName: "checked", children: _jsx(Switch, {}) }), _jsx(Form.Item, { name: "headless", label: "\u9759\u9ED8\u6A21\u5F0F", valuePropName: "checked", tooltip: "\u5F00\u542F\u540E\u6D4F\u89C8\u5668\u5728\u540E\u53F0\u8FD0\u884C\uFF0C\u4E0D\u663E\u793A\u7A97\u53E3", children: _jsx(Switch, {}) }), _jsx(Form.Item, { name: "browser_type", label: "\u6D4F\u89C8\u5668\u7C7B\u578B", tooltip: "\u9009\u62E9\u6267\u884C\u81EA\u52A8\u5316\u7684\u6D4F\u89C8\u5668", children: _jsx(Select, { onChange: (value) => {
                                    // Clear browser_path when switching away from custom
                                    if (value !== "custom") {
                                        form.setFieldValue("browser_path", null);
                                    }
                                }, options: [
                                    { label: "Chromium（全新浏览器）", value: "chromium" },
                                    { label: "Chrome（系统浏览器，可复用登录）", value: "chrome" },
                                    { label: "Edge（系统浏览器，可复用登录）", value: "edge" },
                                    { label: "Firefox", value: "firefox" },
                                    { label: "自定义路径", value: "custom" },
                                ] }) }), _jsx(Form.Item, { noStyle: true, shouldUpdate: (prevValues, currentValues) => prevValues.browser_type !== currentValues.browser_type, children: ({ getFieldValue }) => getFieldValue("browser_type") === "custom" ? (_jsx(Form.Item, { name: "browser_path", label: "\u6D4F\u89C8\u5668\u8DEF\u5F84", rules: [{ required: true, message: "请输入浏览器可执行文件路径" }], children: _jsx(Input, { placeholder: "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" }) })) : null }), _jsx(Form.Item, { name: "use_cdp_mode", label: "CDP\u6A21\u5F0F\uFF08\u72EC\u7ACB\u81EA\u52A8\u5316\u6D4F\u89C8\u5668\uFF09", valuePropName: "checked", tooltip: "\u4F7F\u7528\u590D\u5236\u7684\u6D4F\u89C8\u5668\u914D\u7F6E\uFF0C\u5B8C\u5168\u9694\u79BB\uFF0C\u652F\u6301headless\u9759\u9ED8\u6A21\u5F0F", extra: _jsxs("span", { style: { fontSize: '12px', color: '#52c41a' }, children: ["\u2705 ", _jsx("strong", { children: "\u9996\u6B21" }), "\uFF1A\u81EA\u52A8\u590D\u5236\u6D4F\u89C8\u5668\u914D\u7F6E\uFF08\u5305\u542B\u4E66\u7B7E\u3001\u6269\u5C55\uFF09\uFF0C\u9700\u767B\u5F55\u4E00\u6B21\u7F51\u7AD9", _jsx("br", {}), "\u2705 ", _jsx("strong", { children: "\u540E\u7EED" }), "\uFF1A\u5B8C\u5168\u81EA\u52A8\u5316\uFF0C\u767B\u5F55\u72B6\u6001\u6301\u4E45\u4FDD\u5B58\uFF0C\u4E0E\u65E5\u5E38\u6D4F\u89C8\u5668\u5B8C\u5168\u9694\u79BB", _jsx("br", {}), "\u2705 ", _jsx("strong", { children: "\u652F\u6301" }), "\uFF1A\u9759\u9ED8\u6A21\u5F0F\uFF08headless\uFF09+ CDP\u6A21\u5F0F \u540C\u65F6\u542F\u7528"] }), children: _jsx(Switch, {}) }), _jsx(Form.Item, { noStyle: true, shouldUpdate: (prevValues, currentValues) => prevValues.use_cdp_mode !== currentValues.use_cdp_mode, children: ({ getFieldValue }) => getFieldValue("use_cdp_mode") ? (_jsxs(_Fragment, { children: [_jsx(Form.Item, { name: "cdp_port", label: "CDP\u8C03\u8BD5\u7AEF\u53E3", rules: [{ required: true, message: "请输入CDP端口" }], tooltip: "\u6D4F\u89C8\u5668\u8C03\u8BD5\u7AEF\u53E3\uFF0C\u901A\u5E38\u4E3A9222", children: _jsx(Input, { type: "number", placeholder: "9222" }) }), _jsx(Form.Item, { name: "cdp_user_data_dir", label: "\u81EA\u5B9A\u4E49\u914D\u7F6E\u76EE\u5F55\uFF08\u9AD8\u7EA7\u9009\u9879\uFF09", tooltip: "\u7559\u7A7A\u5219\u81EA\u52A8\u4F7F\u7528\u590D\u5236\u7684\u4E13\u7528\u914D\u7F6E\u3002\u4EC5\u5F53\u9700\u8981\u4F7F\u7528\u7279\u5B9A\u914D\u7F6E\u65F6\u624D\u586B\u5199\u3002", extra: _jsxs("span", { style: { fontSize: '12px', color: '#666' }, children: ["\u26A0\uFE0F \u7559\u7A7A = \u81EA\u52A8\u590D\u5236\u5E76\u4F7F\u7528\u4E13\u7528\u914D\u7F6E\uFF08\u63A8\u8350\uFF0C\u4E0E\u65E5\u5E38\u6D4F\u89C8\u5668\u9694\u79BB\uFF09", _jsx("br", {}), "\u9AD8\u7EA7\uFF1A\u6307\u5B9A\u7279\u5B9A\u8DEF\u5F84\uFF08\u5982 C:\\Users\\\u4F60\\AppData\\Roaming\\autoTool\\cdp_browser_profile\uFF09"] }), children: _jsx(Input, { placeholder: "\u7559\u7A7A\u4F7F\u7528\u81EA\u52A8\u590D\u5236\u7684\u4E13\u7528\u914D\u7F6E\uFF08\u63A8\u8350\uFF09" }) })] })) : null })] }) })] }));
};
export default FlowsPage;
