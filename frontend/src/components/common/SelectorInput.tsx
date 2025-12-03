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
import { parseToSelector, validateSelector, type ParseResult } from "../../utils/selectorParser";

const { Text } = Typography;

interface SelectorInputProps {
  value?: string;
  onChange: (value: string) => void;
  placeholder?: string;
  size?: "small" | "middle" | "large";
  /** Show the auto-parser input below */
  showAutoParser?: boolean;
  /** Disable the input */
  disabled?: boolean;
}

// Confidence badge colors
const CONFIDENCE_COLORS: Record<ParseResult['confidence'], string> = {
  high: 'green',
  medium: 'orange',
  low: 'red',
};

/**
 * SelectorInput - Input for CSS selectors with optional HTML auto-parser
 */
function SelectorInput({
  value,
  onChange,
  placeholder = "#id、.class、text=文本",
  size = "small",
  showAutoParser = true,
  disabled = false,
}: SelectorInputProps) {
  const [parserInput, setParserInput] = useState("");
  const [parserExpanded, setParserExpanded] = useState(false);
  const [lastResult, setLastResult] = useState<ParseResult | null>(null);

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
      } else if (validation.warning) {
        message.info({
          content: validation.warning,
          duration: 3,
        });
      } else {
        message.success({
          content: result.reason,
          duration: 2,
        });
      }
    } else {
      message.error("无法解析，请检查输入格式");
    }
  }, [parserInput, onChange]);

  // Handle paste in parser input - auto parse on paste
  const handleParserPaste = useCallback((e: React.ClipboardEvent<HTMLTextAreaElement>) => {
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
        } else {
          message.success({
            content: `已解析: ${result.reason}`,
            duration: 2,
          });
        }
      }
    }, 50);
  }, [onChange]);

  return (
    <div>
      {/* Main selector input */}
      <Input
        size={size}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        disabled={disabled}
        suffix={
          showAutoParser && (
            <Tooltip title="智能解析：粘贴 DevTools 复制的选择器或 HTML 元素">
              <Button
                type="text"
                size="small"
                icon={<ThunderboltOutlined style={{ color: parserExpanded ? "#1890ff" : "#999" }} />}
                onClick={() => setParserExpanded(!parserExpanded)}
                style={{ marginRight: -8 }}
              />
            </Tooltip>
          )
        }
      />

      {/* Auto-parser section */}
      {showAutoParser && parserExpanded && (
        <div style={{ marginTop: 6, padding: 8, background: "#fafafa", borderRadius: 4, border: "1px dashed #d9d9d9" }}>
          <Space.Compact style={{ width: "100%" }}>
            <Input.TextArea
              size={size}
              rows={2}
              value={parserInput}
              onChange={(e) => setParserInput(e.target.value)}
              onPaste={handleParserPaste}
              placeholder="粘贴后自动解析，支持：&#10;• Chrome DevTools 右键 → Copy → Copy selector&#10;• Chrome DevTools 右键 → Copy → Copy element"
              style={{ fontSize: 11, fontFamily: "monospace" }}
            />
          </Space.Compact>
          
          <div style={{ marginTop: 6, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <Text type="secondary" style={{ fontSize: 10 }}>
              <InfoCircleOutlined /> 推荐使用 <Text code style={{ fontSize: 10 }}>Copy selector</Text> 最精确
            </Text>
            <Button size="small" type="primary" onClick={handleParse} disabled={!parserInput.trim()}>
              解析
            </Button>
          </div>
          
          {/* Show last parse result */}
          {lastResult && (
            <div style={{ marginTop: 6, fontSize: 10 }}>
              <Tag color={CONFIDENCE_COLORS[lastResult.confidence]} style={{ fontSize: 10 }}>
                {lastResult.confidence === 'high' ? '高' : lastResult.confidence === 'medium' ? '中' : '低'}可靠度
              </Tag>
              <Text type="secondary" style={{ fontSize: 10 }}>{lastResult.reason}</Text>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default memo(SelectorInput);
