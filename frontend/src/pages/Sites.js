import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { DeleteOutlined, EditOutlined, PlusOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Form, Input, Modal, Popconfirm, Space, Switch, Table, Tag, Tooltip, Typography, message, } from "antd";
import { motion } from "framer-motion";
import { useState } from "react";
import { fetchTags, createTag as apiCreateTag, deleteTag as apiDeleteTag } from "../api/catalog";
import { createSite, deleteSite, fetchSites, updateSite } from "../api/sites";
import TagManager from "../components/TagManager";
import { containerVariants, itemVariants } from "../constants/animations";
const { Title, Text, Paragraph, Link } = Typography;
const tagPairColors = [
    { bg: "#E0F7FA", text: "#006064" },
    { bg: "#F1F8E9", text: "#33691E" },
    { bg: "#FFF8E1", text: "#F57F17" },
    { bg: "#F3E5F5", text: "#4A148C" },
    { bg: "#E8EAF6", text: "#1A237E" },
    { bg: "#FBE9E7", text: "#BF360C" },
    { bg: "#EDE7F6", text: "#311B92" },
    { bg: "#E1F5FE", text: "#01579B" },
];
const getTagTextColor = (bgColor) => {
    if (!bgColor)
        return "#262626";
    const pair = tagPairColors.find((pair) => pair.bg === bgColor);
    return pair ? pair.text : "#262626";
};
const SitesPage = () => {
    const [modalOpen, setModalOpen] = useState(false);
    const [editingSite, setEditingSite] = useState(null);
    const [form] = Form.useForm();
    const queryClient = useQueryClient();
    const { data: sitesData, isLoading } = useQuery({ queryKey: ["sites"], queryFn: fetchSites });
    const { data: allTags = [], isLoading: tagsLoading } = useQuery({ queryKey: ["tags"], queryFn: fetchTags });
    const createSiteMutation = useMutation({
        mutationFn: createSite,
        onSuccess: () => {
            message.success("站点创建成功");
            handleCloseModal();
            queryClient.invalidateQueries({ queryKey: ["sites"] });
        },
        onError: () => message.error("创建站点失败，请重试。"),
    });
    const updateSiteMutation = useMutation({
        mutationFn: ({ id, payload }) => updateSite(id, payload),
        onSuccess: () => {
            message.success("站点更新成功");
            handleCloseModal();
            queryClient.invalidateQueries({ queryKey: ["sites"] });
        },
        onError: () => message.error("更新站点失败，请重试。"),
    });
    const deleteSiteMutation = useMutation({
        mutationFn: deleteSite,
        onSuccess: () => {
            message.success("站点已删除");
            queryClient.invalidateQueries({ queryKey: ["sites"] });
        },
        onError: () => message.error("删除站点失败，请重试。"),
    });
    const createTagMutation = useMutation({
        mutationFn: apiCreateTag,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["tags"] });
            // 刷新站点数据以确保标签关联正确显示
            queryClient.invalidateQueries({ queryKey: ["sites"] });
        },
        onError: (error) => {
            message.error("创建标签失败");
        },
    });
    const deleteTagMutation = useMutation({
        mutationFn: apiDeleteTag,
        onSuccess: () => {
            message.success("标签已删除");
            queryClient.invalidateQueries({ queryKey: ["tags"] });
            queryClient.invalidateQueries({ queryKey: ["sites"] });
        },
        onError: (error) => {
            message.error("删除标签失败");
        },
    });
    const columns = [
        {
            title: "名称",
            dataIndex: "name",
            key: "name",
            width: 180,
            render: (text) => _jsx("span", { style: { fontWeight: 600 }, children: text }),
        },
        {
            title: "URL",
            dataIndex: "url",
            key: "url",
            width: 220,
            render: (value) => (_jsx(Link, { href: value, target: "_blank", rel: "noreferrer", children: value })),
        },
        {
            title: "描述",
            dataIndex: "description",
            key: "description",
            width: 260,
            render: (value) => value ? (_jsx(Tooltip, { title: value, children: _jsx(Paragraph, { ellipsis: { rows: 2 }, style: { marginBottom: 0 }, children: value }) })) : (_jsx(Text, { type: "secondary", children: "-" })),
        },
        {
            title: "标签",
            dataIndex: "tags",
            key: "tags",
            width: 220,
            render: (tags) => (_jsx(Space, { size: [4, 8], wrap: true, children: tags.length ? (tags.map((tag) => (_jsx(Tag, { style: {
                        backgroundColor: tag.color || "#f0f0f0",
                        color: getTagTextColor(tag.color),
                        border: "none",
                    }, children: tag.name }, tag.id)))) : (_jsx(Text, { type: "secondary", children: "\u65E0" })) })),
        },
        {
            title: "状态",
            dataIndex: "is_active",
            key: "is_active",
            width: 120,
            render: (value) => (_jsx(Tag, { color: value ? "success" : "error", style: { borderRadius: 12 }, children: value ? "正常" : "禁用" })),
        },
        {
            title: "操作",
            key: "actions",
            width: 220,
            render: (record) => (_jsxs(Space, { children: [_jsx(Button, { icon: _jsx(EditOutlined, {}), onClick: () => handleOpenModal(record), children: "\u7F16\u8F91" }), _jsx(Popconfirm, { title: "\u786E\u8BA4\u5220\u9664\u6B64\u7AD9\u70B9\uFF1F", description: "\u6B64\u64CD\u4F5C\u4E0D\u53EF\u64A4\u9500\u3002", okText: "\u5220\u9664", cancelText: "\u53D6\u6D88", okButtonProps: { danger: true }, onConfirm: () => deleteSiteMutation.mutate(record.id), children: _jsx(Button, { icon: _jsx(DeleteOutlined, {}), danger: true, loading: deleteSiteMutation.isPending, children: "\u5220\u9664" }) })] })),
        },
    ];
    const handleOpenModal = (site) => {
        setModalOpen(true);
        if (site) {
            setEditingSite(site);
            const tagIds = site.tags.map((tag) => tag.id);
            console.log("Opening modal for site:", site.name, "with tags:", site.tags, "tag_ids:", tagIds);
            form.setFieldsValue({
                name: site.name,
                url: site.url,
                description: site.description,
                tag_ids: tagIds,
                is_active: site.is_active,
            });
        }
        else {
            setEditingSite(null);
            form.resetFields();
            form.setFieldsValue({ tag_ids: [], is_active: true });
        }
    };
    const handleCloseModal = () => {
        setModalOpen(false);
        // Don't reset here - will be done in afterClose to prevent flash
    };
    const handleSubmit = async () => {
        try {
            const values = await form.validateFields();
            const payload = {
                ...values,
                tag_ids: values.tag_ids || [],
                description: values.description?.trim() || null,
            };
            console.log("Submitting site with payload:", payload);
            if (editingSite) {
                updateSiteMutation.mutate({ id: editingSite.id, payload });
            }
            else {
                createSiteMutation.mutate(payload);
            }
        }
        catch (error) {
            console.error("Form validation error:", error);
        }
    };
    return (_jsxs(motion.div, { variants: containerVariants, initial: "hidden", animate: "show", children: [_jsxs(motion.div, { variants: itemVariants, style: { display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }, children: [_jsx(Title, { level: 4, style: { margin: 0 }, children: "\u7AD9\u70B9\u7BA1\u7406" }), _jsx(Button, { type: "primary", icon: _jsx(PlusOutlined, {}), onClick: () => handleOpenModal(), size: "large", children: "\u65B0\u5EFA\u7AD9\u70B9" })] }), _jsx(motion.div, { variants: itemVariants, className: "glass-panel", style: { padding: 24, borderRadius: 16 }, children: _jsx(Table, { loading: isLoading, dataSource: sitesData?.items ?? [], columns: columns, rowKey: "id", pagination: { pageSize: 10, showTotal: (total) => `共 ${total} 条` } }) }), _jsx(Modal, { title: editingSite ? "编辑站点" : "新建站点", open: modalOpen, onOk: handleSubmit, confirmLoading: createSiteMutation.isPending || updateSiteMutation.isPending, onCancel: handleCloseModal, afterClose: () => {
                    // Reset form after modal animation completes to prevent flash
                    setEditingSite(null);
                    form.resetFields();
                }, centered: true, maskClosable: true, width: 720, children: _jsxs(Form, { form: form, layout: "vertical", style: { marginTop: 12 }, initialValues: { is_active: true, tag_ids: [] }, children: [_jsx(Form.Item, { name: "name", label: "\u540D\u79F0", rules: [{ required: true, message: "请输入名称" }], children: _jsx(Input, { placeholder: "\u793A\u4F8B\u7AD9\u70B9", size: "large" }) }), _jsx(Form.Item, { name: "url", label: "URL", rules: [{ required: true, type: "url", message: "请输入有效的 URL" }], children: _jsx(Input, { placeholder: "https://example.com", size: "large" }) }), _jsx(Form.Item, { name: "description", label: "\u63CF\u8FF0", children: _jsx(Input.TextArea, { rows: 3, placeholder: "\u5907\u6CE8\u4FE1\u606F" }) }), _jsx(Form.Item, { name: "tag_ids", style: { display: "none" }, children: _jsx(Input, {}) }), _jsx(Form.Item, { label: "\u6807\u7B7E", shouldUpdate: true, children: () => tagsLoading ? (_jsx(Text, { type: "secondary", children: "\u52A0\u8F7D\u4E2D..." })) : (_jsx(TagManager, { value: form.getFieldValue("tag_ids") || [], onChange: (ids) => form.setFieldsValue({ tag_ids: ids }), availableTags: allTags, onCreateTag: (payload) => createTagMutation.mutateAsync(payload), onDeleteTag: (tagId) => deleteTagMutation.mutateAsync(tagId), creating: createTagMutation.isPending, deleting: deleteTagMutation.isPending })) }), _jsx(Form.Item, { name: "is_active", label: "\u542F\u7528", valuePropName: "checked", children: _jsx(Switch, {}) })] }) })] }));
};
export default SitesPage;
