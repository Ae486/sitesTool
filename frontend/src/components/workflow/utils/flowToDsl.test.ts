/**
 * Tests for flowToDsl utility
 */
import { describe, it, expect } from "vitest";
import { flowToDsl } from "./flowToDsl";
import type { WorkflowNode, WorkflowEdge } from "../types";
import { START_NODE_ID } from "../nodes";

// Helper to create a test node
function createNode(
  id: string,
  stepType: string,
  fields: Record<string, unknown> = {},
  position = { x: 0, y: 0 }
): WorkflowNode {
  return {
    id,
    type: stepType === "loop" || stepType === "if_else" || stepType === "loop_array" 
      ? `${stepType}Node` 
      : "stepNode",
    position,
    data: {
      stepType,
      label: stepType,
      fields,
    },
  };
}

// Helper to create an edge
function createEdge(
  source: string,
  target: string,
  sourceHandle?: string
): WorkflowEdge {
  return {
    id: `edge_${source}_${target}`,
    source,
    target,
    sourceHandle,
    type: "deletable",
  };
}

describe("flowToDsl", () => {
  describe("empty flow", () => {
    it("should return empty DSL for empty nodes", () => {
      const dsl = flowToDsl([], []);
      expect(dsl.version).toBe(1);
      expect(dsl.steps).toHaveLength(0);
    });

    it("should return empty DSL for only start node", () => {
      const startNode: WorkflowNode = {
        id: START_NODE_ID,
        type: "startNode",
        position: { x: 0, y: 0 },
        data: { stepType: "__start__", label: "开始", fields: {} },
      };
      
      const dsl = flowToDsl([startNode], []);
      expect(dsl.steps).toHaveLength(0);
    });
  });

  describe("linear flow", () => {
    it("should convert a linear flow to DSL", () => {
      const nodes: WorkflowNode[] = [
        {
          id: START_NODE_ID,
          type: "startNode",
          position: { x: 0, y: 0 },
          data: { stepType: "__start__", label: "开始", fields: {} },
        },
        createNode("node1", "navigate", { url: "https://example.com" }),
        createNode("node2", "click", { selector: "#login" }),
        createNode("node3", "input", { selector: "#username", value: "test" }),
      ];

      const edges: WorkflowEdge[] = [
        createEdge(START_NODE_ID, "node1", "bottom"),
        createEdge("node1", "node2"),
        createEdge("node2", "node3"),
      ];

      const dsl = flowToDsl(nodes, edges);

      expect(dsl.version).toBe(1);
      expect(dsl.steps).toHaveLength(3);
      
      expect(dsl.steps[0].type).toBe("navigate");
      expect(dsl.steps[0].url).toBe("https://example.com");
      
      expect(dsl.steps[1].type).toBe("click");
      expect(dsl.steps[1].selector).toBe("#login");
      
      expect(dsl.steps[2].type).toBe("input");
      expect(dsl.steps[2].selector).toBe("#username");
      expect(dsl.steps[2].value).toBe("test");
    });
  });

  describe("container steps", () => {
    it("should convert loop node with children connected via body handle", () => {
      const nodes: WorkflowNode[] = [
        {
          id: START_NODE_ID,
          type: "startNode",
          position: { x: 0, y: 0 },
          data: { stepType: "__start__", label: "开始", fields: {} },
        },
        createNode("loop1", "loop", { times: 3 }),
        createNode("child1", "click", { selector: ".item" }),
        createNode("child2", "wait_time", { duration: 1000 }),
      ];

      const edges: WorkflowEdge[] = [
        createEdge(START_NODE_ID, "loop1", "bottom"),
        createEdge("loop1", "child1", "body"),  // body handle for children
        createEdge("child1", "child2"),         // sequential within loop
      ];

      const dsl = flowToDsl(nodes, edges);

      expect(dsl.steps).toHaveLength(1);
      expect(dsl.steps[0].type).toBe("loop");
      expect(dsl.steps[0].times).toBe(3);
      
      // Children should be nested
      expect(dsl.steps[0].children).toBeDefined();
      expect(dsl.steps[0].children).toHaveLength(2);
      expect(dsl.steps[0].children![0].type).toBe("click");
      expect(dsl.steps[0].children![1].type).toBe("wait_time");
    });

    it("should convert if_else node with then and else branches", () => {
      const nodes: WorkflowNode[] = [
        {
          id: START_NODE_ID,
          type: "startNode",
          position: { x: 0, y: 0 },
          data: { stepType: "__start__", label: "开始", fields: {} },
        },
        createNode("if1", "if_else", { 
          condition_type: "element_exists", 
          condition_selector: "#popup" 
        }),
        createNode("then1", "click", { selector: "#close" }),
        createNode("else1", "wait_time", { duration: 500 }),
      ];

      const edges: WorkflowEdge[] = [
        createEdge(START_NODE_ID, "if1", "bottom"),
        createEdge("if1", "then1", "then"),  // then branch
        createEdge("if1", "else1", "else"),  // else branch
      ];

      const dsl = flowToDsl(nodes, edges);

      expect(dsl.steps).toHaveLength(1);
      expect(dsl.steps[0].type).toBe("if_else");
      expect(dsl.steps[0].condition_type).toBe("element_exists");
      
      // Should have children (then branch)
      expect(dsl.steps[0].children).toBeDefined();
      expect(dsl.steps[0].children).toHaveLength(1);
      expect(dsl.steps[0].children![0].type).toBe("click");
      
      // Should have else_children
      expect(dsl.steps[0].else_children).toBeDefined();
      expect(dsl.steps[0].else_children).toHaveLength(1);
      expect(dsl.steps[0].else_children![0].type).toBe("wait_time");
    });
  });

  describe("round-trip conversion", () => {
    it("should preserve structure when converting back and forth", () => {
      // Create a flow manually
      const nodes: WorkflowNode[] = [
        {
          id: START_NODE_ID,
          type: "startNode",
          position: { x: 0, y: 0 },
          data: { stepType: "__start__", label: "开始", fields: {} },
        },
        createNode("n1", "navigate", { url: "https://example.com" }),
        createNode("n2", "click", { selector: "#btn" }),
      ];

      const edges: WorkflowEdge[] = [
        createEdge(START_NODE_ID, "n1", "bottom"),
        createEdge("n1", "n2"),
      ];

      // Convert to DSL
      const dsl = flowToDsl(nodes, edges);
      
      expect(dsl.steps).toHaveLength(2);
      expect(dsl.steps[0].type).toBe("navigate");
      expect(dsl.steps[0].url).toBe("https://example.com");
      expect(dsl.steps[1].type).toBe("click");
      expect(dsl.steps[1].selector).toBe("#btn");
    });
  });
});
