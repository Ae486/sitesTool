import { DSL_SCHEMA, STEP_TYPE_OPTIONS } from "../constants/dsl";
const STEP_REQUIREMENTS = Object.fromEntries(Object.entries(DSL_SCHEMA).map(([type, config]) => [
    type,
    {
        required: config.fields.filter((field) => field.required).map((field) => field.name),
        description: config.description,
        isContainer: config.container || false,
        hasElse: config.hasElse || false,
    },
]));
const isRecord = (value) => typeof value === "object" && value !== null;
// Recursive step validation
const validateStep = (step, path, errors, countRef) => {
    if (!isRecord(step)) {
        errors.push(`${path} 必须是对象`);
        return;
    }
    const type = step.type;
    if (typeof type !== "string") {
        errors.push(`${path} 缺少字符串类型的 type`);
        return;
    }
    const schema = STEP_REQUIREMENTS[type];
    if (!schema) {
        errors.push(`${path} 类型 "${type}" 暂不支持，请检查拼写或文档`);
        return;
    }
    // Check required fields
    schema.required.forEach((key) => {
        const value = step[key];
        if (value === undefined ||
            value === null ||
            (typeof value === "string" && value.trim() === "")) {
            errors.push(`${path} (${type}) 缺少必填字段 "${key}"`);
        }
    });
    // Type-specific validations
    if (type === "wait_time" && typeof step.duration === "number") {
        if (step.duration <= 0) {
            errors.push(`${path} (wait_time) duration 需要大于 0`);
        }
    }
    if (type === "scroll") {
        const s = step;
        if (!s.selector && s.x === undefined && s.y === undefined) {
            errors.push(`${path} (scroll) 需要 selector 或 x/y 参数`);
        }
    }
    if (type === "random_delay") {
        const s = step;
        if (typeof s.min === "number" && typeof s.max === "number" && s.min > s.max) {
            errors.push(`${path} (random_delay) min 不能大于 max`);
        }
    }
    countRef.count += 1;
    // Validate children for container types
    if (schema.isContainer) {
        const children = step.children;
        if (children && Array.isArray(children)) {
            children.forEach((child, idx) => {
                validateStep(child, `${path} > 子步骤 ${idx + 1}`, errors, countRef);
            });
        }
        // Validate else_children for if_else
        if (schema.hasElse && step.else_children) {
            const elseChildren = step.else_children;
            if (Array.isArray(elseChildren)) {
                elseChildren.forEach((child, idx) => {
                    validateStep(child, `${path} > 否则分支步骤 ${idx + 1}`, errors, countRef);
                });
            }
        }
    }
};
export const validateDslStructure = (dsl, options = {}) => {
    const { allowEmptySteps = false } = options;
    const errors = [];
    const countRef = { count: 0 };
    let payload;
    if (typeof dsl === "string") {
        try {
            payload = JSON.parse(dsl);
        }
        catch (error) {
            return {
                valid: false,
                errors: ["DSL 不是合法的 JSON 格式"],
                stepCount: 0,
            };
        }
    }
    else if (isRecord(dsl)) {
        payload = dsl;
    }
    else {
        return {
            valid: false,
            errors: ["DSL 顶层必须是对象"],
            stepCount: 0,
        };
    }
    if (typeof payload.version !== "number") {
        errors.push("字段 version 必须是数字");
    }
    if (!Array.isArray(payload.steps)) {
        errors.push("字段 steps 必须是数组");
        return { valid: false, errors, stepCount: 0 };
    }
    // Only require non-empty steps if not explicitly allowed
    if (payload.steps.length === 0 && !allowEmptySteps) {
        errors.push("steps 至少需要包含 1 个步骤");
    }
    // Validate each step recursively
    payload.steps.forEach((step, index) => {
        validateStep(step, `第 ${index + 1} 步`, errors, countRef);
    });
    return {
        valid: errors.length === 0,
        errors,
        stepCount: countRef.count,
    };
};
export const STEP_OPTIONS = STEP_TYPE_OPTIONS;
