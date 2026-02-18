import api from "./client";
import type { User } from "../types/user";

export interface TokenResponse {
  access_token: string;
  token_type: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthStatus {
  auth_disabled: boolean;
  dev_user?: User;
}

export const login = async (payload: LoginRequest): Promise<TokenResponse> => {
  const form = new URLSearchParams();
  form.append("username", payload.username);
  form.append("password", payload.password);
  const { data } = await api.post<TokenResponse>("/auth/token", form, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });
  return data;
};

export const fetchCurrentUser = async (token?: string): Promise<User> => {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const { data } = await api.get<User>("/auth/me", { headers });
  return data;
};

export const fetchAuthStatus = async (): Promise<AuthStatus> => {
  const { data } = await api.get<AuthStatus>("/auth/status");
  return data;
};
