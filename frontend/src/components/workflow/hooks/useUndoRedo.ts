/**
 * useUndoRedo - Undo/Redo history management hook
 */
import { useState, useCallback, useRef } from "react";
import { useDebounceFn } from "ahooks";
import type { WorkflowNode, WorkflowEdge } from "../types";

interface HistoryState {
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
}

interface UseUndoRedoOptions {
  maxHistory?: number;
  debounceWait?: number;
}

interface UseUndoRedoReturn {
  saveToHistory: () => void;
  seedHistory: (nodes: WorkflowNode[], edges: WorkflowEdge[]) => void;
  undo: () => HistoryState | null;
  redo: () => HistoryState | null;
  canUndo: boolean;
  canRedo: boolean;
  isUndoRedo: React.MutableRefObject<boolean>;
}

/**
 * Hook for managing undo/redo history
 */
export function useUndoRedo(
  nodes: WorkflowNode[],
  edges: WorkflowEdge[],
  options: UseUndoRedoOptions = {}
): UseUndoRedoReturn {
  const { maxHistory = 50, debounceWait = 500 } = options;
  
  const [history, setHistory] = useState<HistoryState[]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);
  const isUndoRedo = useRef(false);

  const seedHistory = useCallback(
    (seedNodes: WorkflowNode[], seedEdges: WorkflowEdge[]) => {
      isUndoRedo.current = false;
      setHistory([{ nodes: [...seedNodes], edges: [...seedEdges] }]);
      setHistoryIndex(0);
    },
    []
  );

  // Save current state to history
  const saveToHistoryImmediate = useCallback(() => {
    if (isUndoRedo.current) {
      isUndoRedo.current = false;
      return;
    }
    setHistory(prev => {
      const newHistory = prev.slice(0, historyIndex + 1);
      newHistory.push({ nodes: [...nodes], edges: [...edges] });
      if (newHistory.length > maxHistory) newHistory.shift();
      return newHistory;
    });
    setHistoryIndex(prev => Math.min(prev + 1, maxHistory - 1));
  }, [nodes, edges, historyIndex, maxHistory]);

  // Debounced save
  const { run: saveToHistory } = useDebounceFn(saveToHistoryImmediate, { wait: debounceWait });

  // Undo
  const undo = useCallback((): HistoryState | null => {
    if (historyIndex > 0) {
      isUndoRedo.current = true;
      const prevState = history[historyIndex - 1];
      setHistoryIndex(historyIndex - 1);
      return prevState;
    }
    return null;
  }, [history, historyIndex]);

  // Redo
  const redo = useCallback((): HistoryState | null => {
    if (historyIndex < history.length - 1) {
      isUndoRedo.current = true;
      const nextState = history[historyIndex + 1];
      setHistoryIndex(historyIndex + 1);
      return nextState;
    }
    return null;
  }, [history, historyIndex]);

  return {
    saveToHistory,
    seedHistory,
    undo,
    redo,
    canUndo: historyIndex > 0,
    canRedo: historyIndex < history.length - 1,
    isUndoRedo,
  };
}

export default useUndoRedo;
