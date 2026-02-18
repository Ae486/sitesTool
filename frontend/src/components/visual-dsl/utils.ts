/**
 * Utility functions for Visual DSL Editor
 */
import { DSL_SCHEMA, STEP_TYPE_OPTIONS, type StepFieldDefinition } from "../../constants/dsl";
import type { FlowDSL, FlowStep } from "../../types/flow";
import type { SortableFlowStep, StepKey } from "./types";

export const DEFAULT_DSL: FlowDSL = { version: 1, steps: [] };
export const DEFAULT_STEP_TYPE = (STEP_TYPE_OPTIONS[0]?.value ?? "navigate") as StepKey;

/**
 * Check if a value is non-empty
 */
export const hasValue = (value: unknown): boolean => {
  if (value === null || value === undefined) return false;
  if (typeof value === "string") return value.trim().length > 0;
  return true;
};

/**
 * Ensure DSL has correct shape
 */
export const ensureDslShape = (candidate?: FlowDSL | string): FlowDSL => {
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

/**
 * Generate a unique ID
 */
export const generateId = (): string => {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return Math.random().toString(36).substring(2, 15);
};

/**
 * Recursively add IDs to a step and its children
 */
export const addIdsToStep = (step: FlowStep | SortableFlowStep): SortableFlowStep => {
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

/**
 * Recursively strip IDs from a step and its children
 */
export const stripIdsFromStep = (step: SortableFlowStep): FlowStep => {
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

/**
 * Ensure every step has a unique ID for sorting
 */
export const ensureDslWithIds = (dsl: FlowDSL): { version: number; steps: SortableFlowStep[] } => {
  return {
    ...dsl,
    steps: dsl.steps.map(addIdsToStep),
  };
};

/**
 * Create an empty step with default values
 */
export const createEmptyStep = (type: StepKey, siteUrl?: string, isFirst = false): SortableFlowStep => {
  const definition = DSL_SCHEMA[type];
  const base = { type, _id: generateId() } as SortableFlowStep;

  definition.fields.forEach((field) => {
    if (field.type === "boolean") {
      base[field.name] = false;
    } else if (isFirst && type === "navigate" && field.name === "url" && siteUrl) {
      base[field.name] = siteUrl;
    } else {
      base[field.name] = undefined;
    }
  });

  return base;
};

/**
 * Check if field should be visible based on showWhen condition
 */
export const shouldShowField = (field: StepFieldDefinition, step: SortableFlowStep): boolean => {
  if (!field.showWhen || field.showWhen.length === 0) return true;
  const conditionType = step.condition_type as string;
  return field.showWhen.includes(conditionType);
};
