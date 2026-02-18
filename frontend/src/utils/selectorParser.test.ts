/**
 * Tests for selectorParser utility
 */
import { describe, it, expect } from "vitest";
import { parseToSelector, parseToSelectorSimple, validateSelector, getSelectorSuggestions } from "./selectorParser";

describe("selectorParser", () => {
  describe("parseToSelector", () => {
    // CSS Selectors (from DevTools)
    describe("CSS selectors", () => {
      it("should preserve DevTools CSS selector as-is", () => {
        const result = parseToSelector("#post_2 > div > div.topic-avatar > div > a > img");
        expect(result).not.toBeNull();
        expect(result?.selector).toBe("#post_2 > div > div.topic-avatar > div > a > img");
        expect(result?.confidence).toBe("high");
      });

      it("should preserve class selector", () => {
        const result = parseToSelector(".btn-primary");
        expect(result?.selector).toBe(".btn-primary");
        expect(result?.confidence).toBe("high");
      });

      it("should preserve ID selector", () => {
        const result = parseToSelector("#submit-button");
        expect(result?.selector).toBe("#submit-button");
        expect(result?.confidence).toBe("high");
      });

      it("should preserve attribute selector", () => {
        const result = parseToSelector('[data-testid="login-btn"]');
        expect(result?.selector).toBe('[data-testid="login-btn"]');
        expect(result?.confidence).toBe("high");
      });
    });

    // HTML Elements
    describe("HTML elements", () => {
      it("should extract data-testid from HTML", () => {
        const result = parseToSelector('<button data-testid="submit">Submit</button>');
        expect(result?.selector).toBe('[data-testid="submit"]');
        expect(result?.confidence).toBe("high");
      });

      it("should extract stable ID from HTML", () => {
        const result = parseToSelector('<div id="main-content">Content</div>');
        expect(result?.selector).toBe("#main-content");
        expect(result?.confidence).toBe("high");
      });

      it("should extract name attribute from form elements", () => {
        const result = parseToSelector('<input name="username" type="text">');
        expect(result?.selector).toBe('input[name="username"]');
        expect(result?.confidence).toBe("high");
      });

      it("should extract src from img elements", () => {
        const result = parseToSelector('<img src="/images/avatar-123.png" alt="avatar">');
        expect(result?.selector).toContain("img[src");
        expect(result?.confidence).toBe("high");
      });

      it("should extract href from link elements", () => {
        const result = parseToSelector('<a href="/login">Login</a>');
        expect(result?.selector).toBe('a[href="/login"]');
        expect(result?.confidence).toBe("high");
      });

      it("should use aria-label for buttons without text", () => {
        const result = parseToSelector('<button aria-label="Close dialog"></button>');
        expect(result?.selector).toBe('[aria-label="Close dialog"]');
        expect(result?.confidence).toBe("high");
      });
    });

    // Plain text
    describe("plain text", () => {
      it("should generate text selector for short text", () => {
        const result = parseToSelector("Login");
        expect(result?.selector).toBe("text=Login");
        expect(result?.confidence).toBe("medium");
      });

      it("should return null for empty input", () => {
        expect(parseToSelector("")).toBeNull();
        expect(parseToSelector("   ")).toBeNull();
      });

      it("should return null for null input", () => {
        expect(parseToSelector(null as unknown as string)).toBeNull();
      });
    });

    // Edge cases
    describe("edge cases", () => {
      it("should handle self-closing elements", () => {
        const result = parseToSelector('<input type="text" name="email" />');
        expect(result?.selector).toBe('input[name="email"]');
      });

      it("should handle elements with special characters in attributes", () => {
        const result = parseToSelector('<div data-testid="user-profile-123">Test</div>');
        expect(result?.selector).toBe('[data-testid="user-profile-123"]');
      });

      it("should avoid dynamic-looking IDs", () => {
        // ember123 looks like a dynamic ID
        const result = parseToSelector('<div id="ember123">Content</div>');
        // Should NOT use the ID since it looks dynamic
        expect(result?.selector).not.toBe("#ember123");
      });
    });
  });

  describe("parseToSelectorSimple", () => {
    it("should return only the selector string", () => {
      const result = parseToSelectorSimple("#my-button");
      expect(result).toBe("#my-button");
    });

    it("should return null for invalid input", () => {
      expect(parseToSelectorSimple("")).toBeNull();
    });
  });

  describe("validateSelector", () => {
    it("should validate simple selectors", () => {
      const result = validateSelector("#submit");
      expect(result.valid).toBe(true);
      expect(result.warning).toBeUndefined();
    });

    it("should warn about empty selector", () => {
      const result = validateSelector("");
      expect(result.valid).toBe(false);
      expect(result.warning).toBeDefined();
    });

    it("should warn about overly long selectors", () => {
      const longSelector = "div > div > div > div > div > div > div > div > button";
      const result = validateSelector(longSelector);
      expect(result.valid).toBe(true);
      expect(result.warning).toContain("路径较长");
    });

    it("should warn about dynamic-looking IDs", () => {
      const result = validateSelector("#ember12345");
      expect(result.valid).toBe(true);
      expect(result.warning).toContain("动态");
    });

    it("should warn about nth-child selectors", () => {
      const result = validateSelector("ul > li:nth-child(3)");
      expect(result.valid).toBe(true);
      expect(result.warning).toContain("位置选择器");
    });
  });

  describe("getSelectorSuggestions", () => {
    it("should suggest simplification for long paths", () => {
      const longSelector = "div > div > div > div > div > button";
      const suggestions = getSelectorSuggestions(longSelector);
      expect(suggestions.length).toBeGreaterThan(0);
      expect(suggestions[0]).toContain("简短");
    });

    it("should return empty for simple selectors", () => {
      const suggestions = getSelectorSuggestions("#button");
      expect(suggestions).toHaveLength(0);
    });
  });
});
