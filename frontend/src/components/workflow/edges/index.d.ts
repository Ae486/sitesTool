/**
 * Custom edge types registration
 */
import DeletableEdge from "./DeletableEdge";
export declare const edgeTypes: {
    deletable: import("react").MemoExoticComponent<({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, style, markerEnd, selected, }: import("./DeletableEdge").DeletableEdgeProps) => import("react/jsx-runtime").JSX.Element>;
};
export { DeletableEdge };
