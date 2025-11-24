"""Manual CDP test - verify browser and CDP connection."""
import subprocess
import time
import urllib.request
import socket
import sys
from pathlib import Path

print("🔍 Manual CDP Connection Test")
print("=" * 60)

port = 9222

# Find Edge executable
edge_paths = [
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    Path.home() / "AppData" / "Local" / "Microsoft" / "Edge" / "Application" / "msedge.exe",
]

edge_path = None
for path in edge_paths:
    p = Path(path) if isinstance(path, str) else path
    if p.exists():
        edge_path = str(p)
        break

if not edge_path:
    print("❌ Edge browser not found!")
    sys.exit(1)

print(f"✅ Found Edge: {edge_path}")

# Check port availability
def check_port(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        try:
            s.bind(('localhost', port))
            return False
        except OSError:
            return True

def check_cdp(port):
    try:
        url = f"http://localhost:{port}/json/version"
        with urllib.request.urlopen(url, timeout=2) as response:
            if response.status == 200:
                import json
                data = json.loads(response.read())
                return True, data
    except Exception as e:
        return False, str(e)
    return False, "Unknown"

print(f"\n1️⃣ Checking port {port}...")
if check_port(port):
    print(f"⚠️  Port {port} is already in use!")
    print(f"Checking if CDP is responding...")
    ready, data = check_cdp(port)
    if ready:
        print(f"✅ CDP is already running: {data}")
        sys.exit(0)
    else:
        print(f"❌ Port in use but CDP not responding: {data}")
        print("Please close any browser using port 9222 and try again")
        sys.exit(1)
else:
    print(f"✅ Port {port} is available")

print(f"\n2️⃣ Starting Edge with CDP flags...")

# Use temporary user data dir to prevent conflicts
import tempfile
user_data_dir = Path(tempfile.gettempdir()) / f"test_cdp_{port}"
user_data_dir.mkdir(parents=True, exist_ok=True)

# Command with user-data-dir
cmd = [
    edge_path,
    f"--remote-debugging-port={port}",
    f"--user-data-dir={user_data_dir}",
    "--no-first-run",
]

print(f"User data dir: {user_data_dir}")
print(f"Command: {' '.join(cmd)}")

try:
    process = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    
    print(f"✅ Process started, PID: {process.pid}")
    
    print(f"\n3️⃣ Waiting for CDP to be ready (max 30s)...")
    
    for i in range(60):  # 30 seconds, check every 0.5s
        time.sleep(0.5)
        
        # Check if process is still running
        if process.poll() is not None:
            stdout, stderr = process.communicate()
            print(f"\n❌ Browser process exited!")
            print(f"Exit code: {process.returncode}")
            if stdout:
                print(f"STDOUT: {stdout.decode('utf-8', errors='ignore')}")
            if stderr:
                print(f"STDERR: {stderr.decode('utf-8', errors='ignore')}")
            sys.exit(1)
        
        # Check port
        port_active = check_port(port)
        cdp_ready, data = check_cdp(port)
        
        if i % 4 == 0:  # Log every 2 seconds
            print(f"  Check {i//2}s: port_active={port_active}, cdp_ready={cdp_ready}")
        
        if port_active and cdp_ready:
            print(f"\n✅ CDP is ready after {(i+1)*0.5:.1f}s!")
            print(f"Browser info: {data}")
            break
    else:
        print(f"\n❌ CDP did not become ready within 30s")
        port_active = check_port(port)
        cdp_ready, error = check_cdp(port)
        print(f"Final state: port_active={port_active}, cdp_ready={cdp_ready}")
        if not cdp_ready:
            print(f"CDP error: {error}")
        
        # Check if browser is still running
        if process.poll() is None:
            print("Browser process is still running but CDP not responding")
        else:
            print(f"Browser process exited with code: {process.poll()}")
    
    print(f"\n4️⃣ Keeping browser running for 5 seconds...")
    print("(You should see the browser window)")
    time.sleep(5)
    
    print(f"\n5️⃣ Stopping browser...")
    process.terminate()
    try:
        process.wait(timeout=5)
        print("✅ Browser stopped cleanly")
    except subprocess.TimeoutExpired:
        process.kill()
        print("⚠️  Browser killed forcefully")

except Exception as e:
    print(f"\n❌ Error: {e}")
    import traceback
    traceback.print_exc()

print("\n" + "=" * 60)
print("✅ Test completed!")
