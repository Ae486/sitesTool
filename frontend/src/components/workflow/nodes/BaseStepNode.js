import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * Base Step Node - renders most DSL step types
 * Optimized with better visual feedback and memoization
 */
import { memo, useMemo } from "react";
import { Handle, Position } from "@xyflow/react";
import { Typography } from "antd";
import { GlobalOutlined, AimOutlined, EditOutlined, CameraOutlined, ClockCircleOutlined, CodeOutlined, SearchOutlined, CheckCircleOutlined, KeyOutlined, SelectOutlined, } from "@ant-design/icons";
import { DSL_SCHEMA } from "../../../constants/dsl";
const { Text } = Typography;
// Icon mapping for step types
const STEP_ICONS = {
    navigate: _jsx(GlobalOutlined, {}),
    click: _jsx(AimOutlined, {}),
    input: _jsx(EditOutlined, {}),
    screenshot: _jsx(CameraOutlined, {}),
    wait_time: _jsx(ClockCircleOutlined, {}),
    wait_for: _jsx(ClockCircleOutlined, {}),
    extract: _jsx(SearchOutlined, {}),
    eval_js: _jsx(CodeOutlined, {}),
    assert_text: _jsx(CheckCircleOutlined, {}),
    assert_visible: _jsx(CheckCircleOutlined, {}),
    keyboard: _jsx(KeyOutlined, {}),
    select: _jsx(SelectOutlined, {}),
};
// Color mapping for step categories
const STEP_COLORS = {
    navigate: "#1890ff",
    click: "#52c41a",
    input: "#faad14",
    screenshot: "#eb2f96",
    wait_time: "#722ed1",
    wait_for: "#722ed1",
    random_delay: "#722ed1",
    extract: "#13c2c2",
    extract_all: "#13c2c2",
    eval_js: "#fa541c",
    assert_text: "#52c41a",
    assert_visible: "#52c41a",
    default: "#8c8c8c",
};
function BaseStepNode({ id, data, selected }) {
    const { stepType, fields } = data;
    const schema = DSL_SCHEMA[stepType];
    const icon = STEP_ICONS[stepType] || _jsx(CodeOutlined, {});
    const color = STEP_COLORS[stepType] || STEP_COLORS.default;
    // Get preview text from fields (memoized)
    const previewText = useMemo(() => {
        if (fields.selector)
            return String(fields.selector).slice(0, 25);
        if (fields.url) {
            const url = String(fields.url);
            return url.length > 28 ? url.slice(0, 28) + "..." : url;
        }
        if (fields.value)
            return String(fields.value).slice(0, 20);
        if (fields.duration)
            return `${fields.duration}ms`;
        if (fields.script)
            return "JS...";
        return "";
    }, [fields]);
    // Handle style
    const handleStyle = {
        background: selected ? "#1890ff" : "#8c8c8c",
        width: 8,
        height: 8,
        border: "2px solid #fff",
    };
    return (_jsxs("div", { style: { minWidth: 160, maxWidth: 220, position: "relative" }, children: [_jsx(Handle, { type: "target", position: Position.Top, id: "top", style: handleStyle }), _jsx(Handle, { type: "target", position: Position.Left, id: "left", style: handleStyle }), _jsxs("div", { style: {
                    background: "#fff",
                    borderRadius: 8,
                    borderLeft: `4px solid ${color}`,
                    boxShadow: selected
                        ? `0 0 0 2px ${color}40, 0 4px 12px rgba(0,0,0,0.15)`
                        : "0 2px 6px rgba(0,0,0,0.08)",
                    padding: "10px 12px",
                    transition: "box-shadow 0.2s ease",
                }, children: [_jsxs("div", { style: { display: "flex", alignItems: "center", gap: 8 }, children: [_jsx("span", { style: { color, fontSize: 15, lineHeight: 1 }, children: icon }), _jsx(Text, { strong: true, style: { fontSize: 12, color: "#262626" }, children: schema?.label || stepType })] }), previewText && (_jsx(Text, { type: "secondary", style: {
                            fontSize: 10,
                            display: "block",
                            marginTop: 6,
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                            color: "#8c8c8c",
                        }, children: previewText })), data.description && (_jsx("div", { style: {
                            marginTop: 6,
                            padding: "2px 6px",
                            background: "#e6f7ff",
                            borderRadius: 4,
                            fontSize: 10,
                            color: "#1890ff",
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                        }, children: data.description }))] }), _jsx(Handle, { type: "source", position: Position.Bottom, id: "bottom", style: handleStyle }), _jsx(Handle, { type: "source", position: Position.Right, id: "right", style: handleStyle })] }));
}
export default memo(BaseStepNode);
