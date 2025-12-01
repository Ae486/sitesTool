/**
 * Convert FlowDSL to React Flow nodes and edges
 * Uses sourceHandle-based connections for container children (body/then/else/next)
 */
import type { FlowDSL, FlowStep } from "../../../types/flow";
import type { WorkflowNode, WorkflowEdge, StepNodeData } from "../types";
import { stepTypeToNodeType, isContainerStepType } from "../nodes";
import { DSL_SCHEMA } from "../../../constants/dsl";
import dagre from "dagre";

let nodeIdCounter = 0;

const generateNodeId = () => `node_${++nodeIdCounter}`;

/**
 * Convert a single DSL step to workflow node data
 */
function stepToNodeData(step: FlowStep): StepNodeData {
  const schema = DSL_SCHEMA[step.type];
  
  // Extract fields (everything except type, children, else_children, description)
  const fields: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(step)) {
    if (!["type", "children", "else_children", "description"].includes(key)) {
      fields[key] = value;
    }
  }
  
  // Store children data for display purposes (actual children are separate nodes)
  const childrenData = step.children?.map(c => stepToNodeData(c));
  const elseChildrenData = step.else_children?.map(c => stepToNodeData(c));
  
  return {
    stepType: step.type,
    label: schema?.label || step.type,
    description: (step as { description?: string }).description,
    fields,
    childrenData,
    elseChildrenData,
  };
}

/**
 * Apply dagre layout to nodes with branch ordering fix
 */
function applyLayout(
  nodes: WorkflowNode[], 
  edges: WorkflowEdge[],
  direction: "TB" | "LR" = "TB"
): WorkflowNode[] {
  const g = new dagre.graphlib.Graph();
  // Shorter vertical spacing (ranksep: 55)
  g.setGraph({ rankdir: direction, nodesep: 40, ranksep: 55 });
  g.setDefaultEdgeLabel(() => ({}));
  
  // Add nodes to graph
  nodes.forEach((node) => {
    g.setNode(node.id, { width: 220, height: 100 });
  });
  
  // Build a map of sourceHandle -> target for branch ordering
  const branchTargets = new Map<string, { then?: string; next?: string; else?: string; body?: string }>();
  
  // Add edges to graph with proper ordering
  edges.forEach((edge) => {
    const handle = edge.sourceHandle || "default";
    
    // Track branch targets for position adjustment
    if (["then", "next", "else", "body"].includes(handle)) {
      const existing = branchTargets.get(edge.source) || {};
      existing[handle as "then" | "next" | "else" | "body"] = edge.target;
      branchTargets.set(edge.source, existing);
    }
    
    g.setEdge(edge.source, edge.target);
  });
  
  // Run layout
  dagre.layout(g);
  
  // Apply positions with branch ordering correction
  const layoutedNodes = nodes.map((node) => {
    const nodeWithPosition = g.node(node.id);
    return {
      ...node,
      position: {
        x: nodeWithPosition.x - 110,
        y: nodeWithPosition.y - 50,
      },
    };
  });
  
  // Fix branch ordering: ensure then is left, next is center, else is right
  branchTargets.forEach((targets, sourceId) => {
    const sourceNode = layoutedNodes.find(n => n.id === sourceId);
    if (!sourceNode) return;
    
    const thenNode = targets.then ? layoutedNodes.find(n => n.id === targets.then) : null;
    const nextNode = targets.next ? layoutedNodes.find(n => n.id === targets.next) : null;
    const elseNode = targets.else ? layoutedNodes.find(n => n.id === targets.else) : null;
    const bodyNode = targets.body ? layoutedNodes.find(n => n.id === targets.body) : null;
    
    // For condition nodes (then/next/else)
    if (thenNode && nextNode && elseNode) {
      const centerX = sourceNode.position.x;
      const spacing = 250;
      
      // Sort by current X to detect if reordering is needed
      const branchNodes = [thenNode, nextNode, elseNode].sort((a, b) => a.position.x - b.position.x);
      
      // Assign correct positions: then(left), next(center), else(right)
      thenNode.position.x = centerX - spacing;
      nextNode.position.x = centerX;
      elseNode.position.x = centerX + spacing;
    } else if (thenNode && elseNode) {
      // Only then and else (no next)
      const centerX = sourceNode.position.x;
      const spacing = 200;
      thenNode.position.x = centerX - spacing / 2;
      elseNode.position.x = centerX + spacing / 2;
    } else if (bodyNode && nextNode) {
      // Loop node: body left, next right
      const centerX = sourceNode.position.x;
      const spacing = 200;
      bodyNode.position.x = centerX - spacing / 2;
      nextNode.position.x = centerX + spacing / 2;
    }
  });
  
  return layoutedNodes;
}

/**
 * Recursively convert steps to nodes and edges
 * Returns { nodes, edges, lastNodeId } for chaining
 */
function convertStepsToFlow(
  steps: FlowStep[],
  startX: number,
  startY: number,
  prevNodeId: string | null,
  sourceHandle: string | null,
  depth: number = 0
): { nodes: WorkflowNode[]; edges: WorkflowEdge[]; lastNodeId: string | null } {
  const nodes: WorkflowNode[] = [];
  const edges: WorkflowEdge[] = [];
  let currentPrevId = prevNodeId;
  let currentSourceHandle = sourceHandle;
  let currentY = startY;
  
  for (let i = 0; i < steps.length; i++) {
    const step = steps[i];
    const nodeId = generateNodeId();
    const nodeType = stepTypeToNodeType(step.type);
    const isContainer = isContainerStepType(step.type);
    
    // Create the node
    const node: WorkflowNode = {
      id: nodeId,
      type: nodeType,
      position: { x: startX, y: currentY },
      data: stepToNodeData(step),
    };
    nodes.push(node);
    
    // Create edge from previous node
    if (currentPrevId) {
      edges.push({
        id: `edge_${currentPrevId}_${nodeId}`,
        source: currentPrevId,
        sourceHandle: currentSourceHandle || undefined,
        target: nodeId,
        type: "deletable",
        style: { strokeWidth: 2 },
      });
    }
    
    // Handle container children
    if (isContainer && depth < 4) {
      // Process children (connected via 'body' or 'then' handle)
      if (step.children && step.children.length > 0) {
        const childHandle = step.type === "if_else" ? "then" : "body";
        const childResult = convertStepsToFlow(
          step.children,
          startX + 250, // Offset to the right
          currentY,
          nodeId,
          childHandle,
          depth + 1
        );
        nodes.push(...childResult.nodes);
        edges.push(...childResult.edges);
      }
      
      // Process else_children for if_else (connected via 'else' handle)
      if (step.type === "if_else" && step.else_children && step.else_children.length > 0) {
        const elseResult = convertStepsToFlow(
          step.else_children,
          startX + 250,
          currentY + 200, // Offset down for else branch
          nodeId,
          "else",
          depth + 1
        );
        nodes.push(...elseResult.nodes);
        edges.push(...elseResult.edges);
      }
    }
    
    // Next iteration connects via 'next' handle for containers, or normal connection
    currentPrevId = nodeId;
    currentSourceHandle = isContainer ? "next" : null;
    currentY += 150;
  }
  
  return { nodes, edges, lastNodeId: currentPrevId };
}

/**
 * Main conversion function: DSL → Flow
 */
export function dslToFlow(
  dsl: FlowDSL, 
  options: { applyLayout?: boolean } = { applyLayout: true }
): { nodes: WorkflowNode[]; edges: WorkflowEdge[] } {
  nodeIdCounter = 0;
  
  if (!dsl.steps || dsl.steps.length === 0) {
    return { nodes: [], edges: [] };
  }
  
  // Convert all steps
  const result = convertStepsToFlow(dsl.steps, 200, 50, null, null, 0);
  
  // Apply automatic layout if requested
  if (options.applyLayout && result.nodes.length > 0) {
    const layoutedNodes = applyLayout(result.nodes, result.edges);
    return { nodes: layoutedNodes, edges: result.edges };
  }
  
  return { nodes: result.nodes, edges: result.edges };
}

/**
 * Re-layout existing nodes
 */
export function relayoutFlow(
  nodes: WorkflowNode[], 
  edges: WorkflowEdge[]
): WorkflowNode[] {
  return applyLayout(nodes, edges);
}
