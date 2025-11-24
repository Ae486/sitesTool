export interface DslValidationResult {
    valid: boolean;
    errors: string[];
    stepCount: number;
}
export declare const validateDslStructure: (dsl: unknown) => DslValidationResult;
export declare const STEP_OPTIONS: {
    value: string;
    label: string;
    description: string;
}[];
