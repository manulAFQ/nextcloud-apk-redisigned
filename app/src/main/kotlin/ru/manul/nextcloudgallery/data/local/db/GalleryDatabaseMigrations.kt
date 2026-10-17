package ru.manul.nextcloudgallery.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object GalleryDatabaseMigrations {
    /**
     * Version 1 was the empty prototype database. Version 2 introduces the
     * persistent Android backend index without storing an app password.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS accounts (
                    account_id TEXT NOT NULL,
                    server_address TEXT NOT NULL,
                    username TEXT NOT NULL,
                    display_name TEXT,
                    active INTEGER NOT NULL,
                    updated_at_epoch_ms INTEGER NOT NULL,
                    PRIMARY KEY(account_id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS media (
                    account_id TEXT NOT NULL,
                    remote_path TEXT NOT NULL,
                    parent_path TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    mime_type TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    etag TEXT,
                    captured_at_epoch_ms INTEGER,
                    modified_at_epoch_ms INTEGER NOT NULL,
                    sort_epoch_ms INTEGER NOT NULL,
                    has_preview INTEGER NOT NULL,
                    sync_generation INTEGER NOT NULL,
                    PRIMARY KEY(account_id, remote_path),
                    FOREIGN KEY(account_id) REFERENCES accounts(account_id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_media_account_id_parent_path_sort_epoch_ms " +
                    "ON media (account_id, parent_path, sort_epoch_ms)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_media_account_id_etag " +
                    "ON media (account_id, etag)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_cursors (
                    account_id TEXT NOT NULL,
                    remote_path TEXT NOT NULL,
                    directory_etag TEXT,
                    last_sync_epoch_ms INTEGER NOT NULL,
                    sync_generation INTEGER NOT NULL,
                    PRIMARY KEY(account_id, remote_path),
                    FOREIGN KEY(account_id) REFERENCES accounts(account_id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS upload_jobs (
                    job_id TEXT NOT NULL,
                    account_id TEXT NOT NULL,
                    local_uri TEXT NOT NULL,
                    remote_path TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    transferred_bytes INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    attempt INTEGER NOT NULL,
                    failure_message TEXT,
                    next_attempt_epoch_ms INTEGER,
                    created_at_epoch_ms INTEGER NOT NULL,
                    updated_at_epoch_ms INTEGER NOT NULL,
                    PRIMARY KEY(job_id),
                    FOREIGN KEY(account_id) REFERENCES accounts(account_id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_upload_jobs_account_id_state_next_attempt_epoch_ms " +
                    "ON upload_jobs (account_id, state, next_attempt_epoch_ms)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_upload_jobs_account_id_remote_path " +
                    "ON upload_jobs (account_id, remote_path)",
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE upload_jobs ADD COLUMN destination_resolved INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL("ALTER TABLE upload_jobs ADD COLUMN result_etag TEXT")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS auto_upload_policies (
                    account_id TEXT NOT NULL,
                    enabled INTEGER NOT NULL,
                    include_camera INTEGER NOT NULL,
                    include_screenshots INTEGER NOT NULL,
                    include_videos INTEGER NOT NULL,
                    wifi_only INTEGER NOT NULL,
                    required_ssid TEXT,
                    require_charging INTEGER NOT NULL,
                    minimum_battery_percent INTEGER NOT NULL,
                    remote_base_path TEXT NOT NULL,
                    device_folder TEXT NOT NULL,
                    PRIMARY KEY(account_id),
                    FOREIGN KEY(account_id) REFERENCES accounts(account_id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS auto_upload_items (
                    account_id TEXT NOT NULL,
                    local_key TEXT NOT NULL,
                    content_uri TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    relative_path TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    mime_type TEXT,
                    size_bytes INTEGER NOT NULL,
                    captured_at_epoch_ms INTEGER NOT NULL,
                    modified_at_epoch_seconds INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    upload_job_id TEXT,
                    updated_at_epoch_ms INTEGER NOT NULL,
                    PRIMARY KEY(account_id, local_key),
                    FOREIGN KEY(account_id) REFERENCES accounts(account_id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_auto_upload_items_upload_job_id " +
                    "ON auto_upload_items (upload_job_id)",
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE auto_upload_policies ADD COLUMN selected_folders TEXT NOT NULL DEFAULT ''",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
