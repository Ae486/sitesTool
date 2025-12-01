import type { StepNodeData } from "../types";
interface BaseStepNodeProps {
    id: string;
    data: StepNodeData;
    selected?: boolean;
}
declare function BaseStepNode({ id, data, selected }: BaseStepNodeProps): import("react/jsx-runtime").JSX.Element;
declare const _default: import("react").MemoExoticComponent<typeof BaseStepNode>;
export default _default;
