import { CopyOutlined, DeleteOutlined, HolderOutlined, PlusOutlined, SettingOutlined } from "@ant-design/icons";
import {
  DndContext,
  DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import {
  Button,
  Card,
  Collapse,
  Empty,
  Form,
  Input,
  InputNumber,
  message,
  Select,
  Space,
  Switch,
  Tooltip,
  Typography,
} from "antd";
import { useDebounceFn } from "ahooks";
import { AnimatePresence } from "framer-motion";
import { forwardRef, memo, useCallback, useEffect, useImperativeHandle, useMemo, useState } from "react";
import { DSL_SCHEMA, STEP_TYPE_OPTIONS, isContainerType, type StepFieldDefinition } from "../constants/dsl";
import type { FlowDSL, FlowStep } from "../types/flow";
import { DslValidationResult, validateDslStructure } from "../utils/dsl";
import SelectorInput from "./common/SelectorInput";

const { Text } = Typography;

type StepKey = keyof typeof DSL_SCHEMA;

// Internal step type with ID for drag-and-drop
// eslint-disable-next-line @typescript-eslint/no-explicit-any
interface SortableFlowStep {
  _id: string;
  type: string;
  children?: SortableFlowStep[];
  else_children?: SortableFlowStep[];
  [key: string]: any; // Allow any field from DSL schema
}

export interface VisualDslEditorRef {
  flush: () => void;
}

interface VisualDslEditorProps {
  value?: FlowDSL | string;
  onChange?: (dsl: FlowDSL) => void;
  siteUrl?: string;
  onValidationChange?: (result: DslValidationResult) => void;
}

const DEFAULT_DSL: FlowDSL = { version: 1, steps: [] };
const DEFAULT_STEP_TYPE = (STEP_TYPE_OPTIONS[0]?.value ?? "navigate") as StepKey;

const hasValue = (value: unknown) => {
  if (value === null || value === undefined) return false;
  if (typeof value === "string") return value.trim().length > 0;
  return true;
};

const ensureDslShape = (candidate?: FlowDSL | string): FlowDSL => {
  if (!candidate) return { ...DEFAULT_DSL };
  if (typeof candidate === "string") {
    try {
      const parsed = JSON.parse(candidate);
      return ensureDslShape(parsed);
    } catch {
      return { ...DEFAULT_DSL };
    }
  }
  return {
    ...candidate,
    version: typeof candidate.version === "number" ? candidate.version : 1,
    steps: Array.isArray(candidate.steps) ? candidate.steps : [],
  };
};

const generateId = () => {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return Math.random().toString(36).substring(2, 15);
};

// Recursively add IDs to a step and its children
const addIdsToStep = (step: FlowStep | SortableFlowStep): SortableFlowStep => {
  const { _id, children, else_children, ...rest } = step as SortableFlowStep;
  const result: SortableFlowStep = {
    ...rest,
    type: step.type,
    _id: _id || generateId(),
  };
  if (children && children.length > 0) {
    result.children = children.map(addIdsToStep);
  } else if ((step as FlowStep).children) {
    result.children = (step as FlowStep).children!.map(addIdsToStep);
  }
  if (else_children && else_children.length > 0) {
    result.else_children = else_children.map(addIdsToStep);
  } else if ((step as FlowStep).else_children) {
    result.else_children = (step as FlowStep).else_children!.map(addIdsToStep);
  }
  return result;
};

// Recursively strip IDs from a step and its children
const stripIdsFromStep = (step: SortableFlowStep): FlowStep => {
  const { _id, children, else_children, ...rest } = step;
  const result: FlowStep = { ...rest } as FlowStep;
  if (children && children.length > 0) {
    result.children = children.map(stripIdsFromStep);
  }
  if (else_children && else_children.length > 0) {
    result.else_children = else_children.map(stripIdsFromStep);
  }
  return result;
};

// Helper to ensure every step has a unique ID for sorting
const ensureDslWithIds = (dsl: FlowDSL): { version: number; steps: SortableFlowStep[] } => {
  return {
    ...dsl,
    steps: dsl.steps.map(addIdsToStep),
  };
};

const createEmptyStep = (type: StepKey, siteUrl?: string, isFirst = false): SortableFlowStep => {
  const definition = DSL_SCHEMA[type];
  const base = { type, _id: generateId() } as SortableFlowStep;

  definition.fields.forEach((field) => {
    if (field.type === "boolean") {
      (base as any)[field.name] = false;
    } else if (isFirst && type === "navigate" && field.name === "url" && siteUrl) {
      (base as any)[field.name] = siteUrl;
    } else {
      (base as any)[field.name] = undefined;
    }
  });

  return base;
};

// Check if field should be visible based on showWhen condition
const shouldShowField = (field: StepFieldDefinition, step: SortableFlowStep): boolean => {
  if (!field.showWhen || field.showWhen.length === 0) return true;
  const conditionType = (step as any).condition_type;
  return field.showWhen.includes(conditionType);
};

// Memoized Field Renderer
const StepField = memo(
  ({
    step,
    stepIndex,
    fieldMeta,
    onFieldChange,
  }: {
    step: SortableFlowStep;
    stepIndex: number;
    fieldMeta: StepFieldDefinition;
    onFieldChange: (index: number, field: string, value: unknown) => void;
  }) => {
    const value = (step as any)[fieldMeta.name];
    const showError = fieldMeta.required && !hasValue(value);
    const commonProps = {
      label: fieldMeta.label,
      required: fieldMeta.required,
      style: { marginBottom: 12 },
      validateStatus: showError ? ("error" as const) : undefined,
      help: showError ? "必填" : undefined,
    };

    // Check showWhen condition for dynamic forms
    if (!shouldShowField(fieldMeta, step)) {
      return null;
    }

    if (fieldMeta.type === "boolean") {
      return (
        <Form.Item {...commonProps}>
          <Switch
            checked={Boolean(value)}
            onChange={(checked) => onFieldChange(stepIndex, fieldMeta.name, checked)}
          />
        </Form.Item>
      );
    }

    if (fieldMeta.type === "number") {
      return (
        <Form.Item {...commonProps}>
          <InputNumber
            style={{ width: "100%" }}
            placeholder={fieldMeta.placeholder}
            value={typeof value === "number" ? value : undefined}
            onChange={(val) => onFieldChange(stepIndex, fieldMeta.name, val ?? undefined)}
          />
        </Form.Item>
      );
    }

    if (fieldMeta.type === "select" && fieldMeta.options) {
      return (
        <Form.Item {...commonProps}>
          <Select
            style={{ width: "100%" }}
            placeholder={fieldMeta.placeholder}
            value={value || undefined}
            onChange={(val) => onFieldChange(stepIndex, fieldMeta.name, val)}
            options={fieldMeta.options.map((opt: { value: string; label: string }) => ({
              value: opt.value,
              label: opt.label,
            }))}
          />
        </Form.Item>
      );
    }

    // Text input with optional auto-parser for selector fields
    if (fieldMeta.hasAutoParser) {
      return (
        <Form.Item {...commonProps}>
          <SelectorInput
            value={typeof value === "string" ? value : ""}
            onChange={(val) => onFieldChange(stepIndex, fieldMeta.name, val)}
            placeholder={fieldMeta.placeholder}
            size="middle"
          />
        </Form.Item>
      );
    }

    return (
      <Form.Item {...commonProps}>
        <Input
          placeholder={fieldMeta.placeholder}
          value={typeof value === "string" ? value : ""}
          onChange={(e) => onFieldChange(stepIndex, fieldMeta.name, e.target.value)}
        />
      </Form.Item>
    );
  },
);

// Container color schemes for visual distinction
const CONTAINER_COLORS: Record<string, { border: string; bg: string; headerBg: string }> = {
  loop: { border: "#722ed1", bg: "#f9f0ff", headerBg: "#efdbff" },
  loop_array: { border: "#13c2c2", bg: "#e6fffb", headerBg: "#b5f5ec" },
  if_else: { border: "#fa8c16", bg: "#fff7e6", headerBg: "#ffd591" },
};

// Maximum nesting depth constant
const MAX_NESTING_DEPTH = 4;

// Nested step card (non-draggable, used inside containers) - supports recursive nesting
const NestedStepCard = memo(
  ({
    step,
    index,
    depth,
    onDelete,
    onDuplicate,
    onTypeChange,
    onFieldChange,
    onChildrenChange,
  }: {
    step: SortableFlowStep;
    index: number;
    depth: number;
    onDelete: () => void;
    onDuplicate: () => void;
    onTypeChange: (type: StepKey) => void;
    onFieldChange: (field: string, value: unknown) => void;
    onChildrenChange?: (children: SortableFlowStep[], branch?: "else") => void;
  }) => {
    const definition = DSL_SCHEMA[step.type as StepKey];
    const isContainer = isContainerType(step.type);
    const colors = CONTAINER_COLORS[step.type];

    // Filter out container types when at max depth
    const allowedOptions = depth >= MAX_NESTING_DEPTH 
      ? STEP_TYPE_OPTIONS.filter(opt => !isContainerType(opt.value))
      : STEP_TYPE_OPTIONS;

    // Handlers for nested children (recursive)
    const handleAddChild = (branch?: "else") => {
      // Block adding at depth 5 (would be depth 4 children)
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

    const handleNestedChildrenChange = (childIndex: number, grandChildren: SortableFlowStep[], branch?: "else", grandBranch?: "else") => {
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
        {/* Render field helper */}
        {(() => {
          const basicFields = definition.fields.filter(f => !f.advanced && shouldShowField(f, step));
          const advancedFields = definition.fields.filter(f => f.advanced && shouldShowField(f, step));
          
          const renderField = (field: StepFieldDefinition) => (
            <Form.Item
              key={field.name}
              label={field.label}
              required={field.required}
              style={{ marginBottom: 8 }}
            >
              {field.type === "boolean" ? (
                <Switch
                  size="small"
                  checked={Boolean(step[field.name])}
                  onChange={(checked) => onFieldChange(field.name, checked)}
                />
              ) : field.type === "number" ? (
                <InputNumber
                  size="small"
                  style={{ width: "100%" }}
                  placeholder={field.placeholder}
                  value={step[field.name]}
                  onChange={(val) => onFieldChange(field.name, val)}
                />
              ) : field.type === "select" && field.options ? (
                <Select
                  size="small"
                  style={{ width: "100%" }}
                  placeholder={field.placeholder}
                  value={step[field.name] || undefined}
                  onChange={(val) => onFieldChange(field.name, val)}
                  options={field.options.map((opt: { value: string; label: string }) => ({
                    value: opt.value,
                    label: opt.label,
                  }))}
                />
              ) : field.hasAutoParser ? (
                <SelectorInput
                  value={step[field.name] || ""}
                  onChange={(val) => onFieldChange(field.name, val)}
                  placeholder={field.placeholder}
                  size="small"
                />
              ) : (
                <Input
                  size="small"
                  placeholder={field.placeholder}
                  value={step[field.name] || ""}
                  onChange={(e) => onFieldChange(field.name, e.target.value)}
                />
              )}
            </Form.Item>
          );
          
          return (
            <Space direction="vertical" style={{ width: "100%" }} size="small">
              {/* Basic fields */}
              {basicFields.map(renderField)}
              
              {/* Description field */}
              <Form.Item label="步骤说明" style={{ marginBottom: 8 }}>
                <Input.TextArea
                  size="small"
                  rows={1}
                  placeholder="可选：描述这个步骤"
                  value={(step as any).description || ""}
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
                    children: advancedFields.map(renderField),
                  }]}
                />
              )}
            </Space>
          );
        })()}

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
  }
);

// Sortable Step Item Component
const SortableStepItem = memo(
  ({
    step,
    index,
    depth = 0,
    onDelete,
    onDuplicate,
    onTypeChange,
    onFieldChange,
    onChildrenChange,
  }: {
    step: SortableFlowStep;
    index: number;
    depth?: number;
    onDelete: (index: number) => void;
    onDuplicate: (index: number) => void;
    onTypeChange: (index: number, type: StepKey) => void;
    onFieldChange: (index: number, field: string, value: unknown) => void;
    onChildrenChange?: (index: number, children: SortableFlowStep[], branch?: "else") => void;
  }) => {
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
    const handleNestedChildrenChange = (childIndex: number, grandChildren: SortableFlowStep[], branch?: "else", grandBranch?: "else") => {
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
          {(() => {
            const basicFields = definition.fields.filter(f => !f.advanced && shouldShowField(f, step));
            const advancedFields = definition.fields.filter(f => f.advanced && shouldShowField(f, step));
            
            return (
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
                    value={(step as any).description || ""}
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
            );
          })()}

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
  },
);



const VisualDslEditor = forwardRef<VisualDslEditorRef, VisualDslEditorProps>(
  ({ value, onChange, siteUrl, onValidationChange }, ref) => {
    // Initialize state with IDs
    const [dsl, setDsl] = useState<{ version: number; steps: SortableFlowStep[] }>(() =>
      ensureDslWithIds(ensureDslShape(value)),
    );
    const [addType, setAddType] = useState<StepKey>(DEFAULT_STEP_TYPE);
    const [validation, setValidation] = useState<DslValidationResult>(() =>
      validateDslStructure(ensureDslShape(value)),
    );

  // Dnd Sensors
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 5, // Prevent accidental drags
      },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  // 防抖的onChange回调 (300ms) - 减少Form重渲染
  const { run: notifyParentChange, flush: flushOnChange } = useDebounceFn(
    (cleanDsl: FlowDSL) => {
      onChange?.(cleanDsl);
    },
    { wait: 300 },
  );

  // 防抖的validation回调 (500ms) - 校验成本高，延迟更长
  const { run: notifyValidationChange, flush: flushValidation } = useDebounceFn(
    (result: DslValidationResult) => {
      onValidationChange?.(result);
    },
    { wait: 500 },
  );

  // 立即更新本地validation状态（用于UI显示），延迟通知父组件
  const emitValidation = useCallback(
    (result: DslValidationResult) => {
      setValidation(result);
      notifyValidationChange(result);
    },
    [notifyValidationChange],
  );

  // Sync with external value changes
  useEffect(() => {
    const normalized = ensureDslShape(value);

    setDsl((prev) => {
      // Create a clean version of current state without IDs for comparison
      const currentClean = {
        ...prev,
        steps: prev.steps.map(stripIdsFromStep),
      };

      // Deep comparison (simple JSON stringify is sufficient for this DSL)
      if (JSON.stringify(normalized) === JSON.stringify(currentClean)) {
        return prev;
      }

      // If different, use addIdsToStep for proper recursive ID handling
      const newSteps = normalized.steps.map((step, index) => {
        const prevStep = prev.steps[index];
        // If a step exists at this index and has the same type, try to preserve its ID
        if (prevStep && prevStep.type === step.type) {
          const withId = addIdsToStep(step);
          withId._id = prevStep._id;
          return withId;
        }
        return addIdsToStep(step);
      });

      return {
        ...normalized,
        steps: newSteps,
      };
    });

    emitValidation(validateDslStructure(normalized));
  }, [value, emitValidation]);

  // Auto-fill site URL for first step
  useEffect(() => {
    if (!siteUrl) return;
    setDsl((prev) => {
      if (prev.steps.length === 0) {
        const nextStep = createEmptyStep("navigate", siteUrl, true);
        const nextDsl = { ...prev, steps: [nextStep] };
        emitValidation(validateDslStructure(nextDsl)); // Validate clean DSL
        onChange?.(nextDsl);
        return { ...prev, steps: [nextStep] }; // Return state with IDs
      }

      const firstStep = prev.steps[0];
      if (firstStep.type === "navigate" && !hasValue((firstStep as any).url)) {
        const nextSteps = [...prev.steps];
        nextSteps[0] = { ...firstStep, url: siteUrl };

        // Create clean DSL for validation/onChange (recursive)
        const cleanDsl = {
          ...prev,
          steps: nextSteps.map(stripIdsFromStep),
        };

        emitValidation(validateDslStructure(cleanDsl));
        onChange?.(cleanDsl);
        return { ...prev, steps: nextSteps };
      }

      return prev;
    });
  }, [siteUrl, emitValidation, onChange]);

  const applyDslUpdate = useCallback(
    (updater: (prev: { version: number; steps: SortableFlowStep[] }) => { version: number; steps: SortableFlowStep[] }) => {
      setDsl((prev) => {
        const next = updater(prev);
        if (next === prev) return prev;

        // Strip IDs before sending to parent (recursively)
        const cleanDsl: FlowDSL = {
          ...next,
          steps: next.steps.map(stripIdsFromStep),
        };

        // 立即更新本地状态（用户看到的UI）
        // 延迟通知父组件（减少重渲染）
        emitValidation(validateDslStructure(cleanDsl));
        notifyParentChange(cleanDsl);
        return next;
      });
    },
    [emitValidation, notifyParentChange],
  );

  const handleAddStep = (type: StepKey) => {
    applyDslUpdate((current) => ({
      ...current,
      steps: [...current.steps, createEmptyStep(type, siteUrl, current.steps.length === 0)],
    }));
  };

  const handleDeleteStep = useCallback((index: number) => {
    applyDslUpdate((current) => ({
      ...current,
      steps: current.steps.filter((_, i) => i !== index),
    }));
  }, [applyDslUpdate]);

  const handleFieldChange = useCallback((index: number, field: string, val: unknown) => {
    applyDslUpdate((current) => {
      const steps = [...current.steps];
      steps[index] = { ...steps[index], [field]: val };
      return { ...current, steps };
    });
  }, [applyDslUpdate]);

  const handleTypeChange = useCallback((index: number, type: StepKey) => {
    applyDslUpdate((current) => {
      const steps = [...current.steps];
      // Preserve ID when changing type to keep focus/position if possible, or generate new?
      // Usually better to generate new to avoid stale fields.
      const newStep = createEmptyStep(type, siteUrl, index === 0 && current.steps.length === 1);
      // Keep the ID so the card doesn't remount completely
      newStep._id = steps[index]._id;
      steps[index] = newStep;
      return { ...current, steps };
    });
  }, [applyDslUpdate, siteUrl]);

  const handleDuplicate = useCallback((index: number) => {
    applyDslUpdate((current) => {
      const steps = [...current.steps];
      // Deep clone with new IDs for children
      const clone = JSON.parse(JSON.stringify(steps[index]));
      clone._id = generateId();
      // Recursively assign new IDs to children
      const assignNewIds = (step: SortableFlowStep) => {
        step._id = generateId();
        step.children?.forEach(assignNewIds);
        step.else_children?.forEach(assignNewIds);
      };
      clone.children?.forEach(assignNewIds);
      clone.else_children?.forEach(assignNewIds);
      steps.splice(index + 1, 0, clone);
      return { ...current, steps };
    });
  }, [applyDslUpdate]);

  // Handle children changes for container steps
  const handleChildrenChange = useCallback((index: number, children: SortableFlowStep[], branch?: "else") => {
    applyDslUpdate((current) => {
      const steps = [...current.steps];
      if (branch === "else") {
        steps[index] = { ...steps[index], else_children: children };
      } else {
        steps[index] = { ...steps[index], children };
      }
      return { ...current, steps };
    });
  }, [applyDslUpdate]);

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;

    if (over && active.id !== over.id) {
      applyDslUpdate((current) => {
        const oldIndex = current.steps.findIndex((step) => step._id === active.id);
        const newIndex = current.steps.findIndex((step) => step._id === over.id);
        return {
          ...current,
          steps: arrayMove(current.steps, oldIndex, newIndex),
        };
      });
    }
  };

  // 暴露 flush 方法给父组件（用于Modal关闭/提交前立即同步）
  useImperativeHandle(
    ref,
    () => ({
      flush: () => {
        flushOnChange();
        flushValidation();
      },
    }),
    [flushOnChange, flushValidation],
  );

  const readableSummary = useMemo(() => {
    if (validation.errors.length > 0) {
      return `有 ${validation.errors.length} 个待修复问题，请检查红色标记字段。`;
    }
    return `DSL 校验通过，当前共 ${dsl.steps.length} 个步骤。`;
  }, [validation.errors.length, dsl.steps.length]);

  return (
    <Space direction="vertical" style={{ width: "100%" }} size="middle">
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragEnd={handleDragEnd}
      >
        <SortableContext
          items={dsl.steps.map((s) => s._id)}
          strategy={verticalListSortingStrategy}
        >
          {dsl.steps.length === 0 ? (
            <Empty description="暂无步骤，先添加一个操作吧" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <AnimatePresence mode="popLayout" initial={false}>
              {dsl.steps.map((step, index) => (
                <SortableStepItem
                  key={step._id}
                  step={step}
                  index={index}
                  onDelete={handleDeleteStep}
                  onDuplicate={handleDuplicate}
                  onTypeChange={handleTypeChange}
                  onFieldChange={handleFieldChange}
                  onChildrenChange={handleChildrenChange}
                />
              ))}
            </AnimatePresence>
          )}
        </SortableContext>
      </DndContext>

      <Card
        size="small"
        style={{
          borderRadius: 12,
          background: "rgba(15,23,42,0.02)",
          borderStyle: "dashed",
        }}
      >
        <Space wrap>
          <Select
            value={addType}
            style={{ minWidth: 220 }}
            options={STEP_TYPE_OPTIONS}
            onChange={(value) => setAddType(value as StepKey)}
            placeholder="选择一个操作类型"
          />
          <Button type="dashed" icon={<PlusOutlined />} onClick={() => handleAddStep(addType)}>
            添加步骤
          </Button>
        </Space>
      </Card>

      <Text type={validation.errors.length > 0 ? "danger" : "secondary"}>{readableSummary}</Text>
    </Space>
  );
});

VisualDslEditor.displayName = "VisualDslEditor";

export default VisualDslEditor;
