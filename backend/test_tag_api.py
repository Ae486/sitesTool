"""Test tag API endpoints."""
import requests

BASE_URL = "http://127.0.0.1:8000/api"

# First, login to get token
login_response = requests.post(
    f"{BASE_URL}/auth/token",
    data={"username": "admin", "password": "admin"},
    headers={"Content-Type": "application/x-www-form-urlencoded"},
)

if login_response.status_code == 200:
    token = login_response.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}
    
    print("✓ Login successful")
    
    # Test GET /catalog/tags
    print("\n1. Testing GET /catalog/tags...")
    tags_response = requests.get(f"{BASE_URL}/catalog/tags")
    print(f"   Status: {tags_response.status_code}")
    print(f"   Response: {tags_response.json()}")
    
    # Test POST /catalog/tags
    print("\n2. Testing POST /catalog/tags...")
    create_response = requests.post(
        f"{BASE_URL}/catalog/tags",
        json={"name": "测试标签", "color": "#E0F7FA"},
        headers=headers,
    )
    print(f"   Status: {create_response.status_code}")
    if create_response.status_code == 201:
        print(f"   Response: {create_response.json()}")
        print("   ✓ Tag created successfully")
    else:
        print(f"   Error: {create_response.text}")
    
    # Test GET /catalog/tags again
    print("\n3. Testing GET /catalog/tags again...")
    tags_response = requests.get(f"{BASE_URL}/catalog/tags")
    print(f"   Status: {tags_response.status_code}")
    print(f"   Response: {tags_response.json()}")
    
else:
    print(f"✗ Login failed: {login_response.status_code}")
    print(f"  Response: {login_response.text}")
