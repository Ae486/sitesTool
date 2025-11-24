"""Browser launcher utility for CDP mode with auto-start capability."""
import asyncio
import logging
import socket
import subprocess
import time
from pathlib import Path
from typing import Optional
import urllib.request
import urllib.error

logger = logging.getLogger(__name__)


def is_port_in_use(port: int) -> bool:
    """Check if a port is already in use."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        try:
            s.bind(('localhost', port))
            return False
        except OSError:
            return True


def is_cdp_ready(port: int, verbose: bool = False) -> bool:
    """Check if CDP is actually ready by trying to fetch version info."""
    try:
        url = f"http://localhost:{port}/json/version"
        req = urllib.request.Request(url, method='GET')
        with urllib.request.urlopen(req, timeout=2) as response:
            if response.status == 200:
                if verbose:
                    import json
                    data = json.loads(response.read())
                    logger.info(f"CDP Version: {data.get('Browser', 'Unknown')}")
                return True
    except urllib.error.URLError as e:
        if verbose:
            logger.debug(f"CDP check URLError: {e}")
    except urllib.error.HTTPError as e:
        if verbose:
            logger.debug(f"CDP check HTTPError: {e}")
    except (TimeoutError, ConnectionError) as e:
        if verbose:
            logger.debug(f"CDP check connection error: {e}")
    except Exception as e:
        if verbose:
            logger.debug(f"CDP check unexpected error: {e}")
    return False


def find_browser_executable(browser_type: str, custom_path: Optional[str] = None) -> Optional[str]:
    """
    Find browser executable path.
    
    Args:
        browser_type: Browser type (chrome, edge, chromium)
        custom_path: Custom browser path if provided
        
    Returns:
        Path to browser executable or None if not found
    """
    if custom_path and Path(custom_path).exists():
        return custom_path
    
    # Common browser paths on Windows
    common_paths = {
        "chrome": [
            r"C:\Program Files\Google\Chrome\Application\chrome.exe",
            r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
            Path.home() / "AppData" / "Local" / "Google" / "Chrome" / "Application" / "chrome.exe",
        ],
        "edge": [
            r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
            r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
            Path.home() / "AppData" / "Local" / "Microsoft" / "Edge" / "Application" / "msedge.exe",
        ],
    }
    
    # Try common paths for the browser type
    for path in common_paths.get(browser_type, []):
        p = Path(path) if isinstance(path, str) else path
        if p.exists():
            return str(p)
    
    return None


def get_default_user_data_dir(browser_type: str) -> Optional[str]:
    """
    Get default user data directory for the browser.
    This is where user's login states, bookmarks, extensions are stored.
    
    Args:
        browser_type: Browser type (chrome, edge, chromium)
        
    Returns:
        Path to user data directory or None if not found
    """
    user_data_dirs = {
        "chrome": Path.home() / "AppData" / "Local" / "Google" / "Chrome" / "User Data",
        "edge": Path.home() / "AppData" / "Local" / "Microsoft" / "Edge" / "User Data",
    }
    
    default_dir = user_data_dirs.get(browser_type)
    if default_dir and default_dir.exists():
        return str(default_dir)
    
    return None


class BrowserManager:
    """Manages browser lifecycle for CDP mode."""
    
    def __init__(self):
        self.process: Optional[subprocess.Popen] = None
        self.port: Optional[int] = None
    
    def start_browser(
        self,
        browser_type: str,
        port: int = 9222,
        custom_path: Optional[str] = None,
        user_data_dir: Optional[str] = None,
        headless: bool = False,
    ) -> bool:
        """
        Start browser with remote debugging port.
        
        Args:
            browser_type: Browser type (chrome, edge)
            port: CDP debug port
            custom_path: Custom browser executable path
            headless: Run in headless mode
            
        Returns:
            True if browser started successfully
        """
        # Check if port is already in use
        if is_port_in_use(port):
            logger.info(f"Port {port} already in use, assuming browser is running")
            self.port = port
            return True
        
        # Find browser executable
        browser_path = find_browser_executable(browser_type, custom_path)
        if not browser_path:
            logger.error(f"Could not find {browser_type} browser executable")
            return False
        
        logger.info(f"Starting {browser_type} on port {port}")
        logger.info(f"Browser path: {browser_path}")
        
        # Determine user data directory
        import tempfile
        import shutil
        
        # Strategy: Copy entire User Data from real browser (first time only)
        # This preserves complete configuration and allows new logins to persist
        if not user_data_dir:
            # Default: Use dedicated automation profile (copied from real browser)
            cdp_profile_dir = Path.home() / "AppData" / "Roaming" / "autoTool" / "cdp_browser_profile"
            
            is_first_time = not (cdp_profile_dir / "Default").exists()
            
            logger.info("=" * 70)
            if is_first_time:
                logger.info("🎯 CDP MODE - FIRST TIME SETUP")
                logger.info("=" * 70)
                logger.info("📦 Copying your browser configuration for automation...")
                logger.info("")
                
                # Get source profile
                source_profile = get_default_user_data_dir(browser_type)
                if not source_profile or not Path(source_profile).exists():
                    logger.error("Cannot find your default browser profile!")
                    logger.info("Creating empty CDP profile...")
                    cdp_profile_dir.mkdir(parents=True, exist_ok=True)
                else:
                    logger.info(f"Source: {source_profile}")
                    logger.info(f"Target: {cdp_profile_dir}")
                    logger.info("")
                    logger.info("⏳ This will take 20-60 seconds...")
                    
                    try:
                        # Copy entire User Data directory
                        def ignore_locked_files(directory, files):
                            """Ignore locked files and temporary files"""
                            ignore = []
                            for filename in files:
                                if (filename.endswith('-lock') or 
                                    filename.endswith('.tmp') or 
                                    filename == 'lockfile' or
                                    filename == 'SingletonLock' or
                                    filename == 'SingletonSocket' or
                                    filename == 'SingletonCookie'):
                                    ignore.append(filename)
                            return ignore
                        
                        shutil.copytree(
                            source_profile, 
                            cdp_profile_dir,
                            ignore=ignore_locked_files,
                            ignore_dangling_symlinks=True,
                            dirs_exist_ok=False
                        )
                        
                        file_count = len(list(cdp_profile_dir.rglob('*')))
                        logger.info(f"✅ Successfully copied {file_count:,} files/folders")
                        logger.info("")
                        logger.info("💡 Benefits:")
                        logger.info("   ✅ Has all your bookmarks and favorites")
                        logger.info("   ✅ Has all your extensions")
                        logger.info("   ✅ Almost identical to your daily browser")
                        logger.info("   ✅ Completely isolated - use daily browser simultaneously")
                        logger.info("")
                        logger.info("📝 ACTION REQUIRED (ONE-TIME):")
                        logger.info("   1. Browser will open (your copied configuration)")
                        logger.info("   2. Please LOGIN to websites you want to automate:")
                        logger.info("      - bilibili.com, weibo.com, etc.")
                        logger.info("   3. Login states will be SAVED permanently")
                        logger.info("   4. Future runs will be fully automated!")
                        
                    except Exception as e:
                        logger.error(f"Failed to copy profile: {e}")
                        logger.info("Creating empty CDP profile instead...")
                        cdp_profile_dir.mkdir(parents=True, exist_ok=True)
            else:
                logger.info("🎯 CDP MODE - Using Dedicated Automation Browser")
                logger.info("=" * 70)
                logger.info(f"✅ Profile: {cdp_profile_dir}")
                logger.info("")
                logger.info("💡 Features:")
                logger.info("   ✅ Saved login states from previous runs")
                logger.info("   ✅ Isolated from your daily browser")
                logger.info("   ✅ No conflicts - browse normally anytime")
                logger.info("   ✅ Reduced detection risk (almost identical to real browser)")
            
            logger.info("=" * 70)
            logger.info("")
            
            user_data_dir = str(cdp_profile_dir)
        
        logger.info(f"📁 User data directory: {user_data_dir}")
        
        # Build command
        cmd = [
            browser_path,
            f"--remote-debugging-port={port}",
            f"--user-data-dir={user_data_dir}",
            "--no-first-run",
            "--no-default-browser-check",
        ]
        
        if headless:
            cmd.append("--headless=new")
        
        logger.info(f"Command: {' '.join(cmd)}")
        
        # Start browser process
        try:
            # Use CREATE_NEW_PROCESS_GROUP to prevent Ctrl+C from killing browser
            creation_flags = subprocess.CREATE_NEW_PROCESS_GROUP if hasattr(subprocess, 'CREATE_NEW_PROCESS_GROUP') else 0
            
            self.process = subprocess.Popen(
                cmd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                creationflags=creation_flags,
            )
            self.port = port
            
            # Wait for browser to be ready
            max_wait = 30  # seconds - increased for slower systems
            start_time = time.time()
            
            logger.info(f"Waiting for browser to initialize (max {max_wait}s)...")
            logger.info(f"Browser process PID: {self.process.pid}")
            
            port_found = False
            check_count = 0
            while time.time() - start_time < max_wait:
                check_count += 1
                
                # Check port every iteration
                port_active = is_port_in_use(port)
                
                if not port_found and port_active:
                    elapsed = time.time() - start_time
                    logger.info(f"✅ Browser port {port} is active after {elapsed:.1f}s")
                    port_found = True
                
                # Check if CDP is actually ready
                if port_found:
                    cdp_ready = is_cdp_ready(port)
                    if check_count % 4 == 0:  # Log every 2 seconds
                        logger.info(f"Checking CDP readiness... (attempt {check_count//4}, port_active={port_active}, cdp_ready={cdp_ready})")
                    
                    if cdp_ready:
                        elapsed = time.time() - start_time
                        logger.info(f"✅ CDP interface ready after {elapsed:.1f}s")
                        # Small additional wait to ensure stability
                        time.sleep(1)
                        logger.info(f"✅ Browser started successfully on port {port}")
                        return True
                else:
                    if check_count % 4 == 0:  # Log every 2 seconds
                        logger.info(f"Waiting for port... (attempt {check_count//4}, port_active={port_active})")
                
                time.sleep(0.5)
            
            logger.error(f"❌ Browser failed to start within {max_wait}s timeout")
            logger.error(f"Port found: {port_found}, Check count: {check_count}")
            if port_found:
                logger.error("Port was active but CDP interface did not respond")
                logger.error(f"Final CDP check: {is_cdp_ready(port)}")
            else:
                logger.error(f"Port {port} was never detected as active")
                logger.error(f"Final port check: {is_port_in_use(port)}")
            
            # Try to get process status
            if self.process.poll() is None:
                logger.error("Browser process is still running but not responding")
            else:
                logger.error(f"Browser process exited with code: {self.process.poll()}")
            
            self.stop_browser()
            return False
            
        except Exception as e:
            logger.error(f"Failed to start browser: {e}")
            return False
    
    def stop_browser(self):
        """Stop the browser process."""
        if self.process:
            try:
                logger.info("Stopping browser...")
                self.process.terminate()
                
                # Wait for process to terminate
                try:
                    self.process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    logger.warning("Browser did not terminate gracefully, killing...")
                    self.process.kill()
                
                logger.info("Browser stopped")
            except Exception as e:
                logger.error(f"Error stopping browser: {e}")
            finally:
                self.process = None
                self.port = None
    
    def __del__(self):
        """Cleanup on deletion."""
        if self.process:
            self.stop_browser()


# Global browser manager instance
_browser_manager: Optional[BrowserManager] = None


def get_browser_manager() -> BrowserManager:
    """Get or create global browser manager instance."""
    global _browser_manager
    if _browser_manager is None:
        _browser_manager = BrowserManager()
    return _browser_manager
