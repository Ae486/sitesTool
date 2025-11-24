import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { PlusOutlined } from "@ant-design/icons";
import { Button, Divider, Input, Popconfirm, Space, Tag, Typography, message } from "antd";
import { useMemo, useState } from "react";
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
const getTextColor = (bgColor) => {
    if (!bgColor) {
        return "#262626";
    }
    const pair = COLOR_PAIRS.find((item) => item.bg === bgColor);
    return pair ? pair.text : "#262626";
};
const TagManager = ({ value = [], onChange, availableTags, onCreateTag, onDeleteTag, creating, deleting, }) => {
    const [newTagName, setNewTagName] = useState("");
    const [deleteConfirmTagId, setDeleteConfirmTagId] = useState(null);
    console.log("TagManager - value (tag_ids):", value);
    console.log("TagManager - availableTags:", availableTags);
    const selectedTags = useMemo(() => availableTags.filter((tag) => value.includes(tag.id)), [availableTags, value]);
    const unselectedTags = useMemo(() => availableTags.filter((tag) => !value.includes(tag.id)), [availableTags, value]);
    console.log("TagManager - selectedTags:", selectedTags);
    console.log("TagManager - unselectedTags:", unselectedTags);
    const handleSelect = (tagId) => {
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
        }
        catch (error) {
            message.error(`创建标签失败: ${error.message || "未知错误"}`);
        }
    };
    const handleDeleteTag = async (tagId, tagName) => {
        console.log("开始删除标签:", tagId, tagName);
        try {
            if (onDeleteTag) {
                await onDeleteTag(tagId);
                message.success(`标签 "${tagName}" 已删除`);
                setDeleteConfirmTagId(null);
            }
        }
        catch (error) {
            message.error(`删除标签失败: ${error.message || "未知错误"}`);
            setDeleteConfirmTagId(null);
        }
    };
    return (_jsxs(Space, { direction: "vertical", style: { width: "100%" }, size: "small", children: [_jsx(Typography.Text, { type: "secondary", children: "\u5DF2\u9009\u6807\u7B7E" }), _jsx(Space, { size: [8, 8], wrap: true, children: selectedTags.length ? (selectedTags.map((tag) => {
                    const bgColor = tag.color || "#E0F7FA";
                    return (_jsx(Tag, { style: {
                            backgroundColor: bgColor,
                            color: getTextColor(bgColor),
                            border: "none",
                        }, closable: true, onClose: (e) => {
                            e.preventDefault();
                            handleSelect(tag.id);
                        }, children: tag.name }, tag.id));
                })) : (_jsx(Typography.Text, { type: "secondary", children: "\u672A\u9009\u62E9\u6807\u7B7E" })) }), _jsx(Divider, { style: { margin: "8px 0" } }), _jsx(Typography.Text, { type: "secondary", children: "\u6807\u7B7E\u5E93\uFF08\u70B9\u51FB\u6807\u7B7E\u6DFB\u52A0\uFF0C\u70B9\u51FB \u00D7 \u5220\u9664\uFF09" }), _jsx(Space, { size: [8, 8], wrap: true, children: unselectedTags.length ? (unselectedTags.map((tag) => {
                    const bgColor = tag.color || "#f0f0f0";
                    return (_jsx(Popconfirm, { title: "\u786E\u8BA4\u5220\u9664\u6807\u7B7E\uFF1F", description: `将永久删除标签 "${tag.name}"，该标签将从所有站点中移除。`, open: deleteConfirmTagId === tag.id, onConfirm: () => handleDeleteTag(tag.id, tag.name), onCancel: () => setDeleteConfirmTagId(null), okText: "\u5220\u9664", cancelText: "\u53D6\u6D88", okButtonProps: { danger: true }, children: _jsx(Tag, { style: {
                                backgroundColor: bgColor,
                                color: getTextColor(bgColor),
                                border: "none",
                                cursor: "pointer",
                            }, closable: Boolean(onDeleteTag), onClose: (e) => {
                                e.preventDefault();
                                e.stopPropagation();
                                console.log("点击删除按钮，标签ID:", tag.id);
                                setDeleteConfirmTagId(tag.id);
                            }, onClick: (e) => {
                                // 只有不是点击关闭按钮时才添加标签
                                const target = e.target;
                                if (!target.closest('.anticon-close')) {
                                    handleSelect(tag.id);
                                }
                            }, children: tag.name }) }, tag.id));
                })) : (_jsx(Typography.Text, { type: "secondary", children: "\u6682\u65E0\u53EF\u7528\u6807\u7B7E" })) }), _jsx(Divider, { style: { margin: "8px 0" } }), _jsxs(Space.Compact, { style: { width: "100%" }, children: [_jsx(Input, { placeholder: "\u8F93\u5165\u65B0\u6807\u7B7E\u540D\u79F0", value: newTagName, onChange: (e) => setNewTagName(e.target.value), onPressEnter: handleCreate }), _jsx(Button, { type: "primary", icon: _jsx(PlusOutlined, {}), onClick: handleCreate, loading: creating, children: "\u6DFB\u52A0" })] })] }));
};
export default TagManager;
