package com.recipe.server.db

import com.recipe.core.model.Ingredient
import com.recipe.core.model.Category
import com.recipe.core.model.MenuRecord
import com.recipe.core.model.OrderItem
import com.recipe.core.model.Recipe
import com.recipe.core.model.UserProfile
import com.recipe.server.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.sql.Statement
import java.sql.DriverManager

/**
 * Real MySQL integration test. Creates a temporary database, exercises the JDBC DAOs and cleans up.
 * Skipped automatically when MySQL is unreachable so the unit-test suite stays green offline.
 */
class MySqlDaoIntegrationTest {

    private val config = ServerConfig(dbName = "recipe_app_test")

    @Before
    fun checkDbOrSkip() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver")
            DriverManager.getConnection(
                "jdbc:mysql://${config.dbHost}:${config.dbPort}/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                config.dbUser,
                config.dbPassword
            )
        } catch (e: Exception) {
            Assume.assumeTrue("MySQL unavailable: ${e.message}", false)
        }
    }

    @Test
    fun `mysql dao persists and reads recipes menus and profile`() {
        val conn = Db.connect(config)
        try {
            val recipeDao = MySqlRecipeDao(conn)
            val saved = recipeDao.save(
                Recipe(
                    name = "水煮肉片",
                    category = "家常菜",
                    foodMaterials = listOf(Ingredient("里脊", "500克")),
                    seasonings = listOf(Ingredient("豆瓣酱", "2勺")),
                    steps = listOf("肉片腌制", "焯水", "炝锅")
                )
            )
            assertTrue(saved.id != 0L)
            val found = recipeDao.get(saved.id)
            assertNotNull(found)
            assertEquals("水煮肉片", found!!.name)
            assertEquals("豆瓣酱", found.seasonings.first().name)
            assertTrue(recipeDao.search("里脊").any { it.id == saved.id })

            // Production data may store the literal value null in a JSON column (the string "null").
            // The DAO must coerce it to an empty list rather than throwing a Gson null error.
            conn.createStatement().use { st ->
                st.executeUpdate("UPDATE recipe SET seasonings = 'null', food_materials = 'null', steps = 'null' WHERE id = ${saved.id}")
            }
            val fromJsonNull = recipeDao.get(saved.id)
            assertNotNull(fromJsonNull)
            assertTrue(fromJsonNull!!.seasonings.isEmpty())
            assertTrue(fromJsonNull.foodMaterials.isEmpty())
            assertTrue(fromJsonNull.steps.isEmpty())
            conn.createStatement().use { st ->
                st.executeUpdate(
                    "UPDATE recipe SET food_materials = '[{\"name\":\"里脊\",\"amount\":\"500克\"}]', " +
                        "seasonings = '[{\"name\":\"豆瓣酱\",\"amount\":\"2勺\"}]', steps = '[\"腌制\"]' WHERE id = ${saved.id}"
                )
            }

            // Image is stored as a BLOB and exposed back.
            val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
            assertTrue(recipeDao.saveImage(saved.id, png))
            val stored = recipeDao.getImage(saved.id)
            assertNotNull(stored)
            assertTrue(stored!!.contentEquals(png))
            assertTrue(recipeDao.get(saved.id)!!.hasImage)
            assertEquals(0, recipeDao.getImage(99999L)?.size ?: 0)

            val menuDao = MySqlMenuDao(conn)
            val menu = menuDao.create(1L, MenuRecord(items = listOf(OrderItem(saved.id, "水煮肉片")), totalCost = 45.0))
            assertEquals(50.0, menuDao.updateCost(1L, menu.id, 50.0)!!.totalCost, 0.0001)
            assertTrue(menuDao.list(1L).any { it.id == menu.id })

            val profileDao = MySqlProfileDao(conn)
            profileDao.save(1L, UserProfile(nickname = "测试用户", location = "上海"))
            assertEquals("测试用户", profileDao.get(1L).nickname)

            recipeDao.delete(saved.id)
            menuDao.delete(1L, menu.id)
        } finally {
            conn.close()
        }
    }

    @Test
    fun `mysql category dao crud and recipe filter by category`() {
        val conn = Db.connect(config)
        try {
            val categoryDao = MySqlCategoryDao(conn)
            val recipeDao = MySqlRecipeDao(conn)

            val created = categoryDao.create(Category(name = "测试分类2", colorHex = "#111111", sortOrder = 99))
            assertTrue(created.id != 0L)
            assertTrue(categoryDao.list().any { it.name == "测试分类2" })

            val updated = categoryDao.update(created.copy(name = "测试分类2改"))
            assertNotNull(updated)
            assertTrue(categoryDao.list().any { it.name == "测试分类2改" })

            val saved = recipeDao.save(
                Recipe(name = "分类过滤菜", category = "汤羹", foodMaterials = listOf(Ingredient("虾", "1斤")), steps = listOf("煮"))
            )
            assertEquals(1, recipeDao.search("", "汤羹").size)
            assertEquals(0, recipeDao.search("", "不存在的分类").size)

            recipeDao.delete(saved.id)
            assertTrue(categoryDao.delete(created.id))
        } finally {
            conn.close()
        }
    }
}
