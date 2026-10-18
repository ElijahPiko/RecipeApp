package com.recipe.server

import com.recipe.server.db.Db
import com.recipe.server.db.CategoryDao
import com.recipe.server.db.InMemoryCategoryDao
import com.recipe.server.db.InMemoryMenuDao
import com.recipe.server.db.InMemoryProfileDao
import com.recipe.server.db.InMemoryRecipeDao
import com.recipe.server.db.InMemoryUserDao
import com.recipe.server.db.JsonHolder
import com.recipe.server.db.MenuDao
import com.recipe.server.db.MySqlCategoryDao
import com.recipe.server.db.MySqlMenuDao
import com.recipe.server.db.MySqlProfileDao
import com.recipe.server.db.MySqlRecipeDao
import com.recipe.server.db.MySqlUserDao
import com.recipe.server.db.ProfileDao
import com.recipe.server.db.RecipeDao
import com.recipe.server.db.UserDao
import com.recipe.server.http.HttpApi
import com.recipe.server.http.RecipeServer
import com.recipe.core.model.Ingredient
import com.recipe.core.model.Recipe
import kotlin.system.exitProcess

fun main() {
    val config = ServerConfig()
    val start = System.currentTimeMillis()

    println("Config env: ${RecipeConfigFile.envName} (source: ${config.sourceDescription})")
    println(
        "Effective: http://${config.host}:${config.port} | " +
            "db=${config.dbHost}:${config.dbPort}/${config.dbName} | inMemory=${config.useInMemory}"
    )

    lateinit var recipeDao: RecipeDao
    lateinit var categoryDao: CategoryDao
    lateinit var menuDao: MenuDao
    lateinit var profileDao: ProfileDao
    lateinit var userDao: UserDao

    if (config.useInMemory) {
        println("Running with in-memory data (no MySQL).")
        recipeDao = InMemoryRecipeDao()
        categoryDao = InMemoryCategoryDao()
        menuDao = InMemoryMenuDao()
        profileDao = InMemoryProfileDao()
        userDao = InMemoryUserDao()
    } else {
        try {
            val conn = Db.connect(config)
            println("Connected to MySQL ${config.dbHost}:${config.dbPort}/${config.dbName}.")
            recipeDao = MySqlRecipeDao(conn)
            categoryDao = MySqlCategoryDao(conn)
            categoryDao.seedDefaults()
            menuDao = MySqlMenuDao(conn)
            profileDao = MySqlProfileDao(conn)
            userDao = MySqlUserDao(conn)
            seedIfEmpty(recipeDao)
        } catch (e: Exception) {
            System.err.println("MySQL connection failed: ${e.message}")
            System.err.println("Falling back to in-memory storage. Set RECIPE_IN_MEMORY=false and fix MySQL to persist data.")
            recipeDao = InMemoryRecipeDao()
            categoryDao = InMemoryCategoryDao()
            menuDao = InMemoryMenuDao()
            profileDao = InMemoryProfileDao()
            userDao = InMemoryUserDao()
        }
    }

    val api = HttpApi(config, recipeDao, menuDao, profileDao, categoryDao, userDao)
    val server = RecipeServer(config, api)
    server.start()
    println("Recipe server started at http://${config.host}:${config.port} (${System.currentTimeMillis() - start}ms)")
    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
    // Block forever.
    Thread.currentThread().join()
}

private fun seedIfEmpty(recipeDao: RecipeDao) {
    if (recipeDao.search("").isNotEmpty()) return
    val now = System.currentTimeMillis()
    listOf(
        Recipe(
            name = "番茄炒蛋",
            imageUrl = "",
            category = "家常菜",
            foodMaterials = listOf(Ingredient("番茄", "2个"), Ingredient("鸡蛋", "3个")),
            seasonings = listOf(Ingredient("盐", "1勺"), Ingredient("糖", "半勺")),
            steps = listOf("番茄切块，鸡蛋打散", "热油炒蛋至凝固盛出", "下番茄翻炒出汁，加调味", "倒入鸡蛋翻炒均匀出锅"),
            createdAt = now - 1000
        ),
        Recipe(
            name = "西红柿牛腩汤",
            imageUrl = "",
            category = "汤羹",
            foodMaterials = listOf(Ingredient("牛腩", "500克"), Ingredient("番茄", "3个")),
            seasonings = listOf(Ingredient("盐", "2勺"), Ingredient("姜", "3片")),
            steps = listOf("牛腩焯水去腥", "番茄炒出沙", "加水炖煮1小时", "调味撒葱花"),
            createdAt = now - 2000
        )
    ).forEach { recipeDao.save(it) }
    println("Seeded sample recipes.")
}
