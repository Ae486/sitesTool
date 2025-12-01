/**
 * Convert React Flow nodes and edges back to FlowDSL
 * Uses sourceHandle-based edge detection to identify children (body/then/else)
 */
import type { FlowDSL } from "../../../types/flow";
import type { WorkflowNode, WorkflowEdge } from "../types";
/**
 * Main conversion function: Flow → DSL
 */
export declare function flowToDsl(nodes: WorkflowNode[], edges: WorkflowEdge[]): FlowDSL;
