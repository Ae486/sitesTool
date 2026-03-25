import { useMutation } from "@tanstack/react-query";
import {
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Segmented,
  Select,
  Space,
  Tabs,
  message,
} from "antd";
import { useState } from "react";
import { addProxy, batchImportProxies } from "../../../api/proxy";
import ImportPanel from "./ImportPanel";

interface AddModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const AddModal = ({ open, onClose, onSuccess }: AddModalProps) => {
  const [activeTab, setActiveTab] = useState<string>("manual");
  const [inputMode, setInputMode] = useState<string>("single");
  const [batchText, setBatchText] = useState("");
  const [singleForm] = Form.useForm();

  const handleClose = () => {
    singleForm.resetFields();
    setBatchText("");
    setActiveTab("manual");
    setInputMode("single");
    onClose();
  };

  const addMutation = useMutation({
    mutationFn: addProxy,
    onSuccess: () => { message.success("代理已添加，后台检测中..."); onSuccess(); singleForm.resetFields(); },
    onError: () => message.error("添加失败"),
  });

  const batchMutation = useMutation({
    mutationFn: ({ lines, provider }: { lines: string[]; provider: string }) =>
      batchImportProxies(lines, provider),
    onSuccess: (data) => {
      message.success(`已导入 ${data.imported} 条，跳过 ${data.skipped} 条，后台检测中...`);
      onSuccess();
      setBatchText("");
    },
    onError: () => message.error("批量导入失败"),
  });

  const handleOk = () => {
    if (activeTab === "manual") {
      if (inputMode === "single") {
        singleForm.validateFields().then((v) => addMutation.mutate(v));
      } else {
        const lines = batchText.split("\n").map((l) => l.trim()).filter(Boolean);
        if (!lines.length) { message.warning("请输入至少一条代理"); return; }
        batchMutation.mutate({ lines, provider: "manual" });
      }
    }
  };

  const isLoading = addMutation.isPending || batchMutation.isPending;

  return (
    <Modal
      title="添加代理"
      open={open}
      onCancel={handleClose}
      footer={
        activeTab === "manual" ? (
          <Space>
            <Button onClick={handleClose}>取消</Button>
            <Button type="primary" loading={isLoading} onClick={handleOk}>
              确认导入
            </Button>
          </Space>
        ) : null
      }
      width={640}
      centered
    >
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        style={{ marginTop: 8 }}
        items={[
          {
            key: "manual",
            label: "手动添加",
            children: (
              <div>
                <Segmented
                  value={inputMode}
                  onChange={setInputMode}
                  options={[
                    { label: "单条", value: "single" },
                    { label: "批量", value: "batch" },
                  ]}
                  style={{ marginBottom: 16 }}
                />
                {inputMode === "single" ? (
                  <Form form={singleForm} layout="vertical">
                    <Row gutter={16}>
                      <Col span={14}>
                        <Form.Item
                          name="ip"
                          label="IP 地址"
                          rules={[{ required: true, message: "请输入 IP" }]}
                        >
                          <Input placeholder="192.168.1.1" />
                        </Form.Item>
                      </Col>
                      <Col span={10}>
                        <Form.Item
                          name="port"
                          label="端口"
                          rules={[{ required: true, message: "请输入端口" }]}
                        >
                          <InputNumber
                            min={1}
                            max={65535}
                            style={{ width: "100%" }}
                            placeholder="8080"
                          />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Row gutter={16}>
                      <Col span={12}>
                        <Form.Item name="protocol" label="协议" initialValue="http">
                          <Select
                            options={[
                              { value: "http", label: "HTTP" },
                              { value: "https", label: "HTTPS" },
                              { value: "socks5", label: "SOCKS5" },
                            ]}
                          />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item name="region" label="地区（可选）">
                          <Input placeholder="CN" />
                        </Form.Item>
                      </Col>
                    </Row>
                  </Form>
                ) : (
                  <div>
                    <Input.TextArea
                      value={batchText}
                      onChange={(e) => setBatchText(e.target.value)}
                      rows={8}
                      placeholder={"每行一条，支持以下格式：\nIP:PORT\nIP:PORT:PROTOCOL\nIP:PORT:PROTOCOL:REGION\n\n例如：\n103.210.22.17:3128\n1.2.3.4:8080:socks5:CN"}
                      style={{ fontFamily: "monospace", fontSize: 13 }}
                    />
                    <div
                      style={{
                        marginTop: 8,
                        fontSize: 12,
                        color: "#71717a",
                        padding: "6px 10px",
                        background: "#f9fafb",
                        borderRadius: 6,
                        border: "1px solid #e5e7eb",
                      }}
                    >
                      协议与地区可省略，导入后自动在后台检测补全
                    </div>
                  </div>
                )}
              </div>
            ),
          },
          {
            key: "api",
            label: "API 拉取",
            children: (
              <ImportPanel onSuccess={onSuccess} />
            ),
          },
        ]}
      />
    </Modal>
  );
};

export default AddModal;
