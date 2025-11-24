"""验证 CDP 最终方案代码是否正确"""
import re
from pathlib import Path

def check_file(file_path, checks):
    """检查文件是否包含必需的代码片段"""
    print(f"\n{'='*70}")
    print(f"检查文件: {file_path}")
    print('='*70)
    
    try:
        content = Path(file_path).read_text(encoding='utf-8')
        
        all_passed = True
        for check_name, pattern in checks.items():
            if isinstance(pattern, str):
                found = pattern in content
            else:  # regex
                found = bool(re.search(pattern, content, re.DOTALL))
            
            status = "✅" if found else "❌"
            print(f"{status} {check_name}")
            if not found:
                all_passed = False
        
        return all_passed
    except Exception as e:
        print(f"❌ 读取文件失败: {e}")
        return False

def main():
    print("="*70)
    print("CDP 最终方案代码验证")
    print("="*70)
    
    results = {}
    
    # 1. 检查 browser_launcher.py
    browser_launcher_checks = {
        "包含 shutil.copytree 调用": "shutil.copytree(",
        "包含 ignore_locked_files 函数": "def ignore_locked_files(directory, files):",
        "包含首次复制逻辑": "is_first_time = not (cdp_profile_dir / \"Default\").exists()",
        "包含完整复制逻辑": "ignore=ignore_locked_files,",
        "包含复制成功日志": "Successfully copied",
        "包含 headless 支持": "if headless:",
    }
    results['browser_launcher'] = check_file(
        "h:/autoTool/backend/app/services/automation/browser_launcher.py",
        browser_launcher_checks
    )
    
    # 2. 检查 playwright_executor.py
    playwright_executor_checks = {
        "包含 CDP Mode 日志": "🎯 CDP Mode enabled",
        "包含 headless 日志": "logger.info(f\"   Headless: {self.headless}\")",
        "正确传递 headless 参数": "headless=self.headless,  # Important",
        "包含浏览器启动逻辑": "browser_manager.start_browser(",
        "包含 CDP 连接逻辑": "connect_over_cdp",
    }
    results['playwright_executor'] = check_file(
        "h:/autoTool/backend/app/services/automation/playwright_executor.py",
        playwright_executor_checks
    )
    
    # 3. 检查前端 Flows.tsx
    flows_tsx_checks = {
        "包含正确的CDP标题": "CDP模式（独立自动化浏览器）",
        "包含首次说明": "首次.*自动复制浏览器配置",
        "包含后续说明": "后续.*完全自动化",
        "包含headless支持说明": "支持.*静默模式.*headless",
        "包含 use_cdp_mode 字段": "name=\"use_cdp_mode\"",
        "包含 cdp_port 字段": "name=\"cdp_port\"",
        "包含 cdp_user_data_dir 字段": "name=\"cdp_user_data_dir\"",
    }
    results['flows_tsx'] = check_file(
        "h:/autoTool/frontend/src/pages/Flows.tsx",
        flows_tsx_checks
    )
    
    # 总结
    print("\n" + "="*70)
    print("验证总结")
    print("="*70)
    
    all_passed = all(results.values())
    
    for file_name, passed in results.items():
        status = "✅ 通过" if passed else "❌ 失败"
        print(f"{status} - {file_name}")
    
    print()
    if all_passed:
        print("🎉 所有检查通过！CDP 最终方案代码正确。")
    else:
        print("⚠️  部分检查失败，请参考 CDP模式-最终方案.md 进行修复。")
        print()
        print("修复步骤:")
        print("1. 对比文档中的代码")
        print("2. 恢复失败的部分")
        print("3. 重新运行此验证脚本")
    
    return all_passed

if __name__ == "__main__":
    success = main()
    exit(0 if success else 1)
