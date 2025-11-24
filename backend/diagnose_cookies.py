"""Diagnose why cookies are not working."""
import sqlite3
from pathlib import Path

cdp_profile = Path.home() / "AppData" / "Roaming" / "autoTool" / "cdp_browser_profile"

print("🔍 Diagnosing Cookie Issues")
print("=" * 60)

# Check cookies file
cookies_path = cdp_profile / "Default" / "Network" / "Cookies"
print(f"📁 Cookies file: {cookies_path}")

if not cookies_path.exists():
    print("❌ Cookies file does NOT exist!")
    exit(1)

size = cookies_path.stat().st_size
print(f"✅ Cookies file exists: {size:,} bytes")

# Try to read cookies database
try:
    conn = sqlite3.connect(str(cookies_path))
    cursor = conn.cursor()
    
    # Get table info
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = cursor.fetchall()
    print(f"\n📊 Database tables: {[t[0] for t in tables]}")
    
    # Count cookies
    cursor.execute("SELECT COUNT(*) FROM cookies")
    count = cursor.fetchone()[0]
    print(f"🍪 Total cookies in database: {count}")
    
    # Show some cookies
    cursor.execute("SELECT host_key, name, encrypted_value, expires_utc FROM cookies LIMIT 10")
    rows = cursor.fetchall()
    
    print(f"\n📋 Sample cookies:")
    for host, name, enc_value, expires in rows:
        encrypted = len(enc_value) > 0
        print(f"   {host} / {name}")
        print(f"      Encrypted: {encrypted}, Expires: {expires}")
    
    # Check for bilibili cookies
    cursor.execute("SELECT COUNT(*) FROM cookies WHERE host_key LIKE '%bilibili%'")
    bilibili_count = cursor.fetchone()[0]
    print(f"\n🎯 Bilibili cookies: {bilibili_count}")
    
    if bilibili_count > 0:
        cursor.execute("SELECT host_key, name, encrypted_value FROM cookies WHERE host_key LIKE '%bilibili%' LIMIT 5")
        bili_rows = cursor.fetchall()
        for host, name, enc_value in bili_rows:
            print(f"   {host} / {name} (encrypted: {len(enc_value)} bytes)")
    
    conn.close()
    
    print("\n" + "=" * 60)
    print("💡 Diagnosis:")
    if count > 0:
        print(f"   ✅ Database has {count} cookies")
        if bilibili_count > 0:
            print(f"   ✅ Has {bilibili_count} bilibili cookies")
            print(f"   ⚠️  Cookies exist but may not decrypt correctly")
            print(f"   🔥 Possible causes:")
            print(f"      1. Browser profile mismatch (Chrome uses profile fingerprinting)")
            print(f"      2. Timestamp/session validation failed")
            print(f"      3. DPAPI decryption key mismatch")
            print(f"      4. Browser detected copied profile and cleared sessions")
        else:
            print(f"   ⚠️  No bilibili cookies found")
    else:
        print(f"   ❌ Database is EMPTY!")
        
except Exception as e:
    print(f"❌ Error reading cookies database: {e}")
    import traceback
    traceback.print_exc()

print("\n🔧 Recommendation:")
print("   Instead of copying files, we should:")
print("   1. Start a fresh CDP browser")
print("   2. Let user login ONCE in that browser")
print("   3. Cookies will be saved in CDP profile")
print("   4. Future runs will use those cookies")
