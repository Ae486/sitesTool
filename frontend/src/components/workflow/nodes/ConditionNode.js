import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * Condition Node - if_else with dual outputs (true/false branches)
 */
import { memo } from "react";
import { Handle, Position } from "@xyflow/react";
import { Card, Typography, Tag } from "antd";
import { BranchesOutlined } from "@ant-design/icons";
const { Text } = Typography;
// Condition type labels
const CONDITION_LABELS = {
    variable_truthy: "变量为真",
    variable_equals: "变量等于",
    variable_contains: "变量包含",
    variable_greater: "变量大于",
    variable_less: "变量小于",
    element_exists: "元素存在",
    element_visible: "元素可见",
    element_text_equals: "文本等于",
    element_text_contains: "文本包含",
};
function ConditionNode({ id, data, selected }) {
    const { fields, description } = data;
    const conditionType = fields.condition_type || "variable_truthy";
    const conditionLabel = CONDITION_LABELS[conditionType] || conditionType;
    // Get condition target
    const target = fields.condition_variable || fields.condition_selector || "";
    const handleStyle = {
        background: selected ? "#fa8c16" : "#8c8c8c",
        width: 8,
        height: 8,
        border: "2px solid #fff",
    };
    return (_jsxs("div", { style: { minWidth: 200, position: "relative" }, children: [_jsx(Handle, { type: "target", position: Position.Top, id: "top", style: handleStyle }), _jsx(Handle, { type: "target", position: Position.Left, id: "left", style: handleStyle }), _jsxs(Card, { size: "small", style: {
                    borderRadius: 8,
                    borderLeft: "4px solid #fa8c16",
                    background: "#fff7e6",
                    boxShadow: selected
                        ? "0 4px 12px rgba(250, 140, 22, 0.3)"
                        : "0 2px 8px rgba(0,0,0,0.1)",
                    border: selected ? "1px solid #fa8c16" : undefined,
                }, styles: {
                    body: { padding: "8px 12px" },
                }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8 }, children: [_jsx(BranchesOutlined, { style: { color: "#fa8c16", fontSize: 16 } }), _jsx(Text, { strong: true, style: { fontSize: 13 }, children: "\u6761\u4EF6\u5206\u652F" })] }), _jsxs("div", { style: { marginTop: 8 }, children: [_jsx(Tag, { color: "orange", children: conditionLabel }), target && (_jsx(Text, { type: "secondary", style: { fontSize: 11, display: "block", marginTop: 4 }, children: String(target).slice(0, 25) }))] }), description && (_jsx(Tag, { color: "blue", style: { marginTop: 4, fontSize: 10 }, children: description })), _jsxs("div", { style: {
                            display: "flex",
                            justifyContent: "space-between",
                            marginTop: 12,
                            fontSize: 10,
                            padding: "0 8px",
                        }, children: [_jsx("span", { style: { color: "#52c41a" }, children: "\u2713 \u6210\u7ACB" }), _jsx("span", { style: { color: "#8c8c8c" }, children: "\u2193 \u540E\u7EED" }), _jsx("span", { style: { color: "#ff4d4f" }, children: "\u2717 \u4E0D\u6210\u7ACB" })] })] }), _jsx(Handle, { type: "source", position: Position.Bottom, id: "then", style: {
                    background: "#52c41a",
                    width: 10,
                    height: 10,
                    border: "2px solid #fff",
                    left: "25%",
                } }), _jsx(Handle, { type: "source", position: Position.Bottom, id: "next", style: {
                    background: "#8c8c8c",
                    width: 10,
                    height: 10,
                    border: "2px solid #fff",
                    left: "50%",
                } }), _jsx(Handle, { type: "source", position: Position.Bottom, id: "else", style: {
                    background: "#ff4d4f",
                    width: 10,
                    height: 10,
                    border: "2px solid #fff",
                    left: "75%",
                } }), _jsx(Handle, { type: "source", position: Position.Right, id: "right", style: handleStyle })] }));
}
export default memo(ConditionNode);
