"""Test the output format of run_automation.py"""
import asyncio
import json
import sys

# Critical fix for Windows + Python 3.12 + Playwright
if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsProactorEventLoopPolicy())

from app.services.automation.dsl_parser import parser
from app.services.automation.playwright_executor import PlaywrightExecutor

# Test DSL with intentional error
test_dsl = {
    "version": 1,
    "steps": [
        {
            "type": "navigate",
            "url": "http://127.0.0.1:8000/test/",
            "description": "打开测试页面"
        },
        {
            "type": "input",
            "selector": "#username",
            "value": "testuser",
            "description": "输入用户名 - 成功"
        },
        {
            "type": "click",
            "selector": "#wrong-selector",
            "timeout": 2000,
            "description": "点击错误选择器 - 失败"
        },
        {
            "type": "input",
            "selector": "#password",
            "value": "password123",
            "description": "输入密码 - 这步不会执行"
        }
    ]
}


async def test():
    # Parse DSL
    steps = parser.parse(json.dumps(test_dsl))
    
    # Create executor
    executor = PlaywrightExecutor(headless=False)
    
    # Execute
    result = await executor.execute(999, steps)
    
    # Convert to output format (same as run_automation.py)
    step_results = []
    for step_result in result.step_results:
        step_dict = {
            "step_index": step_result.step_index,
            "step_type": step_result.step_type,
            "success": step_result.success,
            "duration_ms": step_result.duration_ms,
        }
        if step_result.message:
            step_dict["message"] = step_result.message
        if step_result.error:
            step_dict["error"] = step_result.error
        if step_result.extracted_data:
            step_dict["extracted_data"] = step_result.extracted_data
        if step_result.screenshot_path:
            step_dict["screenshot_path"] = step_result.screenshot_path
        if step_result.description:
            step_dict["description"] = step_result.description
        step_results.append(step_dict)
    
    output = {
        "status": result.status,
        "steps_executed": result.steps_executed,
        "steps_failed": result.steps_failed,
        "total_duration_ms": result.total_duration_ms,
        "message": f"Executed {result.steps_executed} steps, {result.steps_failed} failed",
        "step_results": step_results,
        "variables": result.variables,
    }
    
    # Use ensure_ascii=True to avoid encoding issues on Windows
    print(json.dumps(output, indent=2, ensure_ascii=True))


if __name__ == "__main__":
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        loop.run_until_complete(test())
    finally:
        loop.close()
