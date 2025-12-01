import rawSchema from "./dslSchema.json";
export const DSL_SCHEMA = rawSchema;
/** Get all container step types */
export const CONTAINER_STEP_TYPES = Object.entries(DSL_SCHEMA)
    .filter(([_, def]) => def.container)
    .map(([type]) => type);
/** Check if a step type is a container */
export const isContainerType = (type) => DSL_SCHEMA[type]?.container === true;
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
