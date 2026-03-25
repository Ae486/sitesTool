import { Form, Input, Select, Switch, Divider, Segmented } from "antd";
import type { FormInstance } from "antd";
import { useQuery } from "@tanstack/react-query";
import { fetchProxies } from "../../api/proxy";

interface BrowserConfigFieldsProps {
  form: FormInstance;
}

const BrowserConfigFields = ({ form }: BrowserConfigFieldsProps) => {
  const { data: proxyData } = useQuery({
    queryKey: ["proxies", 0, 100],
    queryFn: () => fetchProxies(0, 100),
  });

  const activeProxies = (proxyData?.items ?? []).filter((p) => p.is_active);

  return (
    <>
      <Form.Item
        name="headless"
        label="静默模式"
        valuePropName="checked"
        tooltip="开启后浏览器在后台运行，不显示窗口"
      >
        <Switch />
      </Form.Item>

      <Form.Item
        name="browser_type"
        label="浏览器类型"
        tooltip="选择执行自动化的浏览器"
      >
        <Select
          onChange={(value) => {
            if (value !== "custom") {
              form.setFieldValue("browser_path", null);
            }
          }}
          options={[
            { label: "Chromium（全新浏览器）", value: "chromium" },
            { label: "Chrome（系统浏览器，可复用登录）", value: "chrome" },
            { label: "Edge（系统浏览器，可复用登录）", value: "edge" },
            { label: "Firefox", value: "firefox" },
            { label: "自定义路径", value: "custom" },
          ]}
        />
      </Form.Item>

      <Form.Item
        noStyle
        shouldUpdate={(prevValues, currentValues) =>
          prevValues.browser_type !== currentValues.browser_type
        }
      >
        {({ getFieldValue }) =>
          getFieldValue("browser_type") === "custom" ? (
            <Form.Item
              name="browser_path"
              label="浏览器路径"
              rules={[{ required: true, message: "请输入浏览器可执行文件路径" }]}
            >
              <Input placeholder="C:\Program Files\Google\Chrome\Application\chrome.exe" />
            </Form.Item>
          ) : null
        }
      </Form.Item>

      <Form.Item
        name="use_cdp_mode"
        label="CDP模式（独立自动化浏览器）"
        valuePropName="checked"
        tooltip="使用复制的浏览器配置，完全隔离，支持headless静默模式"
        extra={
          <span style={{ fontSize: "12px", color: "#52c41a" }}>
            ✅ <strong>首次</strong>：自动复制浏览器配置（包含书签、扩展），需登录一次网站
            <br />
            ✅ <strong>后续</strong>：完全自动化，登录状态持久保存，与日常浏览器完全隔离
            <br />
            ✅ <strong>支持</strong>：静默模式（headless）+ CDP模式 同时启用
          </span>
        }
      >
        <Switch />
      </Form.Item>

      <Form.Item
        noStyle
        shouldUpdate={(prevValues, currentValues) =>
          prevValues.use_cdp_mode !== currentValues.use_cdp_mode
        }
      >
        {({ getFieldValue }) =>
          getFieldValue("use_cdp_mode") ? (
            <>
              <Form.Item
                name="cdp_port"
                label="CDP调试端口"
                rules={[{ required: true, message: "请输入CDP端口" }]}
                tooltip="浏览器调试端口，通常为9222"
              >
                <Input type="number" placeholder="9222" />
              </Form.Item>

              <Form.Item
                name="cdp_user_data_dir"
                label="自定义配置目录（高级选项）"
                tooltip="留空则自动使用复制的专用配置。仅当需要使用特定配置时才填写。"
                extra={
                  <span style={{ fontSize: "12px", color: "#666" }}>
                    留空 = 自动复制并使用专用配置（推荐，与日常浏览器隔离）
                  </span>
                }
              >
                <Input placeholder="留空使用自动复制的专用配置（推荐）" />
              </Form.Item>
            </>
          ) : null
        }
      </Form.Item>

      <Divider style={{ borderColor: "#e4e4e7" }}>代理设置</Divider>

      <Form.Item
        name="use_proxy"
        label="使用代理"
        valuePropName="checked"
      >
        <Switch />
      </Form.Item>

      <Form.Item
        noStyle
        shouldUpdate={(prev, curr) =>
          prev.use_proxy !== curr.use_proxy ||
          prev._proxy_mode !== curr._proxy_mode
        }
      >
        {({ getFieldValue }) =>
          getFieldValue("use_proxy") ? (
            <>
              <Form.Item name="_proxy_mode" label="分配方式" initialValue="auto">
                <Segmented
                  options={[
                    { label: "自动分配", value: "auto" },
                    { label: "指定代理", value: "specific" },
                  ]}
                  onChange={(v) => {
                    if (v === "auto") form.setFieldValue("proxy_id", null);
                  }}
                />
              </Form.Item>

              {getFieldValue("_proxy_mode") === "specific" && (
                <Form.Item name="proxy_id" label="选择代理">
                  <Select
                    placeholder="选择代理节点"
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    options={activeProxies.map((p) => ({
                      value: p.id,
                      label: `${p.ip}:${p.port}`,
                      desc: `${p.region || "未知"} · ${p.avg_latency_ms}ms`,
                    }))}
                    optionRender={(option) => (
                      <div style={{ display: "flex", justifyContent: "space-between" }}>
                        <span>{option.label}</span>
                        <span style={{ color: "#71717a", fontSize: 12 }}>
                          {(option.data as any).desc}
                        </span>
                      </div>
                    )}
                  />
                </Form.Item>
              )}
            </>
          ) : null
        }
      </Form.Item>
    </>
  );
};

export default BrowserConfigFields;
