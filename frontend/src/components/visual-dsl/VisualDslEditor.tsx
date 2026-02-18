/**
 * VisualDslEditor - Main component for visual DSL editing
 * Refactored from the original 1114-line monolithic component
 */
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useState } from "react";
import { PlusOutlined } from "@ant-design/icons";
import {
  DndContext,
  DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { Button, Card, Empty, Select, Space, Typography } from "antd";
import { useDebounceFn } from "ahooks";
import { AnimatePresence } from "framer-motion";
import { STEP_TYPE_OPTIONS } from "../../constants/dsl";
import type { FlowDSL } from "../../types/flow";
import { DslValidationResult, validateDslStructure } from "../../utils/dsl";
import type { SortableFlowStep, StepKey, VisualDslEditorProps, VisualDslEditorRef } from "./types";
import {
  DEFAULT_STEP_TYPE,
  addIdsToStep,
  createEmptyStep,
  ensureDslShape,
  ensureDslWithIds,
  generateId,
  hasValue,
  stripIdsFromStep,
} from "./utils";
import SortableStepItem from "./SortableStepItem";

const { Text } = Typography;

/**
 * VisualDslEditor - Visual editor for automation DSL
 */
const VisualDslEditor = forwardRef<VisualDslEditorRef, VisualDslEditorProps>(
  ({ value, onChange, siteUrl, onValidationChange }, ref) => {
    // Initialize state with IDs
    const [dsl, setDsl] = useState<{ version: number; steps: SortableFlowStep[] }>(() =>
      ensureDslWithIds(ensureDslShape(value)),
    );
    const [addType, setAddType] = useState<StepKey>(DEFAULT_STEP_TYPE);
    const [validation, setValidation] = useState<DslValidationResult>(() =>
      validateDslStructure(ensureDslShape(value)),
    );

    // Dnd Sensors
    const sensors = useSensors(
      useSensor(PointerSensor, {
        activationConstraint: {
          distance: 5, // Prevent accidental drags
        },
      }),
      useSensor(KeyboardSensor, {
        coordinateGetter: sortableKeyboardCoordinates,
      }),
    );

    // Debounced onChange callback (300ms)
    const { run: notifyParentChange, flush: flushOnChange } = useDebounceFn(
      (cleanDsl: FlowDSL) => {
        onChange?.(cleanDsl);
      },
      { wait: 300 },
    );

    // Debounced validation callback (500ms)
    const { run: notifyValidationChange, flush: flushValidation } = useDebounceFn(
      (result: DslValidationResult) => {
        onValidationChange?.(result);
      },
      { wait: 500 },
    );

    // Update local validation state immediately, notify parent with delay
    const emitValidation = useCallback(
      (result: DslValidationResult) => {
        setValidation(result);
        notifyValidationChange(result);
      },
      [notifyValidationChange],
    );

    // Sync with external value changes
    useEffect(() => {
      const normalized = ensureDslShape(value);

      setDsl((prev) => {
        const currentClean = {
          ...prev,
          steps: prev.steps.map(stripIdsFromStep),
        };

        if (JSON.stringify(normalized) === JSON.stringify(currentClean)) {
          return prev;
        }

        const newSteps = normalized.steps.map((step, index) => {
          const prevStep = prev.steps[index];
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
      if (!siteUrl) return;
      setDsl((prev) => {
        if (prev.steps.length === 0) {
          const nextStep = createEmptyStep("navigate", siteUrl, true);
          const nextDsl = { ...prev, steps: [nextStep] };
          emitValidation(validateDslStructure(nextDsl));
          onChange?.(nextDsl);
          return { ...prev, steps: [nextStep] };
        }

        const firstStep = prev.steps[0];
        if (firstStep.type === "navigate" && !hasValue(firstStep.url)) {
          const nextSteps = [...prev.steps];
          nextSteps[0] = { ...firstStep, url: siteUrl };

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

    const applyDslUpdate = useCallback(
      (updater: (prev: { version: number; steps: SortableFlowStep[] }) => { version: number; steps: SortableFlowStep[] }) => {
        setDsl((prev) => {
          const next = updater(prev);
          if (next === prev) return prev;

          const cleanDsl: FlowDSL = {
            ...next,
            steps: next.steps.map(stripIdsFromStep),
          };

          emitValidation(validateDslStructure(cleanDsl));
          notifyParentChange(cleanDsl);
          return next;
        });
      },
      [emitValidation, notifyParentChange],
    );

    const handleAddStep = (type: StepKey) => {
      applyDslUpdate((current) => ({
        ...current,
        steps: [...current.steps, createEmptyStep(type, siteUrl, current.steps.length === 0)],
      }));
    };

    const handleDeleteStep = useCallback((index: number) => {
      applyDslUpdate((current) => ({
        ...current,
        steps: current.steps.filter((_, i) => i !== index),
      }));
    }, [applyDslUpdate]);

    const handleFieldChange = useCallback((index: number, field: string, val: unknown) => {
      applyDslUpdate((current) => {
        const steps = [...current.steps];
        steps[index] = { ...steps[index], [field]: val };
        return { ...current, steps };
      });
    }, [applyDslUpdate]);

    const handleTypeChange = useCallback((index: number, type: StepKey) => {
      applyDslUpdate((current) => {
        const steps = [...current.steps];
        const newStep = createEmptyStep(type, siteUrl, index === 0 && current.steps.length === 1);
        newStep._id = steps[index]._id;
        steps[index] = newStep;
        return { ...current, steps };
      });
    }, [applyDslUpdate, siteUrl]);

    const handleDuplicate = useCallback((index: number) => {
      applyDslUpdate((current) => {
        const steps = [...current.steps];
        const clone = JSON.parse(JSON.stringify(steps[index]));
        clone._id = generateId();
        const assignNewIds = (step: SortableFlowStep) => {
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

    const handleChildrenChange = useCallback((index: number, children: SortableFlowStep[], branch?: "else") => {
      applyDslUpdate((current) => {
        const steps = [...current.steps];
        if (branch === "else") {
          steps[index] = { ...steps[index], else_children: children };
        } else {
          steps[index] = { ...steps[index], children };
        }
        return { ...current, steps };
      });
    }, [applyDslUpdate]);

    const handleDragEnd = (event: DragEndEvent) => {
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

    // Expose flush method for parent component
    useImperativeHandle(
      ref,
      () => ({
        flush: () => {
          flushOnChange();
          flushValidation();
        },
      }),
      [flushOnChange, flushValidation],
    );

    const readableSummary = useMemo(() => {
      if (validation.errors.length > 0) {
        return `有 ${validation.errors.length} 个待修复问题，请检查红色标记字段。`;
      }
      return `DSL 校验通过，当前共 ${dsl.steps.length} 个步骤。`;
    }, [validation.errors.length, dsl.steps.length]);

    return (
      <Space direction="vertical" style={{ width: "100%" }} size="middle">
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
        >
          <SortableContext
            items={dsl.steps.map((s) => s._id)}
            strategy={verticalListSortingStrategy}
          >
            {dsl.steps.length === 0 ? (
              <Empty description="暂无步骤，先添加一个操作吧" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <AnimatePresence mode="popLayout" initial={false}>
                {dsl.steps.map((step, index) => (
                  <SortableStepItem
                    key={step._id}
                    step={step}
                    index={index}
                    onDelete={handleDeleteStep}
                    onDuplicate={handleDuplicate}
                    onTypeChange={handleTypeChange}
                    onFieldChange={handleFieldChange}
                    onChildrenChange={handleChildrenChange}
                  />
                ))}
              </AnimatePresence>
            )}
          </SortableContext>
        </DndContext>

        <Card
          size="small"
          style={{
            borderRadius: 12,
            background: "rgba(15,23,42,0.02)",
            borderStyle: "dashed",
          }}
        >
          <Space wrap>
            <Select
              value={addType}
              style={{ minWidth: 220 }}
              options={STEP_TYPE_OPTIONS}
              onChange={(value) => setAddType(value as StepKey)}
              placeholder="选择一个操作类型"
            />
            <Button type="dashed" icon={<PlusOutlined />} onClick={() => handleAddStep(addType)}>
              添加步骤
            </Button>
          </Space>
        </Card>

        <Text type={validation.errors.length > 0 ? "danger" : "secondary"}>{readableSummary}</Text>
      </Space>
    );
  }
);

VisualDslEditor.displayName = "VisualDslEditor";

export default VisualDslEditor;
