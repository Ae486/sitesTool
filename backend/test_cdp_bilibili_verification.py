#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CDP模式Bilibili自动化验证脚本

功能：
1. 验证CDP浏览器连接
2. 导航到Bilibili网站
3. 检测登录状态
4. 完整的错误处理和状态输出

使用方法：
    poetry run python test_cdp_bilibili_verification.py
"""

import asyncio
import sys
import io
import time
import urllib.request
import socket
from pathlib import Path
from playwright.async_api import async_playwright, TimeoutError as PlaywrightTimeoutError

# 修复Windows CMD编码问题
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')


# ============================================================================
# 颜色输出工具
# ============================================================================
class Colors:
    """终端颜色代码"""
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'


def print_header(text: str):
    """打印标题"""
    print(f"\n{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.ENDC}")
    print(f"{Colors.BOLD}{Colors.HEADER}{text.center(70)}{Colors.ENDC}")
    print(f"{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.ENDC}\n")


def print_success(text: str):
    """打印成功信息"""
    print(f"{Colors.OKGREEN}✅ {text}{Colors.ENDC}")


def print_error(text: str):
    """打印错误信息"""
    print(f"{Colors.FAIL}❌ {text}{Colors.ENDC}")


def print_warning(text: str):
    """打印警告信息"""
    print(f"{Colors.WARNING}⚠️  {text}{Colors.ENDC}")


def print_info(text: str):
    """打印信息"""
    print(f"{Colors.OKCYAN}ℹ️  {text}{Colors.ENDC}")


def print_step(step: str, text: str):
    """打印步骤信息"""
    print(f"\n{Colors.BOLD}{step}{Colors.ENDC} {text}")


# ============================================================================
# CDP工具函数
# ============================================================================

def is_port_in_use(port: int) -> bool:
    """检查端口是否被占用"""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        try:
            s.bind(('localhost', port))
            return False
        except OSError:
            return True


def is_cdp_ready(port: int, verbose: bool = True) -> bool:
    """
    检查CDP接口是否就绪
    
    Args:
        port: CDP端口
        verbose: 是否输出详细信息
        
    Returns:
        bool: CDP是否就绪
    """
    try:
        url = f"http://localhost:{port}/json/version"
        req = urllib.request.Request(url, method='GET')
        
        with urllib.request.urlopen(req, timeout=2) as response:
            if response.status == 200:
                if verbose:
                    import json
                    data = json.loads(response.read().decode('utf-8'))
                    browser_info = data.get('Browser', 'Unknown')
                    protocol_version = data.get('Protocol-Version', 'Unknown')
                    print_info(f"CDP就绪 - {browser_info} (协议版本: {protocol_version})")
                return True
    except Exception as e:
        if verbose:
            print_warning(f"CDP接口未响应: {e}")
        return False
    
    return False


def find_browser_executable() -> tuple[str, str]:
    """
    查找已安装的浏览器可执行文件
    
    Returns:
        (browser_type, browser_path): 浏览器类型和路径
    """
    # Chrome路径
    chrome_paths = [
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        Path.home() / "AppData" / "Local" / "Google" / "Chrome" / "Application" / "chrome.exe",
    ]
    
    # Edge路径
    edge_paths = [
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        Path.home() / "AppData" / "Local" / "Microsoft" / "Edge" / "Application" / "msedge.exe",
    ]
    
    # 优先使用Chrome
    for path in chrome_paths:
        p = Path(path) if isinstance(path, str) else path
        if p.exists():
            return "chrome", str(p)
    
    # 备选Edge
    for path in edge_paths:
        p = Path(path) if isinstance(path, str) else path
        if p.exists():
            return "edge", str(p)
    
    return None, None


def start_cdp_browser(port: int = 9222) -> bool:
    """
    启动CDP模式的浏览器
    
    Args:
        port: CDP端口
        
    Returns:
        bool: 是否成功启动
    """
    import subprocess
    import tempfile
    
    browser_type, browser_path = find_browser_executable()
    
    if not browser_path:
        print_error("未找到Chrome或Edge浏览器")
        return False
    
    print_info(f"找到浏览器: {browser_type} - {browser_path}")
    
    # 使用临时用户数据目录（避免与正在运行的浏览器冲突）
    user_data_dir = Path(tempfile.gettempdir()) / f"cdp_test_{port}"
    user_data_dir.mkdir(parents=True, exist_ok=True)
    
    print_info(f"使用临时用户数据目录: {user_data_dir}")
    
    # 构建启动命令
    cmd = [
        browser_path,
        f"--remote-debugging-port={port}",
        f"--user-data-dir={user_data_dir}",
        "--no-first-run",
        "--no-default-browser-check",
    ]
    
    print_info(f"启动命令: {' '.join(cmd[:3])}...")
    
    try:
        # 启动浏览器进程（独立进程组）
        process = subprocess.Popen(
            cmd,
            creationflags=subprocess.CREATE_NEW_PROCESS_GROUP,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        
        print_info(f"浏览器进程已启动 (PID: {process.pid})")
        
        # 等待CDP就绪
        max_wait = 30
        start_time = time.time()
        
        print_info("等待CDP接口就绪...")
        
        while time.time() - start_time < max_wait:
            if is_cdp_ready(port, verbose=False):
                print_success(f"CDP接口就绪 (耗时: {time.time() - start_time:.1f}秒)")
                return True
            time.sleep(0.5)
        
        print_error(f"CDP接口未在{max_wait}秒内就绪")
        return False
        
    except Exception as e:
        print_error(f"启动浏览器失败: {e}")
        return False


# ============================================================================
# Bilibili登录检测
# ============================================================================

async def check_bilibili_login_status(page) -> dict:
    """
    检测Bilibili登录状态
    
    Args:
        page: Playwright页面对象
        
    Returns:
        dict: 包含登录状态、用户名等信息
    """
    result = {
        "logged_in": False,
        "username": None,
        "user_id": None,
        "cookies": {},
        "error": None
    }
    
    try:
        # 方法1: 检查用户中心元素
        print_info("检测方法1: 查找用户中心元素...")
        
        try:
            # 等待页面加载完成
            await page.wait_for_load_state("networkidle", timeout=10000)
            
            # 查找登录按钮或用户中心
            login_button = await page.query_selector(".nav-user-btn")
            user_center = await page.query_selector("a.nav-user-center")
            
            if user_center:
                print_success("发现用户中心元素 - 已登录")
                result["logged_in"] = True
                
                # 尝试获取用户名
                try:
                    # 多种可能的用户名选择器
                    username_selectors = [
                        ".header-entry-mini .name",
                        ".bili-avatar-text",
                        ".header-avatar-wrap .name",
                        ".nav-user-center .user-name"
                    ]
                    
                    for selector in username_selectors:
                        username_elem = await page.query_selector(selector)
                        if username_elem:
                            username = await username_elem.text_content()
                            if username and username.strip():
                                result["username"] = username.strip()
                                print_info(f"用户名: {result['username']}")
                                break
                except Exception as e:
                    print_warning(f"无法获取用户名: {e}")
            
            elif login_button:
                print_warning("发现登录按钮 - 未登录")
                result["logged_in"] = False
            else:
                print_warning("未找到登录相关元素")
        
        except PlaywrightTimeoutError:
            print_warning("页面加载超时，尝试其他方法...")
        
        # 方法2: 检查Cookies
        print_info("检测方法2: 检查关键Cookies...")
        
        try:
            context = page.context
            all_cookies = await context.cookies()
            bilibili_cookies = [c for c in all_cookies if 'bilibili' in c.get('domain', '')]
            
            # Bilibili关键登录Cookie
            key_cookies = ['SESSDATA', 'bili_jct', 'DedeUserID']
            found_cookies = {}
            
            for key in key_cookies:
                cookie = next((c for c in bilibili_cookies if c['name'] == key), None)
                if cookie:
                    found_cookies[key] = cookie['value']
                    print_success(f"找到 {key}: {cookie['value'][:20]}...")
            
            result["cookies"] = found_cookies
            
            # 如果有关键Cookie，认为已登录
            if len(found_cookies) >= 2:
                result["logged_in"] = True
                print_success(f"检测到 {len(found_cookies)} 个关键Cookie - 已登录")
                
                # 从DedeUserID获取用户ID
                if 'DedeUserID' in found_cookies:
                    result["user_id"] = found_cookies['DedeUserID']
            else:
                print_warning(f"仅找到 {len(found_cookies)} 个关键Cookie - 可能未登录")
        
        except Exception as e:
            print_warning(f"检查Cookie失败: {e}")
        
        # 方法3: 执行JavaScript检测
        print_info("检测方法3: 执行JavaScript检测...")
        
        try:
            js_result = await page.evaluate("""
                () => {
                    // 检查是否有用户信息全局变量
                    if (window.__INITIAL_STATE__ && window.__INITIAL_STATE__.isLogin) {
                        return {
                            logged_in: true,
                            method: 'global_state'
                        };
                    }
                    
                    // 检查localStorage
                    const localUser = localStorage.getItem('userInfo');
                    if (localUser) {
                        return {
                            logged_in: true,
                            method: 'localStorage',
                            data: JSON.parse(localUser)
                        };
                    }
                    
                    return { logged_in: false };
                }
            """)
            
            if js_result.get('logged_in'):
                print_success(f"JavaScript检测到已登录 (方法: {js_result.get('method')})")
                result["logged_in"] = True
        
        except Exception as e:
            print_warning(f"JavaScript检测失败: {e}")
    
    except Exception as e:
        result["error"] = str(e)
        print_error(f"登录状态检测失败: {e}")
    
    return result


# ============================================================================
# 主测试流程
# ============================================================================

async def main():
    """主测试流程"""
    
    print_header("CDP模式Bilibili自动化验证")
    
    port = 9222
    test_passed = True
    
    # ========================================================================
    # 步骤1: 检查CDP连接
    # ========================================================================
    print_step("1️⃣", "检查CDP连接")
    
    if is_port_in_use(port):
        print_info(f"端口 {port} 已被占用")
        
        if is_cdp_ready(port):
            print_success("CDP已就绪，将使用现有浏览器")
        else:
            print_error("端口被占用但CDP未响应，请关闭占用端口的程序")
            return False
    else:
        print_info(f"端口 {port} 可用")
        print_step("🚀", "启动CDP浏览器...")
        
        if not start_cdp_browser(port):
            print_error("无法启动CDP浏览器")
            return False
    
    # ========================================================================
    # 步骤2: 连接到浏览器
    # ========================================================================
    print_step("2️⃣", "连接到CDP浏览器")
    
    try:
        async with async_playwright() as p:
            cdp_endpoint = f"http://localhost:{port}"
            print_info(f"CDP端点: {cdp_endpoint}")
            
            try:
                browser = await p.chromium.connect_over_cdp(
                    endpoint_url=cdp_endpoint,
                    timeout=60000
                )
                print_success("成功连接到CDP浏览器")
            except Exception as e:
                print_error(f"连接CDP失败: {e}")
                return False
            
            # 获取浏览器上下文
            if browser.contexts:
                context = browser.contexts[0]
                print_success(f"使用现有浏览器上下文 (页面数: {len(context.pages)})")
            else:
                context = await browser.new_context()
                print_info("创建新的浏览器上下文")
            
            # ================================================================
            # 步骤3: 导航到Bilibili
            # ================================================================
            print_step("3️⃣", "导航到Bilibili网站")
            
            # 检查是否已有Bilibili页面
            bilibili_page = None
            for page in context.pages:
                if 'bilibili.com' in page.url:
                    bilibili_page = page
                    print_info(f"找到已存在的Bilibili页面: {page.url}")
                    break
            
            if not bilibili_page:
                print_info("创建新页面...")
                bilibili_page = await context.new_page()
            
            try:
                print_info("导航到 https://www.bilibili.com")
                await bilibili_page.goto(
                    "https://www.bilibili.com",
                    wait_until="domcontentloaded",
                    timeout=30000
                )
                print_success("页面加载完成")
                
                # 等待页面稳定
                await asyncio.sleep(2)
                
                # 获取页面标题
                title = await bilibili_page.title()
                print_info(f"页面标题: {title}")
            
            except PlaywrightTimeoutError:
                print_error("导航超时（30秒）")
                test_passed = False
            except Exception as e:
                print_error(f"导航失败: {e}")
                test_passed = False
            
            # ================================================================
            # 步骤4: 检测登录状态
            # ================================================================
            print_step("4️⃣", "检测登录状态")
            
            login_result = await check_bilibili_login_status(bilibili_page)
            
            print("\n" + "=" * 70)
            print_header("检测结果")
            
            if login_result["logged_in"]:
                print_success("登录状态: ✅ 已登录")
                
                if login_result["username"]:
                    print_info(f"用户名: {login_result['username']}")
                
                if login_result["user_id"]:
                    print_info(f"用户ID: {login_result['user_id']}")
                
                cookie_count = len(login_result["cookies"])
                print_info(f"关键Cookie数量: {cookie_count}/3")
                
                print("\n" + "=" * 70)
                print_success("🎉 测试通过！")
                print("\n💡 提示:")
                print("   - CDP模式正常工作")
                print("   - 浏览器连接成功")
                print("   - 登录状态已保持")
                print("   - 可以开始使用自动化功能")
                
            else:
                print_warning("登录状态: ⚠️  未登录")
                
                cookie_count = len(login_result["cookies"])
                if cookie_count > 0:
                    print_info(f"找到 {cookie_count} 个关键Cookie")
                
                print("\n" + "=" * 70)
                print_warning("⚠️  测试部分通过")
                print("\n💡 建议:")
                print("   1. 在打开的浏览器窗口中手动登录Bilibili")
                print("   2. 登录完成后，再次运行此脚本验证")
                print("   3. 或者使用CDP独立模式，预先登录后执行自动化")
                print("\n   重新运行命令:")
                print("   poetry run python test_cdp_bilibili_verification.py")
            
            if login_result["error"]:
                print_error(f"检测过程中的错误: {login_result['error']}")
            
            print("=" * 70 + "\n")
            
            # ================================================================
            # 清理
            # ================================================================
            print_step("5️⃣", "清理资源")
            
            # 不关闭页面，让用户可以继续使用
            # await bilibili_page.close()
            
            # 断开CDP连接（浏览器继续运行）
            await browser.close()
            print_success("CDP连接已断开，浏览器继续运行")
            
            print_info("测试完成，浏览器窗口保持打开状态供您使用")
    
    except Exception as e:
        print_error(f"测试过程中发生错误: {e}")
        import traceback
        print("\n详细错误信息:")
        print(traceback.format_exc())
        return False
    
    return test_passed


# ============================================================================
# 入口点
# ============================================================================

if __name__ == "__main__":
    print(f"\n{Colors.BOLD}CDP模式Bilibili自动化验证脚本{Colors.ENDC}")
    print(f"{Colors.BOLD}版本: 1.0.0{Colors.ENDC}")
    print(f"{Colors.BOLD}时间: {time.strftime('%Y-%m-%d %H:%M:%S')}{Colors.ENDC}\n")
    
    try:
        success = asyncio.run(main())
        sys.exit(0 if success else 1)
    except KeyboardInterrupt:
        print_warning("\n用户中断测试")
        sys.exit(130)
    except Exception as e:
        print_error(f"未预期的错误: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
