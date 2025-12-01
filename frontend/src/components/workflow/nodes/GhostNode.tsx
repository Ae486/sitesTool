/**
 * Ghost Node - Semi-transparent preview node that follows cursor
 * Performance optimized: uses CSS transform, pointer-events: none
 */
import { memo } from "react";
import { Typography } from "antd";
import {
  GlobalOutlined,
  AimOutlined,
  EditOutlined,
  CameraOutlined,
  ClockCircleOutlined,
  CodeOutlined,
  BranchesOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import { DSL_SCHEMA } from "../../../constants/dsl";

const { Text } = Typography;

// Icon mapping
const STEP_ICONS: Record<string, React.ReactNode> = {
  navigate: <GlobalOutlined />,
  click: <AimOutlined />,
  input: <EditOutlined />,
  screenshot: <CameraOutlined />,
  wait_time: <ClockCircleOutlined />,
  wait_for: <ClockCircleOutlined />,
  if_else: <BranchesOutlined />,
  loop: <ReloadOutlined />,
  loop_array: <ReloadOutlined />,
  default: <CodeOutlined />,
};

// Color mapping
const STEP_COLORS: Record<string, string> = {
  navigate: "#1890ff",
  click: "#52c41a",
  input: "#faad14",
  screenshot: "#eb2f96",
  wait_time: "#722ed1",
  wait_for: "#722ed1",
  if_else: "#fa8c16",
  loop: "#722ed1",
  loop_array: "#13c2c2",
  default: "#8c8c8c",
};

interface GhostNodeProps {
  stepType: string;
  x: number;
  y: number;
}

function GhostNode({ stepType, x, y }: GhostNodeProps) {
  const schema = DSL_SCHEMA[stepType];
  const icon = STEP_ICONS[stepType] || STEP_ICONS.default;
  const color = STEP_COLORS[stepType] || STEP_COLORS.default;

  return (
    <div
      style={{
        position: "fixed",
        left: 0,
        top: 0,
        // Use transform for smooth animation (GPU accelerated)
        transform: `translate(${x}px, ${y}px) translate(-50%, -50%)`,
        pointerEvents: "none",
        zIndex: 9999,
        opacity: 0.7,
        willChange: "transform", // Hint browser for optimization
      }}
    >
      <div
        style={{
          background: "#fff",
          borderRadius: 8,
          borderLeft: `4px solid ${color}`,
          boxShadow: `0 0 0 2px ${color}40, 0 4px 12px rgba(0,0,0,0.15)`,
          padding: "10px 12px",
          minWidth: 140,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ color, fontSize: 15 }}>{icon}</span>
          <Text strong style={{ fontSize: 12, color: "#262626" }}>
            {schema?.label || stepType}
          </Text>
        </div>
        <Text
          type="secondary"
          style={{ fontSize: 10, display: "block", marginTop: 4 }}
        >
          点击放置节点
        </Text>
      </div>
    </div>
  );
}

export default memo(GhostNode);
