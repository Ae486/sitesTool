"""Add headless column to automation_flows table."""
import sqlite3
from pathlib import Path

# Get database path
db_path = Path(__file__).parent / "data" / "app.db"

if not db_path.exists():
    print(f"Database not found at {db_path}")
    exit(1)

# Connect to database
conn = sqlite3.connect(db_path)
cursor = conn.cursor()

try:
    # Check if column already exists
    cursor.execute("PRAGMA table_info(automation_flows)")
    columns = [row[1] for row in cursor.fetchall()]
    
    if "headless" in columns:
        print("✓ Column 'headless' already exists")
    else:
        # Add headless column with default value True
        cursor.execute(
            "ALTER TABLE automation_flows ADD COLUMN headless BOOLEAN DEFAULT 1 NOT NULL"
        )
        conn.commit()
        print("✓ Added 'headless' column to automation_flows table")
    
except Exception as e:
    print(f"✗ Error: {e}")
    conn.rollback()
finally:
    conn.close()
