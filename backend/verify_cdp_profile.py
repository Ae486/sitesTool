"""Verify CDP profile directory and files."""
import sys
from pathlib import Path
import tempfile

# Check CDP profile directory
port = 9222
cdp_profile_dir = Path(tempfile.gettempdir()) / f"playwright_cdp_profile_{port}"

print("🔍 Verifying CDP Profile Directory")
print("=" * 60)
print(f"📁 CDP Profile: {cdp_profile_dir}")
print()

if not cdp_profile_dir.exists():
    print("❌ CDP profile directory does NOT exist!")
    print("   Browser may not have been started yet.")
    sys.exit(1)

print("✅ CDP profile directory exists")
print()

# Check Local State
local_state = cdp_profile_dir / "Local State"
print(f"🔑 Local State: {local_state}")
if local_state.exists():
    size = local_state.stat().st_size
    print(f"   ✅ EXISTS (size: {size} bytes)")
    
    # Try to read encrypted_key
    try:
        import json
        with open(local_state, 'r', encoding='utf-8') as f:
            data = json.load(f)
            if 'os_crypt' in data and 'encrypted_key' in data['os_crypt']:
                key = data['os_crypt']['encrypted_key']
                print(f"   ✅ encrypted_key found: {key[:50]}...")
            else:
                print("   ❌ encrypted_key NOT found in Local State!")
    except Exception as e:
        print(f"   ⚠️  Could not read Local State: {e}")
else:
    print("   ❌ Local State does NOT exist!")
    print("   🔥 This is the problem! Cookies cannot be decrypted without it!")

print()

# Check Default directory
default_dir = cdp_profile_dir / "Default"
print(f"📂 Default directory: {default_dir}")
if default_dir.exists():
    print("   ✅ EXISTS")
    
    # Check files
    files_to_check = ["Cookies", "Login Data", "Web Data", "Preferences"]
    for filename in files_to_check:
        filepath = default_dir / filename
        if filepath.exists():
            size = filepath.stat().st_size
            print(f"   ✅ {filename}: {size} bytes")
        else:
            print(f"   ❌ {filename}: NOT found")
else:
    print("   ❌ Default directory does NOT exist!")

print()
print("=" * 60)

# Check source profile
from app.services.automation.browser_launcher import get_default_user_data_dir

source_profile = get_default_user_data_dir("edge")
if source_profile:
    print(f"📦 Source Profile: {source_profile}")
    source_path = Path(source_profile)
    
    # Check source Local State
    source_local_state = source_path / "Local State"
    if source_local_state.exists():
        size = source_local_state.stat().st_size
        print(f"   ✅ Source Local State: {size} bytes")
    else:
        print(f"   ❌ Source Local State: NOT found")
    
    # Check source Default
    source_default = source_path / "Default"
    if source_default.exists():
        print(f"   ✅ Source Default directory exists")
        
        # Check source cookies
        source_cookies = source_default / "Cookies"
        if source_cookies.exists():
            size = source_cookies.stat().st_size
            print(f"   ✅ Source Cookies: {size} bytes")
            
            # Try to check if locked
            try:
                with open(source_cookies, 'rb') as f:
                    f.read(1)
                print(f"   ✅ Source Cookies can be read (not locked)")
            except Exception as e:
                print(f"   ⚠️  Source Cookies may be locked: {e}")
        else:
            print(f"   ❌ Source Cookies: NOT found")

print()
print("💡 Diagnosis:")
if local_state.exists():
    print("   ✅ Local State is present - encryption should work")
else:
    print("   ❌ Local State is MISSING - this is why cookies don't work!")
    print("   🔧 The copy operation must have failed")
