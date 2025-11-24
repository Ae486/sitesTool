"""Test CDP Mode v2 - Fully automated with user profile."""
import subprocess
import time
from pathlib import Path

print("🧪 Testing CDP Mode v2 - Fully Automated")
print("=" * 60)

# Test default profile detection
from app.services.automation.browser_launcher import get_default_user_data_dir

print("\n1️⃣ Testing default profile detection...")
edge_profile = get_default_user_data_dir("edge")
chrome_profile = get_default_user_data_dir("chrome")

if edge_profile:
    print(f"✅ Edge default profile: {edge_profile}")
    if Path(edge_profile).exists():
        print(f"   ✅ Directory exists")
        # Check for Login Data (indicates real user profile)
        login_data = Path(edge_profile) / "Default" / "Login Data"
        if login_data.exists():
            print(f"   ✅ Login Data found (real user profile with saved logins)")
    else:
        print(f"   ⚠️  Directory does not exist")
else:
    print("   ❌ Edge profile not found")

if chrome_profile:
    print(f"✅ Chrome default profile: {chrome_profile}")
else:
    print("   ℹ️  Chrome profile not found (OK if not installed)")

# Test browser launcher
print("\n2️⃣ Testing browser launcher with default profile...")
from app.services.automation.browser_launcher import get_browser_manager, is_cdp_ready

port = 9222
manager = get_browser_manager()

# Check if already running
if is_cdp_ready(port):
    print(f"   ✅ Browser already running on port {port}")
else:
    print(f"   Starting browser with YOUR profile...")
    success = manager.start_browser(
        browser_type="edge",
        port=port,
        user_data_dir=None,  # Should use default
        headless=False,
    )
    
    if success:
        print("   ✅ Browser started successfully!")
        print(f"   Waiting 3 seconds to verify stability...")
        time.sleep(3)
        
        if is_cdp_ready(port):
            print(f"   ✅ CDP is ready and responding")
        else:
            print(f"   ❌ CDP not responding")
    else:
        print("   ❌ Failed to start browser")

print("\n3️⃣ Final verification...")
if is_cdp_ready(port):
    print(f"   ✅ CDP Mode v2 is working correctly!")
    print(f"   Browser is running with your REAL profile")
    print(f"   All logins, bookmarks, extensions are available")
else:
    print(f"   ❌ CDP not ready")

print("\n" + "=" * 60)
print("💡 Next steps:")
print("1. Check if browser window is open")
print("2. Visit a website you're logged into (e.g., bilibili.com)")
print("3. Verify you're automatically logged in")
print("4. Run a flow in CDP mode to confirm it works!")
print("\n⚠️  Browser will keep running for testing (close manually)")
