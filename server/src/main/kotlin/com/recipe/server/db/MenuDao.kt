package com.recipe.server.db

import com.recipe.core.domain.MenuStats
import com.recipe.core.domain.MenuStatsCalculator
import com.recipe.core.model.MenuRecord
import com.recipe.core.model.OrderItem
import java.sql.Connection

interface MenuDao {
    fun list(): List<MenuRecord>
    fun stats(): MenuStats
    fun create(record: MenuRecord): MenuRecord
    fun updateCost(id: Long, totalCost: Double): MenuRecord?
    fun delete(id: Long)
}

class MySqlMenuDao(private val connection: Connection) : MenuDao {

    override fun list(): List<MenuRecord> =
        connection.prepareStatement(
            "SELECT id, created_at, items, total_cost, note FROM menu_record ORDER BY created_at DESC"
        ).use { ps ->
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

    override fun stats(): MenuStats = MenuStatsCalculator.calculate(list())

    override fun create(record: MenuRecord): MenuRecord {
        val gson = JsonHolder.gson
        return if (record.id == 0L) {
            connection.prepareStatement(
                "INSERT INTO menu_record (created_at, items, total_cost, note) VALUES (?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS
            ).use { ps ->
                ps.setLong(1, record.createdAt)
                ps.setString(2, gson.toJson(record.items))
                ps.setDouble(3, record.totalCost)
                ps.setString(4, record.note)
                ps.executeUpdate()
                val id = ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
                record.copy(id = id)
            }
        } else {
            record
        }
    }

    override fun updateCost(id: Long, totalCost: Double): MenuRecord? {
        val rows = connection.prepareStatement("UPDATE menu_record SET total_cost = ? WHERE id = ?").use { ps ->
            ps.setDouble(1, totalCost)
            ps.setLong(2, id)
            ps.executeUpdate()
        }
        if (rows == 0) return null
        return list().find { it.id == id }
    }

    override fun delete(id: Long) {
        connection.prepareStatement("DELETE FROM menu_record WHERE id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeUpdate()
        }
    }
}
