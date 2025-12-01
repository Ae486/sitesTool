import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { PlusOutlined, StopOutlined } from "@ant-design/icons";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button, Form, Typography, message } from "antd";
import { motion } from "framer-motion";
import { useEffect, useMemo, useState } from "react";
import { createFlow, deleteFlow, fetchFlows, getRunningFlows, stopFlow, triggerFlow, updateFlow, } from "../api/flows";
import { fetchSites } from "../api/sites";
import { FlowTable, FlowFormModal } from "../components/flows";
import { containerVariants, itemVariants } from "../constants/animations";
const EMPTY_DSL = { version: 1, steps: [] };
const FlowsPage = () => {
    const [modalOpen, setModalOpen] = useState(false);
    const [editingFlow, setEditingFlow] = useState(null);
    const [runningFlows, setRunningFlows] = useState(new Set());
    const [executingFlows, setExecutingFlows] = useState(new Set());
    const [form] = Form.useForm();
    const { data: flowData, isLoading, refetch } = useQuery({
        queryKey: ["flows"],
        queryFn: fetchFlows,
    });
    const { data: sites } = useQuery({
        queryKey: ["sites"],
        queryFn: fetchSites,
    });
    // Poll for running flows status
    useEffect(() => {
        const checkRunningFlows = async () => {
            try {
                const { running_flows } = await getRunningFlows();
                const newRunningFlows = new Set(running_flows);
                if (newRunningFlows.size !== runningFlows.size ||
                    ![...newRunningFlows].every((id) => runningFlows.has(id))) {
                    setRunningFlows(newRunningFlows);
                }
            }
            catch {
                // Ignore polling errors
            }
        };
        checkRunningFlows();
        if (runningFlows.size > 0 || executingFlows.size > 0) {
            const interval = setInterval(checkRunningFlows, 2000);
            return () => clearInterval(interval);
        }
    }, [runningFlows.size, executingFlows.size]);
    const siteNameMap = useMemo(() => {
        const map = new Map();
        (sites?.items ?? []).forEach((site) => map.set(site.id, site.name));
        return map;
    }, [sites]);
    // Mutations
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
        onError: (_error, flowId) => {
            message.error("触发失败");
            setExecutingFlows((prev) => {
                const newSet = new Set(prev);
                newSet.delete(flowId);
                return newSet;
            });
        },
        onSettled: (_data, _error, flowId) => {
            setExecutingFlows((prev) => {
                const newSet = new Set(prev);
                newSet.delete(flowId);
                return newSet;
            });
        },
    });
    const stopMutation = useMutation({
        mutationFn: stopFlow,
        onSuccess: (_data, flowId) => {
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
    // Handlers
    const handleEdit = (flow) => {
        setEditingFlow(flow);
        form.setFieldsValue({
            name: flow.name,
            site_id: flow.site_id,
            cron_expression: flow.cron_expression,
            dsl: flow.dsl,
            is_active: flow.is_active,
            headless: flow.headless ?? true,
            browser_type: flow.browser_type ?? "chromium",
            browser_path: flow.browser_path,
            use_cdp_mode: flow.use_cdp_mode,
            cdp_port: flow.cdp_port,
            cdp_user_data_dir: flow.cdp_user_data_dir,
        });
        setModalOpen(true);
    };
    const handleCreate = () => {
        setEditingFlow(null);
        form.setFieldsValue({
            name: "",
            site_id: undefined,
            cron_expression: "",
            dsl: EMPTY_DSL,
            is_active: true,
            headless: true,
            browser_type: "chromium",
            use_cdp_mode: false,
            cdp_port: 9222,
            cdp_user_data_dir: undefined,
        });
        setModalOpen(true);
    };
    const handleModalCancel = () => {
        setModalOpen(false);
    };
    const handleModalSubmit = (values, dsl) => {
        if (editingFlow) {
            updateMutation.mutate({ id: editingFlow.id, data: { ...values, dsl } });
        }
        else {
            createMutation.mutate({ ...values, dsl });
        }
    };
    return (_jsxs(motion.div, { variants: containerVariants, initial: "hidden", animate: "show", children: [_jsxs(motion.div, { variants: itemVariants, style: {
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    marginBottom: 24,
                }, children: [_jsx(Typography.Title, { level: 4, style: { margin: 0 }, children: "\u81EA\u52A8\u5316\u6D41\u7A0B" }), _jsx(Button, { type: "primary", icon: _jsx(PlusOutlined, {}), size: "large", onClick: handleCreate, children: "\u65B0\u5EFA\u6D41\u7A0B" })] }), _jsx(motion.div, { variants: itemVariants, className: "glass-panel", style: { padding: 24, borderRadius: 16 }, children: _jsx(FlowTable, { flows: flowData?.items ?? [], loading: isLoading, siteNameMap: siteNameMap, runningFlows: runningFlows, executingFlows: executingFlows, onEdit: handleEdit, onDelete: (id) => deleteMutation.mutate(id), onTrigger: (id) => triggerMutation.mutate(id), onStop: (id) => stopMutation.mutate(id), deleteLoading: deleteMutation.isPending, stopLoading: stopMutation.isPending, stoppingFlowId: stopMutation.variables }) }), _jsx(FlowFormModal, { open: modalOpen, editingFlow: editingFlow, sites: sites?.items ?? [], form: form, loading: createMutation.isPending || updateMutation.isPending, onCancel: handleModalCancel, onSubmit: handleModalSubmit })] }));
};
export default FlowsPage;
