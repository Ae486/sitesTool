"""Process manager for tracking and controlling automation executions."""
from __future__ import annotations

import logging
import subprocess
from dataclasses import dataclass
from datetime import datetime
from typing import Dict, Optional

logger = logging.getLogger(__name__)


@dataclass
class RunningProcess:
    """Information about a running automation process."""

    flow_id: int
    process: subprocess.Popen
    started_at: datetime
    command: list[str]


class ProcessManager:
    """Manages running automation processes."""

    def __init__(self):
        self._processes: Dict[int, RunningProcess] = {}

    def start_process(
        self, flow_id: int, command: list[str], cwd: str
    ) -> subprocess.Popen:
        """
        Start a new automation process.

        Args:
            flow_id: ID of the flow
            command: Command to execute
            cwd: Working directory

        Returns:
            Popen process object

        Raises:
            RuntimeError: If flow is already running
        """
        if self.is_running(flow_id):
            raise RuntimeError(f"Flow {flow_id} is already running")

        # Start process
        process = subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            cwd=cwd,
        )

        # Track process
        self._processes[flow_id] = RunningProcess(
            flow_id=flow_id,
            process=process,
            started_at=datetime.utcnow(),
            command=command,
        )

        logger.info(f"Started process for flow {flow_id}, PID: {process.pid}")
        return process

    def stop_process(self, flow_id: int) -> bool:
        """
        Stop a running process.

        Args:
            flow_id: ID of the flow

        Returns:
            True if process was stopped, False if not running
        """
        if flow_id not in self._processes:
            logger.warning(f"Flow {flow_id} is not running")
            return False

        running_process = self._processes[flow_id]
        process = running_process.process

        try:
            # Try graceful termination first
            process.terminate()
            try:
                process.wait(timeout=5)
                logger.info(f"Process for flow {flow_id} terminated gracefully")
            except subprocess.TimeoutExpired:
                # Force kill if not terminated
                process.kill()
                process.wait()
                logger.warning(f"Process for flow {flow_id} was force killed")

            return True

        except Exception as e:
            logger.error(f"Error stopping process for flow {flow_id}: {e}")
            return False

        finally:
            # Remove from tracking
            del self._processes[flow_id]

    def is_running(self, flow_id: int) -> bool:
        """Check if a flow is currently running."""
        if flow_id not in self._processes:
            return False

        # Check if process is still alive
        process = self._processes[flow_id].process
        if process.poll() is not None:
            # Process has finished, clean up
            del self._processes[flow_id]
            return False

        return True

    def get_running_flows(self) -> list[int]:
        """Get list of currently running flow IDs."""
        # Clean up finished processes
        finished = []
        for flow_id, running_process in self._processes.items():
            if running_process.process.poll() is not None:
                finished.append(flow_id)

        for flow_id in finished:
            del self._processes[flow_id]

        return list(self._processes.keys())

    def get_process_info(self, flow_id: int) -> Optional[RunningProcess]:
        """Get information about a running process."""
        return self._processes.get(flow_id)

    def cleanup_finished(self):
        """Remove finished processes from tracking."""
        finished = []
        for flow_id, running_process in self._processes.items():
            if running_process.process.poll() is not None:
                finished.append(flow_id)

        for flow_id in finished:
            del self._processes[flow_id]


# Global process manager instance
process_manager = ProcessManager()
