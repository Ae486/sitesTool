export interface User {
    id: number;
    email: string;
    full_name?: string | null;
    is_active: boolean;
    created_at: string;
}
