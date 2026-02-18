/**
 * Tests for dslToFlow utility
 */
import { describe, it, expect, beforeEach } from "vitest";
import { dslToFlow } from "./dslToFlow";
import type { FlowDSL } from "../../../types/flow";
import { START_NODE_ID } from "../nodes";

describe("dslToFlow", () => {
  beforeEach(() => {
    // Reset any state if needed
  });

  describe("empty DSL", () => {
    it("should return empty arrays for empty DSL", () => {
      const dsl: FlowDSL = { version: 1, steps: [] };
      const { nodes, edges } = dslToFlow(dsl);
      expect(nodes).toHaveLength(0);
      expect(edges).toHaveLength(0);
    });
  });

  describe("linear flow", () => {
    it("should convert a simple linear flow", () => {
      const dsl: FlowDSL = {
        version: 1,
        steps: [
          { type: "navigate", url: "https://example.com" },
          { type: "click", selector: "#login" },
          { type: "input", selector: "#username", value: "test" },
        ],
      };

      const { nodes, edges } = dslToFlow(dsl);

      // Should have 3 nodes (one for each step)
      expect(nodes).toHaveLength(3);

      // Each node should have correct type
      expect(nodes[0].data.stepType).toBe("navigate");
      expect(nodes[1].data.stepType).toBe("click");
      expect(nodes[2].data.stepType).toBe("input");

      // Should have 2 edges connecting them linearly
      expect(edges).toHaveLength(2);
      expect(edges[0].source).toBe(nodes[0].id);
      expect(edges[0].target).toBe(nodes[1].id);
      expect(edges[1].source).toBe(nodes[1].id);
      expect(edges[1].target).toBe(nodes[2].id);
    });

    it("should preserve field values in nodes", () => {
      const dsl: FlowDSL = {
        version: 1,
        steps: [
          { type: "navigate", url: "https://example.com", wait_until: "networkidle" },
          { type: "click", selector: "#btn", timeout: 5000 },
        ],
      };

      const { nodes } = dslToFlow(dsl);

      expect(nodes[0].data.fields.url).toBe("https://example.com");
      expect(nodes[0].data.fields.wait_until).toBe("networkidle");
      expect(nodes[1].data.fields.selector).toBe("#btn");
      expect(nodes[1].data.fields.timeout).toBe(5000);
    });
  });

  describe("container steps", () => {
    it("should convert a loop step with children", () => {
      const dsl: FlowDSL = {
        version: 1,
        steps: [
          {
            type: "loop",
            times: 3,
            children: [
              { type: "click", selector: ".item" },
              { type: "wait_time", duration: 1000 },
            ],
          },
        ],
      };

      const { nodes, edges } = dslToFlow(dsl);

      // Should have nodes for the loop and its children
      expect(nodes.length).toBeGreaterThanOrEqual(1);
      
      // First node should be the loop
      const loopNode = nodes[0];
      expect(loopNode.data.stepType).toBe("loop");
      expect(loopNode.data.fields.times).toBe(3);
      
      // Loop should have childrenData for display
      expect(loopNode.data.childrenData).toBeDefined();
      expect(loopNode.data.childrenData).toHaveLength(2);
    });

    it("should convert an if_else step with children and else_children", () => {
      const dsl: FlowDSL = {
        version: 1,
        steps: [
          {
            type: "if_else",
            condition_type: "element_exists",
            condition_selector: "#popup",
            children: [
              { type: "click", selector: "#close-popup" },
            ],
            else_children: [
              { type: "wait_time", duration: 500 },
            ],
          },
        ],
      };

      const { nodes, edges } = dslToFlow(dsl);

      expect(nodes.length).toBeGreaterThanOrEqual(1);
      
      const ifElseNode = nodes[0];
      expect(ifElseNode.data.stepType).toBe("if_else");
      
      // Should have both branches in data
      expect(ifElseNode.data.childrenData).toBeDefined();
      expect(ifElseNode.data.elseChildrenData).toBeDefined();
    });
  });

  describe("layout", () => {
    it("should apply layout when requested", () => {
      const dsl: FlowDSL = {
        version: 1,
        steps: [
          { type: "navigate", url: "https://example.com" },
          { type: "click", selector: "#btn" },
        ],
      };

      const { nodes } = dslToFlow(dsl, { applyLayout: true });

      // After layout, nodes should have position
      expect(nodes[0].position).toBeDefined();
      expect(nodes[0].position.x).toBeGreaterThanOrEqual(0);
      expect(nodes[0].position.y).toBeGreaterThanOrEqual(0);
    });
  });
});
