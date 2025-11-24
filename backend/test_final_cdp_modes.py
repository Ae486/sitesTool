"""测试最终CDP方案的所有模式"""
import asyncio
import sys
from pathlib import Path

# Add backend to path
backend_dir = Path(__file__).parent
sys.path.insert(0, str(backend_dir))

from app.services.automation.playwright_executor import PlaywrightExecutor

async def test_mode(mode_name, headless, use_cdp):
    """测试特定模式"""
    print("\n" + "=" * 70)
    print(f"🧪 测试模式: {mode_name}")
    print("=" * 70)
    print(f"   Headless: {headless}")
    print(f"   CDP Mode: {use_cdp}")
    print()
    
    executor = PlaywrightExecutor(
        browser_type="edge",
        headless=headless,
        browser_path=None,
        timeout=30000
    )
    
    # Simple test DSL
    steps = [
        {
            "action": "navigate",
            "params": {"url": "https://www.bilibili.com"}
        },
        {
            "action": "wait",
            "params": {"duration": 2}
        },
        {
            "action": "screenshot",
            "params": {"path": f"test_{mode_name}.png"}
        }
    ]
    
    try:
        result = await executor.execute(
            flow_id=9999,
            steps=steps,
            use_cdp_mode=use_cdp,
            cdp_port=9222,
            cdp_user_data_dir=None  # Use default (copied profile)
        )
        
        if result["success"]:
            print(f"✅ {mode_name} 测试成功!")
            print(f"   执行步骤: {len(result['steps'])}")
            print(f"   截图保存: test_{mode_name}.png")
        else:
            print(f"❌ {mode_name} 测试失败!")
            print(f"   错误: {result.get('error')}")
            
    except Exception as e:
        print(f"❌ {mode_name} 测试异常!")
        print(f"   异常: {e}")
        import traceback
        traceback.print_exc()

async def main():
    print("=" * 70)
    print("🎯 CDP模式完整测试")
    print("=" * 70)
    print()
    print("测试计划:")
    print("1. CDP模式 + 非静默（headless=False）")
    print("2. CDP模式 + 静默（headless=True）")
    print("3. 普通模式 + 静默（对照组）")
    print()
    print("⚠️  注意：CDP模式首次运行需要20-60秒复制浏览器配置")
    print()
    
    input("按Enter开始测试...")
    
    # Test 1: CDP + Non-headless
    await test_mode("CDP_NonHeadless", headless=False, use_cdp=True)
    
    # Test 2: CDP + Headless (THIS IS THE KEY TEST!)
    print("\n⏳ 等待5秒...")
    await asyncio.sleep(5)
    
    await test_mode("CDP_Headless", headless=True, use_cdp=True)
    
    # Test 3: Regular + Headless (for comparison)
    print("\n⏳ 等待5秒...")
    await asyncio.sleep(5)
    
    await test_mode("Regular_Headless", headless=True, use_cdp=False)
    
    print("\n" + "=" * 70)
    print("📊 测试总结")
    print("=" * 70)
    print()
    print("检查:")
    print("1. test_CDP_NonHeadless.png - 应该能看到bilibili页面")
    print("2. test_CDP_Headless.png - 应该能看到bilibili页面（关键！）")
    print("3. test_Regular_Headless.png - 应该能看到bilibili页面")
    print()
    print("验证CDP模式:")
    print("- CDP模式的截图应该显示登录状态（如果之前登录过）")
    print("- 普通模式的截图应该是未登录状态")
    print()
    print("✅ 如果CDP_Headless也成功，说明静默模式支持完美！")

if __name__ == "__main__":
    asyncio.run(main())
