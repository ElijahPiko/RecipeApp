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
                    "estimated_cost DOUBLE NOT NULL DEFAULT 0," +
                    "image_data MEDIUMBLOB," +
                    "photo_updated_at BIGINT NOT NULL DEFAULT 0," +
                    "note VARCHAR(255))"
            )
            st.execute(
                "CREATE TABLE IF NOT EXISTS category (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(64) NOT NULL UNIQUE," +
                    "color_hex VARCHAR(16) NOT NULL DEFAULT '#FF9800'," +
                    "sort_order INT NOT NULL DEFAULT 0)"
            )
            // 账号与个人资料合并成一张表：一个账号就是一行，昵称/签名/所在地都在这行上。
            st.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(64) NOT NULL UNIQUE," +
                    "password_hash VARCHAR(255) NOT NULL," +
                    "salt VARCHAR(64) NOT NULL," +
                    "nickname VARCHAR(128)," +
                    "avatar_url TEXT," +
                    "signature VARCHAR(255)," +
                    "location VARCHAR(128)," +
                    "favorite_category VARCHAR(64)," +
                    "token VARCHAR(64)," +
                    "created_at BIGINT NOT NULL)"
            )
            // 材料账单：一条购买记录（材料名 / 重量 kg / 金额 元）。
            st.execute(
                "CREATE TABLE IF NOT EXISTS material_bill (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id BIGINT NOT NULL," +
                    "name VARCHAR(64) NOT NULL," +
                    "weight_kg DOUBLE NOT NULL," +
                    "price DOUBLE NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "INDEX idx_material_bill_user (user_id, created_at))"
            )
            // 材料均价表：按 (user_id, name) 维护累计重量与累计金额，均价 = 总价 / 总重量（元/千克）。
            st.execute(
                "CREATE TABLE IF NOT EXISTS material_price (" +
                    "user_id BIGINT NOT NULL," +
                    "name VARCHAR(64) NOT NULL," +
                    "total_weight_kg DOUBLE NOT NULL DEFAULT 0," +
                    "total_price DOUBLE NOT NULL DEFAULT 0," +
                    "bill_count INT NOT NULL DEFAULT 0," +
                    "updated_at BIGINT NOT NULL," +
                    "PRIMARY KEY (user_id, name))"
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
        // Theoretical cost snapshot (材料均价 × 菜谱重量), added after the first release.
        if (!columnExists("menu_record", "estimated_cost")) {
            createStatement().use {
                it.execute("ALTER TABLE menu_record ADD COLUMN estimated_cost DOUBLE NOT NULL DEFAULT 0")
            }
        }
        // Optional per-record photo (BLOB) + a version stamp used to bust the client image cache.
        if (!columnExists("menu_record", "image_data")) {
            createStatement().use {
                it.execute("ALTER TABLE menu_record ADD COLUMN image_data MEDIUMBLOB")
            }
        }
        if (!columnExists("menu_record", "photo_updated_at")) {
            createStatement().use {
                it.execute("ALTER TABLE menu_record ADD COLUMN photo_updated_at BIGINT NOT NULL DEFAULT 0")
            }
        }
        // ---- users / profile 二合一 ----
        // 老库里个人资料单独一张 profile 表；现在统一落到 users 这一行上。
        listOf(
            "avatar_url" to "TEXT",
            "signature" to "VARCHAR(255)",
            "location" to "VARCHAR(128)",
            "favorite_category" to "VARCHAR(64)"
        ).forEach { (column, type) ->
            if (!columnExists("users", column)) {
                createStatement().use { it.execute("ALTER TABLE users ADD COLUMN $column $type") }
            }
        }
        // 把 profile 里已有的资料搬进 users，然后把旧表改名留档（不直接删，出问题还能查）。
        if (tableExists("profile") && !tableExists("profile_merged_backup")) {
            if (columnExists("profile", "user_id")) {
                createStatement().use {
                    it.execute(
                        "UPDATE users u JOIN profile p ON p.user_id = u.id SET " +
                            "u.nickname = COALESCE(NULLIF(p.nickname, ''), u.nickname), " +
                            "u.avatar_url = p.avatar_url, " +
                            "u.signature = p.signature, " +
                            "u.location = p.location, " +
                            "u.favorite_category = p.favorite_category"
                    )
                }
            }
            createStatement().use { it.execute("RENAME TABLE profile TO profile_merged_backup") }
        }
    }

    private fun Connection.tableExists(table: String): Boolean =
        prepareStatement(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?"
        ).use { ps ->
            ps.setString(1, table)
            ps.executeQuery().use { rs -> rs.next() && rs.getInt(1) > 0 }
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
