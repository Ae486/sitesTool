"""Apply migration 003: Add user_data_dir field to automation_flows table."""
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
    # Check if column already exists
    cursor.execute("PRAGMA table_info(automation_flows)")
    columns = {col[1] for col in cursor.fetchall()}
    
    if "user_data_dir" in columns:
        print("⚠️  Column 'user_data_dir' already exists")
    else:
        print("➕ Adding column 'user_data_dir'...")
        cursor.execute(
            "ALTER TABLE automation_flows ADD COLUMN user_data_dir TEXT"
        )
        print("✅ Column 'user_data_dir' added")
    
    # Commit changes
    conn.commit()
    
    print("=" * 60)
    print("✨ Migration 003 applied successfully!")
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
    print("📝 使用说明:")
    print("1. 创建独立的浏览器Profile（推荐）:")
    print("   - 打开Edge，在地址栏输入: edge://version")
    print("   - 查看 '配置文件路径'，例如: C:\\Users\\YourName\\AppData\\Local\\Microsoft\\Edge\\User Data\\Default")
    print("   - 创建新Profile: 在User Data目录下创建文件夹 'AutoProfile'")
    print("   - 在流程配置中填入: C:\\Users\\YourName\\AppData\\Local\\Microsoft\\Edge\\User Data\\AutoProfile")
    print()
    print("2. 首次使用:")
    print("   - 执行流程时会创建Profile并保存所有登录")
    print("   - 后续执行自动复用所有登录状态")
    print()
    print("3. 重要提醒:")
    print("   ⚠️  使用此功能时，请确保Edge浏览器未在运行")
    print("   ⚠️  不要使用日常使用的Default profile，会导致冲突")

except sqlite3.OperationalError as e:
    conn.rollback()
    print(f"❌ Error applying migration: {e}")
    print()
    print("Possible issues:")
    print("- Column may already exist (check with PRAGMA table_info)")
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
