"""Test CDP Mode - Verify cookies and login states work correctly."""
import asyncio
from pathlib import Path
from playwright.async_api import async_playwright

async def test_cookies():
    print("🧪 Testing CDP Mode - Cookie/Login Verification")
    print("=" * 60)
    
    # Check if CDP browser is running
    from app.services.automation.browser_launcher import is_cdp_ready, get_browser_manager
    
    port = 9222
    
    if not is_cdp_ready(port):
        print("⚠️  CDP browser not running, starting...")
        manager = get_browser_manager()
        success = manager.start_browser(
            browser_type="edge",
            port=port,
            user_data_dir=None,
            headless=False,
        )
        if not success:
            print("❌ Failed to start browser")
            return
        
        await asyncio.sleep(5)  # Wait for browser to fully start
    
    print("\n1️⃣ Connecting to CDP browser...")
    async with async_playwright() as p:
        try:
            browser = await p.chromium.connect_over_cdp(
                f"http://localhost:{port}",
                timeout=30000
            )
            print("   ✅ Connected to CDP browser")
            
            # Get existing context (with cookies)
            context = browser.contexts[0] if browser.contexts else await browser.new_context()
            print(f"   ✅ Using context with {len(context.pages)} pages")
            
            # Create new page
            page = await context.new_page()
            
            print("\n2️⃣ Testing cookie reading...")
            # Navigate to bilibili
            print("   Navigating to bilibili.com...")
            await page.goto("https://www.bilibili.com", timeout=30000)
            await asyncio.sleep(2)
            
            # Check cookies
            cookies = await context.cookies()
            print(f"   📦 Found {len(cookies)} cookies")
            
            # Look for bilibili session cookies
            bilibili_cookies = [c for c in cookies if 'bilibili' in c.get('domain', '')]
            if bilibili_cookies:
                print(f"   ✅ Found {len(bilibili_cookies)} bilibili cookies")
                for cookie in bilibili_cookies[:3]:  # Show first 3
                    print(f"      - {cookie['name']}: {cookie['value'][:20]}...")
            else:
                print("   ⚠️  No bilibili cookies found (not logged in yet)")
            
            print("\n3️⃣ Testing login state detection...")
            # Check if logged in by looking for user info
            try:
                # Wait for login indicator (if logged in)
                login_indicator = await page.query_selector(".header-entry-mini")
                if login_indicator:
                    print("   ✅ Detected login indicator on page!")
                    
                    # Try to get username
                    username_elem = await page.query_selector(".header-entry-mini .name")
                    if username_elem:
                        username = await username_elem.text_content()
                        print(f"   ✅ Logged in as: {username}")
                    else:
                        print("   ✅ Login indicator found (username not visible)")
                else:
                    print("   ℹ️  Not logged in (expected if first time)")
                    print("   💡 Please login in the opened browser window")
                    print("   💡 Cookies will be saved for future runs")
            except Exception as e:
                print(f"   ℹ️  Could not detect login state: {e}")
            
            print("\n4️⃣ Summary:")
            print("   ✅ CDP connection working")
            print("   ✅ Cookie reading working")
            print(f"   ✅ Total cookies: {len(cookies)}")
            
            if bilibili_cookies:
                print("   ✅ Login cookies detected - should work in automation!")
            else:
                print("   ⚠️  No login cookies yet - please login once")
            
            print("\n💡 Keeping browser open for inspection...")
            print("   Check the browser window to verify login state")
            print("   Close this script when done")
            
            await browser.close()
            
        except Exception as e:
            print(f"❌ Error: {e}")
            import traceback
            traceback.print_exc()

if __name__ == "__main__":
    asyncio.run(test_cookies())
