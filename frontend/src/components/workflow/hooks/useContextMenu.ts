/**
 * useContextMenu - Right-click context menu management
 */
import { useState, useCallback, useMemo } from "react";
import { message } from "antd";
import type { MenuProps } from "antd";
import type { WorkflowNode, WorkflowEdge } from "../types";
import { START_NODE_ID } from "../nodes";

interface ContextMenuState {
  x: number;
  y: number;
  nodeId?: string;
  edgeId?: string;
}

interface UseContextMenuOptions {
  nodes: WorkflowNode[];
  onDeleteNode: (nodeId: string) => void;
  onDeleteEdge: (edgeId: string) => void;
  onCopyNode: (node: WorkflowNode) => void;
}

interface UseContextMenuReturn {
  contextMenu: ContextMenuState | null;
  contextMenuItems: MenuProps["items"];
  onNodeContextMenu: (event: React.MouseEvent, node: WorkflowNode) => void;
  onEdgeContextMenu: (event: React.MouseEvent, edge: WorkflowEdge) => void;
  onPaneContextMenu: (event: MouseEvent | React.MouseEvent) => void;
  closeContextMenu: () => void;
}

/**
 * Hook for managing right-click context menus
 */
export function useContextMenu(options: UseContextMenuOptions): UseContextMenuReturn {
  const { nodes, onDeleteNode, onDeleteEdge, onCopyNode } = options;
  
  const [contextMenu, setContextMenu] = useState<ContextMenuState | null>(null);

  // Close context menu
  const closeContextMenu = useCallback(() => {
    setContextMenu(null);
  }, []);

  // Right-click on node
  const onNodeContextMenu = useCallback((event: React.MouseEvent, node: WorkflowNode) => {
    event.preventDefault();
    if (node.id === START_NODE_ID) return;
    setContextMenu({ x: event.clientX, y: event.clientY, nodeId: node.id });
  }, []);

  // Right-click on edge
  const onEdgeContextMenu = useCallback((event: React.MouseEvent, edge: WorkflowEdge) => {
    event.preventDefault();
    setContextMenu({ x: event.clientX, y: event.clientY, edgeId: edge.id });
  }, []);

  // Right-click on pane (close menu)
  const onPaneContextMenu = useCallback((event: MouseEvent | React.MouseEvent) => {
    event.preventDefault();
    setContextMenu(null);
  }, []);

  // Context menu items
  const contextMenuItems: MenuProps["items"] = useMemo(() => {
    if (!contextMenu) return [];
    
    if (contextMenu.edgeId) {
      return [
        { 
          key: "delete", 
          label: "删除连线", 
          danger: true, 
          onClick: () => {
            onDeleteEdge(contextMenu.edgeId!);
            setContextMenu(null);
          }
        },
      ];
    }
    
    if (contextMenu.nodeId) {
      const node = nodes.find(n => n.id === contextMenu.nodeId);
      return [
        { 
          key: "copy", 
          label: "复制", 
          onClick: () => {
            if (node) {
              onCopyNode(node);
            }
            setContextMenu(null);
          }
        },
        { 
          key: "delete", 
          label: "删除", 
          danger: true, 
          onClick: () => {
            onDeleteNode(contextMenu.nodeId!);
            setContextMenu(null);
          }
        },
      ];
    }
    
    return [];
  }, [contextMenu, nodes, onDeleteNode, onDeleteEdge, onCopyNode]);

  return {
    contextMenu,
    contextMenuItems,
    onNodeContextMenu,
    onEdgeContextMenu,
    onPaneContextMenu,
    closeContextMenu,
  };
}

export default useContextMenu;
