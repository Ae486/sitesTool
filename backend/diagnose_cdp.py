"""Quick diagnostic tool for CDP mode."""
import json
import sqlite3
from pathlib import Path

print("🔍 CDP Mode Diagnostic Tool")
print("=" * 60)

# 1. Check database
db_path = Path(__file__).parent / "data" / "app.db"
if not db_path.exists():
    print("❌ Database not found!")
    exit(1)

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

cursor.execute("PRAGMA table_info(automation_flows)")
columns = {col[1] for col in cursor.fetchall()}

cdp_columns = ['use_cdp_mode', 'cdp_port']
missing = [col for col in cdp_columns if col not in columns]

if missing:
    print(f"❌ Missing columns: {missing}")
    print("   Run: poetry run python apply_migration_cdp.py")
    exit(1)
else:
    print("✅ Database columns OK")

# 2. Check recent flows
cursor.execute("""
    SELECT id, name, use_cdp_mode, cdp_port, created_at 
    FROM automation_flows 
    ORDER BY created_at DESC 
    LIMIT 5
""")
rows = cursor.fetchall()

if rows:
    print(f"\n📊 Recent flows:")
    for row in rows:
        cdp_status = "🟢 CDP ON" if row[2] else "⚪ CDP OFF"
        print(f"  ID:{row[0]} {row[1]:20} {cdp_status} Port:{row[3]}")
else:
    print("\n📊 No flows found")

conn.close()

# 3. Check models
print("\n🔧 Checking Python models...")
try:
    from app.models.automation import AutomationFlow
    from app.schemas.flow import AutomationFlowCreate, AutomationFlowUpdate
    
    # Check model fields
    flow_fields = AutomationFlow.__fields__.keys()
    schema_fields = AutomationFlowCreate.__fields__.keys()
    
    if 'use_cdp_mode' in flow_fields and 'use_cdp_mode' in schema_fields:
        print("✅ Models contain CDP fields")
    else:
        print("❌ Models missing CDP fields")
        print(f"   Flow fields: {flow_fields}")
        print(f"   Schema fields: {schema_fields}")
except Exception as e:
    print(f"❌ Error loading models: {e}")

# 4. Instructions
print("\n" + "=" * 60)
print("📋 Next Steps:")
print("1. ✅ Backend ready (models + database OK)")
print("2. 🔄 Refresh frontend page (Ctrl+F5)")
print("3. ➕ Create new flow")
print("4. ✅ Check 'CDP模式' checkbox")
print("5. 💾 Save and reopen to verify")
print("\n💡 If checkbox doesn't stay checked:")
print("   - Check browser console for errors (F12)")
print("   - Check Network tab for API response")
print("   - Verify payload contains 'use_cdp_mode': true")
