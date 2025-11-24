import type { AutomationFlow, FlowListResponse, FlowDSL } from "../types/flow";
export declare const fetchFlows: () => Promise<FlowListResponse>;
export interface FlowPayload {
    site_id: number;
    name: string;
    description?: string;
    cron_expression?: string;
    is_active?: boolean;
    dsl: FlowDSL;
}
export declare const createFlow: (payload: FlowPayload) => Promise<AutomationFlow>;
export declare const updateFlow: (flowId: number, payload: Partial<FlowPayload>) => Promise<AutomationFlow>;
export declare const deleteFlow: (flowId: number) => Promise<void>;
export declare const triggerFlow: (flowId: number) => Promise<{
    status: string;
    message?: string;
}>;
export declare const stopFlow: (flowId: number) => Promise<{
    status: string;
    message?: string;
}>;
export declare const getFlowStatus: (flowId: number) => Promise<{
    is_running: boolean;
}>;
export declare const getRunningFlows: () => Promise<{
    running_flows: number[];
}>;
