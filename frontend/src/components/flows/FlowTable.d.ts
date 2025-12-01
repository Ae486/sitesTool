import type { AutomationFlow } from "../../types/flow";
interface FlowTableProps {
    flows: AutomationFlow[];
    loading: boolean;
    siteNameMap: Map<number, string>;
    runningFlows: Set<number>;
    executingFlows: Set<number>;
    onEdit: (flow: AutomationFlow) => void;
    onDelete: (flowId: number) => void;
    onTrigger: (flowId: number) => void;
    onStop: (flowId: number) => void;
    deleteLoading: boolean;
    stopLoading: boolean;
    stoppingFlowId?: number;
}
declare const FlowTable: ({ flows, loading, siteNameMap, runningFlows, executingFlows, onEdit, onDelete, onTrigger, onStop, deleteLoading, stopLoading, stoppingFlowId, }: FlowTableProps) => import("react/jsx-runtime").JSX.Element;
export default FlowTable;
