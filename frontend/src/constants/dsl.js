import rawSchema from "./dslSchema.json";
export const DSL_SCHEMA = rawSchema;
export const STEP_TYPE_OPTIONS = Object.entries(DSL_SCHEMA).map(([value, config]) => ({
    value,
    label: config.label,
    description: config.description,
}));
export const REQUIRED_FIELD_MAP = Object.fromEntries(Object.entries(DSL_SCHEMA).map(([type, config]) => [
    type,
    config.fields
        .filter((field) => Boolean(field.required))
        .map((field) => field.name),
]));
