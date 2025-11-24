-- Migration: Add user_data_dir field to automation_flows table
-- Date: 2025-11-21
-- Description: Adds user_data_dir field to support persistent browser context with user profile

-- SQLite
ALTER TABLE automation_flows ADD COLUMN user_data_dir TEXT;

-- Note: 
-- - user_data_dir: Path to browser user data directory (e.g., Chrome/Edge profile folder)
-- - When specified, browser will use all existing logins and settings from that profile
-- - WARNING: Browser must not be running when using this feature
-- - Recommended: Create a separate profile for automation to avoid conflicts
