/**
 * Workflow Editor - Node-based visual DSL editor
 * Features: Drag & drop, connection validation, keyboard shortcuts, debounced saves
 * Ghost node preview for click-to-place interaction
 */
import { useCallback, useEffect, useRef, useState, useMemo } from "react";
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Panel,
  useNodesState,
  useEdgesState,
  addEdge,
  BackgroundVariant,
  useReactFlow,
  ReactFlowProvider,
  type Connection,
  type OnConnect,
  type NodeChange,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Button, Space, Tooltip, Segmented, message, Empty, Dropdown } from "antd";
import type { MenuProps } from "antd";
import { 
  ReloadOutlined, 
  AppstoreOutlined,
  UnorderedListOutlined,
  DeleteOutlined,
  CopyOutlined,
  UndoOutlined,
  RedoOutlined,
} from "@ant-design/icons";
import { useDebounceFn } from "ahooks";
import type { FlowDSL } from "../../types/flow";
import type { WorkflowNode, WorkflowEdge, StepNodeData } from "./types";
import { nodeTypes, stepTypeToNodeType, START_NODE_ID } from "./nodes";
import { edgeTypes } from "./edges";
import { dslToFlow, relayoutFlow } from "./utils/dslToFlow";
import { flowToDsl } from "./utils/flowToDsl";
import { DSL_SCHEMA } from "../../constants/dsl";
import NodePalette from "./panels/NodePalette";
import NodeProperties from "./panels/NodeProperties";
import GhostNode from "./nodes/GhostNode";

interface WorkflowEditorProps {
  value?: FlowDSL;
  onChange?: (dsl: FlowDSL) => void;
  onModeChange?: (mode: "workflow" | "list") => void;
  siteUrl?: string;
  readOnly?: boolean;
}

// Clipboard data structure for copy/paste
interface ClipboardData {
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
}

// History state for undo/redo
interface HistoryState {
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
}

// Unique ID generator using crypto for better uniqueness
const generateId = () => 
  typeof crypto !== "undefined" && crypto.randomUUID
    ? `wf_${crypto.randomUUID()}`
    : `wf_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

// Create the start node - always present, not deletable
const createStartNode = (): WorkflowNode => ({
  id: START_NODE_ID,
  type: "startNode",
  position: { x: 200, y: 30 },
  data: { stepType: "__start__", label: "开始", fields: {} },
  deletable: false,
  draggable: true,
});

// Inner component with ReactFlow context
function WorkflowEditorInner({
  value,
  onChange,
  onModeChange,
  siteUrl,
  readOnly = false,
}: WorkflowEditorProps) {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const { screenToFlowPosition } = useReactFlow();
  
  // Initialize with start node
  const [nodes, setNodes, onNodesChange] = useNodesState<WorkflowNode>([createStartNode()]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<WorkflowEdge>([]);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedNodeIds, setSelectedNodeIds] = useState<string[]>([]); // Multi-selection
  const [clipboard, setClipboard] = useState<ClipboardData | null>(null);
  
  // Track initialization state - only load from value ONCE on mount
  const hasInitialized = useRef(false);
  // Track if we're currently syncing to prevent loops
  const isSyncing = useRef(false);
  
  // Ghost node placement state
  const [pendingNodeType, setPendingNodeType] = useState<string | null>(null);
  const [ghostPosition, setGhostPosition] = useState({ x: 0, y: 0 });
  const [isMouseInCanvas, setIsMouseInCanvas] = useState(false);
  
  // Track mouse position for paste
  const mousePositionRef = useRef({ x: 0, y: 0 });

  // Undo/Redo history
  const [history, setHistory] = useState<HistoryState[]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);
  const isUndoRedo = useRef(false);

  // Right-click context menu state
  const [contextMenu, setContextMenu] = useState<{
    x: number;
    y: number;
    nodeId?: string;
    edgeId?: string;
  } | null>(null);

  // Get selected node
  const selectedNode = useMemo(
    () => nodes.find((n) => n.id === selectedNodeId) || null,
    [nodes, selectedNodeId]
  );

  // Sync to parent - called when nodes/edges change (NOT on value prop change)
  const syncToParent = useCallback(() => {
    if (onChange && hasInitialized.current && !isSyncing.current) {
      const dsl = flowToDsl(nodes, edges);
      onChange(dsl);
    }
  }, [nodes, edges, onChange]);

  // Debounced sync to avoid too many updates
  const { run: debouncedSync } = useDebounceFn(syncToParent, { wait: 300 });

  // Save to history for undo/redo
  const saveToHistory = useCallback(() => {
    if (isUndoRedo.current) {
      isUndoRedo.current = false;
      return;
    }
    setHistory(prev => {
      const newHistory = prev.slice(0, historyIndex + 1);
      newHistory.push({ nodes: [...nodes], edges: [...edges] });
      // Keep max 50 history items
      if (newHistory.length > 50) newHistory.shift();
      return newHistory;
    });
    setHistoryIndex(prev => Math.min(prev + 1, 49));
  }, [nodes, edges, historyIndex]);

  // Debounced history save
  const { run: debouncedSaveHistory } = useDebounceFn(saveToHistory, { wait: 500 });

  // Undo
  const undo = useCallback(() => {
    if (historyIndex > 0) {
      isUndoRedo.current = true;
      const prevState = history[historyIndex - 1];
      setNodes(prevState.nodes);
      setEdges(prevState.edges);
      setHistoryIndex(historyIndex - 1);
    }
  }, [history, historyIndex, setNodes, setEdges]);

  // Redo
  const redo = useCallback(() => {
    if (historyIndex < history.length - 1) {
      isUndoRedo.current = true;
      const nextState = history[historyIndex + 1];
      setNodes(nextState.nodes);
      setEdges(nextState.edges);
      setHistoryIndex(historyIndex + 1);
    }
  }, [history, historyIndex, setNodes, setEdges]);

  const canUndo = historyIndex > 0;
  const canRedo = historyIndex < history.length - 1;

  // Initialize from value prop ONLY ONCE on mount
  useEffect(() => {
    if (hasInitialized.current) return; // Already initialized, ignore value changes
    
    hasInitialized.current = true;
    isSyncing.current = true;
    
    if (value && value.steps && value.steps.length > 0) {
      // Load from existing DSL
      const { nodes: newNodes, edges: newEdges } = dslToFlow(value, { 
        applyLayout: true 
      });
      
      // Ensure start node always exists
      const hasStartNode = newNodes.some(n => n.id === START_NODE_ID);
      const startNode = createStartNode();
      const finalNodes = hasStartNode ? newNodes : [startNode, ...newNodes];
      let finalEdges = newEdges;
      
      // Auto-connect first DSL step to start node
      if (!hasStartNode && newNodes.length > 0) {
        const firstStepNode = newNodes[0];
        finalEdges = [
          {
            id: `edge_${START_NODE_ID}_${firstStepNode.id}`,
            source: START_NODE_ID,
            sourceHandle: "bottom",
            target: firstStepNode.id,
            targetHandle: "top",
            type: "deletable",
            style: { strokeWidth: 2 },
          },
          ...newEdges,
        ];
      }
      
      setNodes(finalNodes);
      setEdges(finalEdges);
    }
    // If no value or empty steps, keep the default start node
    
    // Allow sync after a short delay
    setTimeout(() => {
      isSyncing.current = false;
    }, 100);
  }, []); // Empty deps - run only on mount

  // Sync to parent when nodes/edges change (after initialization)
  useEffect(() => {
    if (hasInitialized.current && !isSyncing.current) {
      debouncedSync();
      debouncedSaveHistory();
    }
  }, [nodes, edges, debouncedSync, debouncedSaveHistory]);

  // Connection validation - prevent self-connections and duplicates
  const isValidConnection = useCallback(
    (connection: Connection | WorkflowEdge) => {
      if (connection.source === connection.target) return false;
      
      // Prevent duplicate connections
      const isDuplicate = edges.some(
        (e) => e.source === connection.source && e.target === connection.target
      );
      return !isDuplicate;
    },
    [edges]
  );

  // Handle new connections with deletable edge type
  const onConnect: OnConnect = useCallback(
    (connection: Connection) => {
      if (!isValidConnection(connection)) return;
      
      setEdges((eds) =>
        addEdge(
          {
            ...connection,
            type: "deletable",
            style: { strokeWidth: 2 },
          },
          eds
        )
      );
    },
    [setEdges, isValidConnection]
  );

  // Handle node selection
  const handleNodesChange = useCallback(
    (changes: NodeChange<WorkflowNode>[]) => {
      onNodesChange(changes);
      
      // Track selection
      const selectionChange = changes.find(
        (c) => c.type === "select" && c.selected
      );
      if (selectionChange && "id" in selectionChange) {
        setSelectedNodeId(selectionChange.id);
      }
    },
    [onNodesChange]
  );

  // Handle background click - place ghost node or deselect
  const onPaneClick = useCallback(
    (event: React.MouseEvent) => {
      // If we have a pending node type, place it
      if (pendingNodeType && isMouseInCanvas) {
        const position = screenToFlowPosition({
          x: event.clientX,
          y: event.clientY,
        });
        
        const schema = DSL_SCHEMA[pendingNodeType];
        const nodeType = stepTypeToNodeType(pendingNodeType);
        
        const newNode: WorkflowNode = {
          id: generateId(),
          type: nodeType,
          position,
          data: {
            stepType: pendingNodeType,
            label: schema?.label || pendingNodeType,
            fields: {},
          },
        };

        setNodes((nds) => [...nds, newNode]);
        message.success(`已添加 ${schema?.label || pendingNodeType}`);
        setPendingNodeType(null);
        return;
      }
      
      // Otherwise deselect
      setSelectedNodeId(null);
    },
    [pendingNodeType, isMouseInCanvas, screenToFlowPosition, setNodes]
  );

  // Start placing a node (called from palette click)
  const startPlacingNode = useCallback((stepType: string) => {
    setPendingNodeType(stepType);
    message.info("点击画布放置节点，按 ESC 取消", 2);
  }, []);

  // Cancel placing node
  const cancelPlacing = useCallback(() => {
    setPendingNodeType(null);
  }, []);

  // Add new node at specific position (for drag & drop)
  const addNodeAtPosition = useCallback(
    (stepType: string, position: { x: number; y: number }) => {
      const schema = DSL_SCHEMA[stepType];
      const nodeType = stepTypeToNodeType(stepType);

      const newNode: WorkflowNode = {
        id: generateId(),
        type: nodeType,
        position,
        data: {
          stepType,
          label: schema?.label || stepType,
          fields: {},
        },
      };

      setNodes((nds) => [...nds, newNode]);
      return newNode.id;
    },
    [setNodes]
  );

  // Handle drag & drop from palette
  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();

      const stepType = event.dataTransfer.getData("application/stepType");
      if (!stepType) return;

      const position = screenToFlowPosition({
        x: event.clientX,
        y: event.clientY,
      });

      addNodeAtPosition(stepType, position);
      message.success(`已添加 ${DSL_SCHEMA[stepType]?.label || stepType}`);
    },
    [screenToFlowPosition, addNodeAtPosition]
  );

  // Update node data
  const updateNodeData = useCallback(
    (nodeId: string, data: Partial<StepNodeData>) => {
      setNodes((nds) =>
        nds.map((n) =>
          n.id === nodeId ? { ...n, data: { ...n.data, ...data } } : n
        )
      );
    },
    [setNodes]
  );

  // Delete node (prevent deleting start node)
  const deleteNode = useCallback(
    (nodeId: string) => {
      if (nodeId === START_NODE_ID) {
        message.warning("开始节点不能删除");
        return;
      }
      setNodes((nds) => nds.filter((n) => n.id !== nodeId));
      setEdges((eds) => eds.filter((e) => e.source !== nodeId && e.target !== nodeId));
      if (selectedNodeId === nodeId) setSelectedNodeId(null);
    },
    [setNodes, setEdges, selectedNodeId]
  );

  // Copy selected nodes (supports multi-selection)
  const copyNode = useCallback(() => {
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
  }, [selectedNodeIds, selectedNode, nodes, edges]);

  // Paste nodes at mouse position (supports multiple nodes)
  const pasteNode = useCallback(() => {
    if (!clipboard || clipboard.nodes.length === 0 || !reactFlowWrapper.current) return;
    
    // Convert screen position to flow position
    const bounds = reactFlowWrapper.current.getBoundingClientRect();
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
      const newId = `node_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
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
    
    // Add new nodes and edges
    setNodes(nds => [...nds, ...newNodes]);
    setEdges(eds => [...eds, ...newEdges]);
    
    message.success(`已粘贴 ${newNodes.length} 个节点`);
  }, [clipboard, screenToFlowPosition, setNodes, setEdges]);

  // Handle selection change (for multi-select)
  const onSelectionChange = useCallback(({ nodes: selectedNodes }: { nodes: WorkflowNode[] }) => {
    const ids = selectedNodes.map(n => n.id).filter(id => id !== START_NODE_ID);
    setSelectedNodeIds(ids);
    // Single selection: also update selectedNodeId
    if (ids.length === 1) {
      setSelectedNodeId(ids[0]);
    } else if (ids.length === 0) {
      // Keep last selected if nothing selected
    }
  }, []);

  // Delete selected nodes (multi-delete)
  const deleteSelectedNodes = useCallback(() => {
    const idsToDelete = selectedNodeIds.filter(id => id !== START_NODE_ID);
    if (idsToDelete.length === 0) return;
    
    setNodes((nds) => nds.filter((n) => !idsToDelete.includes(n.id)));
    setEdges((eds) => eds.filter((e) => 
      !idsToDelete.includes(e.source) && !idsToDelete.includes(e.target)
    ));
    setSelectedNodeIds([]);
    setSelectedNodeId(null);
    message.success(`已删除 ${idsToDelete.length} 个节点`);
  }, [selectedNodeIds, setNodes, setEdges]);

  // Check if multiple nodes are selected
  const isMultiSelect = selectedNodeIds.length > 1;

  // Delete edge by id
  const deleteEdge = useCallback((edgeId: string) => {
    setEdges(eds => eds.filter(e => e.id !== edgeId));
    message.success("已删除连线");
  }, [setEdges]);

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
        { key: "delete", label: "删除连线", danger: true, onClick: () => {
          deleteEdge(contextMenu.edgeId!);
          setContextMenu(null);
        }},
      ];
    }
    
    if (contextMenu.nodeId) {
      return [
        { key: "copy", label: "复制", onClick: () => {
          const node = nodes.find(n => n.id === contextMenu.nodeId);
          if (node) {
            setClipboard({ nodes: [node], edges: [] });
            message.success("已复制节点");
          }
          setContextMenu(null);
        }},
        { key: "delete", label: "删除", danger: true, onClick: () => {
          deleteNode(contextMenu.nodeId!);
          setContextMenu(null);
        }},
      ];
    }
    
    return [];
  }, [contextMenu, nodes, deleteNode, deleteEdge]);

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // ESC handling - only handle if we have something to cancel/deselect
      if (e.key === "Escape") {
        // Priority 1: Cancel node placement
        if (pendingNodeType) {
          cancelPlacing();
          e.stopPropagation();
          e.preventDefault();
          return;
        }
        // Priority 2: Clear selection (only if something is selected)
        if (selectedNodeId || selectedNodeIds.length > 0) {
          setSelectedNodeId(null);
          setSelectedNodeIds([]);
          e.stopPropagation();
          e.preventDefault();
          return;
        }
        // Priority 3: Don't handle - let Drawer/Modal handle ESC
        // (no stopPropagation, no preventDefault)
      }
      
      if (readOnly) return;
      
      // Ctrl+Z - Undo
      if (e.ctrlKey && e.key === "z" && !e.shiftKey) {
        e.preventDefault();
        undo();
        return;
      }
      
      // Ctrl+Y or Ctrl+Shift+Z - Redo
      if ((e.ctrlKey && e.key === "y") || (e.ctrlKey && e.shiftKey && e.key === "z")) {
        e.preventDefault();
        redo();
        return;
      }
      
      // Delete key - support multi-delete
      if (e.key === "Delete") {
        if (selectedNodeIds.length > 1) {
          deleteSelectedNodes();
        } else if (selectedNodeId) {
          deleteNode(selectedNodeId);
        }
      }
      
      // Ctrl+C - copy selected nodes
      if (e.ctrlKey && e.key === "c" && (selectedNode || selectedNodeIds.length > 0)) {
        copyNode();
      }
      
      // Ctrl+V - paste from clipboard
      if (e.ctrlKey && e.key === "v" && clipboard) {
        pasteNode();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [readOnly, selectedNodeId, selectedNodeIds, selectedNode, clipboard, pendingNodeType, deleteNode, deleteSelectedNodes, copyNode, pasteNode, cancelPlacing, undo, redo]);

  // Re-layout nodes
  const handleRelayout = useCallback(() => {
    const layoutedNodes = relayoutFlow(nodes, edges);
    setNodes(layoutedNodes);
    message.success("已重新布局");
  }, [nodes, edges, setNodes]);

  // Edge styles - use deletable edge type
  const defaultEdgeOptions = useMemo(
    () => ({
      type: "deletable",
      style: { strokeWidth: 2, stroke: "#b1b1b7" },
    }),
    []
  );

  // Mouse move handler for ghost node and paste position tracking
  const handleMouseMove = useCallback(
    (event: React.MouseEvent) => {
      // Always track mouse position for paste
      mousePositionRef.current = { x: event.clientX, y: event.clientY };
      
      if (pendingNodeType) {
        // Update ghost position using requestAnimationFrame for smooth animation
        setGhostPosition({ x: event.clientX, y: event.clientY });
      }
    },
    [pendingNodeType]
  );

  return (
    <div style={{ display: "flex", height: "100%", minHeight: 500, position: "relative" }}>
      {/* Left: Node Palette - absolutely positioned for proper isolation */}
      {!readOnly && (
        <div
          style={{
            position: "absolute",
            left: 0,
            top: 0,
            bottom: 0,
            width: 200,
            borderRight: "1px solid #f0f0f0",
            background: "#fafafa",
            zIndex: 10,
            display: "flex",
            flexDirection: "column",
          }}
        >
          <div
            style={{
              flex: 1,
              overflowY: "auto",
              overflowX: "hidden",
            }}
          >
            <NodePalette onAddNode={startPlacingNode} />
          </div>
        </div>
      )}

      {/* Center: Canvas - with left margin for palette */}
      <div 
        ref={reactFlowWrapper} 
        style={{ 
          flex: 1, 
          position: "relative",
          marginLeft: readOnly ? 0 : 200,
        }}
        onMouseMove={handleMouseMove}
        onMouseEnter={() => setIsMouseInCanvas(true)}
        onMouseLeave={() => setIsMouseInCanvas(false)}
      >
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={handleNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onPaneClick={onPaneClick}
          onDragOver={onDragOver}
          onDrop={onDrop}
          onSelectionChange={onSelectionChange}
          onNodeContextMenu={onNodeContextMenu}
          onEdgeContextMenu={onEdgeContextMenu}
          onPaneContextMenu={onPaneContextMenu}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          defaultEdgeOptions={defaultEdgeOptions}
          isValidConnection={isValidConnection}
          fitView
          fitViewOptions={{ padding: 0.2 }}
          attributionPosition="bottom-left"
          nodesDraggable={!readOnly}
          nodesConnectable={!readOnly}
          elementsSelectable={!readOnly}
          selectionOnDrag={false}
          panOnDrag={true}
          selectionKeyCode="Shift"
          deleteKeyCode={null}
        >
          <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="#e0e0e0" />
          <Controls showInteractive={false} />
          <MiniMap
            nodeStrokeWidth={3}
            style={{ height: 100, width: 150 }}
            maskColor="rgba(0, 0, 0, 0.1)"
            position="top-left"
          />

          {/* Top toolbar */}
          <Panel position="top-right">
            <Space
              style={{
                background: "#fff",
                padding: "6px 12px",
                borderRadius: 8,
                boxShadow: "0 2px 8px rgba(0,0,0,0.1)",
              }}
            >
              {/* Multi-selection controls */}
              {isMultiSelect && !readOnly && (
                <>
                  <span style={{ fontSize: 12, color: "#666" }}>
                    已选 {selectedNodeIds.length} 个
                  </span>
                  <Tooltip title="删除选中 (Delete)">
                    <Button
                      icon={<DeleteOutlined />}
                      size="small"
                      danger
                      onClick={deleteSelectedNodes}
                    />
                  </Tooltip>
                </>
              )}
              {/* Single selection controls */}
              {!isMultiSelect && selectedNode && !readOnly && (
                <>
                  <Tooltip title="复制 (Ctrl+C)">
                    <Button icon={<CopyOutlined />} size="small" onClick={copyNode} />
                  </Tooltip>
                  <Tooltip title="删除 (Delete)">
                    <Button
                      icon={<DeleteOutlined />}
                      size="small"
                      danger
                      onClick={() => deleteNode(selectedNodeId!)}
                    />
                  </Tooltip>
                </>
              )}
              {!readOnly && (
                <>
                  <Tooltip title="撤销 (Ctrl+Z)">
                    <Button 
                      icon={<UndoOutlined />} 
                      size="small" 
                      onClick={undo}
                      disabled={!canUndo}
                    />
                  </Tooltip>
                  <Tooltip title="重做 (Ctrl+Y)">
                    <Button 
                      icon={<RedoOutlined />} 
                      size="small" 
                      onClick={redo}
                      disabled={!canRedo}
                    />
                  </Tooltip>
                </>
              )}
              <Tooltip title="自动布局">
                <Button icon={<ReloadOutlined />} size="small" onClick={handleRelayout} />
              </Tooltip>
              {onModeChange && (
                <Segmented
                  size="small"
                  options={[
                    { value: "workflow", icon: <AppstoreOutlined /> },
                    { value: "list", icon: <UnorderedListOutlined /> },
                  ]}
                  value="workflow"
                  onChange={(v) => onModeChange(v as "workflow" | "list")}
                />
              )}
            </Space>
          </Panel>

          {/* Hint when only start node exists */}
          {nodes.length <= 1 && (
            <Panel position="top-center" style={{ marginTop: 100 }}>
              <div
                style={{
                  background: "#fff",
                  padding: "12px 20px",
                  borderRadius: 8,
                  boxShadow: "0 2px 8px rgba(0,0,0,0.1)",
                  textAlign: "center",
                }}
              >
                <span style={{ color: "#8c8c8c", fontSize: 13 }}>
                  从左侧点击或拖拽节点到画布，然后连接到开始节点
                </span>
              </div>
            </Panel>
          )}
        </ReactFlow>
      </div>

      {/* Right: Properties Panel - hidden when multi-select */}
      {!isMultiSelect && selectedNode && !readOnly && selectedNode.id !== START_NODE_ID && (
        <div
          style={{
            width: 280,
            borderLeft: "1px solid #f0f0f0",
            overflow: "auto",
            background: "#fff",
          }}
        >
          <NodeProperties
            node={selectedNode}
            onUpdate={(data: Partial<StepNodeData>) => updateNodeData(selectedNode.id, data)}
            onDelete={() => deleteNode(selectedNode.id)}
          />
        </div>
      )}

      {/* Ghost node preview - rendered when placing a node */}
      {pendingNodeType && isMouseInCanvas && (
        <GhostNode
          stepType={pendingNodeType}
          x={ghostPosition.x}
          y={ghostPosition.y}
        />
      )}

      {/* Right-click context menu */}
      {contextMenu && (
        <Dropdown
          menu={{ items: contextMenuItems }}
          open={true}
          onOpenChange={(open) => !open && setContextMenu(null)}
        >
          <div
            style={{
              position: "fixed",
              left: contextMenu.x,
              top: contextMenu.y,
              width: 1,
              height: 1,
            }}
          />
        </Dropdown>
      )}
    </div>
  );
}

// Wrapper with ReactFlowProvider
export default function WorkflowEditor(props: WorkflowEditorProps) {
  return (
    <ReactFlowProvider>
      <WorkflowEditorInner {...props} />
    </ReactFlowProvider>
  );
}
