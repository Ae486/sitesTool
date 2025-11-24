import {
  ApartmentOutlined,
  ApiOutlined,
  HistoryOutlined,
  LogoutOutlined,
  RadarChartOutlined,
} from "@ant-design/icons";
import { Avatar, Button, Layout, Menu, theme } from "antd";
import { AnimatePresence, motion } from "framer-motion";
import { pageVariants } from "../constants/animations";
import { useMemo } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import useAuthStore from "../store/auth";

const { Header, Content, Sider } = Layout;

const AppLayout = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const {
    token: { borderRadiusLG },
  } = theme.useToken();

  const selectedKey = useMemo(() => {
    if (location.pathname.startsWith("/sites")) return "/sites";
    if (location.pathname.startsWith("/flows")) return "/flows";
    if (location.pathname.startsWith("/history")) return "/history";
    return "/";
  }, [location.pathname]);

  return (
    <Layout
      style={{
        minHeight: "100vh",
        background: "var(--bg-body)",
      }}
    >
      <Sider
        breakpoint="lg"
        collapsedWidth="0"
        width={260}
        style={{
          background: "var(--bg-sidebar)",
          boxShadow: "4px 0 24px rgba(0,0,0,0.1)",
          zIndex: 10,
        }}
      >
        <div
          style={{
            color: "white",
            padding: "24px 24px",
            fontSize: "20px",
            fontWeight: 700,
            display: "flex",
            alignItems: "center",
            gap: "12px",
            background: "rgba(255,255,255,0.05)",
            marginBottom: "8px",
          }}
        >
          <div
            style={{
              width: "32px",
              height: "32px",
              background: "var(--primary-color)",
              borderRadius: "8px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <RadarChartOutlined style={{ fontSize: "18px" }} />
          </div>
          导航签到
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          style={{
            background: "transparent",
            border: "none",
            padding: "0 12px",
          }}
          items={[
            { key: "/", icon: <RadarChartOutlined />, label: "仪表盘" },
            { key: "/sites", icon: <ApartmentOutlined />, label: "站点管理" },
            { key: "/flows", icon: <ApiOutlined />, label: "自动化流程" },
            { key: "/history", icon: <HistoryOutlined />, label: "执行历史" },
          ]}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          className="glass-panel"
          style={{
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
          }}
        >
          <div style={{ fontWeight: 600, fontSize: "16px", color: "var(--text-secondary)" }}>
            {selectedKey === "/" && "概览"}
            {selectedKey === "/sites" && "站点管理"}
            {selectedKey === "/flows" && "自动化流程"}
            {selectedKey === "/history" && "执行历史"}
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: 12,
                padding: "6px 12px",
                background: "rgba(255,255,255,0.5)",
                borderRadius: "20px",
                border: "1px solid rgba(255,255,255,0.6)",
              }}
            >
              <Avatar
                size="small"
                style={{ backgroundColor: "var(--primary-color)" }}
              >
                {user?.full_name?.[0] ?? user?.email[0]?.toUpperCase() ?? "U"}
              </Avatar>
              <span style={{ fontWeight: 500, fontSize: "14px" }}>
                {user?.full_name || user?.email}
              </span>
            </div>
            <Button
              type="text"
              icon={<LogoutOutlined />}
              onClick={logout}
              style={{ color: "var(--text-secondary)" }}
            >
              退出
            </Button>
          </div>
        </Header>
        <Content style={{ margin: "24px", minHeight: 280, overflow: "hidden" }}>
          <div
            style={{
              height: "100%",
              background: "transparent",
              borderRadius: borderRadiusLG,
            }}
          >
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  );
};

export default AppLayout;
