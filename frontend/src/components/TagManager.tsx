import { PlusOutlined } from "@ant-design/icons";
import { Button, Divider, Input, Modal, Popconfirm, Space, Tag, Typography, message } from "antd";
import { useMemo, useState } from "react";
import type { Tag as TagType } from "../types/site";

interface TagManagerProps {
  value?: number[];
  onChange?: (ids: number[]) => void;
  availableTags: TagType[];
  onCreateTag: (tag: { name: string; color: string }) => Promise<TagType | void>;
  onDeleteTag?: (tagId: number) => Promise<void>;
  creating?: boolean;
  deleting?: boolean;
}

const COLOR_PAIRS = [
  { bg: "#E0F7FA", text: "#006064" },
  { bg: "#F1F8E9", text: "#33691E" },
  { bg: "#FFF8E1", text: "#F57F17" },
  { bg: "#F3E5F5", text: "#4A148C" },
  { bg: "#E8EAF6", text: "#1A237E" },
  { bg: "#FBE9E7", text: "#BF360C" },
  { bg: "#EDE7F6", text: "#311B92" },
  { bg: "#E1F5FE", text: "#01579B" },
];

const randomColor = () => COLOR_PAIRS[Math.floor(Math.random() * COLOR_PAIRS.length)].bg;

const getTextColor = (bgColor?: string | null) => {
  if (!bgColor) {
    return "#262626";
  }
  const pair = COLOR_PAIRS.find((item) => item.bg === bgColor);
  return pair ? pair.text : "#262626";
};

const TagManager = ({
  value = [],
  onChange,
  availableTags,
  onCreateTag,
  onDeleteTag,
  creating,
  deleting,
}: TagManagerProps) => {
  const [newTagName, setNewTagName] = useState("");
  const [deleteConfirmTagId, setDeleteConfirmTagId] = useState<number | null>(null);

  console.log("TagManager - value (tag_ids):", value);
  console.log("TagManager - availableTags:", availableTags);

  const selectedTags = useMemo(
    () => availableTags.filter((tag) => value.includes(tag.id)),
    [availableTags, value],
  );
  const unselectedTags = useMemo(
    () => availableTags.filter((tag) => !value.includes(tag.id)),
    [availableTags, value],
  );

  console.log("TagManager - selectedTags:", selectedTags);
  console.log("TagManager - unselectedTags:", unselectedTags);

  const handleSelect = (tagId: number) => {
    const newValue = value.includes(tagId)
      ? value.filter((id) => id !== tagId)
      : [...value, tagId];
    console.log("TagManager - handleSelect:", tagId, "new value:", newValue);
    onChange?.(newValue);
  };

  const handleCreate = async () => {
    const name = newTagName.trim();
    if (!name) {
      message.warning("请输入标签名称");
      return;
    }
    try {
      const color = randomColor();
      const result = await onCreateTag({ name, color });
      if (result) {
        onChange?.([...value, result.id]);
        setNewTagName("");
        message.success(`标签 "${result.name}" 创建成功`);
      }
    } catch (error: any) {
      message.error(`创建标签失败: ${error.message || "未知错误"}`);
    }
  };

  const handleDeleteTag = async (tagId: number, tagName: string) => {
    console.log("开始删除标签:", tagId, tagName);
    try {
      if (onDeleteTag) {
        await onDeleteTag(tagId);
        message.success(`标签 "${tagName}" 已删除`);
        setDeleteConfirmTagId(null);
      }
    } catch (error: any) {
      message.error(`删除标签失败: ${error.message || "未知错误"}`);
      setDeleteConfirmTagId(null);
    }
  };

  return (
    <Space direction="vertical" style={{ width: "100%" }} size="small">
      <Typography.Text type="secondary">已选标签</Typography.Text>
      <Space size={[8, 8]} wrap>
        {selectedTags.length ? (
          selectedTags.map((tag) => {
            const bgColor = tag.color || "#E0F7FA";
            return (
              <Tag
                key={tag.id}
                style={{
                  backgroundColor: bgColor,
                  color: getTextColor(bgColor),
                  border: "none",
                }}
                closable
                onClose={(e) => {
                  e.preventDefault();
                  handleSelect(tag.id);
                }}
              >
                {tag.name}
              </Tag>
            );
          })
        ) : (
          <Typography.Text type="secondary">未选择标签</Typography.Text>
        )}
      </Space>

      <Divider style={{ margin: "8px 0" }} />

      <Typography.Text type="secondary">标签库（点击标签添加，点击 × 删除）</Typography.Text>
      <Space size={[8, 8]} wrap>
        {unselectedTags.length ? (
          unselectedTags.map((tag) => {
            const bgColor = tag.color || "#f0f0f0";
            
            return (
              <Popconfirm
                key={tag.id}
                title="确认删除标签？"
                description={`将永久删除标签 "${tag.name}"，该标签将从所有站点中移除。`}
                open={deleteConfirmTagId === tag.id}
                onConfirm={() => handleDeleteTag(tag.id, tag.name)}
                onCancel={() => setDeleteConfirmTagId(null)}
                okText="删除"
                cancelText="取消"
                okButtonProps={{ danger: true }}
              >
                <Tag
                  style={{
                    backgroundColor: bgColor,
                    color: getTextColor(bgColor),
                    border: "none",
                    cursor: "pointer",
                  }}
                  closable={Boolean(onDeleteTag)}
                  onClose={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    console.log("点击删除按钮，标签ID:", tag.id);
                    setDeleteConfirmTagId(tag.id);
                  }}
                  onClick={(e) => {
                    // 只有不是点击关闭按钮时才添加标签
                    const target = e.target as HTMLElement;
                    if (!target.closest('.anticon-close')) {
                      handleSelect(tag.id);
                    }
                  }}
                >
                  {tag.name}
                </Tag>
              </Popconfirm>
            );
          })
        ) : (
          <Typography.Text type="secondary">暂无可用标签</Typography.Text>
        )}
      </Space>

      <Divider style={{ margin: "8px 0" }} />

      <Space.Compact style={{ width: "100%" }}>
        <Input
          placeholder="输入新标签名称"
          value={newTagName}
          onChange={(e) => setNewTagName(e.target.value)}
          onPressEnter={handleCreate}
        />
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={handleCreate}
          loading={creating}
        >
          添加
        </Button>
      </Space.Compact>
    </Space>
  );
};

export default TagManager;
