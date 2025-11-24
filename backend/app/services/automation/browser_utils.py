"""Browser utilities for auto-detecting user data directories."""
import os
from pathlib import Path
from typing import Optional


def get_default_user_data_dir(browser_type: str) -> Optional[str]:
    """
    Auto-detect default user data directory for different browsers.
    
    Args:
        browser_type: Browser type (chrome, edge, firefox)
        
    Returns:
        Path to user data directory, or None if not found
    """
    user_home = Path.home()
    
    if browser_type == "chrome":
        # Chrome user data locations
        possible_paths = [
            user_home / "AppData" / "Local" / "Google" / "Chrome" / "User Data",
            user_home / ".config" / "google-chrome",  # Linux
            user_home / "Library" / "Application Support" / "Google" / "Chrome",  # macOS
        ]
    elif browser_type == "edge":
        # Edge user data locations
        possible_paths = [
            user_home / "AppData" / "Local" / "Microsoft" / "Edge" / "User Data",
            user_home / ".config" / "microsoft-edge",  # Linux
            user_home / "Library" / "Application Support" / "Microsoft Edge",  # macOS
        ]
    elif browser_type == "firefox":
        # Firefox profiles location (different structure)
        possible_paths = [
            user_home / "AppData" / "Roaming" / "Mozilla" / "Firefox" / "Profiles",
            user_home / ".mozilla" / "firefox",  # Linux
            user_home / "Library" / "Application Support" / "Firefox" / "Profiles",  # macOS
        ]
    else:
        return None
    
    # Return first existing path
    for path in possible_paths:
        if path.exists():
            return str(path)
    
    return None


def get_automation_profile_path(browser_type: str) -> Optional[str]:
    """
    Get or create automation profile path within user data directory.
    Creates a dedicated 'Automation' profile to avoid conflicts.
    
    Args:
        browser_type: Browser type (chrome, edge, firefox)
        
    Returns:
        Path to automation profile, or None if base directory not found
    """
    base_dir = get_default_user_data_dir(browser_type)
    
    if not base_dir:
        return None
    
    # Create Automation profile directory
    automation_profile = Path(base_dir) / "Automation"
    
    # Create directory if it doesn't exist
    automation_profile.mkdir(parents=True, exist_ok=True)
    
    return str(automation_profile)
