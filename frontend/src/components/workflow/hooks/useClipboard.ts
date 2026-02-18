/**
 * useClipboard - Copy/Paste functionality for workflow nodes
 */
import { useState, useCallback, useRef } from "react";
import { message } from "antd";
import type { WorkflowNode, WorkflowEdge } from "../types";
import { START_NODE_ID } from "../nodes";

interface ClipboardData {
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
}

interface UseClipboardOptions {
  screenToFlowPosition: (position: { x: number; y: number }) => { x: number; y: number };
}

interface UseClipboardReturn {
  clipboard: ClipboardData | null;
  mousePositionRef: React.MutableRefObject<{ x: number; y: number }>;
  copyNodes: (
    nodes: WorkflowNode[],
    edges: WorkflowEdge[],
    selectedNodeIds: string[],
    selectedNode: WorkflowNode | null
  ) => void;
  pasteNodes: () => { nodes: WorkflowNode[]; edges: WorkflowEdge[] } | null;
  updateMousePosition: (x: number, y: number) => void;
}

/**
 * Generate unique ID for pasted nodes
 */
const generatePasteId = () => 
  `node_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

/**
 * Hook for managing clipboard operations
 */
export function useClipboard(options: UseClipboardOptions): UseClipboardReturn {
  const { screenToFlowPosition } = options;
  
  const [clipboard, setClipboard] = useState<ClipboardData | null>(null);
  const mousePositionRef = useRef({ x: 0, y: 0 });

  // Update mouse position (call from onMouseMove)
  const updateMousePosition = useCallback((x: number, y: number) => {
    mousePositionRef.current = { x, y };
  }, []);

  // Copy selected nodes
  const copyNodes = useCallback((
    nodes: WorkflowNode[],
    edges: WorkflowEdge[],
    selectedNodeIds: string[],
    selectedNode: WorkflowNode | null
  ) => {
    // Get nodes to copy (multi-select or single select)
    const nodesToCopy = selectedNodeIds.length > 0
      ? nodes.filter(n => selectedNodeIds.includes(n.id) && n.id !== START_NODE_ID)
      : selectedNode && selectedNode.id !== START_NODE_ID
        ? [selectedNode]
        : [];
    
    if (nodesToCopy.length === 0) return;
    
    // Get edges between selected nodes only
    const nodeIdSet = new Set(nodesToCopy.map(n => n.id));
    const edgesToCopy = edges.filter(
      e => nodeIdSet.has(e.source) && nodeIdSet.has(e.target)
    );
    
    setClipboard({ nodes: nodesToCopy, edges: edgesToCopy });
    message.success(`已复制 ${nodesToCopy.length} 个节点`);
  }, []);

  // Paste nodes at current mouse position
  const pasteNodes = useCallback((): { nodes: WorkflowNode[]; edges: WorkflowEdge[] } | null => {
    if (!clipboard || clipboard.nodes.length === 0) return null;
    
    // Convert screen position to flow position
    const flowPosition = screenToFlowPosition({
      x: mousePositionRef.current.x,
      y: mousePositionRef.current.y,
    });
    
    // Calculate offset from the first node's position
    const firstNode = clipboard.nodes[0];
    const offsetX = flowPosition.x - firstNode.position.x;
    const offsetY = flowPosition.y - firstNode.position.y;
    
    // Create ID mapping for new nodes
    const idMapping = new Map<string, string>();
    const newNodes: WorkflowNode[] = [];
    
    clipboard.nodes.forEach(node => {
      const newId = generatePasteId();
      idMapping.set(node.id, newId);
      
      newNodes.push({
        ...node,
        id: newId,
        position: {
          x: node.position.x + offsetX,
          y: node.position.y + offsetY,
        },
        selected: false,
      });
    });
    
    // Create new edges with updated IDs
    const newEdges: WorkflowEdge[] = clipboard.edges.map(edge => ({
      ...edge,
      id: `edge_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      source: idMapping.get(edge.source) || edge.source,
      target: idMapping.get(edge.target) || edge.target,
    }));
    
    message.success(`已粘贴 ${newNodes.length} 个节点`);
    return { nodes: newNodes, edges: newEdges };
  }, [clipboard, screenToFlowPosition]);

  return {
    clipboard,
    mousePositionRef,
    copyNodes,
    pasteNodes,
    updateMousePosition,
  };
}

export default useClipboard;
