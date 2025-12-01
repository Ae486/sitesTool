# Backend Service

FastAPI-based API server providing site management, automation orchestration, and scheduling
capabilities for the navigation check-in platform.

## Key Features

- JWT-authenticated REST API for site/catalog management.
- SQLite persistence via SQLModel/SQLAlchemy.
- Automation DSL execution hooks powered by Playwright workers.
- APScheduler integration for recurring check-in jobs.

## Local Setup (manual steps)

1. Ensure **Python 3.11** is installed and Poetry is available in the shell.
2. Within `backend/`, run `poetry install` (user initiated) to resolve dependencies.
3. Copy `.env.example` to `.env` and adjust values if needed.
4. Launch the API using `poetry run uvicorn app.main:app --reload`.

> Note: Playwright browsers are not installed automatically. After the initial Poetry install,
> run `poetry run playwright install` if you plan to execute automation tasks locally.
