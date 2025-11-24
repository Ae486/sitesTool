import type { User } from "../types/user";
export interface TokenResponse {
    access_token: string;
    token_type: string;
}
export interface LoginRequest {
    username: string;
    password: string;
}
export declare const login: (payload: LoginRequest) => Promise<TokenResponse>;
export declare const fetchCurrentUser: (token?: string) => Promise<User>;
