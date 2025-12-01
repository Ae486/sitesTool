import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
/**
 * Container Group Node - Visual container for loop/if_else with child nodes
 * Uses React Flow's native group/parentId system
 */
import { memo } from "react";
import { Handle, Position, NodeResizer } from "@xyflow/react";
import { Typography, Tag } from "antd";
import { ReloadOutlined, BranchesOutlined } from "@ant-design/icons";
const { Text } = Typography;
// Container theme colors
const CONTAINER_THEMES = {
    loop: {
        border: "#722ed1",
        bg: "rgba(249, 240, 255, 0.8)",
        headerBg: "#efdbff",
        icon: ReloadOutlined,
        label: "循环执行",
    },
    loop_array: {
        border: "#13c2c2",
        bg: "rgba(230, 255, 251, 0.8)",
        headerBg: "#b5f5ec",
        icon: ReloadOutlined,
        label: "遍历数组",
    },
    if_else: {
        border: "#fa8c16",
        bg: "rgba(255, 247, 230, 0.8)",
        headerBg: "#ffd591",
        icon: BranchesOutlined,
        label: "条件分支",
    },
};
function ContainerGroupNode({ id, data, selected }) {
    const { stepType, fields, description, containerType } = data;
    const type = containerType || stepType;
    const theme = CONTAINER_THEMES[type] || CONTAINER_THEMES.loop;
    const Icon = theme.icon;
    // Get display info based on container type
    const getDisplayInfo = () => {
        switch (type) {
            case "loop":
                return `循环 ${fields.times || "?"} 次`;
            case "loop_array":
                return `遍历 ${fields.array_variable || "数组"}`;
            case "if_else":
                const condType = fields.condition_type || "variable_truthy";
                return condType.replace("_", " ");
            default:
                return theme.label;
        }
    };
    return (_jsxs(_Fragment, { children: [_jsx(NodeResizer, { minWidth: 250, minHeight: 150, isVisible: selected, lineClassName: "border-blue-400", handleClassName: "h-3 w-3 bg-white border-2 border-blue-400" }), _jsx(Handle, { type: "target", position: Position.Top, id: "top", style: {
                    background: theme.border,
                    width: 10,
                    height: 10,
                    border: "2px solid #fff",
                } }), _jsx(Handle, { type: "target", position: Position.Left, id: "left", style: {
                    background: theme.border,
                    width: 10,
                    height: 10,
                    border: "2px solid #fff",
                } }), _jsxs("div", { style: {
                    width: "100%",
                    height: "100%",
                    borderRadius: 12,
                    border: `2px solid ${theme.border}`,
                    background: theme.bg,
                    boxShadow: selected
                        ? `0 4px 12px rgba(0,0,0,0.15)`
                        : "0 2px 8px rgba(0,0,0,0.08)",
                    display: "flex",
                    flexDirection: "column",
                }, children: [_jsxs("div", { style: {
                            padding: "8px 12px",
                            background: theme.headerBg,
                            borderRadius: "10px 10px 0 0",
                            borderBottom: `1px solid ${theme.border}`,
                            display: "flex",
                            alignItems: "center",
                            gap: 8,
                        }, children: [_jsx(Icon, { style: { color: theme.border, fontSize: 16 } }), _jsx(Text, { strong: true, style: { fontSize: 13 }, children: theme.label }), _jsx(Tag, { color: type === "if_else" ? "orange" : type === "loop_array" ? "cyan" : "purple", children: getDisplayInfo() })] }), _jsx("div", { style: {
                            flex: 1,
                            padding: 12,
                            minHeight: 100,
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                        }, children: _jsx(Text, { type: "secondary", style: { fontSize: 11 }, children: "\u5C06\u6B65\u9AA4\u62D6\u5165\u6B64\u533A\u57DF" }) }), description && (_jsx("div", { style: { padding: "4px 12px 8px", borderTop: `1px dashed ${theme.border}` }, children: _jsx(Text, { type: "secondary", style: { fontSize: 10 }, children: description }) }))] }), _jsx(Handle, { type: "source", position: Position.Bottom, id: "bottom", style: {
                    background: theme.border,
                    width: 10,
                    height: 10,
                    border: "2px solid #fff",
                } }), _jsx(Handle, { type: "source", position: Position.Right, id: "right", style: {
                    background: theme.border,
                    width: 10,
                    height: 10,
                    border: "2px solid #fff",
                } })] }));
}
export default memo(ContainerGroupNode);
