"""
Migration Script: Remove icon_url column from sites table
Date: 2025-11-19
"""

import sqlite3
import os
import shutil
from datetime import datetime

DB_PATH = "data/app.db"
BACKUP_PATH = f"data/app.db.backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}"

def backup_database():
    """Create a backup of the database before migration."""
    if os.path.exists(DB_PATH):
        shutil.copy2(DB_PATH, BACKUP_PATH)
        print(f"✅ Database backed up to: {BACKUP_PATH}")
        return True
    else:
        print(f"❌ Database not found: {DB_PATH}")
        return False

def check_column_exists(cursor):
    """Check if icon_url column exists in sites table."""
    cursor.execute("PRAGMA table_info(sites)")
    columns = [row[1] for row in cursor.fetchall()]
    return "icon_url" in columns

def try_simple_drop(cursor):
    """Try to drop column using ALTER TABLE (SQLite 3.35.0+)."""
    try:
        cursor.execute("ALTER TABLE sites DROP COLUMN icon_url")
        return True
    except sqlite3.OperationalError as e:
        if "no such column" in str(e).lower():
            print("⚠️  Column icon_url doesn't exist (already removed?)")
            return None
        print(f"⚠️  Simple DROP COLUMN failed: {e}")
        return False

def recreate_table(cursor):
    """Recreate table without icon_url column (for older SQLite)."""
    print("Using table recreation method (for older SQLite)...")
    
    # Check if new table already exists
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='sites_new'")
    if cursor.fetchone():
        cursor.execute("DROP TABLE sites_new")
        print("  Cleaned up existing sites_new table")
    
    # Create new table
    cursor.execute("""
        CREATE TABLE sites_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name VARCHAR(200) NOT NULL,
            url VARCHAR(1024) NOT NULL,
            description TEXT,
            category_id INTEGER,
            sort_order INTEGER DEFAULT 0,
            is_active BOOLEAN DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (category_id) REFERENCES categories(id)
        )
    """)
    print("  ✓ Created new table structure")
    
    # Copy data
    cursor.execute("""
        INSERT INTO sites_new (
            id, name, url, description, category_id, sort_order, 
            is_active, created_at, updated_at
        )
        SELECT 
            id, name, url, description, category_id, sort_order, 
            is_active, created_at, updated_at
        FROM sites
    """)
    print("  ✓ Copied data to new table")
    
    # Drop old table
    cursor.execute("DROP TABLE sites")
    print("  ✓ Dropped old table")
    
    # Rename new table
    cursor.execute("ALTER TABLE sites_new RENAME TO sites")
    print("  ✓ Renamed new table to sites")
    
    # Recreate index
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_sites_name ON sites(name)")
    print("  ✓ Recreated indexes")
    
    return True

def verify_migration(cursor):
    """Verify the migration was successful."""
    cursor.execute("PRAGMA table_info(sites)")
    columns = [row[1] for row in cursor.fetchall()]
    
    if "icon_url" in columns:
        print("❌ Verification failed: icon_url column still exists")
        return False
    
    print("✅ Verification passed: icon_url column removed")
    print(f"   Current columns: {', '.join(columns)}")
    return True

def main():
    print("=" * 60)
    print("Migration: Remove icon_url from sites table")
    print("=" * 60)
    print()
    
    # Step 1: Backup
    print("Step 1: Creating backup...")
    if not backup_database():
        print("\n⚠️  Database not found. Skipping migration.")
        print("   This is normal if you haven't created any data yet.")
        return
    print()
    
    # Step 2: Connect to database
    print("Step 2: Connecting to database...")
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    print("✅ Connected")
    print()
    
    try:
        # Step 3: Check if column exists
        print("Step 3: Checking if icon_url column exists...")
        if not check_column_exists(cursor):
            print("⚠️  Column icon_url doesn't exist. Migration not needed.")
            return
        print("✅ Column exists, proceeding with migration")
        print()
        
        # Step 4: Try simple drop first
        print("Step 4: Attempting to drop column...")
        result = try_simple_drop(cursor)
        
        if result is True:
            print("✅ Column dropped using ALTER TABLE DROP COLUMN")
            conn.commit()
        elif result is False:
            # Step 5: Fallback to table recreation
            print()
            print("Step 5: Falling back to table recreation method...")
            if recreate_table(cursor):
                print("✅ Table recreated successfully")
                conn.commit()
            else:
                print("❌ Table recreation failed")
                conn.rollback()
                return
        else:
            # Column doesn't exist
            return
        
        print()
        
        # Step 6: Verify
        print("Step 6: Verifying migration...")
        if verify_migration(cursor):
            print()
            print("=" * 60)
            print("🎉 Migration completed successfully!")
            print("=" * 60)
            print()
            print("Next steps:")
            print("1. Restart the backend server")
            print("2. Test creating and editing sites")
            print("3. Verify no errors in logs")
            print()
            print(f"Backup saved at: {BACKUP_PATH}")
        else:
            print("\n⚠️  Migration verification failed. Check the database manually.")
            
    except Exception as e:
        print(f"\n❌ Migration failed with error: {e}")
        print("Rolling back changes...")
        conn.rollback()
        print(f"Database restored from backup: {BACKUP_PATH}")
        raise
    finally:
        cursor.close()
        conn.close()

if __name__ == "__main__":
    main()
