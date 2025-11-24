import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { CheckCircleOutlined, CloseCircleOutlined, DeleteOutlined, EyeOutlined, WarningOutlined } from "@ant-design/icons";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Alert, Button, Card, Descriptions, Empty, Image, Modal, Popconfirm, Select, Space, Table, Tag, Timeline, Typography, message, } from "antd";
import { motion } from "framer-motion";
import { useMemo, useState } from "react";
import { deleteHistory, fetchHistory } from "../api/history";
import { containerVariants, itemVariants } from "../constants/animations";
const { Text } = Typography;
const HistoryPage = () => {
    // ... state hooks ...
    const [detailModalOpen, setDetailModalOpen] = useState(false);
    const [selectedHistory, setSelectedHistory] = useState(null);
    const [errorTypeFilter, setErrorTypeFilter] = useState(undefined);
    // ... queries and mutations ...
    const { data: historyData, isLoading, refetch, } = useQuery({
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
        const values = new Set();
        historyItems.forEach((record) => {
            record.error_types?.forEach((type) => values.add(type));
        });
        return Array.from(values).map((type) => ({ label: type, value: type }));
    }, [historyItems]);
    const buildScreenshotUrl = (path) => {
        const normalized = path.replace(/\\/g, "/");
        const segments = normalized.split("/");
        const filename = segments[segments.length - 1] ?? "";
        return filename ? `/screenshots/${filename}` : "";
    };
    const getFlowName = (flowId) => {
        const flow = flowsData?.items.find((f) => f.id === flowId);
        return flow?.name || `流程 #${flowId}`;
    };
    const getStatusColor = (status) => {
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
    const getStatusText = (status) => {
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
    const formatDuration = (ms) => {
        if (!ms)
            return "-";
        if (ms < 1000)
            return `${ms}ms`;
        return `${(ms / 1000).toFixed(2)}s`;
    };
    const formatDateTime = (dateStr) => {
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
            render: (text) => (_jsx("span", { style: { fontWeight: 500 }, children: formatDateTime(text) })),
            width: 180,
        },
        {
            title: "流程名称",
            dataIndex: "flow_id",
            key: "flow_name",
            render: (flowId) => (_jsx(Tag, { color: "blue", style: { borderRadius: "4px" }, children: getFlowName(flowId) })),
        },
        {
            title: "状态",
            dataIndex: "status",
            key: "status",
            render: (status) => (_jsx(Tag, { color: getStatusColor(status), style: { borderRadius: "12px" }, children: getStatusText(status) })),
            width: 100,
        },
        {
            title: "耗时",
            dataIndex: "duration_ms",
            key: "duration",
            render: (ms) => (_jsx("span", { style: { color: "var(--text-secondary)" }, children: formatDuration(ms) })),
            width: 100,
        },
        {
            title: "错误类型",
            dataIndex: "error_types",
            key: "error_types",
            render: (types = []) => types.length ? (_jsx(Space, { size: 4, children: types.map((type) => (_jsx(Tag, { color: "volcano", style: { borderRadius: "4px" }, children: type }, type))) })) : (_jsx(Text, { type: "secondary", children: "-" })),
            width: 200,
        },
        {
            title: "操作",
            key: "actions",
            render: (record) => (_jsxs(Space, { children: [_jsx(Button, { icon: _jsx(EyeOutlined, {}), size: "small", onClick: () => {
                            setSelectedHistory(record);
                            setDetailModalOpen(true);
                        }, children: "\u8BE6\u60C5" }), _jsx(Popconfirm, { title: "\u786E\u8BA4\u5220\u9664", description: "\u5220\u9664\u540E\u65E0\u6CD5\u6062\u590D", onConfirm: () => deleteMutation.mutate(record.id), okText: "\u5220\u9664", cancelText: "\u53D6\u6D88", okButtonProps: { danger: true }, children: _jsx(Button, { icon: _jsx(DeleteOutlined, {}), size: "small", danger: true, loading: deleteMutation.isPending && deleteMutation.variables === record.id, children: "\u5220\u9664" }) })] })),
            width: 150,
        },
    ];
    return (_jsxs(motion.div, { variants: containerVariants, initial: "hidden", animate: "show", children: [_jsxs(motion.div, { variants: itemVariants, style: { display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }, children: [_jsx(Typography.Title, { level: 4, style: { margin: 0 }, children: "\u6267\u884C\u5386\u53F2" }), _jsxs(Space, { children: [_jsx(Select, { allowClear: true, placeholder: "\u6309\u9519\u8BEF\u7C7B\u578B\u7B5B\u9009", style: { minWidth: 200 }, options: errorTypeOptions, value: errorTypeFilter, onChange: (value) => setErrorTypeFilter(value) }), _jsx(Button, { onClick: () => refetch(), disabled: isLoading, children: "\u5237\u65B0" })] })] }), _jsx(motion.div, { variants: itemVariants, className: "glass-panel", style: { padding: 24, borderRadius: 16 }, children: historyItems.length === 0 ? (_jsx(Empty, { description: "\u6682\u65E0\u6267\u884C\u8BB0\u5F55" })) : (_jsx(Table, { dataSource: historyItems, columns: columns, rowKey: "id", loading: isLoading, pagination: {
                        total: historyData?.total,
                        pageSize: 50,
                        showSizeChanger: false,
                        showTotal: (total) => `共 ${total} 条`,
                    } })) }), _jsx(Modal, { title: "\u6267\u884C\u8BE6\u60C5", open: detailModalOpen, onCancel: () => setDetailModalOpen(false), footer: [
                    _jsx(Button, { onClick: () => setDetailModalOpen(false), children: "\u5173\u95ED" }, "close"),
                ], width: 900, centered: true, children: selectedHistory && (_jsxs(Space, { direction: "vertical", style: { width: "100%" }, size: "large", children: [_jsx(Card, { size: "small", title: "\u57FA\u672C\u4FE1\u606F", className: "glass-card", children: _jsxs(Descriptions, { column: 2, size: "small", children: [_jsx(Descriptions.Item, { label: "\u6D41\u7A0B\u540D\u79F0", children: getFlowName(selectedHistory.flow_id) }), _jsx(Descriptions.Item, { label: "\u6267\u884C\u72B6\u6001", children: _jsx(Tag, { color: getStatusColor(selectedHistory.status), children: getStatusText(selectedHistory.status) }) }), _jsx(Descriptions.Item, { label: "\u9519\u8BEF\u7C7B\u578B", span: 2, children: selectedHistory.error_types?.length ? (_jsx(Space, { size: 4, children: selectedHistory.error_types.map((type) => (_jsx(Tag, { color: "volcano", children: type }, type))) })) : (_jsx(Text, { type: "secondary", children: "-" })) }), _jsx(Descriptions.Item, { label: "\u5F00\u59CB\u65F6\u95F4", children: formatDateTime(selectedHistory.started_at) }), _jsx(Descriptions.Item, { label: "\u7ED3\u675F\u65F6\u95F4", children: selectedHistory.finished_at ? formatDateTime(selectedHistory.finished_at) : "-" }), _jsx(Descriptions.Item, { label: "\u6267\u884C\u8017\u65F6", children: formatDuration(selectedHistory.duration_ms) })] }) }), selectedHistory.screenshot_files?.length ? (_jsx(Card, { size: "small", title: "\u622A\u56FE\u6C47\u603B", className: "glass-card", children: _jsx(Image.PreviewGroup, { children: _jsx(Space, { size: "middle", wrap: true, children: selectedHistory.screenshot_files.map((file) => (_jsx(Image, { src: buildScreenshotUrl(file), alt: file, width: 220, style: { borderRadius: 4 } }, file))) }) }) })) : null, selectedHistory.result_payload && (() => {
                            try {
                                const result = JSON.parse(selectedHistory.result_payload);
                                const stepResults = result.step_results || [];
                                if (stepResults.length > 0) {
                                    return (_jsx(Card, { size: "small", title: "\u6B65\u9AA4\u6267\u884C\u8BE6\u60C5", className: "glass-card", children: _jsx(Timeline, { items: stepResults.map((step, index) => {
                                                const isSuccess = step.success;
                                                const hasError = step.error;
                                                return {
                                                    color: isSuccess ? "green" : "red",
                                                    dot: isSuccess ? (_jsx(CheckCircleOutlined, { style: { fontSize: "16px" } })) : (_jsx(CloseCircleOutlined, { style: { fontSize: "16px" } })),
                                                    children: (_jsx("div", { children: _jsxs(Space, { direction: "vertical", style: { width: "100%" }, size: "small", children: [_jsxs("div", { children: [_jsxs(Text, { strong: true, style: { fontSize: "14px" }, children: ["\u6B65\u9AA4 ", index + 1, ": ", step.step_type] }), _jsx(Tag, { color: isSuccess ? "success" : "error", style: { marginLeft: "8px" }, children: isSuccess ? "成功" : "失败" }), _jsxs(Text, { type: "secondary", style: { marginLeft: "8px" }, children: ["\u8017\u65F6: ", formatDuration(step.duration_ms)] })] }), isSuccess && step.message && (_jsx(Text, { type: "secondary", children: step.message })), hasError && (_jsx(Alert, { message: "\u9519\u8BEF\u8BE6\u60C5", description: _jsxs(Space, { direction: "vertical", style: { width: "100%" }, children: [_jsx(Text, { children: step.error }), step.screenshot_path && (_jsxs("div", { children: [_jsx(Text, { strong: true, children: "\u9519\u8BEF\u622A\u56FE\uFF1A" }), _jsx("div", { style: { marginTop: "8px" }, children: _jsx(Image, { src: buildScreenshotUrl(step.screenshot_path), alt: "\u9519\u8BEF\u622A\u56FE", style: { maxWidth: "100%" }, placeholder: _jsx("div", { style: {
                                                                                                    background: "#f0f0f0",
                                                                                                    padding: "20px",
                                                                                                    textAlign: "center"
                                                                                                }, children: "\u52A0\u8F7D\u4E2D..." }) }) })] }))] }), type: "error", showIcon: true, icon: _jsx(WarningOutlined, {}) })), step.extracted_data && (_jsx("div", { children: _jsxs(Text, { type: "secondary", children: ["\u63D0\u53D6\u6570\u636E: ", JSON.stringify(step.extracted_data)] }) }))] }) })),
                                                };
                                            }) }) }));
                                }
                            }
                            catch (e) {
                                console.error("Failed to parse result_payload:", e);
                            }
                            return null;
                        })(), selectedHistory.error_message && (_jsx(Alert, { message: "\u6267\u884C\u5931\u8D25", description: selectedHistory.error_message, type: "error", showIcon: true })), selectedHistory.result_payload && (_jsx(Card, { size: "small", title: "\u539F\u59CB\u6267\u884C\u6570\u636E", style: { marginTop: "16px" }, bodyStyle: { maxHeight: "200px", overflow: "auto" }, className: "glass-card", children: _jsx("pre", { style: {
                                    background: "rgba(0,0,0,0.02)",
                                    padding: "12px",
                                    borderRadius: "4px",
                                    fontSize: "12px",
                                    margin: 0,
                                }, children: JSON.stringify(JSON.parse(selectedHistory.result_payload), null, 2) }) }))] })) })] }));
};
export default HistoryPage;
