import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { CopyOutlined, DeleteOutlined, HolderOutlined, PlusOutlined } from "@ant-design/icons";
import { DndContext, KeyboardSensor, PointerSensor, closestCenter, useSensor, useSensors, } from "@dnd-kit/core";
import { SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy, } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Button, Card, Empty, Form, Input, InputNumber, Select, Space, Switch, Tooltip, Typography, } from "antd";
import { useDebounceFn } from "ahooks";
import { AnimatePresence } from "framer-motion";
import { forwardRef, memo, useCallback, useEffect, useImperativeHandle, useMemo, useState } from "react";
import { DSL_SCHEMA, STEP_TYPE_OPTIONS } from "../constants/dsl";
import { validateDslStructure } from "../utils/dsl";
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
// Helper to ensure every step has a unique ID for sorting
const ensureDslWithIds = (dsl) => {
    return {
        ...dsl,
        steps: dsl.steps.map((step) => ({
            ...step,
            _id: step._id || generateId(),
        })),
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
    if (fieldMeta.type === "boolean") {
        return (_jsx(Form.Item, { ...commonProps, children: _jsx(Switch, { checked: Boolean(value), onChange: (checked) => onFieldChange(stepIndex, fieldMeta.name, checked) }) }));
    }
    if (fieldMeta.type === "number") {
        return (_jsx(Form.Item, { ...commonProps, children: _jsx(InputNumber, { style: { width: "100%" }, placeholder: fieldMeta.placeholder, value: typeof value === "number" ? value : undefined, onChange: (val) => onFieldChange(stepIndex, fieldMeta.name, val ?? undefined) }) }));
    }
    return (_jsx(Form.Item, { ...commonProps, children: _jsx(Input, { placeholder: fieldMeta.placeholder, value: typeof value === "string" ? value : "", onChange: (e) => onFieldChange(stepIndex, fieldMeta.name, e.target.value) }) }));
});
// Sortable Step Item Component
const SortableStepItem = memo(({ step, index, onDelete, onDuplicate, onTypeChange, onFieldChange, }) => {
    const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
        id: step._id,
    });
    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.4 : 1,
        marginBottom: 16,
        position: "relative",
        zIndex: isDragging ? 999 : 1,
    };
    const definition = DSL_SCHEMA[step.type];
    return (_jsx("div", { ref: setNodeRef, style: style, children: _jsx(Card, { size: "small", style: {
                borderRadius: 12,
                boxShadow: isDragging
                    ? "0 8px 24px rgba(0,0,0,0.15)"
                    : "0 1px 4px rgba(15, 23, 42, 0.08)",
                border: isDragging ? "1px solid var(--primary-color)" : undefined,
            }, title: _jsxs(Space, { size: "small", align: "center", children: [_jsx(Tooltip, { title: "\u62D6\u62FD\u6392\u5E8F", children: _jsx("div", { ...attributes, ...listeners, style: { cursor: "grab", display: "flex", alignItems: "center", touchAction: "none" }, children: _jsx(HolderOutlined, { style: { color: "var(--text-secondary)", fontSize: 16 } }) }) }), _jsxs(Text, { strong: true, children: ["\u6B65\u9AA4 ", index + 1] }), _jsx(Select, { value: step.type, bordered: false, style: { width: 160 }, onChange: (nextType) => onTypeChange(index, nextType), options: STEP_TYPE_OPTIONS, onMouseDown: (e) => e.stopPropagation() })] }), extra: _jsxs(Space, { size: "small", children: [_jsx(Tooltip, { title: "\u590D\u5236\u6B65\u9AA4", children: _jsx(Button, { icon: _jsx(CopyOutlined, {}), size: "small", onClick: () => onDuplicate(index) }) }), _jsx(Tooltip, { title: "\u5220\u9664\u6B65\u9AA4", children: _jsx(Button, { icon: _jsx(DeleteOutlined, {}), size: "small", danger: true, onClick: () => onDelete(index) }) })] }), children: _jsx(Space, { direction: "vertical", style: { width: "100%" }, size: "small", children: definition.fields.map((field) => (_jsx(StepField, { step: step, stepIndex: index, fieldMeta: field, onFieldChange: onFieldChange }, field.name))) }) }) }));
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
                steps: prev.steps.map(({ _id, ...rest }) => rest),
            };
            // Deep comparison (simple JSON stringify is sufficient for this DSL)
            if (JSON.stringify(normalized) === JSON.stringify(currentClean)) {
                return prev;
            }
            // If different, we need to update.
            // Try to preserve IDs if possible to avoid re-renders of unchanged items
            const newSteps = normalized.steps.map((step, index) => {
                const prevStep = prev.steps[index];
                // If a step exists at this index and has the same type, reuse its ID
                // This is a heuristic; for complex reorders external to this component it might be imperfect,
                // but for standard usage it preserves stability.
                if (prevStep && prevStep.type === step.type) {
                    return { ...step, _id: prevStep._id };
                }
                return { ...step, _id: generateId() };
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
                // Create clean DSL for validation/onChange
                const cleanDsl = {
                    ...prev,
                    steps: nextSteps.map(({ _id, ...rest }) => rest),
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
            // Strip IDs before sending to parent
            const cleanDsl = {
                ...next,
                steps: next.steps.map(({ _id, ...rest }) => rest),
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
            const clone = { ...steps[index], _id: generateId() };
            steps.splice(index + 1, 0, clone);
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
    return (_jsxs(Space, { direction: "vertical", style: { width: "100%" }, size: "middle", children: [_jsx(DndContext, { sensors: sensors, collisionDetection: closestCenter, onDragEnd: handleDragEnd, children: _jsx(SortableContext, { items: dsl.steps.map((s) => s._id), strategy: verticalListSortingStrategy, children: dsl.steps.length === 0 ? (_jsx(Empty, { description: "\u6682\u65E0\u6B65\u9AA4\uFF0C\u5148\u6DFB\u52A0\u4E00\u4E2A\u64CD\u4F5C\u5427", image: Empty.PRESENTED_IMAGE_SIMPLE })) : (_jsx(AnimatePresence, { mode: "popLayout", initial: false, children: dsl.steps.map((step, index) => (_jsx(SortableStepItem, { step: step, index: index, onDelete: handleDeleteStep, onDuplicate: handleDuplicate, onTypeChange: handleTypeChange, onFieldChange: handleFieldChange }, step._id))) })) }) }), _jsx(Card, { size: "small", style: {
                    borderRadius: 12,
                    background: "rgba(15,23,42,0.02)",
                    borderStyle: "dashed",
                }, children: _jsxs(Space, { wrap: true, children: [_jsx(Select, { value: addType, style: { minWidth: 220 }, options: STEP_TYPE_OPTIONS, onChange: (value) => setAddType(value), placeholder: "\u9009\u62E9\u4E00\u4E2A\u64CD\u4F5C\u7C7B\u578B" }), _jsx(Button, { type: "dashed", icon: _jsx(PlusOutlined, {}), onClick: () => handleAddStep(addType), children: "\u6DFB\u52A0\u6B65\u9AA4" })] }) }), _jsx(Text, { type: validation.errors.length > 0 ? "danger" : "secondary", children: readableSummary })] }));
});
VisualDslEditor.displayName = "VisualDslEditor";
export default VisualDslEditor;
