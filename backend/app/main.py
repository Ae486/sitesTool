from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.api.router import api_router
from app.core.config import settings
from app.core.scheduler import shutdown_scheduler, start_scheduler
from app.db.session import init_db


def create_application() -> FastAPI:
    application = FastAPI(title=settings.project_name, version=settings.version)

    application.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    application.include_router(api_router, prefix=settings.api_prefix)

    @application.on_event("startup")
    def startup_event() -> None:
        init_db()
        start_scheduler()

    @application.on_event("shutdown")
    def shutdown_event() -> None:
        shutdown_scheduler()

    # Mount test pages for automation testing
    test_pages_dir = Path(__file__).resolve().parent.parent / "test_pages"
    if test_pages_dir.exists():
        application.mount(
            "/test", StaticFiles(directory=test_pages_dir, html=True), name="test_pages"
        )

    # Mount screenshots directory (use /screenshots instead of /api/screenshots to avoid conflicts)
    screenshots_dir = Path(__file__).resolve().parent.parent / "data" / "screenshots"
    screenshots_dir.mkdir(parents=True, exist_ok=True)
    application.mount(
        "/screenshots", StaticFiles(directory=screenshots_dir), name="screenshots"
    )

    # Mount frontend dist
    # Note: This should be mounted LAST to avoid intercepting API routes
    dist_dir = Path(__file__).resolve().parent.parent.parent / "frontend" / "dist"
    if dist_dir.exists():
        application.mount("/", StaticFiles(directory=dist_dir, html=True), name="frontend")

    @application.get("/healthz")
    def healthcheck() -> dict[str, str]:
        return {"status": "ok"}

    return application


app = create_application()
