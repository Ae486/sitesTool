import api from "./client";
export const fetchHistory = async (skip = 0, limit = 50, errorType) => {
    const params = new URLSearchParams({
        skip: String(skip),
        limit: String(limit),
    });
    if (errorType) {
        params.append("error_type", errorType);
    }
    const { data } = await api.get(`/history?${params.toString()}`);
    return data;
};
export const getHistoryDetail = async (historyId) => {
    const { data } = await api.get(`/history/${historyId}`);
    return data;
};
export const deleteHistory = async (historyId) => {
    await api.delete(`/history/${historyId}`);
};
