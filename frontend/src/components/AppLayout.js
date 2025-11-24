import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { ApartmentOutlined, ApiOutlined, HistoryOutlined, LogoutOutlined, RadarChartOutlined, } from "@ant-design/icons";
import { Avatar, Button, Layout, Menu, theme } from "antd";
import { useMemo } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import useAuthStore from "../store/auth";
const { Header, Content, Sider } = Layout;
const AppLayout = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const { user, logout } = useAuthStore();
    const { token: { borderRadiusLG }, } = theme.useToken();
    const selectedKey = useMemo(() => {
        if (location.pathname.startsWith("/sites"))
            return "/sites";
        if (location.pathname.startsWith("/flows"))
            return "/flows";
        if (location.pathname.startsWith("/history"))
            return "/history";
        return "/";
    }, [location.pathname]);
    return (_jsxs(Layout, { style: {
            minHeight: "100vh",
            background: "var(--bg-body)",
        }, children: [_jsxs(Sider, { breakpoint: "lg", collapsedWidth: "0", width: 260, style: {
                    background: "var(--bg-sidebar)",
                    boxShadow: "4px 0 24px rgba(0,0,0,0.1)",
                    zIndex: 10,
                }, children: [_jsxs("div", { style: {
                            color: "white",
                            padding: "24px 24px",
                            fontSize: "20px",
                            fontWeight: 700,
                            display: "flex",
                            alignItems: "center",
                            gap: "12px",
                            background: "rgba(255,255,255,0.05)",
                            marginBottom: "8px",
                        }, children: [_jsx("div", { style: {
                                    width: "32px",
                                    height: "32px",
                                    background: "var(--primary-color)",
                                    borderRadius: "8px",
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                }, children: _jsx(RadarChartOutlined, { style: { fontSize: "18px" } }) }), "\u5BFC\u822A\u7B7E\u5230"] }), _jsx(Menu, { theme: "dark", mode: "inline", selectedKeys: [selectedKey], style: {
                            background: "transparent",
                            border: "none",
                            padding: "0 12px",
                        }, items: [
                            { key: "/", icon: _jsx(RadarChartOutlined, {}), label: "仪表盘" },
                            { key: "/sites", icon: _jsx(ApartmentOutlined, {}), label: "站点管理" },
                            { key: "/flows", icon: _jsx(ApiOutlined, {}), label: "自动化流程" },
                            { key: "/history", icon: _jsx(HistoryOutlined, {}), label: "执行历史" },
                        ], onClick: ({ key }) => navigate(key) })] }), _jsxs(Layout, { children: [_jsxs(Header, { className: "glass-panel", style: {
                            padding: "0 32px",
                            margin: "16px 24px 0",
                            borderRadius: "16px",
                            display: "flex",
                            justifyContent: "space-between",
                            alignItems: "center",
                            height: "64px",
                            position: "sticky",
                            top: 16,
                            zIndex: 9,
                        }, children: [_jsxs("div", { style: { fontWeight: 600, fontSize: "16px", color: "var(--text-secondary)" }, children: [selectedKey === "/" && "概览", selectedKey === "/sites" && "站点管理", selectedKey === "/flows" && "自动化流程", selectedKey === "/history" && "执行历史"] }), _jsxs("div", { style: { display: "flex", alignItems: "center", gap: 16 }, children: [_jsxs("div", { style: {
                                            display: "flex",
                                            alignItems: "center",
                                            gap: 12,
                                            padding: "6px 12px",
                                            background: "rgba(255,255,255,0.5)",
                                            borderRadius: "20px",
                                            border: "1px solid rgba(255,255,255,0.6)",
                                        }, children: [_jsx(Avatar, { size: "small", style: { backgroundColor: "var(--primary-color)" }, children: user?.full_name?.[0] ?? user?.email[0]?.toUpperCase() ?? "U" }), _jsx("span", { style: { fontWeight: 500, fontSize: "14px" }, children: user?.full_name || user?.email })] }), _jsx(Button, { type: "text", icon: _jsx(LogoutOutlined, {}), onClick: logout, style: { color: "var(--text-secondary)" }, children: "\u9000\u51FA" })] })] }), _jsx(Content, { style: { margin: "24px", minHeight: 280, overflow: "hidden" }, children: _jsx("div", { style: {
                                height: "100%",
                                background: "transparent",
                                borderRadius: borderRadiusLG,
                            }, children: _jsx(Outlet, {}) }) })] })] }));
};
export default AppLayout;
