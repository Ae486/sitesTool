import { DSL_SCHEMA, STEP_TYPE_OPTIONS } from "../constants/dsl";
const STEP_REQUIREMENTS = Object.fromEntries(Object.entries(DSL_SCHEMA).map(([type, config]) => [
    type,
    {
        required: config.fields.filter((field) => field.required).map((field) => field.name),
        description: config.description,
    },
]));
const isRecord = (value) => typeof value === "object" && value !== null;
export const validateDslStructure = (dsl) => {
    const errors = [];
    let stepCount = 0;
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
    if (payload.steps.length === 0) {
        errors.push("steps 至少需要包含 1 个步骤");
    }
    payload.steps.forEach((step, index) => {
        const stepNo = index + 1;
        if (!isRecord(step)) {
            errors.push(`第 ${stepNo} 步必须是对象`);
            return;
        }
        const type = step.type;
        if (typeof type !== "string") {
            errors.push(`第 ${stepNo} 步缺少字符串类型的 type`);
            return;
        }
        const schema = STEP_REQUIREMENTS[type];
        if (!schema) {
            errors.push(`第 ${stepNo} 步类型 "${type}" 暂不支持，请检查拼写或文档`);
            return;
        }
        schema.required.forEach((key) => {
            if (step[key] === undefined ||
                step[key] === null ||
                (typeof step[key] === "string" && step[key].trim() === "")) {
                errors.push(`第 ${stepNo} 步 (${type}) 缺少必填字段 "${key}"`);
            }
        });
        if (type === "wait_time" && typeof step.duration === "number") {
            if (step.duration <= 0) {
                errors.push(`第 ${stepNo} 步 (wait_time) duration 需要大于 0`);
            }
        }
        if (type === "scroll" && !step.selector && step.x === undefined && step.y === undefined) {
            errors.push(`第 ${stepNo} 步 (scroll) 需要 selector 或 x/y 参数`);
        }
        stepCount += 1;
    });
    return {
        valid: errors.length === 0,
        errors,
        stepCount,
    };
};
export const STEP_OPTIONS = STEP_TYPE_OPTIONS;
