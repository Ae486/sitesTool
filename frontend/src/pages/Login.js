import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
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
    const handleLogin = async (values) => {
        setLoading(true);
        try {
            const tokenResponse = await login({ username: values.username, password: values.password });
            const user = await fetchCurrentUser(tokenResponse.access_token);
            setAuth(tokenResponse.access_token, user);
            message.success("登录成功");
            navigate("/");
        }
        catch (error) {
            message.error("登录失败，请检查用户名和密码");
        }
        finally {
            setLoading(false);
        }
    };
    return (_jsx("div", { style: {
            minHeight: "100vh",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            background: "#f4f4f5",
            padding: "20px",
        }, children: _jsx(motion.div, { variants: itemVariants, initial: "hidden", animate: "show", children: _jsxs(Card, { className: "glass-panel", style: {
                    width: "100%",
                    maxWidth: "420px",
                    padding: "24px",
                }, bordered: false, children: [_jsxs("div", { style: { textAlign: "center", marginBottom: 32 }, children: [_jsx("div", { style: {
                                    width: "64px",
                                    height: "64px",
                                    background: "var(--primary-color)",
                                    borderRadius: "16px",
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                    margin: "0 auto 16px",
                                    boxShadow: "0 8px 24px rgba(0, 0, 0, 0.15)",
                                }, children: _jsx(LockOutlined, { style: { fontSize: "32px", color: "white" } }) }), _jsx(Typography.Title, { level: 2, style: { margin: 0, color: "var(--text-primary)" }, children: "\u5BFC\u822A\u7B7E\u5230\u5E73\u53F0" }), _jsx(Typography.Text, { type: "secondary", children: "\u8BF7\u767B\u5F55\u4EE5\u7EE7\u7EED" })] }), _jsxs(Form, { onFinish: handleLogin, size: "large", autoComplete: "off", children: [_jsx(Form.Item, { name: "username", rules: [{ required: true, message: "请输入用户名" }], children: _jsx(Input, { prefix: _jsx(UserOutlined, { style: { color: "var(--text-secondary)" } }), placeholder: "\u7528\u6237\u540D" }) }), _jsx(Form.Item, { name: "password", rules: [{ required: true, message: "请输入密码" }], children: _jsx(Input.Password, { prefix: _jsx(LockOutlined, { style: { color: "var(--text-secondary)" } }), placeholder: "\u5BC6\u7801" }) }), _jsx(Form.Item, { style: { marginBottom: 0 }, children: _jsx(Button, { type: "primary", htmlType: "submit", loading: loading, block: true, size: "large", style: { height: "48px", fontSize: "16px", fontWeight: 600 }, children: "\u767B\u5F55" }) })] }), _jsx("div", { style: { marginTop: 24, textAlign: "center" }, children: _jsx(Typography.Text, { type: "secondary", style: { fontSize: "12px" }, children: "\u9ED8\u8BA4\u8D26\u6237: admin / admin" }) })] }) }) }));
};
export default LoginPage;
