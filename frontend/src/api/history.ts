import api from "./client";

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

export const fetchHistory = async (
  skip = 0,
  limit = 50,
  errorType?: string,
): Promise<HistoryListResponse> => {
  const params = new URLSearchParams({
    skip: String(skip),
    limit: String(limit),
  });
  if (errorType) {
    params.append("error_type", errorType);
  }
  const { data } = await api.get<HistoryListResponse>(`/history?${params.toString()}`);
  return data;
};

export const getHistoryDetail = async (historyId: number): Promise<HistoryRecord> => {
  const { data } = await api.get<HistoryRecord>(`/history/${historyId}`);
  return data;
};

export const deleteHistory = async (historyId: number): Promise<void> => {
  await api.delete(`/history/${historyId}`);
};
