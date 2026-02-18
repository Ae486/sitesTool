/**
 * StepField - Renders a single field for a DSL step
 */
import { memo } from "react";
import { Form, Input, InputNumber, Select, Switch } from "antd";
import type { StepFieldDefinition } from "../../constants/dsl";
import SelectorInput from "../common/SelectorInput";
import type { StepFieldProps } from "./types";
import { hasValue, shouldShowField } from "./utils";

/**
 * Memoized Field Renderer for step fields
 */
const StepField = memo(function StepField({
  step,
  stepIndex,
  fieldMeta,
  onFieldChange,
}: StepFieldProps) {
  const value = step[fieldMeta.name];
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
          value={(value as string) || undefined}
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
});

export default StepField;

/**
 * Renders a field for nested step cards (smaller size)
 */
export function renderNestedField(
  field: StepFieldDefinition,
  step: { [key: string]: unknown },
  onFieldChange: (field: string, value: unknown) => void
) {
  const value = step[field.name];

  if (field.type === "boolean") {
    return (
      <Switch
        size="small"
        checked={Boolean(value)}
        onChange={(checked) => onFieldChange(field.name, checked)}
      />
    );
  }

  if (field.type === "number") {
    return (
      <InputNumber
        size="small"
        style={{ width: "100%" }}
        placeholder={field.placeholder}
        value={value as number}
        onChange={(val) => onFieldChange(field.name, val)}
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
        onChange={(val) => onFieldChange(field.name, val)}
        options={field.options.map((opt) => ({
          value: opt.value,
          label: opt.label,
        }))}
      />
    );
  }

  if (field.hasAutoParser) {
    return (
      <SelectorInput
        value={(value as string) || ""}
        onChange={(val) => onFieldChange(field.name, val)}
        placeholder={field.placeholder}
        size="small"
      />
    );
  }

  return (
    <Input
      size="small"
      placeholder={field.placeholder}
      value={(value as string) || ""}
      onChange={(e) => onFieldChange(field.name, e.target.value)}
    />
  );
}
