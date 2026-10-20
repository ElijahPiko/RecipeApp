package com.recipe.server.db

import com.recipe.server.ServerConfig
import java.sql.Connection
import java.sql.DriverManager

/**
 * Opens the connection pool against MySQL and creates the schema/database on first run.
 *
 * The pool — not a single [Connection] — is the handle the DAOs get. That keeps the
 * "one dead connection kills the whole server" failure mode out of the picture: a broken
 * connection is discarded and replaced on the next borrow.
 */
object Db {

    /**
     * Ensures the database exists, starts the pool and applies the schema.
     *
     * Throws if MySQL cannot be reached, which lets [com.recipe.server.main] fall back to
     * in-memory storage instead of starting a server that fails every request.
     */
    fun open(config: ServerConfig): HikariConnectionProvider {
        // The database may not exist yet, and the pool URL includes it — so create it first.
        createDatabase(config)
        val pool = HikariConnectionProvider(config)
        try {
            pool.withConnection { it.createSchema() }
        } catch (e: Exception) {
            pool.close()
            throw e
        }
        return pool
    }

    private fun createDatabase(config: ServerConfig) {
        val url = "jdbc:mysql://${config.dbHost}:${config.dbPort}/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8"
        DriverManager.getConnection(url, config.dbUser, config.dbPassword).use { conn ->
            conn.createStatement().use {
                it.execute("CREATE DATABASE IF NOT EXISTS `${config.dbName}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
            }
        }
    }

    private fun Connection.createSchema() {
        createStatement().use { st ->
            st.execute(
                "CREATE TABLE IF NOT EXISTS recipe (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(255) NOT NULL," +
                    "image_url TEXT," +
                    "image_data MEDIUMBLOB," +
                    "category VARCHAR(64) NOT NULL," +
                    "food_materials JSON," +
                    "seasonings JSON," +
                    "steps JSON," +
                    "created_at BIGINT NOT NULL)"
            )
            st.execute(
                "CREATE TABLE IF NOT EXISTS menu_record (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "created_at BIGINT NOT NULL," +
                    "items JSON," +
                    "total_cost DOUBLE NOT NULL DEFAULT 0," +
                    "note VARCHAR(255))"
            )
            st.execute(
                "CREATE TABLE IF NOT EXISTS category (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(64) NOT NULL UNIQUE," +
                    "color_hex VARCHAR(16) NOT NULL DEFAULT '#FF9800'," +
                    "sort_order INT NOT NULL DEFAULT 0)"
            )
            st.execute(
                "CREATE TABLE IF NOT EXISTS profile (" +
                    "user_id BIGINT PRIMARY KEY," +
                    "nickname VARCHAR(128)," +
                    "avatar_url TEXT," +
                    "signature VARCHAR(255)," +
                    "location VARCHAR(128)," +
                    "favorite_category VARCHAR(64))"
            )
            st.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(64) NOT NULL UNIQUE," +
                    "password_hash VARCHAR(255) NOT NULL," +
                    "salt VARCHAR(64) NOT NULL," +
                    "nickname VARCHAR(128)," +
                    "token VARCHAR(64)," +
                    "created_at BIGINT NOT NULL)"
            )
        }
        // Migrate existing databases created before image_data existed (images were stored as URLs).
        if (!columnExists("recipe", "image_data")) {
            createStatement().use { it.execute("ALTER TABLE recipe ADD COLUMN image_data MEDIUMBLOB") }
        }
        // Multi-user migration: menu records and profiles become per-user.
        if (!columnExists("menu_record", "user_id")) {
            createStatement().use {
                it.execute("ALTER TABLE menu_record ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0")
            }
        }
        if (!columnExists("profile", "user_id")) {
            createStatement().use {
                it.execute("ALTER TABLE profile ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0")
            }
        }
        // Legacy profile tables were keyed by `id` (fixed row id=1) and cannot accept
        // per-user inserts. Rebuild the key around user_id; the old single row holds
        // only default values, so dropping it loses nothing.
        if (columnExists("profile", "id")) {
            createStatement().use { st ->
                st.execute("ALTER TABLE profile DROP PRIMARY KEY")
                st.execute("ALTER TABLE profile DROP COLUMN id")
                st.execute("DELETE FROM profile WHERE user_id = 0")
                st.execute("ALTER TABLE profile ADD PRIMARY KEY (user_id)")
            }
        }
    }

    private fun Connection.columnExists(table: String, column: String): Boolean =
        prepareStatement(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?"
        ).use { ps ->
            ps.setString(1, table)
            ps.setString(2, column)
            ps.executeQuery().use { rs -> rs.next() && rs.getInt(1) > 0 }
        }
}
