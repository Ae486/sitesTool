"""Migration script to normalize history screenshots and error types."""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path

DB_PATH = Path(__file__).parent / "data" / "app.db"


def column_exists(cursor: sqlite3.Cursor, table: str, column: str) -> bool:
    cursor.execute(f"PRAGMA table_info({table})")
    return any(row[1] == column for row in cursor.fetchall())


def add_column_if_missing(conn: sqlite3.Connection, column: str) -> None:
    cursor = conn.cursor()
    if column_exists(cursor, "checkin_history", column):
        print(f"✓ Column '{column}' already exists")
        return

    conn.execute(
        f"""
        ALTER TABLE checkin_history
        ADD COLUMN {column} TEXT NOT NULL DEFAULT '[]'
        """
    )
    conn.commit()
    print(f"✓ Column '{column}' added")


def migrate_screenshot_data(conn: sqlite3.Connection) -> None:
    cursor = conn.cursor()
    cursor.execute(
        "SELECT id, screenshot_paths, screenshot_files FROM checkin_history"
    )
    rows = cursor.fetchall()
    updated = 0

    for history_id, old_paths, new_value in rows:
        if (new_value and new_value not in ("[]", None)) or not old_paths:
            continue

        paths = [p.strip() for p in old_paths.split(",") if p.strip()]
        files = [Path(p).name for p in paths]
        conn.execute(
            "UPDATE checkin_history SET screenshot_files = ? WHERE id = ?",
            (json.dumps(files), history_id),
        )
        updated += 1

    if updated:
        conn.commit()
    print(f"✓ Migrated screenshot data for {updated} history records")


def main() -> None:
    if not DB_PATH.exists():
        print(f"Database not found at {DB_PATH}, nothing to migrate.")
        return

    conn = sqlite3.connect(DB_PATH)
    try:
        add_column_if_missing(conn, "screenshot_files")
        add_column_if_missing(conn, "error_types")
        migrate_screenshot_data(conn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
