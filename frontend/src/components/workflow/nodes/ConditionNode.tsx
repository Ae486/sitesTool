/**
 * Condition Node - if_else with dual outputs (true/false branches)
 */
import { memo } from "react";
import { Handle, Position } from "@xyflow/react";
import { Card, Typography, Tag } from "antd";
import { BranchesOutlined } from "@ant-design/icons";
import type { StepNodeData } from "../types";

const { Text } = Typography;

// Condition type labels
const CONDITION_LABELS: Record<string, string> = {
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

interface ConditionNodeProps {
  id: string;
  data: StepNodeData;
  selected?: boolean;
}

function ConditionNode({ id, data, selected }: ConditionNodeProps) {
  const { fields, description } = data;
  const conditionType = fields.condition_type as string || "variable_truthy";
  const conditionLabel = CONDITION_LABELS[conditionType] || conditionType;
  
  // Get condition target
  const target = fields.condition_variable || fields.condition_selector || "";

  const handleStyle = {
    background: selected ? "#fa8c16" : "#8c8c8c",
    width: 8,
    height: 8,
    border: "2px solid #fff",
  };

  return (
    <div style={{ minWidth: 200, position: "relative" }}>
      {/* Target handles (top and left) */}
      <Handle
        type="target"
        position={Position.Top}
        id="top"
        style={handleStyle}
      />
      <Handle
        type="target"
        position={Position.Left}
        id="left"
        style={handleStyle}
      />
      
      <Card
        size="small"
        style={{
          borderRadius: 8,
          borderLeft: "4px solid #fa8c16",
          background: "#fff7e6",
          boxShadow: selected 
            ? "0 4px 12px rgba(250, 140, 22, 0.3)" 
            : "0 2px 8px rgba(0,0,0,0.1)",
          border: selected ? "1px solid #fa8c16" : undefined,
        }}
        styles={{
          body: { padding: "8px 12px" },
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <BranchesOutlined style={{ color: "#fa8c16", fontSize: 16 }} />
          <Text strong style={{ fontSize: 13 }}>条件分支</Text>
        </div>
        
        <div style={{ marginTop: 8 }}>
          <Tag color="orange">{conditionLabel}</Tag>
          {target && (
            <Text 
              type="secondary" 
              style={{ fontSize: 11, display: "block", marginTop: 4 }}
            >
              {String(target).slice(0, 25)}
            </Text>
          )}
        </div>
        
        {description && (
          <Tag color="blue" style={{ marginTop: 4, fontSize: 10 }}>
            {description}
          </Tag>
        )}
        
        {/* Branch labels */}
        <div style={{ 
          display: "flex", 
          justifyContent: "space-between", 
          marginTop: 12,
          fontSize: 10,
          padding: "0 8px",
        }}>
          <span style={{ color: "#52c41a" }}>✓ 成立</span>
          <span style={{ color: "#8c8c8c" }}>↓ 后续</span>
          <span style={{ color: "#ff4d4f" }}>✗ 不成立</span>
        </div>
      </Card>
      
      {/* Source handles: then (25%) - next (50%) - else (75%) */}
      <Handle
        type="source"
        position={Position.Bottom}
        id="then"
        style={{ 
          background: "#52c41a", 
          width: 10, 
          height: 10,
          border: "2px solid #fff",
          left: "25%",
        }}
      />
      <Handle
        type="source"
        position={Position.Bottom}
        id="next"
        style={{ 
          background: "#8c8c8c", 
          width: 10, 
          height: 10,
          border: "2px solid #fff",
          left: "50%",
        }}
      />
      <Handle
        type="source"
        position={Position.Bottom}
        id="else"
        style={{ 
          background: "#ff4d4f", 
          width: 10, 
          height: 10,
          border: "2px solid #fff",
          left: "75%",
        }}
      />
      <Handle
        type="source"
        position={Position.Right}
        id="right"
        style={handleStyle}
      />
    </div>
  );
}

export default memo(ConditionNode);
