package com.recipe.core.domain

import com.recipe.core.model.MenuRecord
import com.recipe.core.model.OrderItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuStatsCalculatorTest {

    @Test
    fun `empty list gives zero stats`() {
        val stats = MenuStatsCalculator.calculate(emptyList())
        assertEquals(0, stats.orderCount)
        assertEquals(0.0, stats.totalCost, 0.0001)
        assertEquals(0.0, stats.averageCost, 0.0001)
    }

    @Test
    fun `single order equals the total and average`() {
        val record = MenuRecord(id = 1, items = listOf(OrderItem(1, "A")), totalCost = 88.5)
        val stats = MenuStatsCalculator.calculate(listOf(record))
        assertEquals(1, stats.orderCount)
        assertEquals(88.5, stats.totalCost, 0.0001)
        assertEquals(88.5, stats.averageCost, 0.0001)
    }

    @Test
    fun `sums cost and computes average`() {
        val stats = MenuStatsCalculator.calculate(
            listOf(
                MenuRecord(id = 1, totalCost = 100.0),
                MenuRecord(id = 2, totalCost = 50.0),
                MenuRecord(id = 3, totalCost = 30.0)
            )
        )
        assertEquals(3, stats.orderCount)
        assertEquals(180.0, stats.totalCost, 0.0001)
        assertEquals(60.0, stats.averageCost, 0.0001)
    }

    @Test
    fun `average is rounded to money precision`() {
        val stats = MenuStatsCalculator.calculate(
            listOf(
                MenuRecord(id = 1, totalCost = 10.0),
                MenuRecord(id = 2, totalCost = 7.0)
            )
        )
        assertEquals(8.5, stats.averageCost, 0.0001)
    }
}
