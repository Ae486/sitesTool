"""检查Flow的DSL内容"""
import sys
import json
from app.db.session import session_scope
from app.crud.flow import get_flow


def check_flow_dsl(flow_id: int):
    """检查指定Flow的DSL"""
    print("=" * 70)
    print(f"检查 Flow ID: {flow_id}")
    print("=" * 70)
    
    with session_scope() as db:
        flow = get_flow(db, flow_id)
        
        if not flow:
            print(f"❌ Flow {flow_id} 不存在")
            return
        
        print(f"\n📋 Flow 信息:")
        print(f"   ID: {flow.id}")
        print(f"   Name: {flow.name}")
        print(f"   Description: {flow.description}")
        print(f"   Headless: {flow.headless}")
        print(f"   Browser Type: {flow.browser_type}")
        print(f"   CDP Mode: {flow.use_cdp_mode}")
        if flow.use_cdp_mode:
            print(f"   CDP Port: {flow.cdp_port}")
        
        print(f"\n📝 DSL (原始):")
        print("-" * 70)
        print(flow.dsl)
        print("-" * 70)
        
        # Parse DSL
        try:
            dsl = json.loads(flow.dsl)
            print(f"\n🔍 DSL 解析:")
            print(f"   步骤总数: {len(dsl.get('steps', []))}")
            print()
            
            # Check for duplicate navigates
            navigate_steps = []
            
            for i, step in enumerate(dsl.get("steps", []), 1):
                step_type = step.get("type", "unknown")
                # Handle both formats: {"params": {...}} and direct params
                params = step.get("params", step)
                description = step.get("description", "")
                
                print(f"   步骤 {i}: {step_type}")
                if description:
                    print(f"      描述: {description}")
                
                # Display key parameters
                if step_type == "navigate":
                    # Try both 'url' locations
                    url = params.get("url", "") or step.get("url", "")
                    print(f"      URL: {url}")
                    navigate_steps.append((i, url))
                elif step_type == "click":
                    selector = params.get("selector", "")
                    print(f"      Selector: {selector}")
                elif step_type == "input":
                    selector = params.get("selector", "")
                    text = params.get("text", "")
                    print(f"      Selector: {selector}")
                    print(f"      Text: {text[:50]}...")
                elif step_type == "wait_for":
                    selector = params.get("selector", "")
                    print(f"      Selector: {selector}")
                elif step_type == "screenshot":
                    name = params.get("name", "")
                    print(f"      Name: {name}")
                
                print()
            
            # Check for issues
            print("🔍 问题检查:")
            if len(navigate_steps) > 1:
                print(f"   ⚠️  发现多个 navigate 步骤:")
                for idx, url in navigate_steps:
                    print(f"      步骤 {idx}: {url}")
                print()
                print("   💡 这可能导致浏览器先后跳转到多个URL")
                print("   💡 建议：删除不需要的navigate步骤，只保留目标URL")
            else:
                print("   ✅ navigate 步骤正常（只有1个或0个）")
            
        except json.JSONDecodeError as e:
            print(f"❌ DSL 解析失败: {e}")
        except Exception as e:
            print(f"❌ 处理失败: {e}")
    
    print()
    print("=" * 70)


if __name__ == "__main__":
    if len(sys.argv) > 1:
        flow_id = int(sys.argv[1])
    else:
        flow_id = 111  # 默认检查Flow 111
    
    check_flow_dsl(flow_id)
