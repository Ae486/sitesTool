/**
 * Selector Parser Utility
 * Parses HTML elements or CSS selectors to generate optimal selectors
 *
 * Strategy:
 * 1. If user pastes a CSS selector from DevTools - use it directly (most precise)
 * 2. If user pastes HTML - generate the most unique selector possible
 */
/**
 * Parse result with confidence info
 */
export interface ParseResult {
    selector: string;
    confidence: 'high' | 'medium' | 'low';
    reason: string;
}
/**
 * Main function: Parse input and return optimal selector with confidence info
 *
 * @param input - HTML element string, CSS selector, or mixed content
 * @returns ParseResult with selector, confidence level, and reason
 */
export declare function parseToSelector(input: string): ParseResult | null;
/**
 * Simple version that just returns the selector string
 */
export declare function parseToSelectorSimple(input: string): string | null;
/**
 * Validate if a selector is likely to work
 */
export declare function validateSelector(selector: string): {
    valid: boolean;
    warning?: string;
};
/**
 * Get suggestions for improving a selector
 */
export declare function getSelectorSuggestions(selector: string): string[];
