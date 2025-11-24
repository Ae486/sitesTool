-- Migration: Remove icon_url column from sites table
-- Date: 2025-11-19
-- Description: Removes the unused icon_url field from the sites table

-- SQLite
ALTER TABLE sites DROP COLUMN icon_url;

-- Note: If this fails with SQLite (older versions don't support DROP COLUMN),
-- you may need to recreate the table. See migration_notes.md for details.
