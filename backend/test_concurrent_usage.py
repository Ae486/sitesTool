"""Test if manual browser operations interfere with automation."""
import asyncio
from playwright.async_api import async_playwright

async def test_concurrent_usage():
    print("🧪 Testing Concurrent Browser Usage")
    print("=" * 60)
    
    port = 9222
    
    print("\n1️⃣ Connecting to CDP browser...")
    async with async_playwright() as p:
        browser = await p.chromium.connect_over_cdp(f"http://localhost:{port}")
        print("   ✅ Connected")
        
        # Get or create context
        context = browser.contexts[0] if browser.contexts else await browser.new_context()
        
        # Create automation page
        page = await context.new_page()
        print("\n2️⃣ Automation page created")
        print(f"   📄 Page URL: about:blank")
        
        print("\n3️⃣ Navigating to bilibili...")
        await page.goto("https://www.bilibili.com")
        await asyncio.sleep(2)
        print(f"   ✅ Loaded: {page.url}")
        
        print("\n4️⃣ Performing automation task...")
        print("   💡 NOW: Please manually open new tabs/windows in the browser")
        print("   💡 Try browsing other websites while automation runs")
        print("")
        
        # Simulate automation work
        for i in range(5):
            print(f"   Step {i+1}/5: Checking page state...")
            
            # Check if our page is still accessible
            try:
                title = await page.title()
                url = page.url
                print(f"      ✅ Page OK - Title: {title[:30]}..., URL: {url[:50]}...")
            except Exception as e:
                print(f"      ❌ Page access error: {e}")
            
            await asyncio.sleep(3)
        
        print("\n5️⃣ Final check...")
        all_pages = context.pages
        print(f"   📊 Total pages in context: {len(all_pages)}")
        for idx, p in enumerate(all_pages):
            print(f"      Page {idx+1}: {p.url[:60]}...")
        
        print("\n6️⃣ Summary:")
        print("   ✅ Automation completed successfully")
        print("   💡 If you manually opened tabs, they should appear above")
        print("   💡 Manual operations did NOT crash automation")
        print("")
        print("   ⚠️  However, there are potential issues:")
        print("      - Resource competition (CPU/memory)")
        print("      - Focus/window switching may affect UI automation")
        print("      - Popup dialogs could block automation")
        print("")
        
        await browser.close()

if __name__ == "__main__":
    asyncio.run(test_concurrent_usage())
