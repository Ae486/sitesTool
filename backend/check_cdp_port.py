"""检查CDP端口状态并提供解决方案"""
import subprocess
import sys
import urllib.request
import json


def check_port_in_use(port: int):
    """检查端口是否被占用"""
    try:
        result = subprocess.run(
            ['netstat', '-ano'],
            capture_output=True,
            text=True,
            check=True
        )
        
        lines = result.stdout.split('\n')
        for line in lines:
            if f':{port}' in line and 'LISTENING' in line:
                # 提取PID
                parts = line.split()
                if parts:
                    pid = parts[-1]
                    return True, pid
        return False, None
    except Exception as e:
        print(f"❌ 检查失败: {e}")
        return False, None


def get_process_name(pid: str):
    """根据PID获取进程名"""
    try:
        result = subprocess.run(
            ['tasklist', '/FI', f'PID eq {pid}', '/FO', 'CSV', '/NH'],
            capture_output=True,
            text=True,
            check=True
        )
        if result.stdout:
            # CSV格式: "进程名","PID","会话名","会话#","内存使用"
            parts = result.stdout.strip().split(',')
            if parts:
                return parts[0].strip('"')
        return "未知"
    except Exception as e:
        return "未知"


def check_cdp_ready(port: int):
    """检查CDP接口是否可访问"""
    try:
        url = f"http://localhost:{port}/json/version"
        req = urllib.request.Request(url, method='GET')
        with urllib.request.urlopen(req, timeout=2) as response:
            if response.status == 200:
                data = json.loads(response.read())
                return True, data
        return False, None
    except Exception:
        return False, None


def get_cdp_tabs(port: int):
    """获取CDP标签页列表"""
    try:
        url = f"http://localhost:{port}/json"
        req = urllib.request.Request(url, method='GET')
        with urllib.request.urlopen(req, timeout=2) as response:
            if response.status == 200:
                data = json.loads(response.read())
                return data
        return []
    except Exception:
        return []


def kill_process(pid: str):
    """强制结束进程"""
    try:
        subprocess.run(['taskkill', '/F', '/PID', pid], check=True)
        return True
    except Exception as e:
        print(f"❌ 结束进程失败: {e}")
        return False


def main():
    print("=" * 70)
    print("CDP 端口状态检查工具")
    print("=" * 70)
    print()
    
    # 检查常用CDP端口
    ports = [9222, 9223, 9224, 9225]
    
    occupied_ports = []
    free_ports = []
    
    for port in ports:
        print(f"检查端口 {port}...")
        print("-" * 70)
        
        # 1. 检查端口占用
        in_use, pid = check_port_in_use(port)
        
        if in_use:
            occupied_ports.append(port)
            process_name = get_process_name(pid)
            print(f"❌ 端口 {port} 已被占用")
            print(f"   PID: {pid}")
            print(f"   进程: {process_name}")
            
            # 2. 检查CDP接口
            cdp_ready, cdp_info = check_cdp_ready(port)
            if cdp_ready:
                print(f"✅ CDP接口可访问")
                if cdp_info:
                    browser = cdp_info.get('Browser', 'Unknown')
                    user_agent = cdp_info.get('User-Agent', 'Unknown')
                    print(f"   浏览器: {browser}")
                    print(f"   User-Agent: {user_agent[:60]}...")
                
                # 3. 获取标签页
                tabs = get_cdp_tabs(port)
                if tabs:
                    print(f"   当前标签页数: {len(tabs)}")
                    for i, tab in enumerate(tabs[:3], 1):  # 只显示前3个
                        title = tab.get('title', 'No Title')[:50]
                        url = tab.get('url', 'No URL')[:60]
                        print(f"      {i}. {title}")
                        print(f"         {url}")
            else:
                print(f"⚠️  端口占用但CDP接口不可访问（可能不是浏览器）")
            
            print()
            print(f"💡 如需释放端口 {port}:")
            print(f"   方法1: 手动关闭浏览器窗口")
            print(f"   方法2: 运行命令 taskkill /F /PID {pid}")
            
        else:
            free_ports.append(port)
            print(f"✅ 端口 {port} 空闲")
        
        print()
    
    # 总结
    print("=" * 70)
    print("总结")
    print("=" * 70)
    
    if occupied_ports:
        print(f"❌ 已占用端口: {', '.join(map(str, occupied_ports))}")
        print(f"✅ 空闲端口: {', '.join(map(str, free_ports)) if free_ports else '无'}")
        print()
        print("⚠️  问题说明:")
        print("   如果CDP端口被占用，自动化将连接到现有浏览器，")
        print("   而不是启动新的，这会导致headless设置失效。")
        print()
        print("💡 解决方案:")
        print("   选项1: 关闭占用端口的浏览器进程")
        print("   选项2: 在前端使用空闲端口（推荐）")
        
        if occupied_ports and free_ports:
            print()
            print(f"   🎯 推荐使用空闲端口: {free_ports[0]}")
            print(f"      在前端创建Flow时，将CDP端口设为 {free_ports[0]}")
        
        print()
        # 询问是否关闭
        if len(occupied_ports) == 1:
            port = occupied_ports[0]
            in_use, pid = check_port_in_use(port)
            if in_use:
                answer = input(f"\n是否关闭端口{port}上的进程（PID {pid}）? (y/N): ").strip().lower()
                if answer == 'y':
                    print(f"正在关闭进程 {pid}...")
                    if kill_process(pid):
                        print(f"✅ 成功关闭进程")
                        print(f"✅ 端口 {port} 现已空闲")
                    else:
                        print(f"❌ 关闭失败，请手动关闭或使用管理员权限")
    else:
        print(f"✅ 所有检查的端口都空闲: {', '.join(map(str, free_ports))}")
        print()
        print("🎉 没有端口冲突，可以正常使用CDP模式！")
        print(f"   推荐使用默认端口: 9222")
    
    print()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n用户取消")
        sys.exit(0)
