/**
 * NestedStepCard - Non-draggable step card for nested containers
 * Supports recursive nesting up to MAX_NESTING_DEPTH
 */
import { memo } from "react";
import { CopyOutlined, DeleteOutlined, PlusOutlined, SettingOutlined } from "@ant-design/icons";
import { Button, Card, Collapse, Form, Input, Select, Space, Tooltip, Typography } from "antd";
import { DSL_SCHEMA, STEP_TYPE_OPTIONS, isContainerType } from "../../constants/dsl";
import type { NestedStepCardProps, SortableFlowStep, StepKey } from "./types";
import { CONTAINER_COLORS, MAX_NESTING_DEPTH } from "./constants";
import { createEmptyStep, generateId, shouldShowField } from "./utils";
import { renderNestedField } from "./StepField";
import { message } from "antd";

const { Text } = Typography;

/**
 * NestedStepCard - Recursively renders nested steps inside container steps
 */
const NestedStepCard = memo(function NestedStepCard({
  step,
  index,
  depth,
  onDelete,
  onDuplicate,
  onTypeChange,
  onFieldChange,
  onChildrenChange,
}: NestedStepCardProps) {
  const definition = DSL_SCHEMA[step.type as StepKey];
  const isContainer = isContainerType(step.type);
  const colors = CONTAINER_COLORS[step.type];

  // Filter out container types when at max depth
  const allowedOptions = depth >= MAX_NESTING_DEPTH 
    ? STEP_TYPE_OPTIONS.filter(opt => !isContainerType(opt.value))
    : STEP_TYPE_OPTIONS;

  // Handlers for nested children (recursive)
  const handleAddChild = (branch?: "else") => {
    if (depth >= MAX_NESTING_DEPTH) {
      message.warning("已达到最大嵌套层数（4层），无法继续添加嵌套步骤");
      return;
    }
    const newStep = createEmptyStep("click");
    const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
    onChildrenChange?.([...targetChildren, newStep], branch);
  };

  const handleChildDelete = (childIndex: number, branch?: "else") => {
    const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
    onChildrenChange?.(targetChildren.filter((_, i) => i !== childIndex), branch);
  };

  const handleChildDuplicate = (childIndex: number, branch?: "else") => {
    const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
    const clone = { ...targetChildren[childIndex], _id: generateId() };
    const newChildren = [...targetChildren];
    newChildren.splice(childIndex + 1, 0, clone);
    onChildrenChange?.(newChildren, branch);
  };

  const handleChildTypeChange = (childIndex: number, type: StepKey, branch?: "else") => {
    const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
    const newChildren = [...targetChildren];
    const newStep = createEmptyStep(type);
    newStep._id = newChildren[childIndex]._id;
    newChildren[childIndex] = newStep;
    onChildrenChange?.(newChildren, branch);
  };

  const handleChildFieldChange = (childIndex: number, field: string, value: unknown, branch?: "else") => {
    const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
    const newChildren = [...targetChildren];
    newChildren[childIndex] = { ...newChildren[childIndex], [field]: value };
    onChildrenChange?.(newChildren, branch);
  };

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
    onChildrenChange?.(newChildren, branch);
  };

  // Render nested children recursively
  const renderNestedChildren = (children: SortableFlowStep[] | undefined, branch?: "else") => (
    <div style={{ 
      marginLeft: 12, 
      paddingLeft: 10, 
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
        <Text type="secondary" style={{ fontSize: 11 }}>暂无步骤</Text>
      )}
      {depth < MAX_NESTING_DEPTH && (
        <Button
          type="dashed"
          size="small"
          icon={<PlusOutlined />}
          onClick={() => handleAddChild(branch)}
          style={{ marginTop: 6, width: "100%", fontSize: 12 }}
        >
          添加{branch === "else" ? "否则" : ""}步骤
        </Button>
      )}
      {depth >= MAX_NESTING_DEPTH && (
        <Text type="warning" style={{ fontSize: 10, display: "block", marginTop: 4 }}>
          已达最大嵌套层数
        </Text>
      )}
    </div>
  );

  // Render fields
  const basicFields = definition.fields.filter(f => !f.advanced && shouldShowField(f, step));
  const advancedFields = definition.fields.filter(f => f.advanced && shouldShowField(f, step));

  return (
    <Card
      size="small"
      style={{
        borderRadius: 8,
        marginBottom: 8,
        borderLeft: isContainer ? `3px solid ${colors?.border || "#1890ff"}` : undefined,
        background: isContainer ? colors?.bg : undefined,
      }}
      title={
        <Space size="small" align="center">
          <Text strong style={{ fontSize: 12 }}>#{index + 1}</Text>
          <Tooltip title={definition?.description} placement="top">
            <Select
              value={step.type}
              bordered={false}
              size="small"
              style={{ width: 140 }}
              onChange={(nextType) => onTypeChange(nextType as StepKey)}
              options={allowedOptions}
            />
          </Tooltip>
        </Space>
      }
      extra={
        <Space size="small">
          <Tooltip title="复制">
            <Button icon={<CopyOutlined />} size="small" onClick={onDuplicate} />
          </Tooltip>
          <Tooltip title="删除">
            <Button icon={<DeleteOutlined />} size="small" danger onClick={onDelete} />
          </Tooltip>
        </Space>
      }
    >
      <Space direction="vertical" style={{ width: "100%" }} size="small">
        {/* Basic fields */}
        {basicFields.map((field) => (
          <Form.Item
            key={field.name}
            label={field.label}
            required={field.required}
            style={{ marginBottom: 8 }}
          >
            {renderNestedField(field, step, onFieldChange)}
          </Form.Item>
        ))}
        
        {/* Description field */}
        <Form.Item label="步骤说明" style={{ marginBottom: 8 }}>
          <Input.TextArea
            size="small"
            rows={1}
            placeholder="可选：描述这个步骤"
            value={(step.description as string) || ""}
            onChange={(e) => onFieldChange("description", e.target.value)}
          />
        </Form.Item>
        
        {/* Advanced fields (collapsed) */}
        {advancedFields.length > 0 && (
          <Collapse
            size="small"
            ghost
            items={[{
              key: "advanced",
              label: <Text type="secondary" style={{ fontSize: 11 }}><SettingOutlined /> 高级选项</Text>,
              children: advancedFields.map((field) => (
                <Form.Item
                  key={field.name}
                  label={field.label}
                  required={field.required}
                  style={{ marginBottom: 8 }}
                >
                  {renderNestedField(field, step, onFieldChange)}
                </Form.Item>
              )),
            }]}
          />
        )}
      </Space>

      {/* Container children - recursive rendering */}
      {isContainer && (
        <div style={{ marginTop: 12 }}>
          <Text strong style={{ fontSize: 11, color: colors?.border }}>
            {definition.containerLabel || "子步骤"}
          </Text>
          {renderNestedChildren(step.children)}

          {/* Else branch for if_else */}
          {definition.hasElse && (
            <div style={{ marginTop: 12 }}>
              <Text strong style={{ fontSize: 11, color: "#fa541c" }}>
                {definition.elseLabel || "否则执行"}
              </Text>
              {renderNestedChildren(step.else_children, "else")}
            </div>
          )}
        </div>
      )}
    </Card>
  );
});

export default NestedStepCard;
