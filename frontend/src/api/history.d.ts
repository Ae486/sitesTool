export interface HistoryRecord {
    id: number;
    flow_id: number;
    status: string;
    started_at: string;
    finished_at: string | null;
    duration_ms: number | null;
    log: string | null;
    result_payload: string | null;
    error_message: string | null;
    screenshot_files: string[];
    error_types: string[];
    created_at: string;
    updated_at: string;
}
export interface HistoryListResponse {
    total: number;
    items: HistoryRecord[];
}
export declare const fetchHistory: (skip?: number, limit?: number, errorType?: string) => Promise<HistoryListResponse>;
export declare const getHistoryDetail: (historyId: number) => Promise<HistoryRecord>;
export declare const deleteHistory: (historyId: number) => Promise<void>;
