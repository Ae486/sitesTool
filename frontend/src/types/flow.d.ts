import type { VisualDsl, VisualDslStep } from "../constants/dsl";
export type FlowDSL = VisualDsl;
export type FlowStep = VisualDslStep;
export interface AutomationFlow {
    id: number;
    site_id: number;
    name: string;
    description?: string | null;
    cron_expression?: string | null;
    is_active: boolean;
    headless: boolean;
    browser_type: string;
    browser_path?: string | null;
    use_cdp_mode: boolean;
    cdp_port: number;
    cdp_user_data_dir?: string | null;
    dsl: FlowDSL;
    last_status: string;
    created_at: string;
    updated_at: string;
}
export interface FlowListResponse {
    total: number;
    items: AutomationFlow[];
}
