"""Standalone script to run automation in a separate process."""
import asyncio
import json
import sys

# Critical fix for Windows + Python 3.12 + Playwright
if sys.platform == "win32":
    # Use ProactorEventLoop which supports subprocesses on Windows
    asyncio.set_event_loop_policy(asyncio.WindowsProactorEventLoopPolicy())

from app.services.automation.dsl_parser import parser
from app.services.automation.playwright_executor import executor as pw_executor


async def run_flow(
    flow_id: int,
    dsl_json: str,
    use_cdp_mode: bool = False,
    cdp_port: int = 9222,
    cdp_user_data_dir: str = None,
):
    """Run the flow asynchronously."""
    # Parse DSL
    steps = parser.parse(dsl_json)
    
    # Execute
    result = await pw_executor.execute(
        flow_id,
        steps,
        use_cdp_mode=use_cdp_mode,
        cdp_port=cdp_port,
        cdp_user_data_dir=cdp_user_data_dir,
    )
    
    # Convert step results to dict
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
    
    return {
        "status": result.status,
        "steps_executed": result.steps_executed,
        "steps_failed": result.steps_failed,
        "total_duration_ms": result.total_duration_ms,
        "message": f"Executed {result.steps_executed} steps, {result.steps_failed} failed",
        "step_results": step_results,
        "variables": result.variables,
    }


def main():
    import argparse
    
    parser = argparse.ArgumentParser()
    parser.add_argument("flow_id", type=int)
    parser.add_argument("dsl_json", type=str)
    parser.add_argument("--headless", action="store_true", default=False)
    parser.add_argument("--headed", action="store_true", default=False)
    parser.add_argument("--browser", type=str, default="chromium", 
                       choices=["chromium", "chrome", "edge", "firefox", "custom"])
    parser.add_argument("--browser-path", type=str, default=None)
    parser.add_argument("--use-cdp-mode", action="store_true", default=False,
                       help="Connect to running browser via CDP (uses all existing logins)")
    parser.add_argument("--cdp-port", type=int, default=9222,
                       help="CDP debug port (default 9222)")
    parser.add_argument("--cdp-user-data-dir", type=str, default=None,
                       help="Custom browser user data directory (uses default profile if not specified)")
    
    args = parser.parse_args()
    
    # Determine headless mode
    headless = args.headless or not args.headed  # Default to headless

    try:
        # Set configuration for executor
        from app.services.automation.playwright_executor import executor as pw_executor
        pw_executor.headless = headless
        pw_executor.browser_type = args.browser
        pw_executor.browser_path = args.browser_path

        # Create new event loop with ProactorEventLoop
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        
        try:
            output = loop.run_until_complete(
                run_flow(
                    args.flow_id,
                    args.dsl_json,
                    use_cdp_mode=args.use_cdp_mode,
                    cdp_port=args.cdp_port,
                    cdp_user_data_dir=args.cdp_user_data_dir,
                )
            )
            print(json.dumps(output))
        finally:
            loop.close()

    except Exception as e:
        import traceback
        error_detail = traceback.format_exc()
        print(json.dumps({"status": "failed", "message": str(e), "detail": error_detail}), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
