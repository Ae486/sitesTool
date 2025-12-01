import type { WorkflowNode, StepNodeData } from "../types";
interface NodePropertiesProps {
    node: WorkflowNode;
    onUpdate: (data: Partial<StepNodeData>) => void;
    onDelete: () => void;
}
declare function NodeProperties({ node, onUpdate, onDelete }: NodePropertiesProps): import("react/jsx-runtime").JSX.Element;
declare const _default: import("react").MemoExoticComponent<typeof NodeProperties>;
export default _default;
