package com.recipe.server.db

import com.recipe.core.model.Category
import com.recipe.core.model.CategoryCatalog
import java.sql.Statement
import java.util.concurrent.atomic.AtomicLong

interface CategoryDao {
    fun list(): List<Category>
    fun create(category: Category): Category
    fun update(category: Category): Category?
    fun delete(id: Long): Boolean
    fun seedDefaults()
}

/** JDBC-backed [CategoryDao]. Categories are their own table, seeded with built-ins on startup. */
class MySqlCategoryDao(private val db: ConnectionProvider) : CategoryDao {

    override fun list(): List<Category> = db.withConnection { connection ->
        connection.prepareStatement(
            "SELECT id, name, color_hex, sort_order FROM category ORDER BY sort_order, id"
        ).use { ps ->
            ps.executeQuery().use { rs ->
                val out = mutableListOf<Category>()
                while (rs.next()) {
                    out += Category(
                        id = rs.getLong("id"),
                        name = rs.getString("name"),
                        colorHex = rs.getString("color_hex") ?: "#FF9800",
                        sortOrder = rs.getInt("sort_order")
                    )
                }
                out
            }
        }
    }

    override fun create(category: Category): Category = db.withConnection { connection ->
        connection.prepareStatement(
            "INSERT INTO category (name, color_hex, sort_order) VALUES (?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS
        ).use { ps ->
            ps.setString(1, category.name)
            ps.setString(2, category.colorHex)
            ps.setInt(3, category.sortOrder)
            ps.executeUpdate()
            val id = ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
            category.copy(id = id)
        }
    }

    override fun update(category: Category): Category? {
        val rows = db.withConnection { connection ->
            connection.prepareStatement(
                "UPDATE category SET name = ?, color_hex = ?, sort_order = ? WHERE id = ?"
            ).use { ps ->
                ps.setString(1, category.name)
                ps.setString(2, category.colorHex)
                ps.setInt(3, category.sortOrder)
                ps.setLong(4, category.id)
                ps.executeUpdate()
            }
        }
        return if (rows == 0) null else category
    }

    override fun delete(id: Long): Boolean = db.withConnection { connection ->
        connection.prepareStatement("DELETE FROM category WHERE id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeUpdate() > 0
        }
    }

    override fun seedDefaults() {
        db.withConnection { connection ->
            CategoryCatalog.defaults.forEach { cat ->
                connection.prepareStatement(
                    "INSERT IGNORE INTO category (name, color_hex, sort_order) VALUES (?, ?, ?)"
                ).use { ps ->
                    ps.setString(1, cat.name)
                    ps.setString(2, cat.colorHex)
                    ps.setInt(3, cat.sortOrder)
                    ps.executeUpdate()
                }
            }
        }
    }
}

/** In-memory [CategoryDao] used by unit tests and the graceful fallback path. */
class InMemoryCategoryDao : CategoryDao {
    private val store = linkedMapOf<Long, Category>()
    private val ids = AtomicLong(0)

    init {
        seedDefaults()
    }

    override fun list(): List<Category> = store.values.sortedBy { it.sortOrder }

    override fun create(category: Category): Category {
        val saved = category.copy(id = ids.incrementAndGet())
        store[saved.id] = saved
        return saved
    }

    override fun update(category: Category): Category? {
        if (!store.containsKey(category.id)) return null
        val saved = category.copy(id = category.id)
        store[saved.id] = saved
        return saved
    }

    override fun delete(id: Long): Boolean = store.remove(id) != null

    override fun seedDefaults() {
        CategoryCatalog.defaults.forEach { cat ->
            if (store.values.none { it.name == cat.name }) create(cat)
        }
    }
}
