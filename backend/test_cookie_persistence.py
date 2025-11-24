"""测试独立浏览器中的Cookie持久化"""
import asyncio
from pathlib import Path
from playwright.async_api import async_playwright

async def test_cookie_persistence():
    print("🧪 测试 Cookie 持久化")
    print("=" * 70)
    
    port = 9222
    
    # 使用独立配置目录
    profile_dir = Path.home() / "AppData" / "Roaming" / "autoTool" / "cdp_browser_profile"
    
    print(f"📁 配置目录: {profile_dir}")
    print()
    
    # Check if this is first time
    is_first_time = not (profile_dir / "Default").exists()
    
    if is_first_time:
        print("⚠️  这是首次使用，需要登录")
    else:
        print("✅ 配置目录已存在，检查是否有保存的登录状态")
    
    print()
    print("=" * 70)
    print()
    
    # Start browser if not running
    from app.services.automation.browser_launcher import is_cdp_ready, get_browser_manager
    
    if not is_cdp_ready(port):
        print("📌 启动浏览器...")
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
    print("📌 连接到浏览器并检查 Cookie...")
    print()
    
    async with async_playwright() as p:
        browser = await p.chromium.connect_over_cdp(f"http://localhost:{port}")
        context = browser.contexts[0] if browser.contexts else await browser.new_context()
        
        # 获取当前所有 cookies
        all_cookies = await context.cookies()
        print(f"📊 当前总 Cookie 数量: {len(all_cookies)}")
        
        # 检查 bilibili cookies
        bilibili_cookies = [c for c in all_cookies if 'bilibili' in c.get('domain', '')]
        print(f"🎯 bilibili Cookie 数量: {len(bilibili_cookies)}")
        
        if bilibili_cookies:
            print()
            print("✅ 发现 bilibili cookies，列出前5个:")
            for cookie in bilibili_cookies[:5]:
                print(f"   - {cookie['name']}: {cookie['value'][:20]}... (domain: {cookie['domain']})")
        
        print()
        print("📌 访问 bilibili 检查登录状态...")
        
        page = await context.new_page()
        await page.goto("https://www.bilibili.com")
        await asyncio.sleep(3)
        
        # 检查登录状态
        try:
            # 查找用户中心元素（登录后才有）
            user_center = await page.query_selector("a.nav-user-center")
            
            if user_center:
                print("   ✅✅✅ 已登录 bilibili！")
                
                # 尝试获取用户名
                try:
                    username_elem = await page.query_selector(".header-entry-mini .name")
                    if username_elem:
                        username = await username_elem.text_content()
                        print(f"   👤 用户名: {username}")
                except:
                    pass
                
                print()
                print("=" * 70)
                print("🎉 结论: Cookie 持久化成功！")
                print("   ✅ 在这个独立浏览器中登录的状态被保存了")
                print("   ✅ 重新启动浏览器会自动登录")
                print("   ✅ 完全自动化实现！")
                
            else:
                # 未登录
                print("   ⚠️  未登录 bilibili")
                print()
                
                if is_first_time:
                    print("=" * 70)
                    print("📝 首次使用 - 请现在登录:")
                    print("   1. 在打开的浏览器窗口中点击右上角登录")
                    print("   2. 扫码或输入账号密码登录")
                    print("   3. 登录成功后，关闭这个脚本")
                    print()
                    print("💡 下次运行时，请再次执行此脚本验证 Cookie 持久化")
                    print()
                    print("⏳ 等待你登录... (手动关闭脚本)")
                    
                    # 等待用户登录
                    while True:
                        await asyncio.sleep(5)
                        # 重新检查
                        await page.reload()
                        await asyncio.sleep(2)
                        user_center = await page.query_selector("a.nav-user-center")
                        if user_center:
                            print()
                            print("🎉 检测到登录成功！")
                            print("   ✅ Cookie 已保存")
                            print("   ✅ 现在可以关闭浏览器")
                            print("   ✅ 下次运行会自动登录")
                            break
                else:
                    print("=" * 70)
                    print("⚠️  奇怪：配置存在但未登录")
                    print("   可能原因:")
                    print("   1. Cookie 已过期")
                    print("   2. 网站清除了会话")
                    print("   3. 之前未成功登录")
                    print()
                    print("💡 建议：重新登录一次")
        
        except Exception as e:
            print(f"   ❌ 检测登录状态时出错: {e}")
        
        # 再次获取 cookies（可能登录后增加了）
        print()
        print("📌 登录后 Cookie 统计:")
        all_cookies_after = await context.cookies()
        bilibili_cookies_after = [c for c in all_cookies_after if 'bilibili' in c.get('domain', '')]
        
        print(f"   总 Cookie: {len(all_cookies)} → {len(all_cookies_after)}")
        print(f"   bilibili Cookie: {len(bilibili_cookies)} → {len(bilibili_cookies_after)}")
        
        if len(bilibili_cookies_after) > len(bilibili_cookies):
            print(f"   ✅ 增加了 {len(bilibili_cookies_after) - len(bilibili_cookies)} 个 bilibili cookies")
        
        await browser.close()

if __name__ == "__main__":
    asyncio.run(test_cookie_persistence())
