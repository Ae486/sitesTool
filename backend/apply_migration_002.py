"""Apply migration 002: Add storage state fields to automation_flows table."""
import sqlite3
import sys
from pathlib import Path

# Get database path
db_path = Path(__file__).parent / "data" / "app.db"

if not db_path.exists():
    print(f"❌ Database not found at: {db_path}")
    print("Please make sure you're running this from the backend directory.")
    sys.exit(1)

print(f"📦 Applying migration to: {db_path}")
print("=" * 60)

# Connect to database
conn = sqlite3.connect(db_path)
cursor = conn.cursor()

try:
    # Check if columns already exist
    cursor.execute("PRAGMA table_info(automation_flows)")
    columns = {col[1] for col in cursor.fetchall()}
    
    if "use_storage_state" in columns:
        print("⚠️  Column 'use_storage_state' already exists")
    else:
        print("➕ Adding column 'use_storage_state'...")
        cursor.execute(
            "ALTER TABLE automation_flows ADD COLUMN use_storage_state BOOLEAN NOT NULL DEFAULT 0"
        )
        print("✅ Column 'use_storage_state' added")
    
    if "storage_state_name" in columns:
        print("⚠️  Column 'storage_state_name' already exists")
    else:
        print("➕ Adding column 'storage_state_name'...")
        cursor.execute(
            "ALTER TABLE automation_flows ADD COLUMN storage_state_name TEXT"
        )
        print("✅ Column 'storage_state_name' added")
    
    # Commit changes
    conn.commit()
    
    print("=" * 60)
    print("✨ Migration 002 applied successfully!")
    print()
    print("📋 Verification:")
    
    # Verify columns
    cursor.execute("PRAGMA table_info(automation_flows)")
    columns_info = cursor.fetchall()
    
    print("\nColumns in automation_flows table:")
    for col in columns_info:
        col_id, name, type_, notnull, default, pk = col
        print(f"  - {name:25} {type_:15} {'NOT NULL' if notnull else 'NULL':8} DEFAULT {default}")
    
    print()
    print("✅ All checks passed!")
    print()
    print("📝 Next steps:")
    print("1. Restart your backend server")
    print("2. Test creating/editing flows with storage state options")
    print("3. Verify storage state files are created in data/storage_states/")

except sqlite3.OperationalError as e:
    conn.rollback()
    print(f"❌ Error applying migration: {e}")
    print()
    print("Possible issues:")
    print("- Columns may already exist (check with PRAGMA table_info)")
    print("- Database may be locked (close other connections)")
    print("- SQLite version may be too old")
    sys.exit(1)

except Exception as e:
    conn.rollback()
    print(f"❌ Unexpected error: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

finally:
    conn.close()
