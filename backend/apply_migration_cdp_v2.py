"""Migrate CDP fields: remove old fields, add cdp_user_data_dir."""
import sqlite3
import sys
from pathlib import Path

db_path = Path(__file__).parent / "data" / "app.db"

if not db_path.exists():
    print(f"❌ Database not found at: {db_path}")
    sys.exit(1)

print("📦 Migrating CDP fields to v2...")
print("=" * 60)

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

try:
    cursor.execute("PRAGMA table_info(automation_flows)")
    columns = {col[1]: col for col in cursor.fetchall()}
    
    # Step 1: Add cdp_user_data_dir if not exists
    if "cdp_user_data_dir" not in columns:
        print("➕ Adding column 'cdp_user_data_dir'...")
        cursor.execute(
            "ALTER TABLE automation_flows ADD COLUMN cdp_user_data_dir TEXT"
        )
        conn.commit()
        print("✅ Column 'cdp_user_data_dir' added")
    else:
        print("✅ Column 'cdp_user_data_dir' already exists")
    
    # Step 2: Remove old fields (SQLite doesn't support DROP COLUMN easily, so we'll just leave them)
    # Users can ignore cdp_auto_start and cdp_auto_close fields
    print("⚠️  Old fields (cdp_auto_start, cdp_auto_close) are kept for compatibility")
    print("   They will be ignored by the new code")
    
    print("=" * 60)
    print("✨ Migration completed!")
    print()
    print("🎯 CDP模式v2使用说明:")
    print()
    print("✅ 完全自动化！无需手动操作！")
    print()
    print("默认行为:")
    print("1. 勾选'CDP模式'")
    print("2. 点击执行")
    print("3. 系统自动检测浏览器：")
    print("   - 已运行 → 直接连接")
    print("   - 未运行 → 自动启动（使用您的默认配置）")
    print("4. ✅ 所有登录状态自动可用！")
    print()
    print("高级选项（可选）:")
    print("- 自定义配置目录：指定cdp_user_data_dir路径")
    print("  例如：C:\\Users\\YourName\\AppData\\Local\\Microsoft\\Edge\\User Data")
    print()
    print("💡 核心改进:")
    print("   ✅ 使用您的真实浏览器配置")
    print("   ✅ 保留所有登录状态")
    print("   ✅ 完全自动化，零手动操作")
    print("   ✅ 浏览器保持运行，可多次执行")

except Exception as e:
    conn.rollback()
    print(f"❌ Error: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

finally:
    conn.close()
