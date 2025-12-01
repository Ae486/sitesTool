import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * Ghost Node - Semi-transparent preview node that follows cursor
 * Performance optimized: uses CSS transform, pointer-events: none
 */
import { memo } from "react";
import { Typography } from "antd";
import { GlobalOutlined, AimOutlined, EditOutlined, CameraOutlined, ClockCircleOutlined, CodeOutlined, BranchesOutlined, ReloadOutlined, } from "@ant-design/icons";
import { DSL_SCHEMA } from "../../../constants/dsl";
const { Text } = Typography;
// Icon mapping
const STEP_ICONS = {
    navigate: _jsx(GlobalOutlined, {}),
    click: _jsx(AimOutlined, {}),
    input: _jsx(EditOutlined, {}),
    screenshot: _jsx(CameraOutlined, {}),
    wait_time: _jsx(ClockCircleOutlined, {}),
    wait_for: _jsx(ClockCircleOutlined, {}),
    if_else: _jsx(BranchesOutlined, {}),
    loop: _jsx(ReloadOutlined, {}),
    loop_array: _jsx(ReloadOutlined, {}),
    default: _jsx(CodeOutlined, {}),
};
// Color mapping
const STEP_COLORS = {
    navigate: "#1890ff",
    click: "#52c41a",
    input: "#faad14",
    screenshot: "#eb2f96",
    wait_time: "#722ed1",
    wait_for: "#722ed1",
    if_else: "#fa8c16",
    loop: "#722ed1",
    loop_array: "#13c2c2",
    default: "#8c8c8c",
};
function GhostNode({ stepType, x, y }) {
    const schema = DSL_SCHEMA[stepType];
    const icon = STEP_ICONS[stepType] || STEP_ICONS.default;
    const color = STEP_COLORS[stepType] || STEP_COLORS.default;
    return (_jsx("div", { style: {
            position: "fixed",
            left: 0,
            top: 0,
            // Use transform for smooth animation (GPU accelerated)
            transform: `translate(${x}px, ${y}px) translate(-50%, -50%)`,
            pointerEvents: "none",
            zIndex: 9999,
            opacity: 0.7,
            willChange: "transform", // Hint browser for optimization
        }, children: _jsxs("div", { style: {
                background: "#fff",
                borderRadius: 8,
                borderLeft: `4px solid ${color}`,
                boxShadow: `0 0 0 2px ${color}40, 0 4px 12px rgba(0,0,0,0.15)`,
                padding: "10px 12px",
                minWidth: 140,
            }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8 }, children: [_jsx("span", { style: { color, fontSize: 15 }, children: icon }), _jsx(Text, { strong: true, style: { fontSize: 12, color: "#262626" }, children: schema?.label || stepType })] }), _jsx(Text, { type: "secondary", style: { fontSize: 10, display: "block", marginTop: 4 }, children: "\u70B9\u51FB\u653E\u7F6E\u8282\u70B9" })] }) }));
}
export default memo(GhostNode);
