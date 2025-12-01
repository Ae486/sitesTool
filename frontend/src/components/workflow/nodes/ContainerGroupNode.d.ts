import type { StepNodeData } from "../types";
interface ContainerGroupNodeProps {
    id: string;
    data: StepNodeData;
    selected?: boolean;
}
declare function ContainerGroupNode({ id, data, selected }: ContainerGroupNodeProps): import("react/jsx-runtime").JSX.Element;
declare const _default: import("react").MemoExoticComponent<typeof ContainerGroupNode>;
export default _default;
