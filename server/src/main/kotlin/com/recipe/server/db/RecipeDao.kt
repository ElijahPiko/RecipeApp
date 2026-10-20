package com.recipe.server.db

import com.recipe.core.model.Ingredient
import com.recipe.core.model.Recipe

interface RecipeDao {
    fun search(search: String, category: String? = null): List<Recipe>
    fun get(id: Long): Recipe?
    fun save(recipe: Recipe): Recipe
    fun delete(id: Long)
    fun categories(): List<String>
    fun getImage(id: Long): ByteArray?
    fun saveImage(id: Long, bytes: ByteArray): Boolean
}

/**
 * JDBC-backed [RecipeDao] storing recipe ingredients/seasonings/steps as JSON columns.
 *
 * Every call borrows a connection from [db] and gives it back when the statement is
 * finished, so no connection is ever held (or shared) across requests.
 */
class MySqlRecipeDao(private val db: ConnectionProvider) : RecipeDao {

    override fun search(search: String, category: String?): List<Recipe> {
        val q = search.trim()
        val conditions = mutableListOf<String>()
        val params = mutableListOf<String>()
        if (q.isNotEmpty()) {
            conditions += "(name LIKE ? OR category LIKE ? OR JSON_SEARCH(food_materials, 'one', concat('%', ?, '%')) IS NOT NULL)"
            params += "%$q%"
            params += "%$q%"
            params += q
        }
        val cat = category?.trim().orEmpty()
        if (cat.isNotEmpty()) {
            conditions += "category = ?"
            params += cat
        }
        val where = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
        val sql = "SELECT id, name, category, food_materials, seasonings, steps, created_at, " +
            "(image_data IS NOT NULL) AS has_image FROM recipe$where ORDER BY created_at DESC"
        return db.withConnection { connection ->
            connection.prepareStatement(sql).use { ps ->
                params.forEachIndexed { i, p -> ps.setString(i + 1, p) }
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<Recipe>()
                    while (rs.next()) out += rs.toRecipe()
                    out
                }
            }
        }
    }

    override fun get(id: Long): Recipe? = db.withConnection { connection ->
        connection.prepareStatement(
            "SELECT id, name, category, food_materials, seasonings, steps, created_at, (image_data IS NOT NULL) AS has_image FROM recipe WHERE id = ?"
        ).use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toRecipe() else null }
        }
    }

    override fun save(recipe: Recipe): Recipe = db.withConnection { connection ->
        val gson = JsonHolder.gson
        if (recipe.id == 0L) {
            connection.prepareStatement(
                "INSERT INTO recipe (name, category, food_materials, seasonings, steps, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS
            ).use { ps ->
                ps.bind(recipe, gson)
                ps.executeUpdate()
                val id = ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
                recipe.copy(id = id)
            }
        } else {
            connection.prepareStatement(
                "UPDATE recipe SET name=?, category=?, food_materials=?, seasonings=?, steps=? WHERE id=?"
            ).use { ps ->
                ps.bind(recipe, gson)
                ps.setLong(6, recipe.id)
                ps.executeUpdate()
            }
            recipe
        }
    }

    override fun delete(id: Long) {
        db.withConnection { connection ->
            connection.prepareStatement("DELETE FROM recipe WHERE id = ?").use { ps ->
                ps.setLong(1, id)
                ps.executeUpdate()
            }
        }
    }

    override fun categories(): List<String> = db.withConnection { connection ->
        connection.prepareStatement(
            "SELECT DISTINCT category FROM recipe ORDER BY category"
        ).use { ps ->
            ps.executeQuery().use { rs ->
                val out = mutableListOf<String>()
                while (rs.next()) out += rs.getString("category")
                out + CategoryCatalogDefaults.defaults.filter { it !in out }
            }
        }
    }

    override fun getImage(id: Long): ByteArray? = db.withConnection { connection ->
        connection.prepareStatement("SELECT image_data FROM recipe WHERE id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getBytes("image_data") else null }
        }
    }

    override fun saveImage(id: Long, bytes: ByteArray): Boolean = db.withConnection { connection ->
        connection.prepareStatement("UPDATE recipe SET image_data = ? WHERE id = ?").use { ps ->
            ps.setBytes(1, bytes)
            ps.setLong(2, id)
            ps.executeUpdate() > 0
        }
    }

    private fun java.sql.ResultSet.toRecipe(): Recipe {
        val gson = JsonHolder.gson
        return Recipe(
            id = getLong("id"),
            name = getString("name"),
            imageUrl = "",
            hasImage = getInt("has_image") != 0,
            category = getString("category"),
            foodMaterials = parseList(getString("food_materials"), gson, Array<Ingredient>::class.java),
            seasonings = parseList(getString("seasonings"), gson, Array<Ingredient>::class.java),
            steps = parseList(getString("steps"), gson, Array<String>::class.java),
            createdAt = getLong("created_at")
        )
    }

    private fun java.sql.PreparedStatement.bind(recipe: Recipe, gson: com.google.gson.Gson) {
        setString(1, recipe.name)
        setString(2, recipe.category)
        setString(3, gson.toJson(recipe.foodMaterials))
        setString(4, gson.toJson(recipe.seasonings))
        setString(5, gson.toJson(recipe.steps))
        setLong(6, recipe.createdAt)
    }

    private fun <T> parseList(json: String?, gson: com.google.gson.Gson, klass: Class<Array<T>>): List<T> {
        // JSON columns may store the literal value null as the string "null".
        if (json.isNullOrBlank() || json == "null") return emptyList()
        val arr = gson.fromJson<Array<T>?>(json, klass)
        return arr?.toList() ?: emptyList()
    }
}

object CategoryCatalogDefaults {
    val defaults = listOf("家常菜", "汤羹", "凉菜", "主食", "烘焙", "饮品", "其他")
}
