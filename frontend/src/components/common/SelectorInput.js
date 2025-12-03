import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
/**
 * Selector Input Component with Auto-Parser
 * Provides a selector input field with an optional HTML/selector parser below it
 *
 * Supports:
 * 1. Direct CSS selector input (from DevTools "Copy selector")
 * 2. HTML element parsing (from DevTools "Copy element" or "Copy outerHTML")
 */
import { useState, useCallback, memo } from "react";
import { Input, Button, Tooltip, message, Space, Typography, Tag } from "antd";
import { ThunderboltOutlined, InfoCircleOutlined } from "@ant-design/icons";
import { parseToSelector, validateSelector } from "../../utils/selectorParser";
const { Text } = Typography;
// Confidence badge colors
const CONFIDENCE_COLORS = {
    high: 'green',
    medium: 'orange',
    low: 'red',
};
/**
 * SelectorInput - Input for CSS selectors with optional HTML auto-parser
 */
function SelectorInput({ value, onChange, placeholder = "#id、.class、text=文本", size = "small", showAutoParser = true, disabled = false, }) {
    const [parserInput, setParserInput] = useState("");
    const [parserExpanded, setParserExpanded] = useState(false);
    const [lastResult, setLastResult] = useState(null);
    // Handle auto-parse
    const handleParse = useCallback(() => {
        if (!parserInput.trim()) {
            message.warning("请先粘贴选择器或 HTML 元素");
            return;
        }
        const result = parseToSelector(parserInput);
        if (result) {
            onChange(result.selector);
            setLastResult(result);
            setParserInput("");
            // Validate and show appropriate message
            const validation = validateSelector(result.selector);
            if (result.confidence === 'low') {
                message.warning({
                    content: result.reason,
                    duration: 4,
                });
            }
            else if (validation.warning) {
                message.info({
                    content: validation.warning,
                    duration: 3,
                });
            }
            else {
                message.success({
                    content: result.reason,
                    duration: 2,
                });
            }
        }
        else {
            message.error("无法解析，请检查输入格式");
        }
    }, [parserInput, onChange]);
    // Handle paste in parser input - auto parse on paste
    const handleParserPaste = useCallback((e) => {
        const pastedText = e.clipboardData.getData("text");
        // Use timeout to let the paste complete first
        setTimeout(() => {
            const result = parseToSelector(pastedText);
            if (result) {
                onChange(result.selector);
                setLastResult(result);
                setParserInput("");
                // Show result info
                if (result.confidence === 'low') {
                    message.warning({
                        content: result.reason,
                        duration: 4,
                    });
                }
                else {
                    message.success({
                        content: `已解析: ${result.reason}`,
                        duration: 2,
                    });
                }
            }
        }, 50);
    }, [onChange]);
    return (_jsxs("div", { children: [_jsx(Input, { size: size, value: value, onChange: (e) => onChange(e.target.value), placeholder: placeholder, disabled: disabled, suffix: showAutoParser && (_jsx(Tooltip, { title: "\u667A\u80FD\u89E3\u6790\uFF1A\u7C98\u8D34 DevTools \u590D\u5236\u7684\u9009\u62E9\u5668\u6216 HTML \u5143\u7D20", children: _jsx(Button, { type: "text", size: "small", icon: _jsx(ThunderboltOutlined, { style: { color: parserExpanded ? "#1890ff" : "#999" } }), onClick: () => setParserExpanded(!parserExpanded), style: { marginRight: -8 } }) })) }), showAutoParser && parserExpanded && (_jsxs("div", { style: { marginTop: 6, padding: 8, background: "#fafafa", borderRadius: 4, border: "1px dashed #d9d9d9" }, children: [_jsx(Space.Compact, { style: { width: "100%" }, children: _jsx(Input.TextArea, { size: size, rows: 2, value: parserInput, onChange: (e) => setParserInput(e.target.value), onPaste: handleParserPaste, placeholder: "\u7C98\u8D34\u540E\u81EA\u52A8\u89E3\u6790\uFF0C\u652F\u6301\uFF1A\n\u2022 Chrome DevTools \u53F3\u952E \u2192 Copy \u2192 Copy selector\n\u2022 Chrome DevTools \u53F3\u952E \u2192 Copy \u2192 Copy element", style: { fontSize: 11, fontFamily: "monospace" } }) }), _jsxs("div", { style: { marginTop: 6, display: "flex", justifyContent: "space-between", alignItems: "center" }, children: [_jsxs(Text, { type: "secondary", style: { fontSize: 10 }, children: [_jsx(InfoCircleOutlined, {}), " \u63A8\u8350\u4F7F\u7528 ", _jsx(Text, { code: true, style: { fontSize: 10 }, children: "Copy selector" }), " \u6700\u7CBE\u786E"] }), _jsx(Button, { size: "small", type: "primary", onClick: handleParse, disabled: !parserInput.trim(), children: "\u89E3\u6790" })] }), lastResult && (_jsxs("div", { style: { marginTop: 6, fontSize: 10 }, children: [_jsxs(Tag, { color: CONFIDENCE_COLORS[lastResult.confidence], style: { fontSize: 10 }, children: [lastResult.confidence === 'high' ? '高' : lastResult.confidence === 'medium' ? '中' : '低', "\u53EF\u9760\u5EA6"] }), _jsx(Text, { type: "secondary", style: { fontSize: 10 }, children: lastResult.reason })] }))] }))] }));
}
export default memo(SelectorInput);
