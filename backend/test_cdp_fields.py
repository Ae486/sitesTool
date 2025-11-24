"""Test CDP fields in database and models."""
import sqlite3
from pathlib import Path

db_path = Path(__file__).parent / "data" / "app.db"

print("Testing CDP fields...")
print("=" * 60)

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# Check table structure
cursor.execute("PRAGMA table_info(automation_flows)")
columns = cursor.fetchall()

print("\n📋 Table columns:")
for col in columns:
    col_id, name, type_, notnull, default, pk = col
    print(f"  {name:20} {type_:15} {'NOT NULL' if notnull else 'NULL':8} DEFAULT {default}")

# Check if CDP columns exist
cdp_columns = [col[1] for col in columns if col[1] in ['use_cdp_mode', 'cdp_port']]
print(f"\n✅ CDP columns found: {cdp_columns}")

# Try to query data
cursor.execute("SELECT id, name, use_cdp_mode, cdp_port FROM automation_flows LIMIT 5")
rows = cursor.fetchall()

if rows:
    print(f"\n📊 Sample data (first 5 rows):")
    for row in rows:
        print(f"  ID: {row[0]}, Name: {row[1]}, CDP Mode: {row[2]}, CDP Port: {row[3]}")
else:
    print("\n📊 No flows in database yet")

conn.close()

print("\n" + "=" * 60)
print("✅ Database check completed!")
print("\nNow test the API:")
print("1. Start backend: poetry run uvicorn app.main:app --reload")
print("2. Refresh frontend page")
print("3. Create/edit flow and check CDP mode checkbox")
