"""Find all cookie-related files in Edge profile."""
from pathlib import Path
from app.services.automation.browser_launcher import get_default_user_data_dir

source_profile = get_default_user_data_dir("edge")
if not source_profile:
    print("❌ Edge profile not found")
    exit(1)

source_path = Path(source_profile)
default_dir = source_path / "Default"

print("🔍 Searching for cookie files...")
print(f"📁 Profile: {source_profile}")
print()

if not default_dir.exists():
    print("❌ Default directory not found")
    exit(1)

# List all files in Default directory
print("📂 Files in Default directory:")
files = list(default_dir.glob("Cookie*"))
for f in files:
    size = f.stat().st_size if f.is_file() else "DIR"
    print(f"   {f.name}: {size}")

print()
print("📂 Files matching 'Login*':")
files = list(default_dir.glob("Login*"))
for f in files:
    size = f.stat().st_size if f.is_file() else "DIR"
    print(f"   {f.name}: {size}")

print()
print("📂 All database files (*.db, *-journal):")
db_files = list(default_dir.glob("*.db")) + list(default_dir.glob("*-journal"))
for f in sorted(db_files)[:20]:  # First 20
    size = f.stat().st_size if f.is_file() else "DIR"
    print(f"   {f.name}: {size}")

print()
print("💡 Note: If 'Cookies' file is missing, it might be:")
print("   1. Named differently (e.g., 'Cookies-journal', 'Network/Cookies')")
print("   2. Locked by running Edge (can't be copied)")
print("   3. Stored in a different location")
