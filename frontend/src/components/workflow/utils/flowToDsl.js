import { START_NODE_ID, isContainerStepType } from "../nodes";
// Handle types that indicate child relationships
const CHILD_HANDLES = ["body", "then", "else"];
/**
 * Build adjacency maps based on sourceHandle
 */
function buildAdjacencyMaps(nodes, edges) {
    const nodeMap = new Map(nodes.map(n => [n.id, n]));
    // Map: sourceId -> { handle -> targetIds[] }
    const handleTargets = new Map();
    // Map: sourceId -> next targets (normal flow, using 'next' handle or no handle)
    const nextTargets = new Map();
    nodes.forEach(n => {
        handleTargets.set(n.id, new Map());
        nextTargets.set(n.id, []);
    });
    edges.forEach(e => {
        const handle = e.sourceHandle || "default";
        if (CHILD_HANDLES.includes(handle)) {
            // Child relationship
            const handles = handleTargets.get(e.source);
            if (handles) {
                const targets = handles.get(handle) || [];
                targets.push(e.target);
                handles.set(handle, targets);
            }
        }
        else {
            // Normal flow (next, default, bottom, right, etc.)
            nextTargets.get(e.source)?.push(e.target);
        }
    });
    return { nodeMap, handleTargets, nextTargets };
}
/**
 * Recursively collect children from a container node
 */
function collectChildren(startNodeId, nodeMap, handleTargets, nextTargets, visited) {
    const result = [];
    const queue = [startNodeId];
    while (queue.length > 0) {
        const nodeId = queue.shift();
        if (visited.has(nodeId))
            continue;
        visited.add(nodeId);
        const node = nodeMap.get(nodeId);
        if (!node)
            continue;
        result.push(node);
        // Follow 'next' handle or default connections within the branch
        const nexts = nextTargets.get(nodeId) || [];
        nexts.forEach(nextId => {
            if (!visited.has(nextId)) {
                queue.push(nextId);
            }
        });
    }
    return result;
}
/**
 * Convert a node to DSL step, recursively handling children
 */
function nodeToStep(node, nodeMap, handleTargets, nextTargets, globalVisited) {
    const { data } = node;
    const step = {
        type: data.stepType,
        ...data.fields,
    };
    if (data.description) {
        step.description = data.description;
    }
    // For container nodes, collect children based on handles
    if (isContainerStepType(data.stepType)) {
        const handles = handleTargets.get(node.id);
        if (handles) {
            // Get 'body' or 'then' children
            const bodyHandle = data.stepType === "if_else" ? "then" : "body";
            const bodyStarts = handles.get(bodyHandle) || [];
            if (bodyStarts.length > 0) {
                const childVisited = new Set();
                const childNodes = bodyStarts.flatMap(startId => collectChildren(startId, nodeMap, handleTargets, nextTargets, childVisited));
                // Mark as globally visited to prevent re-processing
                childNodes.forEach(n => globalVisited.add(n.id));
                step.children = childNodes.map(childNode => nodeToStep(childNode, nodeMap, handleTargets, nextTargets, globalVisited));
            }
            // Get 'else' children for if_else
            if (data.stepType === "if_else") {
                const elseStarts = handles.get("else") || [];
                if (elseStarts.length > 0) {
                    const elseVisited = new Set();
                    const elseNodes = elseStarts.flatMap(startId => collectChildren(startId, nodeMap, handleTargets, nextTargets, elseVisited));
                    elseNodes.forEach(n => globalVisited.add(n.id));
                    step.else_children = elseNodes.map(elseNode => nodeToStep(elseNode, nodeMap, handleTargets, nextTargets, globalVisited));
                }
            }
        }
    }
    return step;
}
/**
 * BFS from start node to get main flow order (excluding child branches)
 */
function getMainFlowOrder(nodes, edges, globalVisited) {
    const nodeMap = new Map(nodes.map(n => [n.id, n]));
    // Build next-only adjacency (skip child handles)
    const nextAdj = new Map();
    nodes.forEach(n => nextAdj.set(n.id, []));
    edges.forEach(e => {
        const handle = e.sourceHandle || "default";
        if (!CHILD_HANDLES.includes(handle)) {
            nextAdj.get(e.source)?.push(e.target);
        }
    });
    // BFS from start
    const result = [];
    const visited = new Set();
    const queue = [START_NODE_ID];
    visited.add(START_NODE_ID);
    while (queue.length > 0) {
        const current = queue.shift();
        const node = nodeMap.get(current);
        if (node && current !== START_NODE_ID && !globalVisited.has(current)) {
            result.push(node);
            globalVisited.add(current);
        }
        nextAdj.get(current)?.forEach(neighbor => {
            if (!visited.has(neighbor)) {
                visited.add(neighbor);
                queue.push(neighbor);
            }
        });
    }
    return result;
}
/**
 * Main conversion function: Flow → DSL
 */
export function flowToDsl(nodes, edges) {
    if (nodes.length === 0) {
        return { version: 1, steps: [] };
    }
    const { nodeMap, handleTargets, nextTargets } = buildAdjacencyMaps(nodes, edges);
    const globalVisited = new Set();
    // Get main flow nodes in order
    const mainFlowNodes = getMainFlowOrder(nodes, edges, globalVisited);
    // Convert to steps
    const steps = mainFlowNodes
        .filter(node => node.data.stepType !== "__start__")
        .map(node => nodeToStep(node, nodeMap, handleTargets, nextTargets, globalVisited));
    return {
        version: 1,
        steps,
    };
}
