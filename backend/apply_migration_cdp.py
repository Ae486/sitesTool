"""Apply CDP mode migration: Add use_cdp_mode and cdp_port fields."""
import sqlite3
import sys
from pathlib import Path

# Get database path
db_path = Path(__file__).parent / "data" / "app.db"

if not db_path.exists():
    print(f"❌ Database not found at: {db_path}")
    sys.exit(1)

print(f"📦 Applying CDP mode migration to: {db_path}")
print("=" * 60)

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

try:
    cursor.execute("PRAGMA table_info(automation_flows)")
    columns = {col[1] for col in cursor.fetchall()}
    
    # Add use_cdp_mode
    if "use_cdp_mode" not in columns:
        print("➕ Adding column 'use_cdp_mode'...")
        cursor.execute(
            "ALTER TABLE automation_flows ADD COLUMN use_cdp_mode BOOLEAN NOT NULL DEFAULT 0"
        )
        print("✅ Column 'use_cdp_mode' added")
    else:
        print("⚠️  Column 'use_cdp_mode' already exists")
    
    # Add cdp_port
    if "cdp_port" not in columns:
        print("➕ Adding column 'cdp_port'...")
        cursor.execute(
            "ALTER TABLE automation_flows ADD COLUMN cdp_port INTEGER NOT NULL DEFAULT 9222"
        )
        print("✅ Column 'cdp_port' added")
    else:
        print("⚠️  Column 'cdp_port' already exists")
    
    conn.commit()
    
    print("=" * 60)
    print("✨ CDP mode migration completed!")
    print()
    print("📋 CDP模式使用说明:")
    print("1. 手动启动浏览器（带调试端口）:")
    print("   chrome.exe --remote-debugging-port=9222")
    print("   或")
    print('   msedge.exe --remote-debugging-port=9222')
    print()
    print("2. 在流程配置中:")
    print("   ✅ 勾选 'CDP模式'")
    print("   ✅ 端口填 9222 (默认)")
    print()
    print("3. 执行流程:")
    print("   - 自动连接到运行中的浏览器")
    print("   - 直接使用所有现有登录状态")
    print("   - 无需任何配置文件或路径")
    print()
    print("✅ 这才是真正的简单！")

except Exception as e:
    conn.rollback()
    print(f"❌ Error: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

finally:
    conn.close()
