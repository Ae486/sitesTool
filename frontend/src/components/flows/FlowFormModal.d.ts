import type { FormInstance } from "antd";
import type { AutomationFlow, FlowDSL } from "../../types/flow";
import type { Site } from "../../types/site";
interface FlowFormModalProps {
    open: boolean;
    editingFlow: AutomationFlow | null;
    sites: Site[];
    form: FormInstance;
    loading: boolean;
    onCancel: () => void;
    onSubmit: (values: any, dsl: FlowDSL) => void;
}
declare const FlowFormModal: ({ open, editingFlow, sites, form, loading, onCancel, onSubmit, }: FlowFormModalProps) => import("react/jsx-runtime").JSX.Element;
export default FlowFormModal;
