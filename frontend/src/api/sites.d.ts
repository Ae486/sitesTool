import type { Site, SiteListResponse } from "../types/site";
export declare const fetchSites: () => Promise<SiteListResponse>;
export interface SitePayload {
    name: string;
    url: string;
    description?: string;
    category_id?: number;
    tag_ids?: number[];
    is_active?: boolean;
    sort_order?: number;
}
export declare const createSite: (payload: SitePayload) => Promise<Site>;
export declare const updateSite: (siteId: number, payload: Partial<SitePayload>) => Promise<Site>;
export declare const deleteSite: (siteId: number) => Promise<void>;
