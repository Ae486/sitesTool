import api from "./client";
import type { AutomationFlow, FlowListResponse, FlowDSL } from "../types/flow";

export const fetchFlows = async (): Promise<FlowListResponse> => {
  const { data } = await api.get<FlowListResponse>("/flows");
  return data;
};

export interface FlowPayload {
  site_id: number;
  name: string;
  description?: string;
  cron_expression?: string;
  is_active?: boolean;
  dsl: FlowDSL;
}

export const createFlow = async (payload: FlowPayload): Promise<AutomationFlow> => {
  const { data } = await api.post<AutomationFlow>("/flows", payload);
  return data;
};

export const updateFlow = async (flowId: number, payload: Partial<FlowPayload>): Promise<AutomationFlow> => {
  const { data } = await api.put<AutomationFlow>(`/flows/${flowId}`, payload);
  return data;
};

export const deleteFlow = async (flowId: number): Promise<void> => {
  await api.delete(`/flows/${flowId}`);
};

export const triggerFlow = async (flowId: number): Promise<{ status: string; message?: string }> => {
  const { data } = await api.post<{ status: string; message?: string }>(`/flows/${flowId}/trigger`);
  return data;
};

export const stopFlow = async (flowId: number): Promise<{ status: string; message?: string }> => {
  const { data } = await api.post<{ status: string; message?: string }>(`/flows/${flowId}/stop`);
  return data;
};

export const getFlowStatus = async (flowId: number): Promise<{ is_running: boolean }> => {
  const { data} = await api.get<{ is_running: boolean }>(`/flows/${flowId}/status`);
  return data;
};

export const getRunningFlows = async (): Promise<{ running_flows: number[] }> => {
  const { data } = await api.get<{ running_flows: number[] }>("/flows/running/list");
  return data;
};
