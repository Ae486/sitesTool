"""Add cdp_auto_close field to automation_flows table."""
import sqlite3
import sys
from pathlib import Path

db_path = Path(__file__).parent / "data" / "app.db"

if not db_path.exists():
    print(f"❌ Database not found at: {db_path}")
    sys.exit(1)

print("📦 Adding cdp_auto_close field...")
print("=" * 60)

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

try:
    cursor.execute("PRAGMA table_info(automation_flows)")
    columns = {col[1] for col in cursor.fetchall()}
    
    if "cdp_auto_close" not in columns:
        print("➕ Adding column 'cdp_auto_close'...")
        cursor.execute(
            "ALTER TABLE automation_flows ADD COLUMN cdp_auto_close BOOLEAN NOT NULL DEFAULT 1"
        )
        conn.commit()
        print("✅ Column 'cdp_auto_close' added")
    else:
        print("⚠️  Column 'cdp_auto_close' already exists")
    
    print("=" * 60)
    print("✨ Migration completed!")
    print()
    print("📋 CDP自动关闭浏览器功能:")
    print("- 默认：流程结束后自动关闭浏览器")
    print("- 可选：保持浏览器运行（用于多次执行或调试）")
    print("- 只影响自动启动的浏览器，不影响手动启动的")

except Exception as e:
    conn.rollback()
    print(f"❌ Error: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

finally:
    conn.close()
