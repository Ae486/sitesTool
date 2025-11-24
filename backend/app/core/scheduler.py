from __future__ import annotations

import logging
from typing import Callable

from apscheduler.schedulers.background import BackgroundScheduler

from app.core.config import settings

logger = logging.getLogger(__name__)

scheduler = BackgroundScheduler(timezone=settings.scheduler_timezone)


def start_scheduler() -> None:
    if not scheduler.running:
        scheduler.start()
        logger.info("Scheduler started")


def shutdown_scheduler() -> None:
    if scheduler.running:
        scheduler.shutdown(wait=False)
        logger.info("Scheduler stopped")


def schedule_job(job_id: str, cron_expression: str, func: Callable, *, replace_existing: bool = True) -> None:
    fields = cron_expression.split()
    if len(fields) != 5:
        raise ValueError("Cron expression must include 5 fields: min hour day month weekday")
    minute, hour, day, month, weekday = fields
    scheduler.add_job(
        func,
        trigger="cron",
        id=job_id,
        replace_existing=replace_existing,
        minute=minute,
        hour=hour,
        day=day,
        month=month,
        day_of_week=weekday,
    )
