/**
 * Node types registration for React Flow
 */
import type { NodeTypes } from "@xyflow/react";
import BaseStepNode from "./BaseStepNode";
import ConditionNode from "./ConditionNode";
import LoopNode from "./LoopNode";
import StartNode from "./StartNode";
export declare const START_NODE_ID = "__start__";
export declare const CONTAINER_STEP_TYPES: string[];
export declare const isContainerStepType: (stepType: string) => boolean;
export declare const nodeTypes: NodeTypes;
export declare const stepTypeToNodeType: (stepType: string) => string;
export { StartNode, BaseStepNode, ConditionNode, LoopNode };
