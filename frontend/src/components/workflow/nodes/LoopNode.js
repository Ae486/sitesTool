import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * Loop Node - loop/loop_array container with expandable content
 */
import { memo, useState } from "react";
import { Handle, Position } from "@xyflow/react";
import { Card, Typography, Tag, Button } from "antd";
import { ReloadOutlined, DownOutlined, RightOutlined } from "@ant-design/icons";
const { Text } = Typography;
function LoopNode({ id, data, selected }) {
    const { stepType, fields, description, childrenData } = data;
    const [expanded, setExpanded] = useState(false);
    const isArrayLoop = stepType === "loop_array";
    const loopInfo = isArrayLoop
        ? `遍历 ${fields.array_variable || "数组"}`
        : `循环 ${fields.times || "?"} 次`;
    const childCount = childrenData?.length || 0;
    const themeColor = isArrayLoop ? "#13c2c2" : "#722ed1";
    const handleStyle = {
        background: selected ? themeColor : "#8c8c8c",
        width: 8,
        height: 8,
        border: "2px solid #fff",
    };
    return (_jsxs("div", { style: { minWidth: 220, position: "relative" }, children: [_jsx(Handle, { type: "target", position: Position.Top, id: "top", style: handleStyle }), _jsx(Handle, { type: "target", position: Position.Left, id: "left", style: handleStyle }), _jsxs(Card, { size: "small", style: {
                    borderRadius: 8,
                    borderLeft: isArrayLoop ? "4px solid #13c2c2" : "4px solid #722ed1",
                    background: isArrayLoop ? "#e6fffb" : "#f9f0ff",
                    boxShadow: selected
                        ? `0 4px 12px rgba(${isArrayLoop ? "19, 194, 194" : "114, 46, 209"}, 0.3)`
                        : "0 2px 8px rgba(0,0,0,0.1)",
                    border: selected
                        ? `1px solid ${isArrayLoop ? "#13c2c2" : "#722ed1"}`
                        : undefined,
                }, styles: {
                    body: { padding: "8px 12px" },
                }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8 }, children: [_jsx(ReloadOutlined, { style: {
                                    color: isArrayLoop ? "#13c2c2" : "#722ed1",
                                    fontSize: 16
                                } }), _jsx(Text, { strong: true, style: { fontSize: 13 }, children: isArrayLoop ? "遍历数组" : "循环执行" }), childCount > 0 && (_jsx(Button, { type: "text", size: "small", icon: expanded ? _jsx(DownOutlined, {}) : _jsx(RightOutlined, {}), onClick: () => setExpanded(!expanded), style: { marginLeft: "auto" } }))] }), _jsxs("div", { style: { marginTop: 8 }, children: [_jsx(Tag, { color: isArrayLoop ? "cyan" : "purple", children: loopInfo }), isArrayLoop && Boolean(fields.item_variable) && (_jsxs(Text, { type: "secondary", style: { fontSize: 11, display: "block", marginTop: 4 }, children: ["\u2192 ", String(fields.item_variable)] }))] }), description && (_jsx(Tag, { color: "blue", style: { marginTop: 4, fontSize: 10 }, children: description })), _jsxs("div", { style: {
                            marginTop: 8,
                            fontSize: 11,
                            color: "#8c8c8c",
                            borderTop: "1px dashed #d9d9d9",
                            paddingTop: 8,
                            display: "flex",
                            justifyContent: "space-between",
                        }, children: [_jsxs("span", { style: { color: themeColor }, children: ["\u21BB \u5FAA\u73AF\u4F53 ", childCount > 0 ? `(${childCount})` : ""] }), _jsx("span", { children: "\u2193 \u540E\u7EED" })] }), expanded && childrenData && childrenData.length > 0 && (_jsx("div", { style: {
                            marginTop: 8,
                            padding: 8,
                            background: "rgba(255,255,255,0.5)",
                            borderRadius: 4,
                            fontSize: 11,
                        }, children: childrenData.map((child, idx) => (_jsxs("div", { style: {
                                padding: "2px 0",
                                color: "#595959",
                            }, children: [idx + 1, ". ", child.label] }, idx))) }))] }), _jsx(Handle, { type: "source", position: Position.Bottom, id: "body", style: {
                    background: themeColor,
                    width: 10,
                    height: 10,
                    border: "2px solid #fff",
                    left: "35%",
                } }), _jsx(Handle, { type: "source", position: Position.Bottom, id: "next", style: {
                    background: "#8c8c8c",
                    width: 10,
                    height: 10,
                    border: "2px solid #fff",
                    left: "65%",
                } }), _jsx(Handle, { type: "source", position: Position.Right, id: "right", style: handleStyle })] }));
}
export default memo(LoopNode);
