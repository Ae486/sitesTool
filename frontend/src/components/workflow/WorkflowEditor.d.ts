import "@xyflow/react/dist/style.css";
import type { FlowDSL } from "../../types/flow";
interface WorkflowEditorProps {
    value?: FlowDSL;
    onChange?: (dsl: FlowDSL) => void;
    onModeChange?: (mode: "workflow" | "list") => void;
    siteUrl?: string;
    readOnly?: boolean;
}
export default function WorkflowEditor(props: WorkflowEditorProps): import("react/jsx-runtime").JSX.Element;
export {};
