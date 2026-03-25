import { DeleteOutlined, DownOutlined, RightOutlined, SyncOutlined } from "@ant-design/icons";
import { Button, Dropdown, Input, Popconfirm, Select, Space } from "antd";
import { AnimatePresence, motion } from "framer-motion";
import { useRef, useState } from "react";
import { useProxyContext } from "../context";

const Toolbar = () => {
  const {
    filters, updateFilter, selectedRowKeys, searchTerm, setSearchTerm,
    handleBatchCheck, handleBatchDelete, handleExport,
  } = useProxyContext();

  const [localSearch, setLocalSearch] = useState(searchTerm);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  const onSearchChange = (value: string) => {
    setLocalSearch(value);
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => setSearchTerm(value), 300);
  };

  const exportLabel = selectedRowKeys.length
    ? `导出选中 (${selectedRowKeys.length})`
    : "导出全部";

  const exportMenuItems = [
    {
      key: "export",
      label: <span>{exportLabel} <RightOutlined style={{ fontSize: 10 }} /></span>,
      children: [
        { key: "export-csv", label: "CSV", onClick: () => handleExport("csv") },
        { key: "export-json", label: "JSON", onClick: () => handleExport("json") },
        { key: "export-txt", label: "TXT", onClick: () => handleExport("txt") },
      ],
    },
  ];

  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12, flexWrap: "wrap", gap: 8 }}>
      <Space size="small" wrap>
        <Input.Search
          allowClear
          placeholder="搜索 IP 地址"
          style={{ width: 180 }}
          size="small"
          value={localSearch}
          onChange={(e) => onSearchChange(e.target.value)}
          onSearch={(v) => setSearchTerm(v)}
        />
        <Select
          placeholder="协议"
          allowClear
          style={{ width: 100 }}
          value={filters.protocol || undefined}
          onChange={(v) => updateFilter("protocol", v)}
          options={[
            { value: "HTTP", label: "HTTP" },
            { value: "HTTPS", label: "HTTPS" },
            { value: "SOCKS5", label: "SOCKS5" },
            { value: "unknown", label: "未知" },
          ]}
        />
        <Select
          placeholder="来源"
          allowClear
          style={{ width: 100 }}
          value={filters.provider || undefined}
          onChange={(v) => updateFilter("provider", v)}
          options={[
            { value: "manual", label: "手动" },
            { value: "api", label: "API 导入" },
          ]}
        />
        <Select
          placeholder="状态"
          allowClear
          style={{ width: 100 }}
          value={filters.status || undefined}
          onChange={(v) => updateFilter("status", v)}
          options={[
            { value: "available", label: "可用" },
            { value: "unavailable", label: "不可用" },
            { value: "pending", label: "待检测" },
          ]}
        />
      </Space>
      <Space size="small">
        {selectedRowKeys.length > 0 && (
          <span style={{ fontSize: 12, color: "#71717a" }}>已选 {selectedRowKeys.length} 条</span>
        )}
        <AnimatePresence>
          {selectedRowKeys.length > 0 && (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              style={{ display: "inline-flex", gap: 4 }}
            >
              <Button size="small" icon={<SyncOutlined />} onClick={handleBatchCheck}>
                批量检测
              </Button>
              <Popconfirm
                title="确认批量删除"
                description={`将删除 ${selectedRowKeys.length} 条代理，不可恢复`}
                onConfirm={handleBatchDelete}
                okText="删除"
                cancelText="取消"
                okButtonProps={{ danger: true }}
              >
                <Button size="small" danger icon={<DeleteOutlined />}>
                  批量删除
                </Button>
              </Popconfirm>
            </motion.div>
          )}
        </AnimatePresence>
        <Dropdown menu={{ items: exportMenuItems }}>
          <Button size="small">
            导出 <DownOutlined />
          </Button>
        </Dropdown>
      </Space>
    </div>
  );
};

export default Toolbar;
