import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { Button, Card, Form, Input, Typography, message } from "antd";
import { motion } from "framer-motion";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { login, fetchCurrentUser } from "../api/auth";
import useAuthStore from "../store/auth";

import { itemVariants } from "../constants/animations";

const LoginPage = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);

  const handleLogin = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const tokenResponse = await login({ username: values.username, password: values.password });
      const user = await fetchCurrentUser(tokenResponse.access_token);
      setAuth(tokenResponse.access_token, user);
      message.success("登录成功");
      navigate("/");
    } catch (error) {
      message.error("登录失败，请检查用户名和密码");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "#f4f4f5",
        padding: "20px",
      }}
    >
      <motion.div
        variants={itemVariants}
        initial="hidden"
        animate="show"
      >
        <Card
          className="glass-panel"
          style={{
            width: "100%",
            maxWidth: "420px",
            padding: "24px",
          }}
          bordered={false}
        >
          <div style={{ textAlign: "center", marginBottom: 32 }}>
            <div
              style={{
                width: "64px",
                height: "64px",
                background: "var(--primary-color)",
                borderRadius: "16px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                margin: "0 auto 16px",
                boxShadow: "0 8px 24px rgba(0, 0, 0, 0.15)",
              }}
            >
              <LockOutlined style={{ fontSize: "32px", color: "white" }} />
            </div>
            <Typography.Title level={2} style={{ margin: 0, color: "var(--text-primary)" }}>
              导航签到平台
            </Typography.Title>
            <Typography.Text type="secondary">请登录以继续</Typography.Text>
          </div>

          <Form onFinish={handleLogin} size="large" autoComplete="off">
            <Form.Item
              name="username"
              rules={[{ required: true, message: "请输入用户名" }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: "var(--text-secondary)" }} />}
                placeholder="用户名"
              />
            </Form.Item>
            <Form.Item
              name="password"
              rules={[{ required: true, message: "请输入密码" }]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: "var(--text-secondary)" }} />}
                placeholder="密码"
              />
            </Form.Item>
            <Form.Item style={{ marginBottom: 0 }}>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                block
                size="large"
                style={{ height: "48px", fontSize: "16px", fontWeight: 600 }}
              >
                登录
              </Button>
            </Form.Item>
          </Form>

          <div style={{ marginTop: 24, textAlign: "center" }}>
            <Typography.Text type="secondary" style={{ fontSize: "12px" }}>
              默认账户: admin / admin
            </Typography.Text>
          </div>
        </Card>
      </motion.div>
    </div>
  );
};

export default LoginPage;
