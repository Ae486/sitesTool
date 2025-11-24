export interface Category {
  id: number;
  name: string;
  description?: string | null;
}

export interface Tag {
  id: number;
  name: string;
  color?: string | null;
}

export interface Site {
  id: number;
  name: string;
  url: string;
  description?: string | null;
  category_id?: number | null;
  sort_order: number;
  is_active: boolean;
  created_at: string;
  updated_at: string;
  category?: Category | null;
  tags: Tag[];
}

export interface SiteListResponse {
  total: number;
  items: Site[];
}
