import api from "./client";
export const fetchSites = async () => {
    const { data } = await api.get("/sites");
    return data;
};
export const createSite = async (payload) => {
    const { data } = await api.post("/sites", payload);
    return data;
};
export const updateSite = async (siteId, payload) => {
    const { data } = await api.put(`/sites/${siteId}`, payload);
    return data;
};
export const deleteSite = async (siteId) => {
    await api.delete(`/sites/${siteId}`);
};
