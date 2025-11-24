import type { FlowDSL } from "../types/flow";
export interface DslTemplate {
    value: string;
    label: string;
    description: string;
    dsl: FlowDSL;
}
export declare const DSL_TEMPLATES: DslTemplate[];
