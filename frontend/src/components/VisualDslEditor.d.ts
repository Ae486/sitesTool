import type { FlowDSL } from "../types/flow";
import { DslValidationResult } from "../utils/dsl";
export interface VisualDslEditorRef {
    flush: () => void;
}
interface VisualDslEditorProps {
    value?: FlowDSL | string;
    onChange?: (dsl: FlowDSL) => void;
    siteUrl?: string;
    onValidationChange?: (result: DslValidationResult) => void;
}
declare const VisualDslEditor: import("react").ForwardRefExoticComponent<VisualDslEditorProps & import("react").RefAttributes<VisualDslEditorRef>>;
export default VisualDslEditor;
