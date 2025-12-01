"""Cloudflare challenge detection and handling."""
import asyncio
import logging
import random
from typing import Optional

from playwright.async_api import Page, Frame

logger = logging.getLogger(__name__)


class CloudflareHandler:
    """
    Automatic Cloudflare challenge detection and handling.
    
    Detects:
    - Cloudflare "Just a moment" interstitial pages
    - Turnstile checkbox challenges
    - hCaptcha challenges (detection only, manual solve needed)
    
    Handles:
    - Auto-waits for JS challenge to complete
    - Auto-clicks Turnstile checkbox when detected
    """
    
    # Cloudflare challenge page indicators
    CF_PAGE_SELECTORS = [
        "#cf-wrapper",
        "#challenge-running", 
        "#challenge-form",
        ".cf-browser-verification",
        "#cf-spinner-please-wait",
        "#cf-please-wait",
    ]
    
    # Turnstile checkbox selectors (the clickable checkbox)
    TURNSTILE_SELECTORS = [
        "iframe[src*='challenges.cloudflare.com']",
        "iframe[src*='turnstile']",
        "#cf-turnstile-response",
        "[data-sitekey]",  # Turnstile widget container
    ]
    
    # Page title indicators
    CF_TITLE_KEYWORDS = [
        "just a moment",
        "checking your browser",
        "please wait",
        "attention required",
        "one more step",
    ]
    
    def __init__(
        self,
        enabled: bool = True,
        check_probability: float = 0.3,  # 30% chance to check between steps
        max_wait_time: int = 45000,  # 45 seconds max wait for challenge
        check_after_navigate: bool = True,  # Always check after navigate
    ):
        self.enabled = enabled
        self.check_probability = check_probability
        self.max_wait_time = max_wait_time
        self.check_after_navigate = check_after_navigate
        self._challenge_count = 0
    
    async def should_check(self, after_navigate: bool = False) -> bool:
        """Determine if we should check for CF challenge."""
        if not self.enabled:
            return False
        if after_navigate and self.check_after_navigate:
            return True
        return random.random() < self.check_probability
    
    async def check_and_handle(self, page: Page, timeout: Optional[int] = None) -> dict:
        """
        Check for Cloudflare challenge and handle if detected.
        
        Returns:
            dict with keys:
                - detected: bool - whether CF challenge was detected
                - handled: bool - whether it was successfully handled
                - type: str - type of challenge (page, turnstile, hcaptcha, none)
                - duration_ms: int - time spent handling
        """
        if not self.enabled:
            return {"detected": False, "handled": True, "type": "none", "duration_ms": 0}
        
        timeout = timeout or self.max_wait_time
        start_time = asyncio.get_event_loop().time()
        
        try:
            # First check: Is this a CF challenge page?
            challenge_type = await self._detect_challenge_type(page)
            
            if challenge_type == "none":
                return {"detected": False, "handled": True, "type": "none", "duration_ms": 0}
            
            logger.info(f"🛡️ Cloudflare {challenge_type} challenge detected!")
            self._challenge_count += 1
            
            # Handle based on type
            if challenge_type == "turnstile":
                handled = await self._handle_turnstile(page, timeout)
            elif challenge_type == "page":
                handled = await self._handle_page_challenge(page, timeout)
            elif challenge_type == "hcaptcha":
                logger.warning("⚠️ hCaptcha detected - requires manual intervention")
                handled = await self._wait_for_manual_solve(page, timeout)
            else:
                handled = False
            
            elapsed = int((asyncio.get_event_loop().time() - start_time) * 1000)
            
            return {
                "detected": True,
                "handled": handled,
                "type": challenge_type,
                "duration_ms": elapsed,
            }
            
        except Exception as e:
            logger.error(f"Error handling CF challenge: {e}")
            elapsed = int((asyncio.get_event_loop().time() - start_time) * 1000)
            return {"detected": True, "handled": False, "type": "error", "duration_ms": elapsed}
    
    async def _detect_challenge_type(self, page: Page) -> str:
        """Detect the type of Cloudflare challenge present."""
        try:
            # Check page title first (fastest)
            title = (await page.title()).lower()
            for keyword in self.CF_TITLE_KEYWORDS:
                if keyword in title:
                    # It's a challenge page, determine specific type
                    return await self._identify_challenge_variant(page)
            
            # Check for Turnstile iframe (can appear on any page)
            for selector in self.TURNSTILE_SELECTORS:
                try:
                    element = await page.query_selector(selector)
                    if element:
                        is_visible = await element.is_visible()
                        if is_visible:
                            return "turnstile"
                except Exception:
                    pass
            
            # Check for CF page elements
            for selector in self.CF_PAGE_SELECTORS:
                try:
                    element = await page.query_selector(selector)
                    if element:
                        is_visible = await element.is_visible()
                        if is_visible:
                            return "page"
                except Exception:
                    pass
            
            return "none"
            
        except Exception as e:
            logger.debug(f"Error detecting CF challenge: {e}")
            return "none"
    
    async def _identify_challenge_variant(self, page: Page) -> str:
        """Identify the specific variant of challenge on a CF page."""
        # Check for hCaptcha
        hcaptcha_selectors = [
            "iframe[src*='hcaptcha']",
            "#cf-hcaptcha-container",
            ".h-captcha",
        ]
        for selector in hcaptcha_selectors:
            try:
                if await page.query_selector(selector):
                    return "hcaptcha"
            except Exception:
                pass
        
        # Check for Turnstile
        for selector in self.TURNSTILE_SELECTORS:
            try:
                if await page.query_selector(selector):
                    return "turnstile"
            except Exception:
                pass
        
        # Default to page challenge (JS-based)
        return "page"
    
    async def _handle_turnstile(self, page: Page, timeout: int) -> bool:
        """Handle Turnstile checkbox challenge."""
        logger.info("🔲 Attempting to solve Turnstile checkbox...")
        
        start_time = asyncio.get_event_loop().time()
        
        while (asyncio.get_event_loop().time() - start_time) * 1000 < timeout:
            try:
                # Find Turnstile iframe
                iframe_element = None
                for selector in ["iframe[src*='challenges.cloudflare.com']", "iframe[src*='turnstile']"]:
                    iframe_element = await page.query_selector(selector)
                    if iframe_element:
                        break
                
                if not iframe_element:
                    # No iframe found, check if challenge is gone
                    if await self._detect_challenge_type(page) == "none":
                        logger.info("✅ Turnstile challenge resolved!")
                        return True
                    await asyncio.sleep(0.5)
                    continue
                
                # Get iframe content
                frame: Frame = await iframe_element.content_frame()
                if not frame:
                    await asyncio.sleep(0.5)
                    continue
                
                # Try to find and click the checkbox
                checkbox_selectors = [
                    "input[type='checkbox']",
                    ".cf-turnstile-checkbox",
                    "[role='checkbox']",
                    "label",  # Sometimes the label is clickable
                ]
                
                for selector in checkbox_selectors:
                    try:
                        checkbox = await frame.query_selector(selector)
                        if checkbox:
                            is_visible = await checkbox.is_visible()
                            if is_visible:
                                # Human-like click with small delay
                                await asyncio.sleep(random.uniform(0.3, 0.8))
                                await checkbox.click()
                                logger.info(f"✅ Clicked Turnstile checkbox ({selector})")
                                
                                # Wait for challenge to process
                                await asyncio.sleep(2)
                                
                                # Verify if solved
                                if await self._detect_challenge_type(page) == "none":
                                    return True
                                break
                    except Exception as e:
                        logger.debug(f"Checkbox click attempt failed: {e}")
                
                await asyncio.sleep(1)
                
            except Exception as e:
                logger.debug(f"Turnstile handling error: {e}")
                await asyncio.sleep(1)
        
        # Final check
        return await self._detect_challenge_type(page) == "none"
    
    async def _handle_page_challenge(self, page: Page, timeout: int) -> bool:
        """Handle JS-based page challenge (auto-completing)."""
        logger.info("⏳ Waiting for JS challenge to auto-complete...")
        
        start_time = asyncio.get_event_loop().time()
        check_interval = 1.0  # Check every second
        
        while (asyncio.get_event_loop().time() - start_time) * 1000 < timeout:
            await asyncio.sleep(check_interval)
            
            challenge_type = await self._detect_challenge_type(page)
            
            if challenge_type == "none":
                logger.info("✅ Page challenge completed!")
                return True
            elif challenge_type == "turnstile":
                # Switched to Turnstile, handle it
                return await self._handle_turnstile(page, timeout - int((asyncio.get_event_loop().time() - start_time) * 1000))
            elif challenge_type == "hcaptcha":
                logger.warning("⚠️ Challenge escalated to hCaptcha")
                return await self._wait_for_manual_solve(page, timeout - int((asyncio.get_event_loop().time() - start_time) * 1000))
        
        logger.warning(f"⚠️ Page challenge timeout after {timeout}ms")
        return False
    
    async def _wait_for_manual_solve(self, page: Page, timeout: int) -> bool:
        """Wait for manual solve of hCaptcha or other unsolvable challenges."""
        logger.warning("=" * 50)
        logger.warning("🚨 MANUAL INTERVENTION REQUIRED!")
        logger.warning("   Please solve the captcha in the browser window")
        logger.warning(f"   Timeout: {timeout/1000:.0f} seconds")
        logger.warning("=" * 50)
        
        start_time = asyncio.get_event_loop().time()
        
        while (asyncio.get_event_loop().time() - start_time) * 1000 < timeout:
            await asyncio.sleep(2)
            
            if await self._detect_challenge_type(page) == "none":
                logger.info("✅ Manual solve completed!")
                return True
        
        return False
    
    @property
    def challenge_count(self) -> int:
        """Number of challenges encountered in this session."""
        return self._challenge_count
