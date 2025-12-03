import rawSchema from "./dslSchema.json";

export type StepFieldType = "text" | "number" | "boolean" | "select";

export interface SelectOption {
  value: string;
  label: string;
}

export interface StepFieldDefinition {
  name: string;
  label: string;
  type: StepFieldType;
  placeholder?: string;
  required?: boolean;
  options?: SelectOption[];  // For select type
  /** If true, this field is an advanced option (collapsed by default in list editor) */
  advanced?: boolean;
  /** If true, this selector field supports auto-parsing from HTML */
  hasAutoParser?: boolean;
  /** Only show this field when condition_type matches one of these values (for dynamic forms) */
  showWhen?: string[];
}

export interface StepDefinition {
  label: string;
  description: string;
  fields: StepFieldDefinition[];
  /** If true, this step can contain children steps */
  container?: boolean;
  /** Label for the children container */
  containerLabel?: string;
  /** If true, this container has an else branch */
  hasElse?: boolean;
  /** Label for the else branch */
  elseLabel?: string;
}

export type StepDefinitionMap = Record<string, StepDefinition>;

export const DSL_SCHEMA = rawSchema as StepDefinitionMap;

/** Get all container step types */
export const CONTAINER_STEP_TYPES = Object.entries(DSL_SCHEMA)
  .filter(([_, def]) => def.container)
  .map(([type]) => type);

/** Check if a step type is a container */
export const isContainerType = (type: string): boolean => 
  DSL_SCHEMA[type]?.container === true;

type FieldTypeMap = {
  text: string;
  number: number;
  boolean: boolean;
};

type FieldDefinition<T extends keyof StepDefinitionMap> =
  StepDefinitionMap[T]["fields"][number];

type FieldValue<F> = F extends { type: infer FieldType }
  ? FieldType extends keyof FieldTypeMap
    ? FieldTypeMap[FieldType] | undefined
    : unknown
  : unknown;

type BuildStep<T extends keyof StepDefinitionMap> = {
  type: T;
} & {
  [F in FieldDefinition<T> as F["name"]]?: FieldValue<F>;
};

/** Base step type without children */
type BaseVisualDslStep = {
  [K in keyof StepDefinitionMap]: BuildStep<K>;
}[keyof StepDefinitionMap];

/** Step that may have nested children */
export type VisualDslStep = BaseVisualDslStep & {
  /** Nested steps for container types (loop, if_else, etc.) */
  children?: VisualDslStep[];
  /** Else branch steps for if_else type */
  else_children?: VisualDslStep[];
};

export interface VisualDsl {
  version: number;
  steps: VisualDslStep[];
}

export const STEP_TYPE_OPTIONS = Object.entries(DSL_SCHEMA).map(
  ([value, config]) => ({
    value,
    label: config.label,
    description: config.description,
  }),
);

export const REQUIRED_FIELD_MAP: Record<string, string[]> = Object.fromEntries(
  Object.entries(DSL_SCHEMA).map(([type, config]) => [
    type,
    config.fields
      .filter((field) => Boolean(field.required))
      .map((field) => field.name),
  ]),
);
