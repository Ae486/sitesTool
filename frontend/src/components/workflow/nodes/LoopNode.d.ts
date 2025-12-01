import type { StepNodeData } from "../types";
interface LoopNodeProps {
    id: string;
    data: StepNodeData;
    selected?: boolean;
}
declare function LoopNode({ id, data, selected }: LoopNodeProps): import("react/jsx-runtime").JSX.Element;
declare const _default: import("react").MemoExoticComponent<typeof LoopNode>;
export default _default;
