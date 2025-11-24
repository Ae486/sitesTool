"""演示独立CDP模式 - 完整流程"""
import asyncio
from playwright.async_api import async_playwright

async def demo_isolated_mode():
    print("🎯 独立CDP模式演示")
    print("=" * 70)
    print()
    
    port = 9222
    
    # 检查浏览器是否运行
    from app.services.automation.browser_launcher import is_cdp_ready, get_browser_manager
    
    if not is_cdp_ready(port):
        print("📌 Step 1: 启动独立自动化浏览器...")
        print()
        manager = get_browser_manager()
        success = manager.start_browser(
            browser_type="edge",
            port=port,
            user_data_dir=None,  # 使用默认独立配置
            headless=False,
        )
        
        if not success:
            print("❌ 浏览器启动失败")
            return
        
        await asyncio.sleep(3)
    
    print()
    print("📌 Step 2: 连接到CDP浏览器...")
    
    async with async_playwright() as p:
        browser = await p.chromium.connect_over_cdp(f"http://localhost:{port}")
        context = browser.contexts[0] if browser.contexts else await browser.new_context()
        
        print("   ✅ 已连接")
        print()
        
        print("📌 Step 3: 检查是否需要首次登录...")
        page = await context.new_page()
        
        # 访问 bilibili
        await page.goto("https://www.bilibili.com")
        await asyncio.sleep(2)
        
        # 检查登录状态
        try:
            login_button = await page.query_selector("a.nav-user-center")
            if login_button:
                # 已登录
                print("   ✅ 已登录 bilibili")
                username_elem = await page.query_selector(".header-entry-mini .name")
                if username_elem:
                    username = await username_elem.text_content()
                    print(f"   👤 用户: {username}")
                else:
                    print("   👤 已登录（未显示用户名）")
            else:
                # 未登录
                print("   ⚠️  未登录 bilibili")
                print()
                print("   📝 请在打开的浏览器窗口中：")
                print("      1. 点击右上角登录按钮")
                print("      2. 扫码或输入账号密码登录")
                print("      3. 登录后关闭浏览器")
                print()
                print("   💡 提示：登录状态会自动保存，以后无需再登录！")
        except Exception as e:
            print(f"   ℹ️  无法检测登录状态: {e}")
        
        print()
        print("📌 Step 4: 演示自动化操作...")
        
        # 简单的自动化操作
        await page.goto("https://www.bilibili.com/v/popular/all")
        await asyncio.sleep(2)
        
        # 获取热门视频标题
        try:
            titles = await page.query_selector_all(".video-card__info .title")
            print(f"   📊 找到 {len(titles[:5])} 个热门视频:")
            for i, title_elem in enumerate(titles[:5], 1):
                title = await title_elem.text_content()
                print(f"      {i}. {title.strip()}")
        except Exception as e:
            print(f"   ℹ️  获取视频列表失败: {e}")
        
        print()
        print("=" * 70)
        print("✅ 演示完成！")
        print()
        print("💡 总结:")
        print("   - 这是一个独立的自动化浏览器")
        print("   - 你可以同时打开日常Edge浏览器，互不影响")
        print("   - 登录状态会永久保存")
        print("   - 下次运行会自动登录")
        print()
        print("🎉 享受完全自动化的CDP模式！")
        
        await browser.close()

if __name__ == "__main__":
    asyncio.run(demo_isolated_mode())
