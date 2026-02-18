/**
 * WorkflowToolbar - Top toolbar for workflow editor
 */
import { memo } from "react";
import { Button, Space, Tooltip, Segmented } from "antd";
import { 
  ReloadOutlined, 
  AppstoreOutlined,
  UnorderedListOutlined,
  DeleteOutlined,
  CopyOutlined,
  UndoOutlined,
  RedoOutlined,
} from "@ant-design/icons";
import type { WorkflowNode } from "../types";
import { START_NODE_ID } from "../nodes";

interface WorkflowToolbarProps {
  selectedNode: WorkflowNode | null;
  selectedNodeIds: string[];
  readOnly: boolean;
  canUndo: boolean;
  canRedo: boolean;
  onCopy: () => void;
  onDelete: (nodeId: string) => void;
  onDeleteSelected: () => void;
  onUndo: () => void;
  onRedo: () => void;
  onRelayout: () => void;
  onModeChange?: (mode: "workflow" | "list") => void;
}

/**
 * Toolbar component for workflow editor actions
 */
const WorkflowToolbar = memo(function WorkflowToolbar({
  selectedNode,
  selectedNodeIds,
  readOnly,
  canUndo,
  canRedo,
  onCopy,
  onDelete,
  onDeleteSelected,
  onUndo,
  onRedo,
  onRelayout,
  onModeChange,
}: WorkflowToolbarProps) {
  const isMultiSelect = selectedNodeIds.length > 1;
  const canDeleteSelected = selectedNode && selectedNode.id !== START_NODE_ID;

  return (
    <Space
      style={{
        background: "#fff",
        padding: "6px 12px",
        borderRadius: 8,
        boxShadow: "0 2px 8px rgba(0,0,0,0.1)",
      }}
    >
      {/* Multi-selection controls */}
      {isMultiSelect && !readOnly && (
        <>
          <span style={{ fontSize: 12, color: "#666" }}>
            已选 {selectedNodeIds.length} 个
          </span>
          <Tooltip title="删除选中 (Delete)">
            <Button
              icon={<DeleteOutlined />}
              size="small"
              danger
              onClick={onDeleteSelected}
            />
          </Tooltip>
        </>
      )}
      
      {/* Single selection controls */}
      {!isMultiSelect && canDeleteSelected && !readOnly && (
        <>
          <Tooltip title="复制 (Ctrl+C)">
            <Button icon={<CopyOutlined />} size="small" onClick={onCopy} />
          </Tooltip>
          <Tooltip title="删除 (Delete)">
            <Button
              icon={<DeleteOutlined />}
              size="small"
              danger
              onClick={() => onDelete(selectedNode!.id)}
            />
          </Tooltip>
        </>
      )}
      
      {/* Undo/Redo */}
      {!readOnly && (
        <>
          <Tooltip title="撤销 (Ctrl+Z)">
            <Button 
              icon={<UndoOutlined />} 
              size="small" 
              onClick={onUndo}
              disabled={!canUndo}
            />
          </Tooltip>
          <Tooltip title="重做 (Ctrl+Y)">
            <Button 
              icon={<RedoOutlined />} 
              size="small" 
              onClick={onRedo}
              disabled={!canRedo}
            />
          </Tooltip>
        </>
      )}
      
      {/* Layout button */}
      <Tooltip title="自动布局">
        <Button icon={<ReloadOutlined />} size="small" onClick={onRelayout} />
      </Tooltip>
      
      {/* Mode switch */}
      {onModeChange && (
        <Segmented
          size="small"
          options={[
            { value: "workflow", icon: <AppstoreOutlined /> },
            { value: "list", icon: <UnorderedListOutlined /> },
          ]}
          value="workflow"
          onChange={(v) => onModeChange(v as "workflow" | "list")}
        />
      )}
    </Space>
  );
});

export default WorkflowToolbar;
