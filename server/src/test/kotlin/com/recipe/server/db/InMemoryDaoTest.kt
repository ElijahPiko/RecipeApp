package com.recipe.server.db

import com.recipe.core.model.Ingredient
import com.recipe.core.model.Category
import com.recipe.core.model.MenuRecord
import com.recipe.core.model.OrderItem
import com.recipe.core.model.Recipe
import com.recipe.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryDaoTest {

    @Test
    fun `recipe dao search matches name category and ingredient`() {
        val dao = InMemoryRecipeDao()
        dao.save(Recipe(name = "番茄炒蛋", category = "家常菜", foodMaterials = listOf(Ingredient("番茄", "2个")), steps = listOf("炒")))
        dao.save(Recipe(name = "酸菜鱼", category = "家常菜", foodMaterials = listOf(Ingredient("草鱼", "1条")), steps = listOf("煮")))
        assertEquals(1, dao.search("番茄").size)
        assertEquals(1, dao.search("草鱼").size)
        assertEquals(2, dao.search("家常菜").size)
    }

    @Test
    fun `recipe dao search filters by category`() {
        val dao = InMemoryRecipeDao()
        dao.save(Recipe(name = "红烧肉", category = "家常菜", foodMaterials = listOf(Ingredient("五花肉", "1斤")), steps = listOf("炖")))
        dao.save(Recipe(name = "海鲜汤", category = "汤羹", foodMaterials = listOf(Ingredient("虾", "半斤")), steps = listOf("煮")))
        assertEquals(1, dao.search("", "家常菜").size)
        assertEquals(0, dao.search("", "不存在的分类").size)
        assertEquals(2, dao.search("", null).size)
    }

    @Test
    fun `category dao crud`() {
        val dao = InMemoryCategoryDao()
        val created = dao.create(Category(name = "海鲜", colorHex = "#123456", sortOrder = 9))
        assertTrue(created.id != 0L)
        assertTrue(dao.list().any { it.name == "海鲜" })
        val updated = dao.update(created.copy(name = "海鲜大餐"))!!
        assertEquals("海鲜大餐", updated.name)
        assertTrue(dao.delete(created.id))
        assertFalse(dao.list().any { it.name == "海鲜大餐" })
    }

    @Test
    fun `recipe dao save assigns id and delete removes`() {
        val dao = InMemoryRecipeDao()
        val saved = dao.save(Recipe(name = "A", category = "B", foodMaterials = listOf(Ingredient("x", "1")), steps = listOf("s")))
        assertTrue(saved.id != 0L)
        assertNotNull(dao.get(saved.id))
        dao.delete(saved.id)
        assertNull(dao.get(saved.id))
    }

    @Test
    fun `categories include built-in defaults`() {
        val dao = InMemoryRecipeDao()
        assertTrue(dao.categories().contains("家常菜"))
        assertTrue(dao.categories().contains("汤羹"))
    }

    @Test
    fun `menu dao stats and cost update are scoped per user`() {
        val dao = InMemoryMenuDao()
        val a = dao.create(1L, MenuRecord(items = listOf(OrderItem(1, "A")), totalCost = 60.0))
        dao.create(1L, MenuRecord(items = listOf(OrderItem(2, "B")), totalCost = 40.0))
        assertEquals(100.0, dao.stats(1L).totalCost, 0.0001)
        assertEquals(50.0, dao.stats(1L).averageCost, 0.0001)
        assertEquals(99.0, dao.updateCost(1L, a.id, 99.0)!!.totalCost, 0.0001)
    }

    @Test
    fun `menu dao isolates users`() {
        val dao = InMemoryMenuDao()
        dao.create(1L, MenuRecord(items = listOf(OrderItem(1, "A")), totalCost = 60.0))
        assertTrue(dao.list(2L).isEmpty())
        assertEquals(1, dao.list(1L).size)
    }

    @Test
    fun `profile dao persists per user`() {
        val dao = InMemoryProfileDao()
        dao.save(1L, UserProfile(nickname = "小明"))
        assertEquals("小明", dao.get(1L).nickname)
        assertEquals("美食家", dao.get(2L).nickname)
    }
}
