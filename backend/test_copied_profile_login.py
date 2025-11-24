"""测试：在复制的配置中登录后，Cookie是否能持久化"""
import asyncio
import shutil
from pathlib import Path
from playwright.async_api import async_playwright

async def test_copied_profile():
    print("=" * 70)
    print("🧪 测试：复制配置后的Cookie持久化")
    print("=" * 70)
    
    source_profile = Path(r"C:\Users\55473\AppData\Local\Microsoft\Edge\User Data")
    cdp_profile = Path.home() / "AppData" / "Roaming" / "autoTool" / "cdp_browser_profile"
    
    print(f"\n📁 源配置: {source_profile}")
    print(f"📁 目标配置: {cdp_profile}")
    
    # Step 1: 删除旧的CDP配置
    print("\n" + "=" * 70)
    print("📌 Step 1: 删除旧的CDP配置...")
    if cdp_profile.exists():
        shutil.rmtree(cdp_profile)
        print("   ✅ 已删除")
    else:
        print("   ℹ️  不存在，跳过")
    
    # Step 2: 复制真实浏览器配置
    print("\n" + "=" * 70)
    print("📌 Step 2: 复制真实浏览器配置...")
    print("   这可能需要几十秒...")
    
    try:
        # 复制整个目录
        def ignore_patterns(directory, files):
            # 忽略锁定文件和临时文件
            ignore = []
            for filename in files:
                if (filename.endswith('-lock') or 
                    filename.endswith('.tmp') or 
                    filename == 'lockfile' or
                    filename == 'SingletonLock' or
                    filename == 'SingletonSocket' or
                    filename == 'SingletonCookie'):
                    ignore.append(filename)
            return ignore
        
        shutil.copytree(source_profile, cdp_profile, 
                       ignore=ignore_patterns,
                       ignore_dangling_symlinks=True,
                       dirs_exist_ok=False)
        
        print("   ✅ 复制完成")
        
        # 统计文件数
        file_count = len(list(cdp_profile.rglob('*')))
        print(f"   📊 共复制 {file_count} 个文件/目录")
        
    except Exception as e:
        print(f"   ❌ 复制失败: {e}")
        return
    
    # Step 3: 记录初始Cookie
    print("\n" + "=" * 70)
    print("📌 Step 3: 启动浏览器并记录初始Cookie...")
    
    from app.services.automation.browser_launcher import get_browser_manager
    
    port = 9222
    manager = get_browser_manager()
    
    success = manager.start_browser(
        browser_type="edge",
        port=port,
        user_data_dir=str(cdp_profile),
        headless=False,
    )
    
    if not success:
        print("   ❌ 浏览器启动失败")
        return
    
    await asyncio.sleep(5)
    
    # 连接并获取初始Cookie
    async with async_playwright() as p:
        browser = await p.chromium.connect_over_cdp(f"http://localhost:{port}")
        context = browser.contexts[0] if browser.contexts else await browser.new_context()
        
        initial_cookies = await context.cookies()
        initial_bilibili = [c for c in initial_cookies if 'bilibili' in c.get('domain', '')]
        
        print(f"   📊 初始Cookie统计:")
        print(f"      总数: {len(initial_cookies)}")
        print(f"      bilibili: {len(initial_bilibili)}")
        
        # 访问bilibili
        page = await context.new_page()
        await page.goto("https://www.bilibili.com")
        await asyncio.sleep(2)
        
        print(f"\n   ✅ 已打开 bilibili.com")
        print()
        print("=" * 70)
        print("⏰ 请在浏览器中进行登录操作")
        print("   等待 2 分钟...")
        print("=" * 70)
        
        await browser.close()
    
    # Step 4: 等待2分钟
    print("\n⏳ 倒计时 2 分钟...")
    for i in range(120, 0, -10):
        print(f"   剩余 {i} 秒...", end='\r')
        await asyncio.sleep(10)
    
    print("\n\n" + "=" * 70)
    print("📌 Step 4: 记录登录后的Cookie...")
    
    # 重新连接获取登录后的Cookie
    async with async_playwright() as p:
        browser = await p.chromium.connect_over_cdp(f"http://localhost:{port}")
        context = browser.contexts[0] if browser.contexts else await browser.new_context()
        
        after_login_cookies = await context.cookies()
        after_login_bilibili = [c for c in after_login_cookies if 'bilibili' in c.get('domain', '')]
        
        print(f"   📊 登录后Cookie统计:")
        print(f"      总数: {len(after_login_cookies)}")
        print(f"      bilibili: {len(after_login_bilibili)}")
        
        cookie_diff = len(after_login_cookies) - len(initial_cookies)
        bilibili_diff = len(after_login_bilibili) - len(initial_bilibili)
        
        if cookie_diff > 0:
            print(f"   ✅ Cookie增加: +{cookie_diff}")
        if bilibili_diff > 0:
            print(f"   ✅ bilibili Cookie增加: +{bilibili_diff}")
        
        # 查找关键登录Cookie
        key_cookies = ['SESSDATA', 'bili_jct', 'DedeUserID']
        print(f"\n   🔑 关键登录Cookie:")
        for key in key_cookies:
            cookie = next((c for c in after_login_bilibili if c['name'] == key), None)
            if cookie:
                print(f"      ✅ {key}: {cookie['value'][:30]}...")
            else:
                print(f"      ❌ {key}: 未找到")
        
        await browser.close()
    
    # Step 5: 关闭浏览器
    print("\n" + "=" * 70)
    print("📌 Step 5: 关闭浏览器...")
    
    manager.stop_browser()
    await asyncio.sleep(2)
    print("   ✅ 已关闭")
    
    # Step 6: 重新启动
    print("\n" + "=" * 70)
    print("📌 Step 6: 重新启动浏览器...")
    
    success = manager.start_browser(
        browser_type="edge",
        port=port,
        user_data_dir=str(cdp_profile),
        headless=False,
    )
    
    if not success:
        print("   ❌ 浏览器重启失败")
        return
    
    await asyncio.sleep(5)
    
    # Step 7: 验证Cookie持久化
    print("\n" + "=" * 70)
    print("📌 Step 7: 验证Cookie是否持久化...")
    
    async with async_playwright() as p:
        browser = await p.chromium.connect_over_cdp(f"http://localhost:{port}")
        context = browser.contexts[0] if browser.contexts else await browser.new_context()
        
        final_cookies = await context.cookies()
        final_bilibili = [c for c in final_cookies if 'bilibili' in c.get('domain', '')]
        
        print(f"   📊 重启后Cookie统计:")
        print(f"      总数: {len(final_cookies)}")
        print(f"      bilibili: {len(final_bilibili)}")
        
        # 对比登录后和重启后的Cookie
        print(f"\n   📈 Cookie变化:")
        print(f"      初始 → 登录后 → 重启后")
        print(f"      总数: {len(initial_cookies)} → {len(after_login_cookies)} → {len(final_cookies)}")
        print(f"      bilibili: {len(initial_bilibili)} → {len(after_login_bilibili)} → {len(final_bilibili)}")
        
        # 检查关键登录Cookie是否还在
        print(f"\n   🔑 关键登录Cookie验证:")
        all_present = True
        for key in key_cookies:
            cookie = next((c for c in final_bilibili if c['name'] == key), None)
            if cookie:
                print(f"      ✅ {key}: 依然存在")
            else:
                print(f"      ❌ {key}: 丢失！")
                all_present = False
        
        # 访问个人空间验证登录
        print(f"\n   📌 访问个人空间验证...")
        dedeuserid_cookie = next((c for c in final_bilibili if c['name'] == 'DedeUserID'), None)
        if dedeuserid_cookie:
            uid = dedeuserid_cookie['value']
            page = await context.new_page()
            await page.goto(f"https://space.bilibili.com/{uid}")
            await asyncio.sleep(3)
            print(f"      ✅ 已打开个人空间")
        
        print()
        print("=" * 70)
        print("📊 最终结论:")
        print("=" * 70)
        
        if len(final_bilibili) >= len(after_login_bilibili) and all_present:
            print("✅✅✅ Cookie持久化成功！")
            print()
            print("   证据:")
            print(f"   1. 登录后Cookie被保留 ({len(after_login_bilibili)} → {len(final_bilibili)})")
            print(f"   2. 关键登录Cookie依然存在")
            print(f"   3. 重启后可以访问个人空间")
            print()
            print("🎉 结论: 在复制的配置中登录后，新的Cookie可以正常保存！")
        else:
            print("❌ Cookie未能完全持久化")
            print()
            print("   可能原因:")
            print("   1. 浏览器安全机制清除了新Cookie")
            print("   2. 配置文件权限问题")
            print("   3. Cookie过期设置")
        
        await browser.close()
    
    print()
    print("🏁 测试完成！")

if __name__ == "__main__":
    asyncio.run(test_copied_profile())
