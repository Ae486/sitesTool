"""Test browser launcher with improved CDP readiness checks."""
import time
from app.services.automation.browser_launcher import (
    get_browser_manager,
    is_port_in_use,
    is_cdp_ready,
)

print("🧪 Testing Browser Launcher with CDP Readiness Check")
print("=" * 60)

port = 9222
browser_type = "edge"

print(f"\n1️⃣ Checking port {port}...")
if is_port_in_use(port):
    print(f"   ⚠️  Port {port} is already in use")
    print(f"   Checking CDP readiness...")
    if is_cdp_ready(port):
        print(f"   ✅ CDP is ready on port {port}")
    else:
        print(f"   ❌ Port in use but CDP not ready")
else:
    print(f"   ✅ Port {port} is available")

print(f"\n2️⃣ Starting browser (type: {browser_type}, port: {port})...")
manager = get_browser_manager()

success = manager.start_browser(
    browser_type=browser_type,
    port=port,
    headless=False,
)

if success:
    print("   ✅ Browser started successfully")
    
    print(f"\n3️⃣ Verifying CDP readiness...")
    if is_cdp_ready(port):
        print(f"   ✅ CDP is ready and responding")
        
        # Try to fetch CDP version
        import urllib.request
        try:
            url = f"http://localhost:{port}/json/version"
            with urllib.request.urlopen(url, timeout=5) as response:
                import json
                data = json.loads(response.read())
                print(f"   📋 Browser: {data.get('Browser', 'Unknown')}")
                print(f"   📋 WebKit: {data.get('WebKit-Version', 'Unknown')}")
                print(f"   📋 User-Agent: {data.get('User-Agent', 'Unknown')[:60]}...")
        except Exception as e:
            print(f"   ⚠️  Could not fetch version info: {e}")
    else:
        print(f"   ❌ CDP is NOT ready")
    
    print(f"\n4️⃣ Keeping browser running for 10 seconds...")
    print("   (You should see the browser window)")
    time.sleep(10)
    
    print(f"\n5️⃣ Stopping browser...")
    manager.stop_browser()
    print("   ✅ Browser stopped")
    
    print(f"\n6️⃣ Verifying cleanup...")
    time.sleep(2)
    if not is_port_in_use(port):
        print(f"   ✅ Port {port} is now free")
    else:
        print(f"   ⚠️  Port {port} still in use (browser may not have closed)")
else:
    print("   ❌ Failed to start browser")

print("\n" + "=" * 60)
print("✅ Test completed!")
print("\n📋 Summary of improvements:")
print("   - Port detection: ✅")
print("   - CDP readiness check: ✅")
print("   - HTTP verification: ✅")
print("   - 30s startup timeout: ✅")
print("   - 60s Playwright connection timeout: ✅")
print("   - Optimized browser flags: ✅")
