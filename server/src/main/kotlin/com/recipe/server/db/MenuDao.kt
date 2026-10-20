package com.recipe.server.db

import com.recipe.core.domain.MenuStats
import com.recipe.core.domain.MenuStatsCalculator
import com.recipe.core.model.MenuRecord
import com.recipe.core.model.OrderItem

/**
 * Menu records are private per user: every method is scoped by [MenuDao.userId]-style
 * parameters so one account can never see or mutate another's history.
 */
interface MenuDao {
    fun list(userId: Long): List<MenuRecord>
    fun stats(userId: Long): MenuStats
    fun create(userId: Long, record: MenuRecord): MenuRecord
    fun updateCost(userId: Long, id: Long, totalCost: Double): MenuRecord?
    fun delete(userId: Long, id: Long)
}

class MySqlMenuDao(private val db: ConnectionProvider) : MenuDao {

    override fun list(userId: Long): List<MenuRecord> = db.withConnection { connection ->
        connection.prepareStatement(
            "SELECT id, created_at, items, total_cost, note FROM menu_record WHERE user_id = ? ORDER BY created_at DESC"
        ).use { ps ->
            ps.setLong(1, userId)
            ps.executeQuery().use { rs ->
                val out = mutableListOf<MenuRecord>()
                while (rs.next()) {
                    val gson = JsonHolder.gson
                    val items = gson.fromJson<Array<OrderItem>>(rs.getString("items"), Array<OrderItem>::class.java)
                    out += MenuRecord(
                        id = rs.getLong("id"),
                        items = items?.toList() ?: emptyList(),
                        totalCost = rs.getDouble("total_cost"),
                        note = rs.getString("note") ?: "",
                        createdAt = rs.getLong("created_at")
                    )
                }
                out
            }
        }
    }

    override fun stats(userId: Long): MenuStats = MenuStatsCalculator.calculate(list(userId))

    override fun create(userId: Long, record: MenuRecord): MenuRecord {
        val gson = JsonHolder.gson
        return if (record.id == 0L) {
            db.withConnection { connection ->
                connection.prepareStatement(
                    "INSERT INTO menu_record (user_id, created_at, items, total_cost, note) VALUES (?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
                ).use { ps ->
                    ps.setLong(1, userId)
                    ps.setLong(2, record.createdAt)
                    ps.setString(3, gson.toJson(record.items))
                    ps.setDouble(4, record.totalCost)
                    ps.setString(5, record.note)
                    ps.executeUpdate()
                    val id = ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
                    record.copy(id = id)
                }
            }
        } else {
            record
        }
    }

    override fun updateCost(userId: Long, id: Long, totalCost: Double): MenuRecord? {
        val rows = db.withConnection { connection ->
            connection.prepareStatement(
                "UPDATE menu_record SET total_cost = ? WHERE id = ? AND user_id = ?"
            ).use { ps ->
                ps.setDouble(1, totalCost)
                ps.setLong(2, id)
                ps.setLong(3, userId)
                ps.executeUpdate()
            }
        }
        if (rows == 0) return null
        return list(userId).find { it.id == id }
    }

    override fun delete(userId: Long, id: Long) {
        db.withConnection { connection ->
            connection.prepareStatement("DELETE FROM menu_record WHERE id = ? AND user_id = ?").use { ps ->
                ps.setLong(1, id)
                ps.setLong(2, userId)
                ps.executeUpdate()
            }
        }
    }
}
