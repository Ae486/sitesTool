/**
 * Selector Parser Utility
 * Parses HTML elements or CSS selectors to generate optimal selectors
 *
 * Strategy:
 * 1. If user pastes a CSS selector from DevTools - use it directly (most precise)
 * 2. If user pastes HTML - generate the most unique selector possible
 */
/**
 * Escape special characters for CSS selector attribute values
 */
function escapeAttrValue(str) {
    return str.replace(/["\\]/g, '\\$&');
}
/**
 * Parse HTML string to extract element
 */
function parseHtmlString(html) {
    try {
        const cleanHtml = html.trim();
        const parser = new DOMParser();
        const doc = parser.parseFromString(cleanHtml, 'text/html');
        return doc.body?.firstElementChild || null;
    }
    catch {
        return null;
    }
}
/**
 * Generate the most unique selector from an Element
 * Priority: Use attributes that are most likely to be unique
 */
function generateSelectorFromElement(el) {
    const tag = el.tagName.toLowerCase();
    // 1. data-testid (designed for testing, always unique)
    const testId = el.getAttribute('data-testid');
    if (testId) {
        return {
            selector: `[data-testid="${escapeAttrValue(testId)}"]`,
            confidence: 'high',
            reason: 'data-testid 是最稳定的选择器'
        };
    }
    // 2. ID (if looks stable)
    const id = el.id;
    if (id && !/^\d+$/.test(id) && !/^(ember|react|vue|ng|_)/.test(id)) {
        return {
            selector: `#${CSS.escape(id)}`,
            confidence: 'high',
            reason: 'ID 选择器通常是唯一的'
        };
    }
    // 3. name attribute (for form elements)
    const name = el.getAttribute('name');
    if (name) {
        return {
            selector: `${tag}[name="${escapeAttrValue(name)}"]`,
            confidence: 'high',
            reason: 'name 属性在表单中通常唯一'
        };
    }
    // 4. For images: use src (most unique)
    if (tag === 'img') {
        const src = el.getAttribute('src');
        if (src) {
            // Use partial src match for flexibility (contains)
            const srcPart = src.includes('/') ? src.split('/').pop() : src;
            if (srcPart && srcPart.length > 5) {
                return {
                    selector: `img[src*="${escapeAttrValue(srcPart)}"]`,
                    confidence: 'high',
                    reason: '图片 src 包含唯一文件名'
                };
            }
            return {
                selector: `img[src="${escapeAttrValue(src)}"]`,
                confidence: 'high',
                reason: '完整 src 路径匹配'
            };
        }
    }
    // 5. For links: use href
    if (tag === 'a') {
        const href = el.getAttribute('href');
        if (href && href.length < 100 && !href.startsWith('javascript:') && !href.startsWith('#')) {
            return {
                selector: `a[href="${escapeAttrValue(href)}"]`,
                confidence: 'high',
                reason: 'href 属性匹配'
            };
        }
    }
    // 6. aria-label (accessible and usually unique)
    const ariaLabel = el.getAttribute('aria-label');
    if (ariaLabel) {
        return {
            selector: `${tag}[aria-label="${escapeAttrValue(ariaLabel)}"]`,
            confidence: 'high',
            reason: 'aria-label 通常描述特定功能'
        };
    }
    // 7. Text content (Playwright text= selector)
    const textContent = el.textContent?.trim();
    if (textContent && textContent.length > 0 && textContent.length < 30 && !/^\s*$/.test(textContent)) {
        return {
            selector: `text=${textContent}`,
            confidence: 'medium',
            reason: '文本匹配（注意：相同文本会冲突）'
        };
    }
    // 8. Placeholder for inputs
    const placeholder = el.getAttribute('placeholder');
    if (placeholder) {
        const type = el.getAttribute('type');
        const typeAttr = type ? `[type="${type}"]` : '';
        return {
            selector: `${tag}${typeAttr}[placeholder="${escapeAttrValue(placeholder)}"]`,
            confidence: 'medium',
            reason: 'placeholder 匹配'
        };
    }
    // 9. title attribute
    const title = el.getAttribute('title');
    if (title) {
        return {
            selector: `${tag}[title="${escapeAttrValue(title)}"]`,
            confidence: 'medium',
            reason: 'title 属性匹配'
        };
    }
    // 10. data-* attributes
    const dataAttrs = Array.from(el.attributes)
        .filter(attr => attr.name.startsWith('data-') && attr.value);
    if (dataAttrs.length > 0) {
        const attr = dataAttrs[0];
        return {
            selector: `${tag}[${attr.name}="${escapeAttrValue(attr.value)}"]`,
            confidence: 'medium',
            reason: `${attr.name} 属性匹配`
        };
    }
    // 11. Class-based (LEAST reliable - many elements share classes)
    const classes = Array.from(el.classList).filter(c => c.length > 2);
    if (classes.length > 0) {
        return {
            selector: `${tag}.${classes.slice(0, 2).join('.')}`,
            confidence: 'low',
            reason: '⚠️ 仅使用 class，可能匹配多个元素！建议从 DevTools 复制完整选择器'
        };
    }
    // 12. Just the tag (very unreliable)
    return {
        selector: tag,
        confidence: 'low',
        reason: '⚠️ 仅匹配标签名，极可能冲突！请从 DevTools 复制选择器'
    };
}
/**
 * Check if input looks like an HTML element
 */
function isHtmlElement(input) {
    const trimmed = input.trim();
    return trimmed.startsWith('<') && trimmed.includes('>');
}
/**
 * Check if input looks like a CSS selector
 */
function isCssSelector(input) {
    const trimmed = input.trim();
    // Starts with #, ., [, or tag name followed by selector chars
    return /^[#.\[]/.test(trimmed) || /^[a-z]+(\[|#|\.|\s|>|$)/i.test(trimmed);
}
/**
 * Check if an ID looks dynamically generated
 */
function isDynamicId(id) {
    if (!id)
        return true;
    // Common patterns for dynamic IDs
    return /^\d+$/.test(id) ||
        /^(ember|react|vue|ng|_)\d*/i.test(id) ||
        /^[a-f0-9]{8,}$/i.test(id); // UUID-like
}
/**
 * Main function: Parse input and return optimal selector with confidence info
 *
 * @param input - HTML element string, CSS selector, or mixed content
 * @returns ParseResult with selector, confidence level, and reason
 */
export function parseToSelector(input) {
    if (!input || typeof input !== 'string')
        return null;
    const trimmed = input.trim();
    if (!trimmed)
        return null;
    // If it's already a CSS selector from DevTools, use it directly (BEST option)
    if (isCssSelector(trimmed) && !isHtmlElement(trimmed)) {
        // Chrome DevTools selectors are precise - don't simplify them!
        return {
            selector: trimmed,
            confidence: 'high',
            reason: '✅ CSS 选择器（来自 DevTools 的选择器最精确）'
        };
    }
    // If it's HTML, parse and generate selector
    if (isHtmlElement(trimmed)) {
        const element = parseHtmlString(trimmed);
        if (element) {
            return generateSelectorFromElement(element);
        }
    }
    // Plain text - treat as text selector
    if (trimmed.length > 0 && trimmed.length < 100 && !trimmed.includes('\n')) {
        return {
            selector: `text=${trimmed}`,
            confidence: 'medium',
            reason: '文本匹配（相同文本的元素会冲突）'
        };
    }
    return null;
}
/**
 * Simple version that just returns the selector string
 */
export function parseToSelectorSimple(input) {
    const result = parseToSelector(input);
    return result?.selector ?? null;
}
/**
 * Validate if a selector is likely to work
 */
export function validateSelector(selector) {
    if (!selector)
        return { valid: false, warning: '选择器不能为空' };
    const trimmed = selector.trim();
    // Check for overly complex selectors
    if (trimmed.split(' > ').length > 7) {
        return { valid: true, warning: '选择器路径较长，DOM 结构变化可能导致失效' };
    }
    // Check for dynamic-looking IDs
    const idMatch = trimmed.match(/#([a-zA-Z0-9_-]+)/);
    if (idMatch && isDynamicId(idMatch[1])) {
        return { valid: true, warning: 'ID 看起来是动态生成的，可能不稳定' };
    }
    // Check for :nth-child which can be fragile
    if (trimmed.includes(':nth-child') || trimmed.includes(':nth-of-type')) {
        return { valid: true, warning: '使用了位置选择器，DOM 变化可能导致失效' };
    }
    return { valid: true };
}
/**
 * Get suggestions for improving a selector
 */
export function getSelectorSuggestions(selector) {
    const suggestions = [];
    if (selector.includes(' > ') && selector.split(' > ').length > 5) {
        suggestions.push('选择器路径较长，建议检查是否有更简短的唯一属性');
    }
    return suggestions;
}
