"""验证登录状态并检查 Cookie"""
import asyncio
from playwright.async_api import async_playwright

async def verify_login():
    print("=" * 70)
    print("🔍 验证登录状态")
    print("=" * 70)
    
    port = 9222
    
    async with async_playwright() as p:
        browser = await p.chromium.connect_over_cdp(f"http://localhost:{port}")
        context = browser.contexts[0] if browser.contexts else await browser.new_context()
        
        # 获取所有 cookies
        all_cookies = await context.cookies()
        bilibili_cookies = [c for c in all_cookies if 'bilibili' in c.get('domain', '')]
        
        print(f"\n📊 Cookie 统计:")
        print(f"   总 Cookie: {len(all_cookies)}")
        print(f"   bilibili Cookie: {len(bilibili_cookies)}")
        
        # 查找关键登录 cookie
        key_cookies = ['SESSDATA', 'bili_jct', 'DedeUserID']
        found_login_cookies = []
        
        print(f"\n🔑 关键登录 Cookie:")
        for key in key_cookies:
            cookie = next((c for c in bilibili_cookies if c['name'] == key), None)
            if cookie:
                found_login_cookies.append(key)
                print(f"   ✅ {key}: {cookie['value'][:20]}...")
            else:
                print(f"   ❌ {key}: 未找到")
        
        # 访问页面检查
        print(f"\n📌 访问 bilibili 检查登录状态...")
        
        # 找到已有的 bilibili 页面或创建新页面
        pages = context.pages
        bilibili_page = None
        for page in pages:
            if 'bilibili.com' in page.url:
                bilibili_page = page
                break
        
        if not bilibili_page:
            bilibili_page = await context.new_page()
            await bilibili_page.goto("https://www.bilibili.com")
            await asyncio.sleep(2)
        else:
            await bilibili_page.reload()
            await asyncio.sleep(2)
        
        # 检查登录状态
        try:
            user_center = await bilibili_page.query_selector("a.nav-user-center")
            
            if user_center:
                print("   ✅✅✅ 已登录!")
                
                # 尝试获取用户名
                try:
                    username_elem = await bilibili_page.query_selector(".header-entry-mini .name")
                    if username_elem:
                        username = await username_elem.text_content()
                        print(f"   👤 用户名: {username}")
                except:
                    pass
                
                print()
                print("=" * 70)
                print("🎉 登录验证成功！")
                print()
                print("💡 现在请:")
                print("   1. 关闭浏览器")
                print("   2. 运行下一个测试验证 Cookie 持久化")
                print()
                print("   命令: poetry run python test_cookie_persistence.py")
                
            else:
                print("   ⚠️  未登录")
                print()
                print("💡 请在浏览器中登录后，再次运行此脚本:")
                print("   poetry run python verify_login.py")
        
        except Exception as e:
            print(f"   ❌ 检测登录状态时出错: {e}")
        
        await browser.close()

if __name__ == "__main__":
    asyncio.run(verify_login())
