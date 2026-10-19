package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0071_KomgaIndex : SQLMigration() {
    override val sql =
        """
        ALTER TABLE komga_connection ADD COLUMN auto_link BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE komga_connection ADD COLUMN show_counter BOOLEAN NOT NULL DEFAULT TRUE;
        ALTER TABLE komga_link ADD COLUMN provenance VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
        CREATE TABLE komga_index (
            user_id INTEGER PRIMARY KEY REFERENCES useraccount(id) ON DELETE CASCADE,
            identity VARCHAR(36) NOT NULL,
            credential VARCHAR(64) NOT NULL,
            fetched_at BIGINT NOT NULL,
            payload TEXT NOT NULL
        );
        """.trimIndent()
}
