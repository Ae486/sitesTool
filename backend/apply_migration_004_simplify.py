"""Apply migration 004: Simplify to use_persistent_browser."""
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
    # Check existing columns
    cursor.execute("PRAGMA table_info(automation_flows)")
    columns = {col[1] for col in cursor.fetchall()}
    
    print("Current columns:", columns)
    
    # Add new column if needed
    if "use_persistent_browser" not in columns:
        print("➕ Adding column 'use_persistent_browser'...")
        cursor.execute(
            "ALTER TABLE automation_flows ADD COLUMN use_persistent_browser BOOLEAN NOT NULL DEFAULT 0"
        )
        print("✅ Column 'use_persistent_browser' added")
    else:
        print("⚠️  Column 'use_persistent_browser' already exists")
    
    # Note: We'll keep old columns for backward compatibility
    # They will simply be ignored by the new code
    
    # Commit changes
    conn.commit()
    
    print("=" * 60)
    print("✨ Migration 004 applied successfully!")
    print()
    print("📋 简化说明:")
    print("1. 新字段 'use_persistent_browser' 已添加")
    print("2. 旧字段保留以防需要回滚（use_storage_state, storage_state_name, user_data_dir）")
    print("3. 新逻辑只使用 use_persistent_browser，自动检测浏览器配置文件")
    print()
    print("✅ 用户体验提升:")
    print("   - 只需勾选一个开关")
    print("   - 无需填写任何路径")
    print("   - 自动检测和使用登录状态")
    print("   - 系统自动创建独立的 'Automation' 配置文件")

except sqlite3.OperationalError as e:
    conn.rollback()
    print(f"❌ Error applying migration: {e}")
    print()
    print("Possible issues:")
    print("- Column may already exist")
    print("- Database may be locked")
    sys.exit(1)

except Exception as e:
    conn.rollback()
    print(f"❌ Unexpected error: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

finally:
    conn.close()
