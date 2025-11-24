import type { Tag } from "../types/site";
export declare const fetchTags: () => Promise<Tag[]>;
export interface TagPayload {
    name: string;
    color?: string | null;
}
export declare const createTag: (payload: TagPayload) => Promise<Tag>;
export declare const deleteTag: (tagId: number) => Promise<void>;
