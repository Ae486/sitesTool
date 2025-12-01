import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { DeleteOutlined, EditOutlined, PlayCircleOutlined, StopOutlined } from "@ant-design/icons";
import { Button, Popconfirm, Space, Table, Tag } from "antd";
import { useMemo } from "react";
const FlowTable = ({ flows, loading, siteNameMap, runningFlows, executingFlows, onEdit, onDelete, onTrigger, onStop, deleteLoading, stopLoading, stoppingFlowId, }) => {
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
            render: (_, flow) => (_jsx(Tag, { color: "default", style: { borderRadius: "4px", border: "1px solid #e4e4e7", background: "#fafafa" }, children: siteNameMap.get(flow.site_id) ?? flow.site_id })),
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
            render: (_, flow) => (_jsxs(Space, { children: [_jsx(Button, { icon: _jsx(EditOutlined, {}), onClick: () => onEdit(flow), children: "\u7F16\u8F91" }), _jsx(Popconfirm, { title: "\u786E\u8BA4\u5220\u9664", description: "\u5220\u9664\u540E\u65E0\u6CD5\u6062\u590D\uFF0C\u786E\u5B9A\u8981\u5220\u9664\u8FD9\u4E2A\u6D41\u7A0B\u5417\uFF1F", onConfirm: () => onDelete(flow.id), okText: "\u5220\u9664", cancelText: "\u53D6\u6D88", okButtonProps: { danger: true }, children: _jsx(Button, { icon: _jsx(DeleteOutlined, {}), danger: true, loading: deleteLoading, children: "\u5220\u9664" }) }), runningFlows.has(flow.id) ? (_jsx(Button, { danger: true, icon: _jsx(StopOutlined, {}), loading: stopLoading && stoppingFlowId === flow.id, onClick: () => onStop(flow.id), children: "\u4E2D\u65AD" })) : (_jsx(Button, { type: "primary", icon: _jsx(PlayCircleOutlined, {}), loading: executingFlows.has(flow.id), disabled: executingFlows.has(flow.id), onClick: () => onTrigger(flow.id), children: "\u6267\u884C" }))] })),
        },
    ], [siteNameMap, runningFlows, executingFlows, onEdit, onDelete, onTrigger, onStop, deleteLoading, stopLoading, stoppingFlowId]);
    return (_jsx(Table, { dataSource: flows, columns: columns, rowKey: "id", loading: loading, pagination: {
            pageSize: 10,
            showTotal: (total) => `共 ${total} 条`,
        } }));
};
export default FlowTable;
