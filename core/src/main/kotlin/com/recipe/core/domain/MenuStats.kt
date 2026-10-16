package com.recipe.core.domain

import com.recipe.core.model.MenuRecord

/**
 * Aggregate statistics shown at the top of the 菜单 page.
 */
data class MenuStats(
    val orderCount: Int,
    val totalCost: Double,
    val averageCost: Double
)

object MenuStatsCalculator {
    fun calculate(records: List<MenuRecord>): MenuStats {
        val count = records.size
        val total = records.sumOf { it.totalCost }
        val average = if (count == 0) 0.0 else total / count
        return MenuStats(orderCount = count, totalCost = total, averageCost = average)
    }
}
