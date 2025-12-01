export interface DslValidationResult {
    valid: boolean;
    errors: string[];
    stepCount: number;
}
export interface DslValidationOptions {
    /** Allow empty steps array (for in-progress editing) */
    allowEmptySteps?: boolean;
}
export declare const validateDslStructure: (dsl: unknown, options?: DslValidationOptions) => DslValidationResult;
export declare const STEP_OPTIONS: {
    value: string;
    label: string;
    description: string;
}[];
