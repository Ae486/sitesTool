import type { StepNodeData } from "../types";
interface ConditionNodeProps {
    id: string;
    data: StepNodeData;
    selected?: boolean;
}
declare function ConditionNode({ id, data, selected }: ConditionNodeProps): import("react/jsx-runtime").JSX.Element;
declare const _default: import("react").MemoExoticComponent<typeof ConditionNode>;
export default _default;
