from contextlib import contextmanager
import json

from sqlmodel import Session, SQLModel, create_engine

from app.core.config import settings
from app.models import auth, automation, checkin, site, user  # noqa: F401

connect_args = {}
if settings.database_url.startswith("sqlite"):
    connect_args["check_same_thread"] = False

engine = create_engine(settings.database_url, connect_args=connect_args)


def init_db() -> None:
    SQLModel.metadata.create_all(bind=engine)
    _ensure_history_columns()


@contextmanager
def session_scope() -> Session:
    session = Session(engine)
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def get_session():
    with Session(engine) as session:
        yield session


def _ensure_history_columns() -> None:
    """Ensure new JSON columns exist and migrate legacy screenshot data."""
    with engine.begin() as conn:
        existing_cols = {
            row[1]
            for row in conn.exec_driver_sql("PRAGMA table_info(checkin_history)").fetchall()
        }

        if "screenshot_files" not in existing_cols:
            conn.exec_driver_sql(
                "ALTER TABLE checkin_history ADD COLUMN screenshot_files TEXT NOT NULL DEFAULT '[]'"
            )
            existing_cols.add("screenshot_files")

        if "error_types" not in existing_cols:
            conn.exec_driver_sql(
                "ALTER TABLE checkin_history ADD COLUMN error_types TEXT NOT NULL DEFAULT '[]'"
            )

        if "screenshot_paths" in existing_cols:
            rows = conn.exec_driver_sql(
                "SELECT id, screenshot_paths FROM checkin_history "
                "WHERE screenshot_paths IS NOT NULL AND screenshot_paths != ''"
            ).fetchall()
            for history_id, old_paths in rows:
                if not old_paths:
                    continue
                files = [
                    path.strip().split("/")[-1].split("\\")[-1]
                    for path in old_paths.split(",")
                    if path and path.strip()
                ]
                conn.exec_driver_sql(
                    "UPDATE checkin_history SET screenshot_files = :files WHERE id = :history_id",
                    {"files": json.dumps(files), "history_id": history_id},
                )
