/**
 * Node types registration for React Flow
 */
import type { NodeTypes } from "@xyflow/react";
import BaseStepNode from "./BaseStepNode";
import ConditionNode from "./ConditionNode";
import LoopNode from "./LoopNode";
import StartNode from "./StartNode";

// Start node ID constant
export const START_NODE_ID = "__start__";

// Container step types
export const CONTAINER_STEP_TYPES = ["loop", "loop_array", "if_else"];

// Check if a step type is a container
export const isContainerStepType = (stepType: string): boolean =>
  CONTAINER_STEP_TYPES.includes(stepType);

// Register all custom node types
export const nodeTypes: NodeTypes = {
  startNode: StartNode,
  stepNode: BaseStepNode,
  conditionNode: ConditionNode,
  loopNode: LoopNode,
};

// Map DSL step types to node types
export const stepTypeToNodeType = (stepType: string): string => {
  switch (stepType) {
    case "if_else":
      return "conditionNode";
    case "loop":
    case "loop_array":
      return "loopNode";
    default:
      return "stepNode";
  }
};

export { StartNode, BaseStepNode, ConditionNode, LoopNode };
