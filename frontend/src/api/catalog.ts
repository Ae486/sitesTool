import api from "./client";
import type { Tag } from "../types/site";

export const fetchTags = async (): Promise<Tag[]> => {
  const { data } = await api.get<Tag[]>("/catalog/tags");
  return data;
};

export interface TagPayload {
  name: string;
  color?: string | null;
}

export const createTag = async (payload: TagPayload): Promise<Tag> => {
  const { data } = await api.post<Tag>("/catalog/tags", payload);
  return data;
};

export const deleteTag = async (tagId: number): Promise<void> => {
  await api.delete(`/catalog/tags/${tagId}`);
};


