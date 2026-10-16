package com.recipe.server.db

import com.recipe.server.ServerConfig
import java.sql.Connection
import java.sql.DriverManager

/**
 * Opens connections to the local MySQL and creates the schema/database on first run.
 */
object Db {

    fun connect(config: ServerConfig): Connection {
        // Ensure the database exists (no db in url), then reconnect to it.
        createDatabase(config)
        return openConnection(config)
    }

    private fun openConnection(config: ServerConfig): Connection {
        val url = "jdbc:mysql://${config.dbHost}:${config.dbPort}/${config.dbName}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8"
        return DriverManager.getConnection(url, config.dbUser, config.dbPassword).apply {
            autoCommit = true
            createSchema()
        }
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
                    "id BIGINT PRIMARY KEY," +
                    "nickname VARCHAR(128)," +
                    "avatar_url TEXT," +
                    "signature VARCHAR(255)," +
                    "location VARCHAR(128)," +
                    "favorite_category VARCHAR(64))"
            )
        }
        // Migrate existing databases created before image_data existed (images were stored as URLs).
        if (!columnExists("recipe", "image_data")) {
            createStatement().use { it.execute("ALTER TABLE recipe ADD COLUMN image_data MEDIUMBLOB") }
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
