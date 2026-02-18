/**
 * Types for Visual DSL Editor
 */
import type { FlowDSL } from "../../types/flow";
import type { DslValidationResult } from "../../utils/dsl";
import { DSL_SCHEMA, type VisualDslStep } from "../../constants/dsl";

export type StepKey = keyof typeof DSL_SCHEMA;

/**
 * Base fields common to all sortable steps
 */
interface SortableStepBase {
  /** Unique ID for drag-and-drop sorting */
  _id: string;
  /** Step type (navigate, click, input, etc.) */
  type: string;
  /** Optional description for the step */
  description?: string;
  /** Nested children for container steps */
  children?: SortableFlowStep[];
  /** Else branch for if_else steps */
  else_children?: SortableFlowStep[];
}

/**
 * Known step field names from DSL schema
 * This provides better autocomplete while still allowing dynamic fields
 */
interface KnownStepFields {
  // Navigation
  url?: string;
  wait_until?: string;
  // Interaction
  selector?: string;
  value?: string;
  timeout?: number;
  // Wait
  duration?: number;
  state?: string;
  // Condition
  condition_type?: string;
  pattern?: string;
  expected?: string;
  // Loop
  count?: number;
  variable?: string;
  array?: string;
  item_variable?: string;
  // Extract
  extract_type?: string;
  attribute?: string;
  // Screenshot
  filename?: string;
  full_page?: boolean;
  // Scroll
  direction?: string;
  distance?: number;
  // Select
  option_type?: string;
  option_value?: string;
  // Checkbox
  check?: boolean;
}

/**
 * Internal step type with ID for drag-and-drop
 * Combines base fields, known fields, and allows additional dynamic fields
 */
export type SortableFlowStep = SortableStepBase & KnownStepFields & {
  /** Allow additional dynamic fields from DSL schema */
  [key: string]: unknown;
};

/**
 * Ref interface for VisualDslEditor
 */
export interface VisualDslEditorRef {
  flush: () => void;
}

/**
 * Props for VisualDslEditor
 */
export interface VisualDslEditorProps {
  value?: FlowDSL | string;
  onChange?: (dsl: FlowDSL) => void;
  siteUrl?: string;
  onValidationChange?: (result: DslValidationResult) => void;
}

/**
 * Props for StepField component
 */
export interface StepFieldProps {
  step: SortableFlowStep;
  stepIndex: number;
  fieldMeta: import("../../constants/dsl").StepFieldDefinition;
  onFieldChange: (index: number, field: string, value: unknown) => void;
}

/**
 * Props for NestedStepCard component
 */
export interface NestedStepCardProps {
  step: SortableFlowStep;
  index: number;
  depth: number;
  onDelete: () => void;
  onDuplicate: () => void;
  onTypeChange: (type: StepKey) => void;
  onFieldChange: (field: string, value: unknown) => void;
  onChildrenChange?: (children: SortableFlowStep[], branch?: "else") => void;
}

/**
 * Props for SortableStepItem component
 */
export interface SortableStepItemProps {
  step: SortableFlowStep;
  index: number;
  depth?: number;
  siteUrl?: string;
  onDelete: (index: number) => void;
  onDuplicate: (index: number) => void;
  onTypeChange: (index: number, type: StepKey) => void;
  onFieldChange: (index: number, field: string, value: unknown) => void;
  onChildrenChange?: (index: number, children: SortableFlowStep[], branch?: "else") => void;
}
