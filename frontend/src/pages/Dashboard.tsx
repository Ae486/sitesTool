import { useQuery } from "@tanstack/react-query";
import { Card, Col, List, Row, Statistic, Tag, Typography } from "antd";
import { motion } from "framer-motion";
import { fetchFlows } from "../api/flows";
import { fetchSites } from "../api/sites";

import { containerVariants, itemVariants } from "../constants/animations";

const DashboardPage = () => {
  const { data: sites } = useQuery({ queryKey: ["sites"], queryFn: fetchSites });
  const { data: flows } = useQuery({ queryKey: ["flows"], queryFn: fetchFlows });
  const siteItems = sites?.items ?? [];
  const flowItems = flows?.items ?? [];
  const successfulFlows = flowItems.filter((flow) => flow.last_status === "success").length;

  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="show"
      style={{ display: "flex", flexDirection: "column", gap: 24 }}
    >
      <Row gutter={[24, 24]}>
        <Col span={8}>
          <motion.div variants={itemVariants}>
            <Card className="glass-card" bordered={false}>
              <Statistic
                title={<span style={{ color: "var(--text-secondary)" }}>已配置站点</span>}
                value={sites?.total ?? 0}
                valueStyle={{ color: "var(--primary-color)", fontWeight: "bold" }}
              />
            </Card>
          </motion.div>
        </Col>
        <Col span={8}>
          <motion.div variants={itemVariants}>
            <Card className="glass-card" bordered={false}>
              <Statistic
                title={<span style={{ color: "var(--text-secondary)" }}>自动化流程</span>}
                value={flows?.total ?? 0}
                valueStyle={{ color: "var(--secondary-color)", fontWeight: "bold" }}
              />
            </Card>
          </motion.div>
        </Col>
        <Col span={8}>
          <motion.div variants={itemVariants}>
            <Card className="glass-card" bordered={false}>
              <Statistic
                title={<span style={{ color: "var(--text-secondary)" }}>今日成功流程</span>}
                value={successfulFlows}
                suffix={
                  <span style={{ fontSize: "14px", color: "var(--text-secondary)" }}>
                    / {flows?.total ?? 0}
                  </span>
                }
                valueStyle={{ color: "var(--success-color)", fontWeight: "bold" }}
              />
            </Card>
          </motion.div>
        </Col>
      </Row>
      <Row gutter={[24, 24]}>
        <Col span={12}>
          <motion.div variants={itemVariants} style={{ height: "100%" }}>
            <Card
              title="站点最近添加"
              className="glass-card"
              bordered={false}
              style={{ height: "100%" }}
              headStyle={{ borderBottom: "1px solid rgba(0,0,0,0.05)" }}
            >
              <List
                dataSource={siteItems.slice(0, 5)}
                renderItem={(item) => (
                  <List.Item style={{ padding: "12px 0", borderBottom: "1px solid rgba(0,0,0,0.05)" }}>
                    <div style={{ display: "flex", flexDirection: "column", width: "100%" }}>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                        <Typography.Text strong style={{ fontSize: "16px" }}>
                          {item.name}
                        </Typography.Text>
                        <div style={{ display: "flex", gap: 4 }}>
                          {item.tags.map((tag) => (
                            <Tag key={tag.id} color={tag.color || "blue"} style={{ margin: 0 }}>
                              {tag.name}
                            </Tag>
                          ))}
                        </div>
                      </div>
                      <Typography.Link
                        href={item.url}
                        target="_blank"
                        rel="noreferrer"
                        style={{ color: "var(--text-secondary)", marginTop: 4 }}
                      >
                        {item.url}
                      </Typography.Link>
                    </div>
                  </List.Item>
                )}
              />
            </Card>
          </motion.div>
        </Col>
        <Col span={12}>
          <motion.div variants={itemVariants} style={{ height: "100%" }}>
            <Card
              title="流程队列"
              className="glass-card"
              bordered={false}
              style={{ height: "100%" }}
              headStyle={{ borderBottom: "1px solid rgba(0,0,0,0.05)" }}
            >
              <List
                dataSource={flowItems.slice(0, 5)}
                renderItem={(item) => (
                  <List.Item style={{ padding: "12px 0", borderBottom: "1px solid rgba(0,0,0,0.05)" }}>
                    <div style={{ display: "flex", flexDirection: "column", width: "100%" }}>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                        <Typography.Text strong style={{ fontSize: "16px" }}>
                          {item.name}
                        </Typography.Text>
                        <Tag
                          color={
                            item.last_status === "success"
                              ? "success"
                              : item.last_status === "failed"
                                ? "error"
                                : "default"
                          }
                        >
                          {item.last_status || "未执行"}
                        </Tag>
                      </div>
                      <Typography.Text type="secondary" style={{ marginTop: 4 }}>
                        调度：{item.cron_expression || "手动触发"}
                      </Typography.Text>
                    </div>
                  </List.Item>
                )}
              />
            </Card>
          </motion.div>
        </Col>
      </Row>
    </motion.div>
  );
};

export default DashboardPage;
