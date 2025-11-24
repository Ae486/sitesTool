-- Migration: Add storage state fields to automation_flows table
-- Date: 2025-11-21
-- Description: Adds use_storage_state and storage_state_name fields to support persistent browser sessions

-- SQLite
ALTER TABLE automation_flows ADD COLUMN use_storage_state BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE automation_flows ADD COLUMN storage_state_name TEXT;

-- Note: 
-- - use_storage_state: Whether to save and reuse browser login state (cookies, localStorage, etc.)
-- - storage_state_name: Name of the storage state file for this flow
-- - Storage state files will be saved in backend/data/storage_states/
