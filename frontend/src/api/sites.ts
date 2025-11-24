import api from "./client";
import type { Site, SiteListResponse } from "../types/site";

export const fetchSites = async (): Promise<SiteListResponse> => {
  const { data } = await api.get<SiteListResponse>("/sites");
  return data;
};

export interface SitePayload {
  name: string;
  url: string;
  description?: string;
  category_id?: number;
  tag_ids?: number[];
  is_active?: boolean;
  sort_order?: number;
}

export const createSite = async (payload: SitePayload): Promise<Site> => {
  const { data } = await api.post<Site>("/sites", payload);
  return data;
};

export const updateSite = async (siteId: number, payload: Partial<SitePayload>): Promise<Site> => {
  const { data } = await api.put<Site>(`/sites/${siteId}`, payload);
  return data;
};

export const deleteSite = async (siteId: number): Promise<void> => {
  await api.delete(`/sites/${siteId}`);
};
