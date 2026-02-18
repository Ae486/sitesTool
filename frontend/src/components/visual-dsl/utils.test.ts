/**
 * Tests for visual-dsl utility functions
 */
import { describe, it, expect } from "vitest";
import {
  hasValue,
  ensureDslShape,
  generateId,
  addIdsToStep,
  stripIdsFromStep,
  createEmptyStep,
  shouldShowField,
} from "./utils";
import type { SortableFlowStep } from "./types";

describe("visual-dsl/utils", () => {
  describe("hasValue", () => {
    it("should return false for null and undefined", () => {
      expect(hasValue(null)).toBe(false);
      expect(hasValue(undefined)).toBe(false);
    });

    it("should return false for empty string", () => {
      expect(hasValue("")).toBe(false);
      expect(hasValue("   ")).toBe(false);
    });

    it("should return true for non-empty values", () => {
      expect(hasValue("hello")).toBe(true);
      expect(hasValue(0)).toBe(true);
      expect(hasValue(false)).toBe(true);
      expect(hasValue([])).toBe(true);
    });
  });

  describe("ensureDslShape", () => {
    it("should return default DSL for undefined", () => {
      const result = ensureDslShape(undefined);
      expect(result).toEqual({ version: 1, steps: [] });
    });

    it("should parse JSON string", () => {
      const json = JSON.stringify({ version: 2, steps: [{ type: "click" }] });
      const result = ensureDslShape(json);
      expect(result.version).toBe(2);
      expect(result.steps).toHaveLength(1);
    });

    it("should handle invalid JSON gracefully", () => {
      const result = ensureDslShape("not valid json");
      expect(result).toEqual({ version: 1, steps: [] });
    });

    it("should preserve valid DSL object", () => {
      const dsl = { version: 1, steps: [{ type: "navigate", url: "https://example.com" }] };
      const result = ensureDslShape(dsl);
      expect(result.steps).toHaveLength(1);
      expect(result.steps[0].type).toBe("navigate");
    });
  });

  describe("generateId", () => {
    it("should generate unique IDs", () => {
      const ids = new Set<string>();
      for (let i = 0; i < 100; i++) {
        ids.add(generateId());
      }
      expect(ids.size).toBe(100);
    });

    it("should generate non-empty string", () => {
      const id = generateId();
      expect(typeof id).toBe("string");
      expect(id.length).toBeGreaterThan(0);
    });
  });

  describe("addIdsToStep / stripIdsFromStep", () => {
    it("should add _id to step", () => {
      const step = { type: "click", selector: "#btn" };
      const withId = addIdsToStep(step);
      expect(withId._id).toBeDefined();
      expect(withId.type).toBe("click");
      expect(withId.selector).toBe("#btn");
    });

    it("should add IDs to nested children", () => {
      const step = {
        type: "loop",
        children: [
          { type: "click", selector: "#a" },
          { type: "input", selector: "#b", value: "test" },
        ],
      };
      const withIds = addIdsToStep(step);
      expect(withIds._id).toBeDefined();
      expect(withIds.children).toHaveLength(2);
      expect(withIds.children![0]._id).toBeDefined();
      expect(withIds.children![1]._id).toBeDefined();
    });

    it("should strip IDs from step", () => {
      const step: SortableFlowStep = { 
        _id: "test-id", 
        type: "click", 
        selector: "#btn" 
      };
      const stripped = stripIdsFromStep(step);
      expect((stripped as Record<string, unknown>)._id).toBeUndefined();
      expect(stripped.type).toBe("click");
    });

    it("should round-trip preserve data", () => {
      const original = { type: "input", selector: "#email", value: "test@example.com" };
      const withId = addIdsToStep(original);
      const stripped = stripIdsFromStep(withId);
      expect(stripped).toEqual(original);
    });
  });

  describe("createEmptyStep", () => {
    it("should create step with type and _id", () => {
      const step = createEmptyStep("click");
      expect(step.type).toBe("click");
      expect(step._id).toBeDefined();
    });

    it("should populate navigate with siteUrl if first step", () => {
      const step = createEmptyStep("navigate", "https://example.com", true);
      expect(step.type).toBe("navigate");
      expect(step.url).toBe("https://example.com");
    });

    it("should not populate url if not first step", () => {
      const step = createEmptyStep("navigate", "https://example.com", false);
      expect(step.url).toBeUndefined();
    });
  });

  describe("shouldShowField", () => {
    it("should return true if no showWhen condition", () => {
      const field = { name: "selector", type: "text" as const, label: "选择器" };
      const step: SortableFlowStep = { _id: "1", type: "click" };
      expect(shouldShowField(field, step)).toBe(true);
    });

    it("should return true if condition matches", () => {
      const field = { 
        name: "selector", 
        type: "text" as const, 
        label: "选择器",
        showWhen: ["element_exists", "text_contains"]
      };
      const step: SortableFlowStep = { _id: "1", type: "if_else", condition_type: "element_exists" };
      expect(shouldShowField(field, step)).toBe(true);
    });

    it("should return false if condition does not match", () => {
      const field = { 
        name: "selector", 
        type: "text" as const, 
        label: "选择器",
        showWhen: ["element_exists"]
      };
      const step: SortableFlowStep = { _id: "1", type: "if_else", condition_type: "url_matches" };
      expect(shouldShowField(field, step)).toBe(false);
    });
  });
});
