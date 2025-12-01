/**
 * Loop Node - loop/loop_array container with expandable content
 */
import { memo, useState } from "react";
import { Handle, Position } from "@xyflow/react";
import { Card, Typography, Tag, Button } from "antd";
import { ReloadOutlined, DownOutlined, RightOutlined } from "@ant-design/icons";
import type { StepNodeData } from "../types";

const { Text } = Typography;

interface LoopNodeProps {
  id: string;
  data: StepNodeData;
  selected?: boolean;
}

function LoopNode({ id, data, selected }: LoopNodeProps) {
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

  return (
    <div style={{ minWidth: 220, position: "relative" }}>
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
          borderLeft: isArrayLoop ? "4px solid #13c2c2" : "4px solid #722ed1",
          background: isArrayLoop ? "#e6fffb" : "#f9f0ff",
          boxShadow: selected 
            ? `0 4px 12px rgba(${isArrayLoop ? "19, 194, 194" : "114, 46, 209"}, 0.3)` 
            : "0 2px 8px rgba(0,0,0,0.1)",
          border: selected 
            ? `1px solid ${isArrayLoop ? "#13c2c2" : "#722ed1"}` 
            : undefined,
        }}
        styles={{
          body: { padding: "8px 12px" },
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <ReloadOutlined 
            style={{ 
              color: isArrayLoop ? "#13c2c2" : "#722ed1", 
              fontSize: 16 
            }} 
          />
          <Text strong style={{ fontSize: 13 }}>
            {isArrayLoop ? "遍历数组" : "循环执行"}
          </Text>
          
          {childCount > 0 && (
            <Button 
              type="text" 
              size="small"
              icon={expanded ? <DownOutlined /> : <RightOutlined />}
              onClick={() => setExpanded(!expanded)}
              style={{ marginLeft: "auto" }}
            />
          )}
        </div>
        
        <div style={{ marginTop: 8 }}>
          <Tag color={isArrayLoop ? "cyan" : "purple"}>{loopInfo}</Tag>
          {isArrayLoop && Boolean(fields.item_variable) && (
            <Text 
              type="secondary" 
              style={{ fontSize: 11, display: "block", marginTop: 4 }}
            >
              → {String(fields.item_variable)}
            </Text>
          )}
        </div>
        
        {description && (
          <Tag color="blue" style={{ marginTop: 4, fontSize: 10 }}>
            {description}
          </Tag>
        )}
        
        {/* Child count indicator & handle labels */}
        <div style={{ 
          marginTop: 8, 
          fontSize: 11, 
          color: "#8c8c8c",
          borderTop: "1px dashed #d9d9d9",
          paddingTop: 8,
          display: "flex",
          justifyContent: "space-between",
        }}>
          <span style={{ color: themeColor }}>
            ↻ 循环体 {childCount > 0 ? `(${childCount})` : ""}
          </span>
          <span>↓ 后续</span>
        </div>
        
        {/* Expanded children preview */}
        {expanded && childrenData && childrenData.length > 0 && (
          <div style={{ 
            marginTop: 8, 
            padding: 8, 
            background: "rgba(255,255,255,0.5)",
            borderRadius: 4,
            fontSize: 11,
          }}>
            {childrenData.map((child, idx) => (
              <div key={idx} style={{ 
                padding: "2px 0",
                color: "#595959",
              }}>
                {idx + 1}. {child.label}
              </div>
            ))}
          </div>
        )}
      </Card>
      
      {/* Source handles: body (35%) - next (65%) */}
      <Handle
        type="source"
        position={Position.Bottom}
        id="body"
        style={{ 
          background: themeColor, 
          width: 10, 
          height: 10,
          border: "2px solid #fff",
          left: "35%",
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
          left: "65%",
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

export default memo(LoopNode);
