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
    options?: SelectOption[];
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
export declare const DSL_SCHEMA: StepDefinitionMap;
/** Get all container step types */
export declare const CONTAINER_STEP_TYPES: string[];
/** Check if a step type is a container */
export declare const isContainerType: (type: string) => boolean;
type FieldTypeMap = {
    text: string;
    number: number;
    boolean: boolean;
};
type FieldDefinition<T extends keyof StepDefinitionMap> = StepDefinitionMap[T]["fields"][number];
type FieldValue<F> = F extends {
    type: infer FieldType;
} ? FieldType extends keyof FieldTypeMap ? FieldTypeMap[FieldType] | undefined : unknown : unknown;
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
export declare const STEP_TYPE_OPTIONS: {
    value: string;
    label: string;
    description: string;
}[];
export declare const REQUIRED_FIELD_MAP: Record<string, string[]>;
export {};
