import { DeleteOutlined, MinusCircleOutlined, PlusOutlined, SyncOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Input, Popconfirm, Select, Space, Table, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import {
  batchImportProxies,
  createApiConfig,
  deleteApiConfig,
  fetchApiConfigs,
  fetchProxyPreview,
  updateApiConfig,
} from "../../../api/proxy";
import type { FetchPreviewItem } from "../../../types/proxy";

let uidCounter = 0;
const nextUid = () => String(++uidCounter);

const buildUrl = (baseUrl: string, params: { key: string; value: string }[]) => {
  const filtered = params.filter((p) => p.key?.trim());
  if (!filtered.length) return baseUrl;
  const qs = filtered
    .map((p) => `${encodeURIComponent(p.key)}=${encodeURIComponent(p.value ?? "")}`)
    .join("&");
  return `${baseUrl}?${qs}`;
};

interface ParamRow {
  key: string;
  value: string;
  uid: string;
}

const ImportPanel = ({ onSuccess }: { onSuccess: () => void }) => {
  const queryClient = useQueryClient();
  const [selectedConfigId, setSelectedConfigId] = useState<number | null>(null);
  const [configName, setConfigName] = useState("");
  const [apiUrl, setApiUrl] = useState("");
  const [params, setParams] = useState<ParamRow[]>([]);
  const [previewItems, setPreviewItems] = useState<FetchPreviewItem[] | null>(null);
  const [fetchingPreview, setFetchingPreview] = useState(false);
  const [importingAll, setImportingAll] = useState(false);
  const [savingConfig, setSavingConfig] = useState(false);

  const { data: apiConfigs = [] } = useQuery({
    queryKey: ["apiConfigs"],
    queryFn: fetchApiConfigs,
  });

  const deleteConfigMutation = useMutation({
    mutationFn: deleteApiConfig,
    onSuccess: () => {
      message.success("配置已删除");
      setSelectedConfigId(null);
      setConfigName("");
      setApiUrl("");
      setParams([]);
      queryClient.invalidateQueries({ queryKey: ["apiConfigs"] });
    },
    onError: () => message.error("删除失败"),
  });

  const loadConfig = (id: number) => {
    const cfg = apiConfigs.find((c) => c.id === id);
    if (!cfg) return;
    setSelectedConfigId(id);
    setConfigName(cfg.name);
    setApiUrl(cfg.base_url);
    try {
      const parsed: { key: string; value: string }[] = JSON.parse(cfg.params_json || "[]");
      setParams(parsed.map((p) => ({ ...p, uid: nextUid() })));
    } catch {
      setParams([]);
    }
    setPreviewItems(null);
  };

  const clearConfig = () => {
    setSelectedConfigId(null);
    setConfigName("");
    setApiUrl("");
    setParams([]);
    setPreviewItems(null);
  };

  const previewUrl = useMemo(() => buildUrl(apiUrl, params), [apiUrl, params]);

  const handleFetch = async () => {
    if (!apiUrl.trim()) { message.warning("请输入接口地址"); return; }
    setFetchingPreview(true);
    try {
      const res = await fetchProxyPreview(apiUrl, params.map(({ key, value }) => ({ key, value })));
      setPreviewItems(res.items);
      if (!res.items.length) message.warning("未获取到代理数据");
      else message.success(`预览获取 ${res.items.length} 条`);
    } catch {
      message.error("拉取失败");
    } finally {
      setFetchingPreview(false);
    }
  };

  const handleSaveConfig = async () => {
    if (!configName.trim()) { message.warning("请填写配置名称"); return; }
    if (!apiUrl.trim()) { message.warning("请填写接口地址"); return; }
    setSavingConfig(true);
    const payload = {
      name: configName,
      base_url: apiUrl,
      params_json: JSON.stringify(params.map(({ key, value }) => ({ key, value }))),
    };
    try {
      if (selectedConfigId) {
        await updateApiConfig(selectedConfigId, payload);
        message.success("配置已更新");
      } else {
        const saved = await createApiConfig(payload);
        setSelectedConfigId(saved.id);
        message.success("配置已保存");
      }
      queryClient.invalidateQueries({ queryKey: ["apiConfigs"] });
    } catch {
      message.error("保存失败");
    } finally {
      setSavingConfig(false);
    }
  };

  const handleImportAll = async () => {
    if (!previewItems?.length) return;
    setImportingAll(true);
    try {
      const lines = previewItems.map((i) => `${i.ip}:${i.port}`);
      const res = await batchImportProxies(lines, configName || "API");
      message.success(`已导入 ${res.imported} 条，后台检测中...`);
      setPreviewItems(null);
      onSuccess();
    } catch {
      message.error("导入失败");
    } finally {
      setImportingAll(false);
    }
  };

  const addParam = () => setParams((prev) => [...prev, { key: "", value: "", uid: nextUid() }]);
  const removeParam = (uid: string) => setParams((prev) => prev.filter((p) => p.uid !== uid));
  const updateParam = (uid: string, field: "key" | "value", val: string) =>
    setParams((prev) => prev.map((p) => (p.uid === uid ? { ...p, [field]: val } : p)));

  const previewColumns: ColumnsType<FetchPreviewItem & { index: number }> = [
    {
      title: "#",
      dataIndex: "index",
      width: 50,
      render: (v: number) => <span style={{ color: "#9ca3af", fontSize: 12 }}>{v + 1}</span>,
    },
    {
      title: "IP:端口",
      key: "addr",
      render: (_, r) => (
        <Typography.Text code style={{ fontSize: 12 }}>
          {r.ip}:{r.port}
        </Typography.Text>
      ),
    },
  ];

  return (
    <div>
      {/* Config selector */}
      <div style={{ display: "flex", gap: 8, marginBottom: 12, alignItems: "center" }}>
        <Select
          value={selectedConfigId}
          placeholder="选择已保存配置"
          style={{ flex: 1 }}
          allowClear
          onClear={clearConfig}
          onChange={(id) => { if (id) loadConfig(id); }}
          options={apiConfigs.map((c) => ({ value: c.id, label: c.name }))}
        />
        {selectedConfigId && (
          <Popconfirm
            title="确认删除该配置？"
            onConfirm={() => deleteConfigMutation.mutate(selectedConfigId)}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              loading={deleteConfigMutation.isPending}
            />
          </Popconfirm>
        )}
      </div>

      {/* Config name */}
      <div style={{ marginBottom: 8 }}>
        <Input
          value={configName}
          onChange={(e) => setConfigName(e.target.value)}
          placeholder="配置名称（保存时必填）"
          prefix={<span style={{ color: "#9ca3af", fontSize: 12 }}>名称</span>}
        />
      </div>

      {/* URL */}
      <div style={{ marginBottom: 8 }}>
        <Input
          value={apiUrl}
          onChange={(e) => setApiUrl(e.target.value)}
          placeholder="https://api.example.com/get_proxy"
          prefix={<span style={{ color: "#9ca3af", fontSize: 12 }}>地址</span>}
        />
      </div>

      {/* Params */}
      <div style={{ marginBottom: 8 }}>
        {params.map((p) => (
          <div key={p.uid} style={{ display: "flex", gap: 6, marginBottom: 6 }}>
            <Input
              value={p.key}
              onChange={(e) => updateParam(p.uid, "key", e.target.value)}
              placeholder="参数名"
              style={{ flex: 1 }}
            />
            <Input
              value={p.value}
              onChange={(e) => updateParam(p.uid, "value", e.target.value)}
              placeholder="参数值"
              style={{ flex: 2 }}
            />
            <Button
              size="small"
              type="text"
              icon={<MinusCircleOutlined />}
              onClick={() => removeParam(p.uid)}
              style={{ color: "#ef4444" }}
            />
          </div>
        ))}
        <Button
          type="dashed"
          icon={<PlusOutlined />}
          size="small"
          onClick={addParam}
          style={{ width: "100%" }}
        >
          添加参数
        </Button>
      </div>

      {/* Preview URL */}
      {apiUrl && (
        <div
          style={{
            padding: "6px 10px",
            background: "#f9fafb",
            borderRadius: 6,
            border: "1px solid #e5e7eb",
            fontSize: 12,
            color: "#374151",
            fontFamily: "monospace",
            wordBreak: "break-all",
            marginBottom: 10,
          }}
        >
          {previewUrl}
        </div>
      )}

      {/* Actions */}
      <Space style={{ marginBottom: 12 }}>
        <Button onClick={handleFetch} loading={fetchingPreview} icon={<SyncOutlined />}>
          预览拉取
        </Button>
        <Button onClick={handleSaveConfig} loading={savingConfig}>
          保存配置
        </Button>
      </Space>

      {/* Preview results */}
      {previewItems !== null && (
        <div>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginBottom: 8,
            }}
          >
            <span style={{ fontSize: 13, color: "#6b7280" }}>
              共 {previewItems.length} 条
            </span>
            <Button
              type="primary"
              size="small"
              loading={importingAll}
              disabled={!previewItems.length}
              onClick={handleImportAll}
            >
              全部导入
            </Button>
          </div>
          <Table
            dataSource={previewItems.map((item, i) => ({ ...item, index: i }))}
            columns={previewColumns}
            rowKey={(r) => `${r.ip}:${r.port}`}
            size="small"
            pagination={{ pageSize: 5, size: "small", showSizeChanger: false }}
            scroll={{ y: 200 }}
          />
        </div>
      )}
    </div>
  );
};

export default ImportPanel;
