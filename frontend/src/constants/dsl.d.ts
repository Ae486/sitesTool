export type StepFieldType = "text" | "number" | "boolean";
export interface StepFieldDefinition {
    name: string;
    label: string;
    type: StepFieldType;
    placeholder?: string;
    required?: boolean;
}
export interface StepDefinition {
    label: string;
    description: string;
    fields: StepFieldDefinition[];
}
export type StepDefinitionMap = Record<string, StepDefinition>;
export declare const DSL_SCHEMA: StepDefinitionMap;
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
export type VisualDslStep = {
    [K in keyof StepDefinitionMap]: BuildStep<K>;
}[keyof StepDefinitionMap];
export interface VisualDsl {
    version: number;
    steps: VisualDslStep[];
    [key: string]: unknown;
}
export declare const STEP_TYPE_OPTIONS: {
    value: string;
    label: string;
    description: string;
}[];
export declare const REQUIRED_FIELD_MAP: Record<string, string[]>;
export {};
