/**
 * Node Palette - Draggable list of available step types
 * Optimized with memoization and better UX
 */
import { useState, useMemo, useCallback, memo } from "react";
import { Input, Typography, Tooltip, Collapse } from "antd";
import { 
  SearchOutlined,
  GlobalOutlined, 
  AimOutlined, 
  EditOutlined, 
  CameraOutlined,
  ClockCircleOutlined,
  CodeOutlined,
  BranchesOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  FileAddOutlined,
  SwapOutlined,
} from "@ant-design/icons";
import { DSL_SCHEMA, isContainerType } from "../../../constants/dsl";

const { Text } = Typography;

// Icon mapping for step types
const STEP_ICONS: Record<string, React.ReactNode> = {
  navigate: <GlobalOutlined style={{ color: "#1890ff" }} />,
  click: <AimOutlined style={{ color: "#52c41a" }} />,
  input: <EditOutlined style={{ color: "#faad14" }} />,
  screenshot: <CameraOutlined style={{ color: "#eb2f96" }} />,
  wait_time: <ClockCircleOutlined style={{ color: "#722ed1" }} />,
  wait_for: <ClockCircleOutlined style={{ color: "#722ed1" }} />,
  if_else: <BranchesOutlined style={{ color: "#fa8c16" }} />,
  loop: <ReloadOutlined style={{ color: "#722ed1" }} />,
  loop_array: <ReloadOutlined style={{ color: "#13c2c2" }} />,
  extract: <SearchOutlined style={{ color: "#13c2c2" }} />,
  assert_text: <CheckCircleOutlined style={{ color: "#52c41a" }} />,
  new_tab: <FileAddOutlined style={{ color: "#1890ff" }} />,
  switch_tab: <SwapOutlined style={{ color: "#1890ff" }} />,
  default: <CodeOutlined style={{ color: "#8c8c8c" }} />,
};

// Category definitions
const CATEGORIES = [
  { key: "basic", title: "基础操作", types: ["navigate", "click", "input", "select", "checkbox", "hover", "scroll"] },
  { key: "wait", title: "等待检测", types: ["wait_time", "wait_for", "random_delay"] },
  { key: "data", title: "数据提取", types: ["extract", "extract_all", "screenshot"] },
  { key: "flow", title: "流程控制", types: ["if_else", "loop", "loop_array", "if_exists"] },
  { key: "advanced", title: "高级功能", types: ["eval_js", "keyboard", "set_variable", "try_click"] },
  { key: "assert", title: "断言验证", types: ["assert_text", "assert_visible"] },
  { key: "tabs", title: "多标签页", types: ["new_tab", "switch_tab", "close_tab"] },
];

// Single palette item component
const PaletteItem = memo(function PaletteItem({
  type,
  onAdd,
}: {
  type: string;
  onAdd: (type: string) => void;
}) {
  const schema = DSL_SCHEMA[type];
  if (!schema) return null;

  const icon = STEP_ICONS[type] || STEP_ICONS.default;
  const isContainer = isContainerType(type);

  const handleDragStart = useCallback(
    (e: React.DragEvent) => {
      e.dataTransfer.setData("application/stepType", type);
      e.dataTransfer.effectAllowed = "move";
      // Add visual feedback
      const target = e.currentTarget as HTMLElement;
      target.style.opacity = "0.5";
    },
    [type]
  );

  const handleDragEnd = useCallback((e: React.DragEvent) => {
    const target = e.currentTarget as HTMLElement;
    target.style.opacity = "1";
  }, []);

  return (
    <Tooltip title={schema.description} placement="right" mouseEnterDelay={0.5}>
      <div
        draggable
        onClick={() => onAdd(type)}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          padding: "6px 10px",
          marginBottom: 2,
          borderRadius: 6,
          cursor: "grab",
          background: "#fff",
          border: "1px solid #f0f0f0",
          borderLeft: isContainer ? "3px solid #fa8c16" : "1px solid #f0f0f0",
          transition: "all 0.15s ease",
          userSelect: "none",
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.background = "#f5f5f5";
          e.currentTarget.style.borderColor = "#d9d9d9";
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.background = "#fff";
          e.currentTarget.style.borderColor = "#f0f0f0";
        }}
      >
        <span style={{ fontSize: 14, lineHeight: 1 }}>{icon}</span>
        <Text style={{ fontSize: 12 }}>{schema.label}</Text>
      </div>
    </Tooltip>
  );
});

interface NodePaletteProps {
  onAddNode: (stepType: string, position?: { x: number; y: number }) => void;
}

export default function NodePalette({ onAddNode }: NodePaletteProps) {
  const [search, setSearch] = useState("");

  // Filter categories by search (memoized)
  const filteredCategories = useMemo(() => {
    if (!search.trim()) return CATEGORIES;
    
    const searchLower = search.toLowerCase();
    return CATEGORIES.map((cat) => ({
      ...cat,
      types: cat.types.filter((type) => {
        const schema = DSL_SCHEMA[type];
        if (!schema) return false;
        return (
          type.includes(searchLower) ||
          schema.label.toLowerCase().includes(searchLower) ||
          schema.description.toLowerCase().includes(searchLower)
        );
      }),
    })).filter((cat) => cat.types.length > 0);
  }, [search]);

  // Collapse items for Ant Design Collapse
  const collapseItems = useMemo(
    () =>
      filteredCategories.map((cat) => ({
        key: cat.key,
        label: (
          <Text style={{ fontSize: 12, fontWeight: 500 }}>
            {cat.title}
            <Text type="secondary" style={{ marginLeft: 4, fontSize: 11 }}>
              ({cat.types.length})
            </Text>
          </Text>
        ),
        children: (
          <div style={{ marginTop: -8 }}>
            {cat.types.map((type) => (
              <PaletteItem key={type} type={type} onAdd={onAddNode} />
            ))}
          </div>
        ),
      })),
    [filteredCategories, onAddNode]
  );

  return (
    <div style={{ padding: "8px 8px 8px 8px" }}>
      <Input
        prefix={<SearchOutlined style={{ color: "#bfbfbf" }} />}
        placeholder="搜索步骤..."
        size="small"
        allowClear
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        style={{ marginBottom: 8 }}
      />

      <Collapse
        ghost
        size="small"
        defaultActiveKey={["basic", "flow"]}
        items={collapseItems}
        style={{ background: "transparent" }}
      />

      <div
        style={{
          marginTop: 12,
          padding: "8px",
          background: "#e6f4ff",
          borderRadius: 6,
          fontSize: 11,
          color: "#1890ff",
        }}
      >
        💡 拖拽节点到画布，或点击快速添加
      </div>
    </div>
  );
}
