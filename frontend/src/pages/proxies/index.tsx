import { useEffect, useState } from "react";
import { PlusOutlined } from "@ant-design/icons";
import { Button, Typography } from "antd";
import { motion } from "framer-motion";
import { containerVariants, itemVariants } from "../../constants/animations";
import { ProxyProvider, useProxyContext } from "./context";
import StatsRow from "./components/StatsRow";
import Toolbar from "./components/Toolbar";
import DataTable from "./components/DataTable";
import AddModal from "./components/AddModal";
import DetailDrawer from "./components/DetailDrawer";

const ProxiesPageInner = () => {
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [detailProxyId, setDetailProxyId] = useState<number | null>(null);
  const { invalidateAll, scheduleRefreshes, setOnRowClick } = useProxyContext();

  useEffect(() => {
    setOnRowClick(() => (id: number) => setDetailProxyId(id));
    return () => setOnRowClick(undefined);
  }, [setOnRowClick]);

  return (
    <motion.div variants={containerVariants} initial="hidden" animate="show">
      <motion.div
        variants={itemVariants}
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 24,
        }}
      >
        <Typography.Title level={4} style={{ margin: 0 }}>
          IP 代理池
        </Typography.Title>
        <Button icon={<PlusOutlined />} size="large" onClick={() => setAddModalOpen(true)}>
          添加代理
        </Button>
      </motion.div>

      <StatsRow />

      <motion.div
        variants={itemVariants}
        className="glass-panel"
        style={{ padding: 24, borderRadius: 16 }}
      >
        <Toolbar />
        <DataTable />
      </motion.div>

      <AddModal
        open={addModalOpen}
        onClose={() => setAddModalOpen(false)}
        onSuccess={() => { setAddModalOpen(false); invalidateAll(); scheduleRefreshes(); }}
      />

      <DetailDrawer
        proxyId={detailProxyId}
        open={detailProxyId !== null}
        onClose={() => setDetailProxyId(null)}
      />
    </motion.div>
  );
};

const ProxiesPage = () => (
  <ProxyProvider>
    <ProxiesPageInner />
  </ProxyProvider>
);

export default ProxiesPage;
