"""手动检查浏览器中的实际登录状态"""
import asyncio
from playwright.async_api import async_playwright

async def manual_check():
    print("=" * 70)
    print("🔍 手动验证登录状态")
    print("=" * 70)
    print()
    print("请在打开的浏览器中检查:")
    print("1. 右上角是否显示你的头像/用户名")
    print("2. 是否显示'登录'按钮（如果有，说明未登录）")
    print()
    print("⏳ 浏览器已打开，请手动检查...")
    print()
    
    port = 9222
    
    async with async_playwright() as p:
        browser = await p.chromium.connect_over_cdp(f"http://localhost:{port}")
        context = browser.contexts[0] if browser.contexts else await browser.new_context()
        
        # 获取 cookies
        all_cookies = await context.cookies()
        bilibili_cookies = [c for c in all_cookies if 'bilibili' in c.get('domain', '')]
        
        # 查找关键登录 cookie
        key_cookies = ['SESSDATA', 'bili_jct', 'DedeUserID', 'DedeUserID__ckMd5']
        
        print("📊 Cookie 详情:")
        print(f"   总 Cookie: {len(all_cookies)}")
        print(f"   bilibili Cookie: {len(bilibili_cookies)}")
        print()
        print("🔑 关键登录 Cookie:")
        
        has_all_key_cookies = True
        for key in key_cookies:
            cookie = next((c for c in bilibili_cookies if c['name'] == key), None)
            if cookie:
                print(f"   ✅ {key}: {cookie['value'][:30]}...")
            else:
                print(f"   ❌ {key}: 未找到")
                has_all_key_cookies = False
        
        print()
        if has_all_key_cookies:
            print("🎉 所有关键登录 Cookie 都存在！")
            print("   这表明登录状态已保存")
        else:
            print("⚠️  缺少某些关键 Cookie")
        
        print()
        print("=" * 70)
        print("📝 请手动确认浏览器中的登录状态:")
        print("   1. 查看右上角是否显示你的用户信息")
        print("   2. 如果显示，说明 Cookie 持久化成功！")
        print("   3. 如果未显示，可能需要刷新页面")
        print()
        
        # 打开个人中心试试
        page = await context.new_page()
        print("📌 正在访问你的个人空间...")
        
        # 从 DedeUserID 获取用户ID
        dedeuserid_cookie = next((c for c in bilibili_cookies if c['name'] == 'DedeUserID'), None)
        if dedeuserid_cookie:
            uid = dedeuserid_cookie['value']
            await page.goto(f"https://space.bilibili.com/{uid}")
            await asyncio.sleep(3)
            print(f"   ✅ 已打开个人空间: https://space.bilibili.com/{uid}")
            print("   💡 检查是否显示'编辑资料'等个人操作按钮")
        
        print()
        print("⏸️  按 Ctrl+C 结束检查")
        
        # 保持运行
        await asyncio.sleep(300)  # 5分钟
        
        await browser.close()

if __name__ == "__main__":
    try:
        asyncio.run(manual_check())
    except KeyboardInterrupt:
        print("\n✅ 检查结束")
