import type { Tag as TagType } from "../types/site";
interface TagManagerProps {
    value?: number[];
    onChange?: (ids: number[]) => void;
    availableTags: TagType[];
    onCreateTag: (tag: {
        name: string;
        color: string;
    }) => Promise<TagType | void>;
    onDeleteTag?: (tagId: number) => Promise<void>;
    creating?: boolean;
    deleting?: boolean;
}
declare const TagManager: ({ value, onChange, availableTags, onCreateTag, onDeleteTag, creating, deleting, }: TagManagerProps) => import("react/jsx-runtime").JSX.Element;
export default TagManager;
