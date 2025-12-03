import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { CopyOutlined, DeleteOutlined, HolderOutlined, PlusOutlined, SettingOutlined } from "@ant-design/icons";
import { DndContext, KeyboardSensor, PointerSensor, closestCenter, useSensor, useSensors, } from "@dnd-kit/core";
import { SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy, } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Button, Card, Collapse, Empty, Form, Input, InputNumber, message, Select, Space, Switch, Tooltip, Typography, } from "antd";
import { useDebounceFn } from "ahooks";
import { AnimatePresence } from "framer-motion";
import { forwardRef, memo, useCallback, useEffect, useImperativeHandle, useMemo, useState } from "react";
import { DSL_SCHEMA, STEP_TYPE_OPTIONS, isContainerType } from "../constants/dsl";
import { validateDslStructure } from "../utils/dsl";
import SelectorInput from "./common/SelectorInput";
const { Text } = Typography;
const DEFAULT_DSL = { version: 1, steps: [] };
const DEFAULT_STEP_TYPE = (STEP_TYPE_OPTIONS[0]?.value ?? "navigate");
const hasValue = (value) => {
    if (value === null || value === undefined)
        return false;
    if (typeof value === "string")
        return value.trim().length > 0;
    return true;
};
const ensureDslShape = (candidate) => {
    if (!candidate)
        return { ...DEFAULT_DSL };
    if (typeof candidate === "string") {
        try {
            const parsed = JSON.parse(candidate);
            return ensureDslShape(parsed);
        }
        catch {
            return { ...DEFAULT_DSL };
        }
    }
    return {
        ...candidate,
        version: typeof candidate.version === "number" ? candidate.version : 1,
        steps: Array.isArray(candidate.steps) ? candidate.steps : [],
    };
};
const generateId = () => {
    if (typeof crypto !== "undefined" && crypto.randomUUID) {
        return crypto.randomUUID();
    }
    return Math.random().toString(36).substring(2, 15);
};
// Recursively add IDs to a step and its children
const addIdsToStep = (step) => {
    const { _id, children, else_children, ...rest } = step;
    const result = {
        ...rest,
        type: step.type,
        _id: _id || generateId(),
    };
    if (children && children.length > 0) {
        result.children = children.map(addIdsToStep);
    }
    else if (step.children) {
        result.children = step.children.map(addIdsToStep);
    }
    if (else_children && else_children.length > 0) {
        result.else_children = else_children.map(addIdsToStep);
    }
    else if (step.else_children) {
        result.else_children = step.else_children.map(addIdsToStep);
    }
    return result;
};
// Recursively strip IDs from a step and its children
const stripIdsFromStep = (step) => {
    const { _id, children, else_children, ...rest } = step;
    const result = { ...rest };
    if (children && children.length > 0) {
        result.children = children.map(stripIdsFromStep);
    }
    if (else_children && else_children.length > 0) {
        result.else_children = else_children.map(stripIdsFromStep);
    }
    return result;
};
// Helper to ensure every step has a unique ID for sorting
const ensureDslWithIds = (dsl) => {
    return {
        ...dsl,
        steps: dsl.steps.map(addIdsToStep),
    };
};
const createEmptyStep = (type, siteUrl, isFirst = false) => {
    const definition = DSL_SCHEMA[type];
    const base = { type, _id: generateId() };
    definition.fields.forEach((field) => {
        if (field.type === "boolean") {
            base[field.name] = false;
        }
        else if (isFirst && type === "navigate" && field.name === "url" && siteUrl) {
            base[field.name] = siteUrl;
        }
        else {
            base[field.name] = undefined;
        }
    });
    return base;
};
// Check if field should be visible based on showWhen condition
const shouldShowField = (field, step) => {
    if (!field.showWhen || field.showWhen.length === 0)
        return true;
    const conditionType = step.condition_type;
    return field.showWhen.includes(conditionType);
};
// Memoized Field Renderer
const StepField = memo(({ step, stepIndex, fieldMeta, onFieldChange, }) => {
    const value = step[fieldMeta.name];
    const showError = fieldMeta.required && !hasValue(value);
    const commonProps = {
        label: fieldMeta.label,
        required: fieldMeta.required,
        style: { marginBottom: 12 },
        validateStatus: showError ? "error" : undefined,
        help: showError ? "必填" : undefined,
    };
    // Check showWhen condition for dynamic forms
    if (!shouldShowField(fieldMeta, step)) {
        return null;
    }
    if (fieldMeta.type === "boolean") {
        return (_jsx(Form.Item, { ...commonProps, children: _jsx(Switch, { checked: Boolean(value), onChange: (checked) => onFieldChange(stepIndex, fieldMeta.name, checked) }) }));
    }
    if (fieldMeta.type === "number") {
        return (_jsx(Form.Item, { ...commonProps, children: _jsx(InputNumber, { style: { width: "100%" }, placeholder: fieldMeta.placeholder, value: typeof value === "number" ? value : undefined, onChange: (val) => onFieldChange(stepIndex, fieldMeta.name, val ?? undefined) }) }));
    }
    if (fieldMeta.type === "select" && fieldMeta.options) {
        return (_jsx(Form.Item, { ...commonProps, children: _jsx(Select, { style: { width: "100%" }, placeholder: fieldMeta.placeholder, value: value || undefined, onChange: (val) => onFieldChange(stepIndex, fieldMeta.name, val), options: fieldMeta.options.map((opt) => ({
                    value: opt.value,
                    label: opt.label,
                })) }) }));
    }
    // Text input with optional auto-parser for selector fields
    if (fieldMeta.hasAutoParser) {
        return (_jsx(Form.Item, { ...commonProps, children: _jsx(SelectorInput, { value: typeof value === "string" ? value : "", onChange: (val) => onFieldChange(stepIndex, fieldMeta.name, val), placeholder: fieldMeta.placeholder, size: "middle" }) }));
    }
    return (_jsx(Form.Item, { ...commonProps, children: _jsx(Input, { placeholder: fieldMeta.placeholder, value: typeof value === "string" ? value : "", onChange: (e) => onFieldChange(stepIndex, fieldMeta.name, e.target.value) }) }));
});
// Container color schemes for visual distinction
const CONTAINER_COLORS = {
    loop: { border: "#722ed1", bg: "#f9f0ff", headerBg: "#efdbff" },
    loop_array: { border: "#13c2c2", bg: "#e6fffb", headerBg: "#b5f5ec" },
    if_else: { border: "#fa8c16", bg: "#fff7e6", headerBg: "#ffd591" },
};
// Maximum nesting depth constant
const MAX_NESTING_DEPTH = 4;
// Nested step card (non-draggable, used inside containers) - supports recursive nesting
const NestedStepCard = memo(({ step, index, depth, onDelete, onDuplicate, onTypeChange, onFieldChange, onChildrenChange, }) => {
    const definition = DSL_SCHEMA[step.type];
    const isContainer = isContainerType(step.type);
    const colors = CONTAINER_COLORS[step.type];
    // Filter out container types when at max depth
    const allowedOptions = depth >= MAX_NESTING_DEPTH
        ? STEP_TYPE_OPTIONS.filter(opt => !isContainerType(opt.value))
        : STEP_TYPE_OPTIONS;
    // Handlers for nested children (recursive)
    const handleAddChild = (branch) => {
        // Block adding at depth 5 (would be depth 4 children)
        if (depth >= MAX_NESTING_DEPTH) {
            message.warning("已达到最大嵌套层数（4层），无法继续添加嵌套步骤");
            return;
        }
        const newStep = createEmptyStep("click");
        const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
        onChildrenChange?.([...targetChildren, newStep], branch);
    };
    const handleChildDelete = (childIndex, branch) => {
        const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
        onChildrenChange?.(targetChildren.filter((_, i) => i !== childIndex), branch);
    };
    const handleChildDuplicate = (childIndex, branch) => {
        const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
        const clone = { ...targetChildren[childIndex], _id: generateId() };
        const newChildren = [...targetChildren];
        newChildren.splice(childIndex + 1, 0, clone);
        onChildrenChange?.(newChildren, branch);
    };
    const handleChildTypeChange = (childIndex, type, branch) => {
        const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
        const newChildren = [...targetChildren];
        const newStep = createEmptyStep(type);
        newStep._id = newChildren[childIndex]._id;
        newChildren[childIndex] = newStep;
        onChildrenChange?.(newChildren, branch);
    };
    const handleChildFieldChange = (childIndex, field, value, branch) => {
        const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
        const newChildren = [...targetChildren];
        newChildren[childIndex] = { ...newChildren[childIndex], [field]: value };
        onChildrenChange?.(newChildren, branch);
    };
    const handleNestedChildrenChange = (childIndex, grandChildren, branch, grandBranch) => {
        const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
        const newChildren = [...targetChildren];
        if (grandBranch === "else") {
            newChildren[childIndex] = { ...newChildren[childIndex], else_children: grandChildren };
        }
        else {
            newChildren[childIndex] = { ...newChildren[childIndex], children: grandChildren };
        }
        onChildrenChange?.(newChildren, branch);
    };
    // Render nested children recursively
    const renderNestedChildren = (children, branch) => (_jsxs("div", { style: {
            marginLeft: 12,
            paddingLeft: 10,
            borderLeft: `2px dashed ${colors?.border || "#d9d9d9"}`,
        }, children: [children && children.length > 0 ? (children.map((child, childIdx) => (_jsx(NestedStepCard, { step: child, index: childIdx, depth: depth + 1, onDelete: () => handleChildDelete(childIdx, branch), onDuplicate: () => handleChildDuplicate(childIdx, branch), onTypeChange: (type) => handleChildTypeChange(childIdx, type, branch), onFieldChange: (field, val) => handleChildFieldChange(childIdx, field, val, branch), onChildrenChange: (grandChildren, grandBranch) => handleNestedChildrenChange(childIdx, grandChildren, branch, grandBranch) }, child._id)))) : (_jsx(Text, { type: "secondary", style: { fontSize: 11 }, children: "\u6682\u65E0\u6B65\u9AA4" })), depth < MAX_NESTING_DEPTH && (_jsxs(Button, { type: "dashed", size: "small", icon: _jsx(PlusOutlined, {}), onClick: () => handleAddChild(branch), style: { marginTop: 6, width: "100%", fontSize: 12 }, children: ["\u6DFB\u52A0", branch === "else" ? "否则" : "", "\u6B65\u9AA4"] })), depth >= MAX_NESTING_DEPTH && (_jsx(Text, { type: "warning", style: { fontSize: 10, display: "block", marginTop: 4 }, children: "\u5DF2\u8FBE\u6700\u5927\u5D4C\u5957\u5C42\u6570" }))] }));
    return (_jsxs(Card, { size: "small", style: {
            borderRadius: 8,
            marginBottom: 8,
            borderLeft: isContainer ? `3px solid ${colors?.border || "#1890ff"}` : undefined,
            background: isContainer ? colors?.bg : undefined,
        }, title: _jsxs(Space, { size: "small", align: "center", children: [_jsxs(Text, { strong: true, style: { fontSize: 12 }, children: ["#", index + 1] }), _jsx(Tooltip, { title: definition?.description, placement: "top", children: _jsx(Select, { value: step.type, bordered: false, size: "small", style: { width: 140 }, onChange: (nextType) => onTypeChange(nextType), options: allowedOptions }) })] }), extra: _jsxs(Space, { size: "small", children: [_jsx(Tooltip, { title: "\u590D\u5236", children: _jsx(Button, { icon: _jsx(CopyOutlined, {}), size: "small", onClick: onDuplicate }) }), _jsx(Tooltip, { title: "\u5220\u9664", children: _jsx(Button, { icon: _jsx(DeleteOutlined, {}), size: "small", danger: true, onClick: onDelete }) })] }), children: [(() => {
                const basicFields = definition.fields.filter(f => !f.advanced && shouldShowField(f, step));
                const advancedFields = definition.fields.filter(f => f.advanced && shouldShowField(f, step));
                const renderField = (field) => (_jsx(Form.Item, { label: field.label, required: field.required, style: { marginBottom: 8 }, children: field.type === "boolean" ? (_jsx(Switch, { size: "small", checked: Boolean(step[field.name]), onChange: (checked) => onFieldChange(field.name, checked) })) : field.type === "number" ? (_jsx(InputNumber, { size: "small", style: { width: "100%" }, placeholder: field.placeholder, value: step[field.name], onChange: (val) => onFieldChange(field.name, val) })) : field.type === "select" && field.options ? (_jsx(Select, { size: "small", style: { width: "100%" }, placeholder: field.placeholder, value: step[field.name] || undefined, onChange: (val) => onFieldChange(field.name, val), options: field.options.map((opt) => ({
                            value: opt.value,
                            label: opt.label,
                        })) })) : field.hasAutoParser ? (_jsx(SelectorInput, { value: step[field.name] || "", onChange: (val) => onFieldChange(field.name, val), placeholder: field.placeholder, size: "small" })) : (_jsx(Input, { size: "small", placeholder: field.placeholder, value: step[field.name] || "", onChange: (e) => onFieldChange(field.name, e.target.value) })) }, field.name));
                return (_jsxs(Space, { direction: "vertical", style: { width: "100%" }, size: "small", children: [basicFields.map(renderField), _jsx(Form.Item, { label: "\u6B65\u9AA4\u8BF4\u660E", style: { marginBottom: 8 }, children: _jsx(Input.TextArea, { size: "small", rows: 1, placeholder: "\u53EF\u9009\uFF1A\u63CF\u8FF0\u8FD9\u4E2A\u6B65\u9AA4", value: step.description || "", onChange: (e) => onFieldChange("description", e.target.value) }) }), advancedFields.length > 0 && (_jsx(Collapse, { size: "small", ghost: true, items: [{
                                    key: "advanced",
                                    label: _jsxs(Text, { type: "secondary", style: { fontSize: 11 }, children: [_jsx(SettingOutlined, {}), " \u9AD8\u7EA7\u9009\u9879"] }),
                                    children: advancedFields.map(renderField),
                                }] }))] }));
            })(), isContainer && (_jsxs("div", { style: { marginTop: 12 }, children: [_jsx(Text, { strong: true, style: { fontSize: 11, color: colors?.border }, children: definition.containerLabel || "子步骤" }), renderNestedChildren(step.children), definition.hasElse && (_jsxs("div", { style: { marginTop: 12 }, children: [_jsx(Text, { strong: true, style: { fontSize: 11, color: "#fa541c" }, children: definition.elseLabel || "否则执行" }), renderNestedChildren(step.else_children, "else")] }))] }))] }));
});
// Sortable Step Item Component
const SortableStepItem = memo(({ step, index, depth = 0, onDelete, onDuplicate, onTypeChange, onFieldChange, onChildrenChange, }) => {
    const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
        id: step._id,
    });
    const isContainer = isContainerType(step.type);
    const definition = DSL_SCHEMA[step.type];
    const colors = CONTAINER_COLORS[step.type];
    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.4 : 1,
        marginBottom: 16,
        position: "relative",
        zIndex: isDragging ? 999 : 1,
    };
    // Handlers for nested children
    const handleAddChild = (branch) => {
        const newStep = createEmptyStep("click");
        const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
        onChildrenChange?.(index, [...targetChildren, newStep], branch);
    };
    const handleChildDelete = (childIndex, branch) => {
        const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
        onChildrenChange?.(index, targetChildren.filter((_, i) => i !== childIndex), branch);
    };
    const handleChildDuplicate = (childIndex, branch) => {
        const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
        const clone = { ...targetChildren[childIndex], _id: generateId() };
        const newChildren = [...targetChildren];
        newChildren.splice(childIndex + 1, 0, clone);
        onChildrenChange?.(index, newChildren, branch);
    };
    const handleChildTypeChange = (childIndex, type, branch) => {
        const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
        const newChildren = [...targetChildren];
        const newStep = createEmptyStep(type);
        newStep._id = newChildren[childIndex]._id;
        newChildren[childIndex] = newStep;
        onChildrenChange?.(index, newChildren, branch);
    };
    const handleChildFieldChange = (childIndex, field, value, branch) => {
        const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
        const newChildren = [...targetChildren];
        newChildren[childIndex] = { ...newChildren[childIndex], [field]: value };
        onChildrenChange?.(index, newChildren, branch);
    };
    // Handle nested grandchildren changes (for recursive nesting)
    const handleNestedChildrenChange = (childIndex, grandChildren, branch, grandBranch) => {
        const targetChildren = branch === "else" ? (step.else_children || []) : (step.children || []);
        const newChildren = [...targetChildren];
        if (grandBranch === "else") {
            newChildren[childIndex] = { ...newChildren[childIndex], else_children: grandChildren };
        }
        else {
            newChildren[childIndex] = { ...newChildren[childIndex], children: grandChildren };
        }
        onChildrenChange?.(index, newChildren, branch);
    };
    // Render nested children with recursive support
    const renderChildren = (children, branch) => (_jsxs("div", { style: {
            marginLeft: 16,
            paddingLeft: 12,
            borderLeft: `2px dashed ${colors?.border || "#d9d9d9"}`,
        }, children: [children && children.length > 0 ? (children.map((child, childIdx) => (_jsx(NestedStepCard, { step: child, index: childIdx, depth: depth + 1, onDelete: () => handleChildDelete(childIdx, branch), onDuplicate: () => handleChildDuplicate(childIdx, branch), onTypeChange: (type) => handleChildTypeChange(childIdx, type, branch), onFieldChange: (field, val) => handleChildFieldChange(childIdx, field, val, branch), onChildrenChange: (grandChildren, grandBranch) => handleNestedChildrenChange(childIdx, grandChildren, branch, grandBranch) }, child._id)))) : (_jsx(Text, { type: "secondary", style: { fontSize: 12 }, children: "\u6682\u65E0\u6B65\u9AA4" })), _jsxs(Button, { type: "dashed", size: "small", icon: _jsx(PlusOutlined, {}), onClick: () => handleAddChild(branch), style: { marginTop: 8, width: "100%" }, children: ["\u6DFB\u52A0", branch === "else" ? "否则" : "", "\u6B65\u9AA4"] })] }));
    return (_jsx("div", { ref: setNodeRef, style: style, children: _jsxs(Card, { size: "small", style: {
                borderRadius: 12,
                boxShadow: isDragging
                    ? "0 8px 24px rgba(0,0,0,0.15)"
                    : "0 1px 4px rgba(15, 23, 42, 0.08)",
                border: isDragging ? "1px solid var(--primary-color)" : undefined,
                borderLeft: isContainer ? `4px solid ${colors?.border}` : undefined,
                background: isContainer ? colors?.bg : undefined,
            }, title: _jsxs(Space, { size: "small", align: "center", children: [_jsx(Tooltip, { title: "\u62D6\u62FD\u6392\u5E8F", children: _jsx("div", { ...attributes, ...listeners, style: { cursor: "grab", display: "flex", alignItems: "center", touchAction: "none" }, children: _jsx(HolderOutlined, { style: { color: "var(--text-secondary)", fontSize: 16 } }) }) }), _jsxs(Text, { strong: true, children: ["\u6B65\u9AA4 ", index + 1] }), _jsx(Tooltip, { title: definition?.description, placement: "top", children: _jsx(Select, { value: step.type, bordered: false, style: { width: 160 }, onChange: (nextType) => onTypeChange(index, nextType), options: STEP_TYPE_OPTIONS, onMouseDown: (e) => e.stopPropagation() }) })] }), extra: _jsxs(Space, { size: "small", children: [_jsx(Tooltip, { title: "\u590D\u5236\u6B65\u9AA4", children: _jsx(Button, { icon: _jsx(CopyOutlined, {}), size: "small", onClick: () => onDuplicate(index) }) }), _jsx(Tooltip, { title: "\u5220\u9664\u6B65\u9AA4", children: _jsx(Button, { icon: _jsx(DeleteOutlined, {}), size: "small", danger: true, onClick: () => onDelete(index) }) })] }), children: [(() => {
                    const basicFields = definition.fields.filter(f => !f.advanced && shouldShowField(f, step));
                    const advancedFields = definition.fields.filter(f => f.advanced && shouldShowField(f, step));
                    return (_jsxs(Space, { direction: "vertical", style: { width: "100%" }, size: "small", children: [basicFields.map((field) => (_jsx(StepField, { step: step, stepIndex: index, fieldMeta: field, onFieldChange: onFieldChange }, field.name))), _jsx(Form.Item, { label: "\u6B65\u9AA4\u8BF4\u660E", style: { marginBottom: 12 }, children: _jsx(Input.TextArea, { rows: 1, placeholder: "\u53EF\u9009\uFF1A\u63CF\u8FF0\u8FD9\u4E2A\u6B65\u9AA4", value: step.description || "", onChange: (e) => onFieldChange(index, "description", e.target.value) }) }), advancedFields.length > 0 && (_jsx(Collapse, { size: "small", ghost: true, items: [{
                                        key: "advanced",
                                        label: _jsxs(Text, { type: "secondary", style: { fontSize: 12 }, children: [_jsx(SettingOutlined, {}), " \u9AD8\u7EA7\u9009\u9879 (", advancedFields.length, ")"] }),
                                        children: (_jsx(Space, { direction: "vertical", style: { width: "100%" }, size: "small", children: advancedFields.map((field) => (_jsx(StepField, { step: step, stepIndex: index, fieldMeta: field, onFieldChange: onFieldChange }, field.name))) })),
                                    }] }))] }));
                })(), isContainer && (_jsxs("div", { style: { marginTop: 16 }, children: [_jsx(Text, { strong: true, style: { fontSize: 12, color: colors?.border }, children: definition.containerLabel || "子步骤" }), renderChildren(step.children), definition.hasElse && (_jsxs("div", { style: { marginTop: 16 }, children: [_jsx(Text, { strong: true, style: { fontSize: 12, color: "#fa541c" }, children: definition.elseLabel || "否则执行" }), renderChildren(step.else_children, "else")] }))] }))] }) }));
});
const VisualDslEditor = forwardRef(({ value, onChange, siteUrl, onValidationChange }, ref) => {
    // Initialize state with IDs
    const [dsl, setDsl] = useState(() => ensureDslWithIds(ensureDslShape(value)));
    const [addType, setAddType] = useState(DEFAULT_STEP_TYPE);
    const [validation, setValidation] = useState(() => validateDslStructure(ensureDslShape(value)));
    // Dnd Sensors
    const sensors = useSensors(useSensor(PointerSensor, {
        activationConstraint: {
            distance: 5, // Prevent accidental drags
        },
    }), useSensor(KeyboardSensor, {
        coordinateGetter: sortableKeyboardCoordinates,
    }));
    // 防抖的onChange回调 (300ms) - 减少Form重渲染
    const { run: notifyParentChange, flush: flushOnChange } = useDebounceFn((cleanDsl) => {
        onChange?.(cleanDsl);
    }, { wait: 300 });
    // 防抖的validation回调 (500ms) - 校验成本高，延迟更长
    const { run: notifyValidationChange, flush: flushValidation } = useDebounceFn((result) => {
        onValidationChange?.(result);
    }, { wait: 500 });
    // 立即更新本地validation状态（用于UI显示），延迟通知父组件
    const emitValidation = useCallback((result) => {
        setValidation(result);
        notifyValidationChange(result);
    }, [notifyValidationChange]);
    // Sync with external value changes
    useEffect(() => {
        const normalized = ensureDslShape(value);
        setDsl((prev) => {
            // Create a clean version of current state without IDs for comparison
            const currentClean = {
                ...prev,
                steps: prev.steps.map(stripIdsFromStep),
            };
            // Deep comparison (simple JSON stringify is sufficient for this DSL)
            if (JSON.stringify(normalized) === JSON.stringify(currentClean)) {
                return prev;
            }
            // If different, use addIdsToStep for proper recursive ID handling
            const newSteps = normalized.steps.map((step, index) => {
                const prevStep = prev.steps[index];
                // If a step exists at this index and has the same type, try to preserve its ID
                if (prevStep && prevStep.type === step.type) {
                    const withId = addIdsToStep(step);
                    withId._id = prevStep._id;
                    return withId;
                }
                return addIdsToStep(step);
            });
            return {
                ...normalized,
                steps: newSteps,
            };
        });
        emitValidation(validateDslStructure(normalized));
    }, [value, emitValidation]);
    // Auto-fill site URL for first step
    useEffect(() => {
        if (!siteUrl)
            return;
        setDsl((prev) => {
            if (prev.steps.length === 0) {
                const nextStep = createEmptyStep("navigate", siteUrl, true);
                const nextDsl = { ...prev, steps: [nextStep] };
                emitValidation(validateDslStructure(nextDsl)); // Validate clean DSL
                onChange?.(nextDsl);
                return { ...prev, steps: [nextStep] }; // Return state with IDs
            }
            const firstStep = prev.steps[0];
            if (firstStep.type === "navigate" && !hasValue(firstStep.url)) {
                const nextSteps = [...prev.steps];
                nextSteps[0] = { ...firstStep, url: siteUrl };
                // Create clean DSL for validation/onChange (recursive)
                const cleanDsl = {
                    ...prev,
                    steps: nextSteps.map(stripIdsFromStep),
                };
                emitValidation(validateDslStructure(cleanDsl));
                onChange?.(cleanDsl);
                return { ...prev, steps: nextSteps };
            }
            return prev;
        });
    }, [siteUrl, emitValidation, onChange]);
    const applyDslUpdate = useCallback((updater) => {
        setDsl((prev) => {
            const next = updater(prev);
            if (next === prev)
                return prev;
            // Strip IDs before sending to parent (recursively)
            const cleanDsl = {
                ...next,
                steps: next.steps.map(stripIdsFromStep),
            };
            // 立即更新本地状态（用户看到的UI）
            // 延迟通知父组件（减少重渲染）
            emitValidation(validateDslStructure(cleanDsl));
            notifyParentChange(cleanDsl);
            return next;
        });
    }, [emitValidation, notifyParentChange]);
    const handleAddStep = (type) => {
        applyDslUpdate((current) => ({
            ...current,
            steps: [...current.steps, createEmptyStep(type, siteUrl, current.steps.length === 0)],
        }));
    };
    const handleDeleteStep = useCallback((index) => {
        applyDslUpdate((current) => ({
            ...current,
            steps: current.steps.filter((_, i) => i !== index),
        }));
    }, [applyDslUpdate]);
    const handleFieldChange = useCallback((index, field, val) => {
        applyDslUpdate((current) => {
            const steps = [...current.steps];
            steps[index] = { ...steps[index], [field]: val };
            return { ...current, steps };
        });
    }, [applyDslUpdate]);
    const handleTypeChange = useCallback((index, type) => {
        applyDslUpdate((current) => {
            const steps = [...current.steps];
            // Preserve ID when changing type to keep focus/position if possible, or generate new?
            // Usually better to generate new to avoid stale fields.
            const newStep = createEmptyStep(type, siteUrl, index === 0 && current.steps.length === 1);
            // Keep the ID so the card doesn't remount completely
            newStep._id = steps[index]._id;
            steps[index] = newStep;
            return { ...current, steps };
        });
    }, [applyDslUpdate, siteUrl]);
    const handleDuplicate = useCallback((index) => {
        applyDslUpdate((current) => {
            const steps = [...current.steps];
            // Deep clone with new IDs for children
            const clone = JSON.parse(JSON.stringify(steps[index]));
            clone._id = generateId();
            // Recursively assign new IDs to children
            const assignNewIds = (step) => {
                step._id = generateId();
                step.children?.forEach(assignNewIds);
                step.else_children?.forEach(assignNewIds);
            };
            clone.children?.forEach(assignNewIds);
            clone.else_children?.forEach(assignNewIds);
            steps.splice(index + 1, 0, clone);
            return { ...current, steps };
        });
    }, [applyDslUpdate]);
    // Handle children changes for container steps
    const handleChildrenChange = useCallback((index, children, branch) => {
        applyDslUpdate((current) => {
            const steps = [...current.steps];
            if (branch === "else") {
                steps[index] = { ...steps[index], else_children: children };
            }
            else {
                steps[index] = { ...steps[index], children };
            }
            return { ...current, steps };
        });
    }, [applyDslUpdate]);
    const handleDragEnd = (event) => {
        const { active, over } = event;
        if (over && active.id !== over.id) {
            applyDslUpdate((current) => {
                const oldIndex = current.steps.findIndex((step) => step._id === active.id);
                const newIndex = current.steps.findIndex((step) => step._id === over.id);
                return {
                    ...current,
                    steps: arrayMove(current.steps, oldIndex, newIndex),
                };
            });
        }
    };
    // 暴露 flush 方法给父组件（用于Modal关闭/提交前立即同步）
    useImperativeHandle(ref, () => ({
        flush: () => {
            flushOnChange();
            flushValidation();
        },
    }), [flushOnChange, flushValidation]);
    const readableSummary = useMemo(() => {
        if (validation.errors.length > 0) {
            return `有 ${validation.errors.length} 个待修复问题，请检查红色标记字段。`;
        }
        return `DSL 校验通过，当前共 ${dsl.steps.length} 个步骤。`;
    }, [validation.errors.length, dsl.steps.length]);
    return (_jsxs(Space, { direction: "vertical", style: { width: "100%" }, size: "middle", children: [_jsx(DndContext, { sensors: sensors, collisionDetection: closestCenter, onDragEnd: handleDragEnd, children: _jsx(SortableContext, { items: dsl.steps.map((s) => s._id), strategy: verticalListSortingStrategy, children: dsl.steps.length === 0 ? (_jsx(Empty, { description: "\u6682\u65E0\u6B65\u9AA4\uFF0C\u5148\u6DFB\u52A0\u4E00\u4E2A\u64CD\u4F5C\u5427", image: Empty.PRESENTED_IMAGE_SIMPLE })) : (_jsx(AnimatePresence, { mode: "popLayout", initial: false, children: dsl.steps.map((step, index) => (_jsx(SortableStepItem, { step: step, index: index, onDelete: handleDeleteStep, onDuplicate: handleDuplicate, onTypeChange: handleTypeChange, onFieldChange: handleFieldChange, onChildrenChange: handleChildrenChange }, step._id))) })) }) }), _jsx(Card, { size: "small", style: {
                    borderRadius: 12,
                    background: "rgba(15,23,42,0.02)",
                    borderStyle: "dashed",
                }, children: _jsxs(Space, { wrap: true, children: [_jsx(Select, { value: addType, style: { minWidth: 220 }, options: STEP_TYPE_OPTIONS, onChange: (value) => setAddType(value), placeholder: "\u9009\u62E9\u4E00\u4E2A\u64CD\u4F5C\u7C7B\u578B" }), _jsx(Button, { type: "dashed", icon: _jsx(PlusOutlined, {}), onClick: () => handleAddStep(addType), children: "\u6DFB\u52A0\u6B65\u9AA4" })] }) }), _jsx(Text, { type: validation.errors.length > 0 ? "danger" : "secondary", children: readableSummary })] }));
});
VisualDslEditor.displayName = "VisualDslEditor";
export default VisualDslEditor;
