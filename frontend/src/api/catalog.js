import api from "./client";
export const fetchTags = async () => {
    const { data } = await api.get("/catalog/tags");
    return data;
};
export const createTag = async (payload) => {
    const { data } = await api.post("/catalog/tags", payload);
    return data;
};
export const deleteTag = async (tagId) => {
    await api.delete(`/catalog/tags/${tagId}`);
};
