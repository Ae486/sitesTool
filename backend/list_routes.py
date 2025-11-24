"""List all FastAPI routes."""
from app.main import app

print("Available routes:")
print("-" * 80)
for route in app.routes:
    if hasattr(route, "methods"):
        methods = ", ".join(sorted(route.methods))
        print(f"{methods:20} {route.path}")
    else:
        print(f"{'':20} {route.path}")
