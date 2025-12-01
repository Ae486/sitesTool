/**
 * Node Properties Panel - Edit selected node's fields
 * With debounced updates for better performance
 */
import { useCallback, useMemo, memo } from "react";
import { Form, Input, InputNumber, Switch, Select, Button, Typography, Divider, Tag } from "antd";
import { DeleteOutlined } from "@ant-design/icons";
import { useDebounceFn } from "ahooks";
import type { WorkflowNode, StepNodeData } from "../types";
import { DSL_SCHEMA, isContainerType } from "../../../constants/dsl";

const { Text, Title } = Typography;

interface NodePropertiesProps {
  node: WorkflowNode;
  onUpdate: (data: Partial<StepNodeData>) => void;
  onDelete: () => void;
}

// Field renderer component
const FieldRenderer = memo(function FieldRenderer({
  field,
  value,
  onChange,
}: {
  field: { name: string; label: string; type: string; placeholder?: string; required?: boolean; options?: { value: string; label: string }[] };
  value: unknown;
  onChange: (value: unknown) => void;
}) {
  // Debounce text input changes (200ms)
  const { run: debouncedChange } = useDebounceFn(onChange, { wait: 200 });

  if (field.type === "boolean") {
    return (
      <Switch
        size="small"
        checked={Boolean(value)}
        onChange={onChange}
      />
    );
  }

  if (field.type === "number") {
    return (
      <InputNumber
        size="small"
        style={{ width: "100%" }}
        placeholder={field.placeholder}
        value={typeof value === "number" ? value : undefined}
        onChange={onChange}
      />
    );
  }

  if (field.type === "select" && field.options) {
    return (
      <Select
        size="small"
        style={{ width: "100%" }}
        placeholder={field.placeholder}
        value={(value as string) || undefined}
        onChange={onChange}
        options={field.options}
      />
    );
  }

  // Text input with debounce
  return (
    <Input
      size="small"
      placeholder={field.placeholder}
      defaultValue={typeof value === "string" ? value : ""}
      onChange={(e) => debouncedChange(e.target.value)}
    />
  );
});

function NodeProperties({ node, onUpdate, onDelete }: NodePropertiesProps) {
  const { data } = node;
  const schema = DSL_SCHEMA[data.stepType];
  const isContainer = isContainerType(data.stepType);

  // Memoized field change handler
  const handleFieldChange = useCallback(
    (fieldName: string, value: unknown) => {
      onUpdate({
        fields: {
          ...data.fields,
          [fieldName]: value,
        },
      });
    },
    [data.fields, onUpdate]
  );

  // Debounced description change
  const { run: handleDescriptionChange } = useDebounceFn(
    (value: string) => onUpdate({ description: value }),
    { wait: 200 }
  );

  if (!schema) {
    return (
      <div style={{ padding: 16 }}>
        <Text type="secondary">未知步骤类型: {data.stepType}</Text>
      </div>
    );
  }

  return (
    <div style={{ padding: 12 }}>
      {/* Header */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 8,
        }}
      >
        <div>
          <Title level={5} style={{ margin: 0, fontSize: 14 }}>
            {schema.label}
          </Title>
          {isContainer && (
            <Tag color="orange" style={{ marginTop: 4, fontSize: 10 }}>
              容器节点
            </Tag>
          )}
        </div>
        <Button
          danger
          size="small"
          type="text"
          icon={<DeleteOutlined />}
          onClick={onDelete}
        />
      </div>

      <Text type="secondary" style={{ fontSize: 11, display: "block", marginBottom: 12 }}>
        {schema.description}
      </Text>

      <Divider style={{ margin: "8px 0" }} />

      {/* Fields */}
      <Form layout="vertical" size="small">
        {schema.fields.map((field) => (
          <Form.Item
            key={field.name}
            label={<span style={{ fontSize: 12 }}>{field.label}</span>}
            required={field.required}
            style={{ marginBottom: 10 }}
          >
            <FieldRenderer
              field={field}
              value={data.fields[field.name]}
              onChange={(val) => handleFieldChange(field.name, val)}
            />
          </Form.Item>
        ))}

        {/* Description field */}
        <Form.Item
          label={<span style={{ fontSize: 12 }}>步骤说明</span>}
          style={{ marginBottom: 0 }}
        >
          <Input.TextArea
            size="small"
            rows={2}
            placeholder="可选：描述这个步骤"
            defaultValue={data.description || ""}
            onChange={(e) => handleDescriptionChange(e.target.value)}
          />
        </Form.Item>
      </Form>

      {/* Node ID (for debugging) */}
      <div style={{ marginTop: 12, fontSize: 10, color: "#bfbfbf" }}>
        ID: {node.id}
      </div>
    </div>
  );
}

export default memo(NodeProperties);
