"""Add browser_type and browser_path columns to automation_flows table."""
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
    # Check existing columns
    cursor.execute("PRAGMA table_info(automation_flows)")
    columns = [row[1] for row in cursor.fetchall()]
    
    # Add browser_type column
    if "browser_type" in columns:
        print("✓ Column 'browser_type' already exists")
    else:
        cursor.execute(
            "ALTER TABLE automation_flows ADD COLUMN browser_type VARCHAR(50) DEFAULT 'chromium' NOT NULL"
        )
        conn.commit()
        print("✓ Added 'browser_type' column to automation_flows table")
    
    # Add browser_path column
    if "browser_path" in columns:
        print("✓ Column 'browser_path' already exists")
    else:
        cursor.execute(
            "ALTER TABLE automation_flows ADD COLUMN browser_path VARCHAR(500)"
        )
        conn.commit()
        print("✓ Added 'browser_path' column to automation_flows table")
    
except Exception as e:
    print(f"✗ Error: {e}")
    conn.rollback()
finally:
    conn.close()
