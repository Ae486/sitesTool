package com.rpacloud.execution.engine.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.nio.file.Path;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BrowserToolsTest {

    @Mock private Page page;
    @TempDir Path tempDir;

    private BrowserTools tools;

    @BeforeEach
    void setUp() {
        tools = new BrowserTools(page, tempDir);
    }

    @Test
    void navigateSuccess() {
        when(page.title()).thenReturn("Example");
        String result = tools.navigate("https://example.com");
        verify(page).navigate("https://example.com");
        assertThat(result).contains("Navigated to").contains("Example");
    }

    @Test
    void navigateFailureReturnsError() {
        doThrow(new RuntimeException("timeout")).when(page).navigate("https://bad.url");
        String result = tools.navigate("https://bad.url");
        assertThat(result).contains("Failed").contains("timeout");
    }

    @Test
    void clickSuccess() {
        String result = tools.click("#btn");
        verify(page).click("#btn");
        assertThat(result).contains("Clicked").contains("#btn");
    }

    @Test
    void clickFailureReturnsError() {
        doThrow(new RuntimeException("not found")).when(page).click("#missing");
        String result = tools.click("#missing");
        assertThat(result).contains("Failed").contains("not found");
    }

    @Test
    void typeSuccess() {
        String result = tools.type("#input", "hello");
        verify(page).fill("#input", "hello");
        assertThat(result).contains("Typed").contains("hello");
    }

    @Test
    void getTextSuccess() {
        when(page.textContent("#el")).thenReturn("  some text  ");
        String result = tools.getText("#el");
        assertThat(result).isEqualTo("some text");
    }

    @Test
    void getTextEmptyReturnsMessage() {
        when(page.textContent("#el")).thenReturn("   ");
        String result = tools.getText("#el");
        assertThat(result).contains("no text content");
    }

    @Test
    void getTextTruncatesLongContent() {
        String longText = "x".repeat(3000);
        when(page.textContent("#el")).thenReturn(longText);
        String result = tools.getText("#el");
        assertThat(result).hasSizeLessThan(2100);
        assertThat(result).endsWith("(truncated)");
    }

    @Test
    void getPageContentSuccess() {
        when(page.textContent("body")).thenReturn("Page body text");
        when(page.url()).thenReturn("https://example.com");
        String result = tools.getPageContent();
        assertThat(result).contains("Current URL: https://example.com");
        assertThat(result).contains("Page body text");
    }

    @Test
    void getPageContentTruncatesLongContent() {
        when(page.textContent("body")).thenReturn("x".repeat(5000));
        when(page.url()).thenReturn("https://example.com");
        String result = tools.getPageContent();
        assertThat(result).contains("(truncated)");
    }

    @Test
    void waitForElementSuccess() {
        String result = tools.waitForElement("#el");
        verify(page).waitForSelector(eq("#el"), any(Page.WaitForSelectorOptions.class));
        assertThat(result).contains("is now visible");
    }

    @Test
    void waitForElementTimeoutReturnsError() {
        doThrow(new RuntimeException("timeout")).when(page).waitForSelector(eq("#el"), any());
        String result = tools.waitForElement("#el");
        assertThat(result).contains("did not appear");
    }

    @Test
    void screenshotSavesFile() {
        String result = tools.screenshot();
        verify(page).screenshot(any(Page.ScreenshotOptions.class));
        assertThat(result).contains("Screenshot saved to");
        assertThat(result).contains("agent-");
    }

    @Test
    void getAttributeSuccess() {
        when(page.getAttribute("#link", "href")).thenReturn("https://example.com");
        String result = tools.getAttribute("#link", "href");
        assertThat(result).isEqualTo("https://example.com");
    }

    @Test
    void getAttributeNotFound() {
        when(page.getAttribute("#link", "data-x")).thenReturn(null);
        String result = tools.getAttribute("#link", "data-x");
        assertThat(result).contains("not found");
    }

    @Test
    void countElementsSuccess() {
        Locator locator = mock(Locator.class);
        when(page.locator(".item")).thenReturn(locator);
        when(locator.count()).thenReturn(5);
        String result = tools.countElements(".item");
        assertThat(result).contains("5 element(s)");
    }
}
