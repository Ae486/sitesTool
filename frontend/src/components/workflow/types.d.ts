/**
 * Workflow Editor Type Definitions
 */
import type { Node, Edge } from "@xyflow/react";
export interface StepNodeData extends Record<string, unknown> {
    stepType: string;
    label: string;
    description?: string;
    fields: Record<string, unknown>;
    childrenData?: StepNodeData[];
    elseChildrenData?: StepNodeData[];
    expanded?: boolean;
    isContainer?: boolean;
    containerType?: "loop" | "loop_array" | "if_else";
    branch?: "then" | "else";
}
export type WorkflowNode = Node<StepNodeData>;
export type WorkflowEdge = Edge;
export type WorkflowNodeType = "stepNode" | "loopNode" | "conditionNode" | "startNode" | "endNode" | "containerGroup";
