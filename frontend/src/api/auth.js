import api from "./client";
export const login = async (payload) => {
    const form = new URLSearchParams();
    form.append("username", payload.username);
    form.append("password", payload.password);
    const { data } = await api.post("/auth/token", form, {
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
    });
    return data;
};
export const fetchCurrentUser = async (token) => {
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const { data } = await api.get("/auth/me", { headers });
    return data;
};
