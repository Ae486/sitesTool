import api from "./client";
export const fetchFlows = async () => {
    const { data } = await api.get("/flows");
    return data;
};
export const createFlow = async (payload) => {
    const { data } = await api.post("/flows", payload);
    return data;
};
export const updateFlow = async (flowId, payload) => {
    const { data } = await api.put(`/flows/${flowId}`, payload);
    return data;
};
export const deleteFlow = async (flowId) => {
    await api.delete(`/flows/${flowId}`);
};
export const triggerFlow = async (flowId) => {
    const { data } = await api.post(`/flows/${flowId}/trigger`);
    return data;
};
export const stopFlow = async (flowId) => {
    const { data } = await api.post(`/flows/${flowId}/stop`);
    return data;
};
export const getFlowStatus = async (flowId) => {
    const { data } = await api.get(`/flows/${flowId}/status`);
    return data;
};
export const getRunningFlows = async () => {
    const { data } = await api.get("/flows/running/list");
    return data;
};
