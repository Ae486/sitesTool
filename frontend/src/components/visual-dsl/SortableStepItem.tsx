/**
 * SortableStepItem - Top-level draggable step card
 * Uses dnd-kit for drag-and-drop functionality
 */
import { memo } from "react";
import { CopyOutlined, DeleteOutlined, HolderOutlined, PlusOutlined, SettingOutlined } from "@ant-design/icons";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Button, Card, Collapse, Form, Input, Select, Space, Tooltip, Typography } from "antd";
import { DSL_SCHEMA, STEP_TYPE_OPTIONS, isContainerType } from "../../constants/dsl";
import type { SortableFlowStep, SortableStepItemProps, StepKey } from "./types";
import { CONTAINER_COLORS } from "./constants";
import { createEmptyStep, generateId, shouldShowField } from "./utils";
import StepField from "./StepField";
import NestedStepCard from "./NestedStepCard";

const { Text } = Typography;

/**
 * SortableStepItem - Top-level step card with drag-and-drop support
 */
const SortableStepItem = memo(function SortableStepItem({
  step,
  index,
  depth = 0,
  onDelete,
  onDuplicate,
  onTypeChange,
  onFieldChange,
  onChildrenChange,
}: SortableStepItemProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: step._id,
  });

  const isContainer = isContainerType(step.type);
  const definition = DSL_SCHEMA[step.type as StepKey];
  const colors = CONTAINER_COLORS[step.type];

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.4 : 1,
    marginBottom: 16,
    position: "relative" as const,
    zIndex: isDragging ? 999 : 1,
  };

  // Handlers for nested children
  const handleAddChild = (branch?: "else") => {
    const newStep = createEmptyStep("click");
    const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
    onChildrenChange?.(index, [...targetChildren, newStep], branch);
  };

  const handleChildDelete = (childIndex: number, branch?: "else") => {
    const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
    onChildrenChange?.(index, targetChildren.filter((_, i) => i !== childIndex), branch);
  };

  const handleChildDuplicate = (childIndex: number, branch?: "else") => {
    const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
    const clone = { ...targetChildren[childIndex], _id: generateId() };
    const newChildren = [...targetChildren];
    newChildren.splice(childIndex + 1, 0, clone);
    onChildrenChange?.(index, newChildren, branch);
  };

  const handleChildTypeChange = (childIndex: number, type: StepKey, branch?: "else") => {
    const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
    const newChildren = [...targetChildren];
    const newStep = createEmptyStep(type);
    newStep._id = newChildren[childIndex]._id;
    newChildren[childIndex] = newStep;
    onChildrenChange?.(index, newChildren, branch);
  };

  const handleChildFieldChange = (childIndex: number, field: string, value: unknown, branch?: "else") => {
    const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
    const newChildren = [...targetChildren];
    newChildren[childIndex] = { ...newChildren[childIndex], [field]: value };
    onChildrenChange?.(index, newChildren, branch);
  };

  // Handle nested grandchildren changes (for recursive nesting)
  const handleNestedChildrenChange = (
    childIndex: number, 
    grandChildren: SortableFlowStep[], 
    branch?: "else", 
    grandBranch?: "else"
  ) => {
    const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
    const newChildren = [...targetChildren];
    if (grandBranch === "else") {
      newChildren[childIndex] = { ...newChildren[childIndex], else_children: grandChildren };
    } else {
      newChildren[childIndex] = { ...newChildren[childIndex], children: grandChildren };
    }
    onChildrenChange?.(index, newChildren, branch);
  };

  // Render nested children with recursive support
  const renderChildren = (children: SortableFlowStep[] | undefined, branch?: "else") => (
    <div style={{ 
      marginLeft: 16, 
      paddingLeft: 12, 
      borderLeft: `2px dashed ${colors?.border || "#d9d9d9"}`,
    }}>
      {children && children.length > 0 ? (
        children.map((child, childIdx) => (
          <NestedStepCard
            key={child._id}
            step={child}
            index={childIdx}
            depth={depth + 1}
            onDelete={() => handleChildDelete(childIdx, branch)}
            onDuplicate={() => handleChildDuplicate(childIdx, branch)}
            onTypeChange={(type) => handleChildTypeChange(childIdx, type, branch)}
            onFieldChange={(field, val) => handleChildFieldChange(childIdx, field, val, branch)}
            onChildrenChange={(grandChildren, grandBranch) => 
              handleNestedChildrenChange(childIdx, grandChildren, branch, grandBranch)
            }
          />
        ))
      ) : (
        <Text type="secondary" style={{ fontSize: 12 }}>暂无步骤</Text>
      )}
      <Button
        type="dashed"
        size="small"
        icon={<PlusOutlined />}
        onClick={() => handleAddChild(branch)}
        style={{ marginTop: 8, width: "100%" }}
      >
        添加{branch === "else" ? "否则" : ""}步骤
      </Button>
    </div>
  );

  // Get fields
  const basicFields = definition.fields.filter(f => !f.advanced && shouldShowField(f, step));
  const advancedFields = definition.fields.filter(f => f.advanced && shouldShowField(f, step));

  return (
    <div ref={setNodeRef} style={style}>
      <Card
        size="small"
        style={{
          borderRadius: 12,
          boxShadow: isDragging
            ? "0 8px 24px rgba(0,0,0,0.15)"
            : "0 1px 4px rgba(15, 23, 42, 0.08)",
          border: isDragging ? "1px solid var(--primary-color)" : undefined,
          borderLeft: isContainer ? `4px solid ${colors?.border}` : undefined,
          background: isContainer ? colors?.bg : undefined,
        }}
        title={
          <Space size="small" align="center">
            <Tooltip title="拖拽排序">
              <div
                {...attributes}
                {...listeners}
                style={{ cursor: "grab", display: "flex", alignItems: "center", touchAction: "none" }}
              >
                <HolderOutlined style={{ color: "var(--text-secondary)", fontSize: 16 }} />
              </div>
            </Tooltip>
            <Text strong>步骤 {index + 1}</Text>
            <Tooltip title={definition?.description} placement="top">
              <Select
                value={step.type as string}
                bordered={false}
                style={{ width: 160 }}
                onChange={(nextType) => onTypeChange(index, nextType as StepKey)}
                options={STEP_TYPE_OPTIONS}
                onMouseDown={(e) => e.stopPropagation()}
              />
            </Tooltip>
          </Space>
        }
        extra={
          <Space size="small">
            <Tooltip title="复制步骤">
              <Button
                icon={<CopyOutlined />}
                size="small"
                onClick={() => onDuplicate(index)}
              />
            </Tooltip>
            <Tooltip title="删除步骤">
              <Button
                icon={<DeleteOutlined />}
                size="small"
                danger
                onClick={() => onDelete(index)}
              />
            </Tooltip>
          </Space>
        }
      >
        {/* Step fields */}
        <Space direction="vertical" style={{ width: "100%" }} size="small">
          {/* Basic fields */}
          {basicFields.map((field) => (
            <StepField
              key={field.name}
              step={step}
              stepIndex={index}
              fieldMeta={field}
              onFieldChange={onFieldChange}
            />
          ))}
          
          {/* Description field */}
          <Form.Item 
            label="步骤说明" 
            style={{ marginBottom: 12 }}
          >
            <Input.TextArea
              rows={1}
              placeholder="可选：描述这个步骤"
              value={(step.description as string) || ""}
              onChange={(e) => onFieldChange(index, "description", e.target.value)}
            />
          </Form.Item>
          
          {/* Advanced fields (collapsed) */}
          {advancedFields.length > 0 && (
            <Collapse
              size="small"
              ghost
              items={[{
                key: "advanced",
                label: <Text type="secondary" style={{ fontSize: 12 }}><SettingOutlined /> 高级选项 ({advancedFields.length})</Text>,
                children: (
                  <Space direction="vertical" style={{ width: "100%" }} size="small">
                    {advancedFields.map((field) => (
                      <StepField
                        key={field.name}
                        step={step}
                        stepIndex={index}
                        fieldMeta={field}
                        onFieldChange={onFieldChange}
                      />
                    ))}
                  </Space>
                ),
              }]}
            />
          )}
        </Space>

        {/* Container children */}
        {isContainer && (
          <div style={{ marginTop: 16 }}>
            <Text strong style={{ fontSize: 12, color: colors?.border }}>
              {definition.containerLabel || "子步骤"}
            </Text>
            {renderChildren(step.children)}

            {/* Else branch for if_else */}
            {definition.hasElse && (
              <div style={{ marginTop: 16 }}>
                <Text strong style={{ fontSize: 12, color: "#fa541c" }}>
                  {definition.elseLabel || "否则执行"}
                </Text>
                {renderChildren(step.else_children, "else")}
              </div>
            )}
          </div>
        )}
      </Card>
    </div>
  );
});

export default SortableStepItem;
