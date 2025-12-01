import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * Start Node - The entry point of workflow, cannot be deleted
 */
import { memo } from "react";
import { Handle, Position } from "@xyflow/react";
import { Typography } from "antd";
import { PlayCircleOutlined } from "@ant-design/icons";
const { Text } = Typography;
function StartNode({ selected }) {
    return (_jsxs("div", { style: {
            minWidth: 120,
            textAlign: "center",
        }, children: [_jsx("div", { style: {
                    background: "linear-gradient(135deg, #52c41a 0%, #73d13d 100%)",
                    borderRadius: 12,
                    padding: "12px 20px",
                    boxShadow: selected
                        ? "0 0 0 2px #52c41a, 0 4px 12px rgba(82, 196, 26, 0.4)"
                        : "0 2px 8px rgba(0, 0, 0, 0.15)",
                    transition: "box-shadow 0.2s ease",
                }, children: _jsxs("div", { style: { display: "flex", alignItems: "center", justifyContent: "center", gap: 8 }, children: [_jsx(PlayCircleOutlined, { style: { color: "#fff", fontSize: 18 } }), _jsx(Text, { strong: true, style: { color: "#fff", fontSize: 14 }, children: "\u5F00\u59CB" })] }) }), _jsx(Handle, { type: "source", position: Position.Bottom, id: "bottom", style: {
                    background: "#52c41a",
                    width: 10,
                    height: 10,
                    border: "2px solid #fff",
                } }), _jsx(Handle, { type: "source", position: Position.Right, id: "right", style: {
                    background: "#52c41a",
                    width: 10,
                    height: 10,
                    border: "2px solid #fff",
                } })] }));
}
export default memo(StartNode);
