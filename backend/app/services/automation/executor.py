from __future__ import annotations

import json
import logging
import sys
import threading
from dataclasses import dataclass
from pathlib import Path

from app.models.automation import AutomationFlow
from app.services.automation.process_manager import process_manager

logger = logging.getLogger(__name__)


@dataclass
class ExecutionResult:
    status: str
    message: str | None = None
    execution_id: int | None = None


class AutomationExecutor:
    """Orchestration layer for running automation flows in separate process."""

    def trigger(self, flow: AutomationFlow) -> ExecutionResult:
        """
        Trigger execution of an automation flow in a separate process.

        Args:
            flow: AutomationFlow model instance

        Returns:
            ExecutionResult with status and message
        """
        try:
            # Check if already running
            if process_manager.is_running(flow.id):
                return ExecutionResult(
                    status="running",
                    message="Flow is already running",
                )

            logger.info(f"Triggering flow {flow.id} in separate process")

            # Get the path to the run_automation.py script
            backend_dir = Path(__file__).resolve().parent.parent.parent.parent
            script_path = backend_dir / "run_automation.py"

            # Build command arguments
            cmd_args = [
                sys.executable,
                str(script_path),
                str(flow.id),
                flow.dsl,
                "--headless" if flow.headless else "--headed",
                "--browser",
                flow.browser_type,
            ]

            # Add browser path if custom
            if flow.browser_path:
                cmd_args.extend(["--browser-path", flow.browser_path])
            
            # Add CDP mode flags if enabled
            if flow.use_cdp_mode:
                cmd_args.append("--use-cdp-mode")
                cmd_args.extend(["--cdp-port", str(flow.cdp_port)])
                if flow.cdp_user_data_dir:
                    cmd_args.extend(["--cdp-user-data-dir", flow.cdp_user_data_dir])

            # Start process using process manager
            process = process_manager.start_process(flow.id, cmd_args, str(backend_dir))

            # Start background thread to wait for completion and save history
            def wait_for_completion():
                from datetime import datetime
                from app.db.session import session_scope
                from app.models.checkin import CheckinHistory
                from app.models.automation import FlowStatus
                
                started_at = datetime.utcnow()
                
                try:
                    stdout, stderr = process.communicate(timeout=300)
                    finished_at = datetime.utcnow()
                    duration_ms = int((finished_at - started_at).total_seconds() * 1000)
                    
                    # Parse result
                    screenshot_files: list[str] = []
                    error_types: set[str] = set()
                    if process.returncode == 0:
                        try:
                            result = json.loads(stdout)
                            status = FlowStatus.SUCCESS if result.get("status") == "success" else FlowStatus.FAILED
                            result_payload = stdout
                            
                            # Generate detailed error message from failed steps
                            step_results = result.get("step_results", [])
                            if status == FlowStatus.FAILED:
                                failed_steps = [s for s in step_results if not s.get("success")]
                                if failed_steps:
                                    error_parts = []
                                    for step in failed_steps:
                                        step_num = step.get("step_index", 0) + 1
                                        step_type = step.get("step_type", "unknown")
                                        description = step.get("description", "")
                                        error_text = step.get("error", "Unknown error")
                                        
                                        # Extract error type (e.g., [TIMEOUT])
                                        error_type = ""
                                        if error_text.startswith("["):
                                            error_type = error_text.split("]")[0] + "]"
                                            error_text = error_text[len(error_type):].strip()
                                            error_types.add(error_type.strip("[]"))
                                        
                                        # Get first line of error (main message)
                                        error_main = error_text.split("\n")[0].split("|")[0].strip()
                                        
                                        # Build error message with description if available
                                        if description:
                                            error_parts.append(f"步骤 {step_num}: {description} - {error_type} {error_main}")
                                        else:
                                            error_parts.append(f"步骤 {step_num} ({step_type}): {error_type} {error_main}")
                                    
                                    error_message = " | ".join(error_parts)
                                else:
                                    error_message = result.get("message", "执行失败")
                            else:
                                error_message = None
                            
                            # Extract screenshot paths from step results
                            paths = [s.get("screenshot_path") for s in step_results if s.get("screenshot_path")]
                            if paths:
                                screenshot_files = [Path(p).name for p in paths if p]
                        except json.JSONDecodeError:
                            status = FlowStatus.FAILED
                            result_payload = None
                            error_message = "Invalid JSON output"
                    else:
                        status = FlowStatus.FAILED
                        result_payload = None
                        error_message = stderr or "Execution failed"
                    
                    # Save to database
                    with session_scope() as db:
                        history = CheckinHistory(
                            flow_id=flow.id,
                            status=status,
                            started_at=started_at,
                            finished_at=finished_at,
                            duration_ms=duration_ms,
                            log=stdout if stdout else stderr,
                            result_payload=result_payload,
                            error_message=error_message,
                            screenshot_files=screenshot_files,
                            error_types=sorted(error_types),
                        )
                        db.add(history)
                        db.commit()
                    
                    logger.info(f"Flow {flow.id} execution completed with status {status}")
                    
                except Exception as e:
                    logger.error(f"Flow {flow.id} execution error: {e}")
                    
                    # Save error to database
                    try:
                        with session_scope() as db:
                            history = CheckinHistory(
                                flow_id=flow.id,
                                status=FlowStatus.FAILED,
                                started_at=started_at,
                                finished_at=datetime.utcnow(),
                                duration_ms=int((datetime.utcnow() - started_at).total_seconds() * 1000),
                                error_message=str(e),
                            )
                            db.add(history)
                            db.commit()
                    except Exception as db_error:
                        logger.error(f"Failed to save error history: {db_error}")
                
                finally:
                    # Clean up from process manager
                    if process_manager.is_running(flow.id):
                        process_manager.stop_process(flow.id)

            thread = threading.Thread(target=wait_for_completion, daemon=True)
            thread.start()

            # Return immediately
            return ExecutionResult(
                status="started",
                message="Flow execution started in background",
            )

        except RuntimeError as e:
            # Already running
            return ExecutionResult(status="running", message=str(e))

        except Exception as e:
            logger.error(f"Execution error for flow {flow.id}: {e}", exc_info=True)
            process_manager.stop_process(flow.id)
            return ExecutionResult(status="failed", message=f"Execution error: {e}")

    def stop(self, flow: AutomationFlow) -> ExecutionResult:
        """
        Stop a running automation flow.

        Args:
            flow: AutomationFlow model instance

        Returns:
            ExecutionResult with status and message
        """
        try:
            if not process_manager.is_running(flow.id):
                return ExecutionResult(
                    status="idle",
                    message="Flow is not running",
                )

            success = process_manager.stop_process(flow.id)

            if success:
                logger.info(f"Successfully stopped flow {flow.id}")
                return ExecutionResult(
                    status="stopped",
                    message="Flow execution stopped",
                )
            else:
                return ExecutionResult(
                    status="failed",
                    message="Failed to stop flow execution",
                )

        except Exception as e:
            logger.error(f"Error stopping flow {flow.id}: {e}", exc_info=True)
            return ExecutionResult(status="failed", message=f"Stop error: {e}")

    def is_running(self, flow_id: int) -> bool:
        """Check if a flow is currently running."""
        return process_manager.is_running(flow_id)

    def get_running_flows(self) -> list[int]:
        """Get list of currently running flow IDs."""
        return process_manager.get_running_flows()


executor = AutomationExecutor()
