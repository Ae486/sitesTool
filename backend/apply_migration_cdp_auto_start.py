"""Add cdp_auto_start field to automation_flows table."""
import sqlite3
import sys
from pathlib import Path

db_path = Path(__file__).parent / "data" / "app.db"

if not db_path.exists():
    print(f"❌ Database not found at: {db_path}")
    sys.exit(1)

print("📦 Adding cdp_auto_start field...")
print("=" * 60)

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

try:
    cursor.execute("PRAGMA table_info(automation_flows)")
    columns = {col[1] for col in cursor.fetchall()}
    
    if "cdp_auto_start" not in columns:
        print("➕ Adding column 'cdp_auto_start'...")
        cursor.execute(
            "ALTER TABLE automation_flows ADD COLUMN cdp_auto_start BOOLEAN NOT NULL DEFAULT 0"
        )
        conn.commit()
        print("✅ Column 'cdp_auto_start' added")
    else:
        print("⚠️  Column 'cdp_auto_start' already exists")
    
    print("=" * 60)
    print("✨ Migration completed!")
    print()
    print("📋 CDP模式使用说明:")
    print()
    print("💡 推荐方式（使用真实浏览器）:")
    print("   1. 关闭所有Edge/Chrome窗口")
    print("   2. Windows搜索栏输入:")
    print("      msedge.exe --remote-debugging-port=9222")
    print("   3. 勾选'CDP模式'，不勾选'自动启动浏览器'")
    print("   4. 执行流程 → ✅ 使用所有现有登录状态")
    print()
    print("⚠️  备选方式（临时浏览器）:")
    print("   1. 勾选'CDP模式' + '自动启动浏览器'")
    print("   2. 执行流程 → ⚠️  创建新浏览器，无登录状态")
    print()
    print("🎯 区别:")
    print("   手动启动 = 你的日常浏览器 + 所有登录状态 ✅")
    print("   自动启动 = 临时空白浏览器 + 无登录状态 ❌")

except Exception as e:
    conn.rollback()
    print(f"❌ Error: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

finally:
    conn.close()
