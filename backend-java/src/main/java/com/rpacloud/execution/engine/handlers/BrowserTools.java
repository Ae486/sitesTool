package com.rpacloud.execution.engine.handlers;

import java.nio.file.Path;
import java.util.UUID;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

@Slf4j
public class BrowserTools {

    private final Page page;
    private final Path screenshotDir;

    public BrowserTools(Page page, Path screenshotDir) {
        this.page = page;
        this.screenshotDir = screenshotDir;
    }

    @Tool(description = "Navigate the browser to a URL. Returns the page title after navigation.")
    public String navigate(String url) {
        try {
            page.navigate(url);
            return "Navigated to " + url + ". Page title: " + page.title();
        } catch (Exception e) {
            return "Failed to navigate to " + url + ": " + e.getMessage();
        }
    }

    @Tool(description = "Click an element on the page identified by CSS selector.")
    public String click(String selector) {
        try {
            page.click(selector);
            return "Clicked element: " + selector;
        } catch (Exception e) {
            return "Failed to click " + selector + ": " + e.getMessage();
        }
    }

    @Tool(description = "Type text into an input element identified by CSS selector. Clears existing content first.")
    public String type(String selector, String text) {
        try {
            page.fill(selector, text);
            return "Typed '" + text + "' into " + selector;
        } catch (Exception e) {
            return "Failed to type into " + selector + ": " + e.getMessage();
        }
    }

    @Tool(description = "Get the text content of an element identified by CSS selector.")
    public String getText(String selector) {
        try {
            String text = page.textContent(selector);
            if (text == null || text.isBlank()) return "Element " + selector + " has no text content.";
            String trimmed = text.strip();
            if (trimmed.length() > 2000) trimmed = trimmed.substring(0, 2000) + "... (truncated)";
            return trimmed;
        } catch (Exception e) {
            return "Failed to get text from " + selector + ": " + e.getMessage();
        }
    }

    @Tool(description = "Get the main visible text content of the current page. Useful for understanding page structure.")
    public String getPageContent() {
        try {
            String text = page.textContent("body");
            if (text == null || text.isBlank()) return "Page has no visible text content.";
            String trimmed = text.strip().replaceAll("\\s+", " ");
            if (trimmed.length() > 3000) trimmed = trimmed.substring(0, 3000) + "... (truncated)";
            return "Current URL: " + page.url() + "\nPage content: " + trimmed;
        } catch (Exception e) {
            return "Failed to get page content: " + e.getMessage();
        }
    }

    @Tool(description = "Wait for an element matching the CSS selector to appear on the page. Default timeout 5 seconds.")
    public String waitForElement(String selector) {
        try {
            page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5000));
            return "Element " + selector + " is now visible.";
        } catch (Exception e) {
            return "Element " + selector + " did not appear within timeout: " + e.getMessage();
        }
    }

    @Tool(description = "Take a screenshot of the current page. Returns the file path of the saved screenshot.")
    public String screenshot() {
        try {
            String filename = "agent-" + UUID.randomUUID().toString().substring(0, 8) + ".png";
            Path path = screenshotDir.resolve(filename);
            page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(false));
            return "Screenshot saved to " + path;
        } catch (Exception e) {
            return "Failed to take screenshot: " + e.getMessage();
        }
    }

    @Tool(description = "Get the value of an attribute from an element identified by CSS selector.")
    public String getAttribute(String selector, String attributeName) {
        try {
            String value = page.getAttribute(selector, attributeName);
            if (value == null) return "Attribute '" + attributeName + "' not found on " + selector;
            return value;
        } catch (Exception e) {
            return "Failed to get attribute from " + selector + ": " + e.getMessage();
        }
    }

    @Tool(description = "Count the number of elements matching a CSS selector on the page.")
    public String countElements(String selector) {
        try {
            Locator locator = page.locator(selector);
            int count = locator.count();
            return "Found " + count + " element(s) matching " + selector;
        } catch (Exception e) {
            return "Failed to count elements for " + selector + ": " + e.getMessage();
        }
    }
}
