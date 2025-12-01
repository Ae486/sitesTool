/**
 * Convert FlowDSL to React Flow nodes and edges
 * Uses sourceHandle-based connections for container children (body/then/else/next)
 */
import type { FlowDSL } from "../../../types/flow";
import type { WorkflowNode, WorkflowEdge } from "../types";
/**
 * Main conversion function: DSL → Flow
 */
export declare function dslToFlow(dsl: FlowDSL, options?: {
    applyLayout?: boolean;
}): {
    nodes: WorkflowNode[];
    edges: WorkflowEdge[];
};
/**
 * Re-layout existing nodes
 */
export declare function relayoutFlow(nodes: WorkflowNode[], edges: WorkflowEdge[]): WorkflowNode[];
