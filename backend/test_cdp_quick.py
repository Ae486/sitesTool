#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CDP快速验证脚本 - 简化版

快速验证CDP连接和Bilibili登录状态
适合日常快速检查

使用方法：
    poetry run python test_cdp_quick.py
"""

import asyncio
import sys
import io
from playwright.async_api import async_playwright

# 修复Windows CMD编码问题
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')


async def quick_test():
    """快速测试CDP连接和Bilibili登录"""
    
    port = 9222
    
    print("\n" + "=" * 60)
    print("🚀 CDP快速验证".center(60))
    print("=" * 60 + "\n")
    
    try:
        async with async_playwright() as p:
            # 连接CDP
            print(f"📡 连接到CDP端口 {port}...")
            browser = await p.chromium.connect_over_cdp(
                f"http://localhost:{port}",
                timeout=10000
            )
            print("✅ CDP连接成功\n")
            
            # 获取上下文
            context = browser.contexts[0] if browser.contexts else await browser.new_context()
            
            # 查找或创建Bilibili页面
            bilibili_page = None
            for page in context.pages:
                if 'bilibili.com' in page.url:
                    bilibili_page = page
                    print(f"📄 找到Bilibili页面: {page.url}")
                    break
            
            if not bilibili_page:
                print("📄 创建新页面...")
                bilibili_page = await context.new_page()
                print("🌐 导航到 https://www.bilibili.com ...")
                await bilibili_page.goto("https://www.bilibili.com", timeout=15000)
            else:
                print("🔄 刷新页面...")
                await bilibili_page.reload()
            
            # 等待页面稳定
            await asyncio.sleep(2)
            
            # 检查登录状态
            print("\n🔍 检测登录状态...")
            
            # 方法1: 检查Cookie
            all_cookies = await context.cookies()
            bilibili_cookies = [c for c in all_cookies if 'bilibili' in c.get('domain', '')]
            key_cookies = ['SESSDATA', 'bili_jct', 'DedeUserID']
            found_keys = [k for k in key_cookies if any(c['name'] == k for c in bilibili_cookies)]
            
            print(f"   Cookie: {len(found_keys)}/3 关键Cookie")
            for key in found_keys:
                print(f"   ✅ {key}")
            
            # 方法2: 检查DOM元素
            user_center = await bilibili_page.query_selector("a.nav-user-center")
            login_button = await bilibili_page.query_selector(".nav-user-btn")
            
            print(f"\n📊 检测结果:")
            print("=" * 60)
            
            if user_center or len(found_keys) >= 2:
                print("✅ 状态: 已登录")
                
                # 尝试获取用户名
                try:
                    username_elem = await bilibili_page.query_selector(".header-entry-mini .name")
                    if username_elem:
                        username = await username_elem.text_content()
                        print(f"👤 用户: {username.strip()}")
                except:
                    pass
                
                print("\n🎉 测试通过！CDP模式工作正常")
                
            else:
                print("⚠️  状态: 未登录")
                print("\n💡 请在浏览器中登录后重新运行测试")
            
            print("=" * 60 + "\n")
            
            # 断开连接
            await browser.close()
            print("✅ CDP连接已断开，浏览器继续运行\n")
            
            return user_center is not None or len(found_keys) >= 2
    
    except Exception as e:
        print(f"\n❌ 错误: {e}\n")
        print("💡 可能的原因:")
        print("   1. CDP浏览器未启动")
        print("   2. 端口9222被其他程序占用")
        print("   3. 网络连接问题\n")
        print("🔧 解决方案:")
        print("   启动CDP浏览器:")
        print('   chrome.exe --remote-debugging-port=9222\n')
        return False


if __name__ == "__main__":
    try:
        success = asyncio.run(quick_test())
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        print("\n⚠️  用户中断\n")
        sys.exit(130)
