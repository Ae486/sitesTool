import { Card, Col, Row, Statistic } from "antd";
import { motion } from "framer-motion";
import { itemVariants } from "../../../constants/animations";
import { useProxyContext } from "../context";

const statsConfig = [
  { title: "总数", key: "total" as const, color: "#18181b" },
  { title: "活跃", key: "active" as const, color: "#16a34a" },
  { title: "失效", key: "inactive" as const, color: "#dc2626" },
  { title: "池容量", key: "pool_size" as const, color: "#71717a" },
];

const StatsRow = () => {
  const { stats } = useProxyContext();

  return (
    <Row gutter={[24, 24]} style={{ marginBottom: 24 }}>
      {statsConfig.map((card) => (
        <Col xs={12} sm={12} md={6} key={card.key}>
          <motion.div variants={itemVariants}>
            <Card className="glass-card" bordered={false}>
              <Statistic
                title={<span style={{ color: "var(--text-secondary)" }}>{card.title}</span>}
                value={stats?.[card.key] ?? 0}
                valueStyle={{ color: card.color, fontWeight: "bold" }}
              />
            </Card>
          </motion.div>
        </Col>
      ))}
    </Row>
  );
};

export default StatsRow;
