"""Add screenshot_paths column to checkin_history table."""
import sqlite3
from pathlib import Path

DB_PATH = Path(__file__).parent / "data" / "app.db"


def migrate():
    """Add screenshot_paths column if it doesn't exist."""
    if not DB_PATH.exists():
        print(f"Database not found at {DB_PATH}")
        return

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    try:
        # Check if column exists
        cursor.execute("PRAGMA table_info(checkin_history)")
        columns = [row[1] for row in cursor.fetchall()]

        if "screenshot_paths" not in columns:
            print("Adding screenshot_paths column...")
            cursor.execute(
                """
                ALTER TABLE checkin_history 
                ADD COLUMN screenshot_paths TEXT
                """
            )
            conn.commit()
            print("✓ screenshot_paths column added successfully")
        else:
            print("✓ screenshot_paths column already exists")

    except Exception as e:
        print(f"Error during migration: {e}")
        conn.rollback()
    finally:
        conn.close()


if __name__ == "__main__":
    migrate()
