/**
 * Workflow Editor Type Definitions
 */
import type { Node, Edge } from "@xyflow/react";

// Node data for all workflow nodes
export interface StepNodeData extends Record<string, unknown> {
  stepType: string;
  label: string;
  description?: string;
  fields: Record<string, unknown>;
  // For container nodes (stored as serializable data, not recursive nodes)
  childrenData?: StepNodeData[];
  elseChildrenData?: StepNodeData[];
  // UI state
  expanded?: boolean;
  // Container group info
  isContainer?: boolean;
  containerType?: "loop" | "loop_array" | "if_else";
  branch?: "then" | "else"; // For child nodes in if_else
}

// Workflow node with our custom data (includes parentId and extent from React Flow)
export type WorkflowNode = Node<StepNodeData>;

// Workflow edge
export type WorkflowEdge = Edge;

// Node types enum
export type WorkflowNodeType = 
  | "stepNode" 
  | "loopNode" 
  | "conditionNode" 
  | "startNode" 
  | "endNode"
  | "containerGroup";
