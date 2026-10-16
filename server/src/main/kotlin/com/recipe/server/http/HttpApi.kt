package com.recipe.server.http

import com.recipe.core.model.Category
import com.recipe.server.ServerConfig
import com.recipe.server.db.CategoryDao
import com.recipe.server.db.JsonHolder
import com.recipe.server.db.MenuDao
import com.recipe.server.db.ProfileDao
import com.recipe.server.db.RecipeDao
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * The REST API. Exposes JSON endpoints backed by the DAOs.
 *
 * Recipe images are stored as BLOBs in MySQL (max 16 MB) and served from
 * `/api/recipes/{id}/image`. The Recipe JSON exposes a client-facing `imageUrl`
 * that points to that endpoint when the recipe has an image.
 */
class HttpApi(
    private val config: ServerConfig,
    private val recipeDao: RecipeDao,
    private val menuDao: MenuDao,
    private val profileDao: ProfileDao,
    private val categoryDao: CategoryDao
) {

    companion object {
        private const val MAX_IMAGE_BYTES = 16 * 1024 * 1024
    }

    fun handle(exchange: HttpExchange) {
        try {
            val method = exchange.requestMethod
            val path = exchange.requestURI.path
            val response: Response = when {
                path == "/api/recipes" && method == "GET" -> listRecipes(exchange)
                path == "/api/recipes" && method == "POST" -> createRecipe(exchange)
                path == "/api/categories" && method == "GET" -> categories()
                path == "/api/categories" && method == "POST" -> createCategory(exchange)
                path.matches(Regex("/api/categories/[0-9]+")) && method == "PUT" -> updateCategory(exchange)
                path.matches(Regex("/api/categories/[0-9]+")) && method == "DELETE" -> deleteCategory(exchange)
                path == "/api/menus" && method == "GET" -> listMenus()
                path == "/api/menus/stats" && method == "GET" -> menuStats()
                path == "/api/menus" && method == "POST" -> createMenu(exchange)
                path == "/api/profile" && method == "GET" -> getProfile()
                path == "/api/profile" && method == "PUT" -> saveProfile(exchange)
                path.matches(Regex("/api/recipes/[0-9]+/image")) && method == "POST" -> uploadRecipeImage(exchange)
                path.matches(Regex("/api/recipes/[0-9]+/image")) && method == "GET" -> serveRecipeImage(exchange)
                path.matches(Regex("/api/recipes/[0-9]+")) &&
                    (method == "GET" || method == "PUT" || method == "DELETE") -> recipeById(exchange, method)
                path.matches(Regex("/api/menus/[0-9]+")) &&
                    (method == "PUT" || method == "DELETE") -> menuById(exchange, method)
                else -> Response(404, JsonHolder.gson.toJson(mapOf("error" to "not found")))
            }
            if (response.raw != null) {
                exchange.sendResponseHeaders(response.status, response.raw.size.toLong())
                exchange.responseBody.use { it.write(response.raw) }
            } else {
                val bytes = response.body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                exchange.sendResponseHeaders(response.status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = JsonHolder.gson.toJson(mapOf("error" to (e.message ?: e.javaClass.simpleName)))
            val bytes = msg.toByteArray()
            try {
                exchange.sendResponseHeaders(500, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } catch (_: Exception) { /* connection may already be closed */ }
        } finally {
            exchange.close()
        }
    }

    // ---- Recipes ----
    private fun listRecipes(exchange: HttpExchange): Response {
        val query = (exchange.requestURI.rawQuery ?: "").parseQuery()
        val search = query["search"] ?: ""
        val category = query["category"]
        val host = host(exchange)
        val recipes = recipeDao.search(search, category).map { attachImageUrl(it, host) }
        return Response(200, JsonHolder.gson.toJson(recipes))
    }

    private fun createRecipe(exchange: HttpExchange): Response {
        val recipe = normalizeRecipe(
            JsonHolder.gson.fromJson<com.recipe.core.model.Recipe>(readBody(exchange), com.recipe.core.model.Recipe::class.java)
        )
        if (recipe.name.isBlank() || recipe.category.isBlank()) return Response(400, JsonHolder.gson.toJson(mapOf("error" to "菜名和分类不能为空")))
        val saved = recipeDao.save(recipe)
        return Response(200, JsonHolder.gson.toJson(saved))
    }

    private fun recipeById(exchange: HttpExchange, method: String): Response {
        val id = exchange.requestURI.path.substringAfterLast('/').toLongOrNull()
            ?: return Response(400, JsonHolder.gson.toJson(mapOf("error" to "invalid id")))
        return when (method) {
            "GET" -> recipeDao.get(id)?.let {
                Response(200, JsonHolder.gson.toJson(attachImageUrl(it, host(exchange))))
            } ?: Response(404, JsonHolder.gson.toJson(mapOf("error" to "not found")))
            "PUT" -> {
                val recipe = normalizeRecipe(
                    JsonHolder.gson.fromJson<com.recipe.core.model.Recipe>(readBody(exchange), com.recipe.core.model.Recipe::class.java)
                )
                val saved = recipeDao.save(recipe.copy(id = id))
                Response(200, JsonHolder.gson.toJson(saved))
            }
            else -> {
                recipeDao.delete(id)
                Response(200, "{}")
            }
        }
    }

    private fun categories(): Response =
        Response(200, JsonHolder.gson.toJson(categoryDao.list()))

    private fun createCategory(exchange: HttpExchange): Response {
        val cat = JsonHolder.gson.fromJson<Category>(readBody(exchange), Category::class.java)
        val name = cat.name ?: ""
        if (name.isBlank()) return Response(400, JsonHolder.gson.toJson(mapOf("error" to "分类名不能为空")))
        val saved = categoryDao.create(normalizeCategory(cat, id = 0, name = name))
        return Response(200, JsonHolder.gson.toJson(saved))
    }

    private fun updateCategory(exchange: HttpExchange): Response {
        val id = exchange.requestURI.path.substringAfterLast('/').toLongOrNull()
            ?: return Response(400, JsonHolder.gson.toJson(mapOf("error" to "invalid id")))
        val cat = JsonHolder.gson.fromJson<Category>(readBody(exchange), Category::class.java)
        val name = cat.name ?: ""
        if (name.isBlank()) return Response(400, JsonHolder.gson.toJson(mapOf("error" to "分类名不能为空")))
        val saved = categoryDao.update(normalizeCategory(cat, id = id, name = name))
            ?: return Response(404, JsonHolder.gson.toJson(mapOf("error" to "not found")))
        return Response(200, JsonHolder.gson.toJson(saved))
    }

    private fun deleteCategory(exchange: HttpExchange): Response {
        val id = exchange.requestURI.path.substringAfterLast('/').toLongOrNull()
            ?: return Response(400, JsonHolder.gson.toJson(mapOf("error" to "invalid id")))
        return if (categoryDao.delete(id)) Response(200, "{}")
        else Response(404, JsonHolder.gson.toJson(mapOf("error" to "not found")))
    }

    // ---- Menus ----
    private fun listMenus(): Response = Response(200, JsonHolder.gson.toJson(menuDao.list()))
    private fun menuStats(): Response = Response(200, JsonHolder.gson.toJson(menuDao.stats()))

    private fun createMenu(exchange: HttpExchange): Response {
        val record = JsonHolder.gson.fromJson<com.recipe.core.model.MenuRecord>(readBody(exchange), com.recipe.core.model.MenuRecord::class.java)
        val saved = menuDao.create(record)
        return Response(200, JsonHolder.gson.toJson(saved))
    }

    private fun menuById(exchange: HttpExchange, method: String): Response {
        val id = exchange.requestURI.path.substringAfterLast('/').toLongOrNull()
            ?: return Response(400, JsonHolder.gson.toJson(mapOf("error" to "invalid id")))
        return when (method) {
            "PUT" -> {
                val body = JsonHolder.gson.fromJson<com.recipe.core.model.MenuRecord>(readBody(exchange), com.recipe.core.model.MenuRecord::class.java)
                val updated = menuDao.updateCost(id, body.totalCost)
                updated?.let { Response(200, JsonHolder.gson.toJson(it)) }
                    ?: Response(404, JsonHolder.gson.toJson(mapOf("error" to "not found")))
            }
            else -> {
                menuDao.delete(id)
                Response(200, "{}")
            }
        }
    }

    // ---- Profile ----
    private fun getProfile(): Response = Response(200, JsonHolder.gson.toJson(profileDao.get()))

    private fun saveProfile(exchange: HttpExchange): Response {
        val profile = JsonHolder.gson.fromJson<com.recipe.core.model.UserProfile>(readBody(exchange), com.recipe.core.model.UserProfile::class.java)
        return Response(200, JsonHolder.gson.toJson(profileDao.save(profile)))
    }

    // ---- Recipe images (BLOB storage) ----
    private fun uploadRecipeImage(exchange: HttpExchange): Response {
        val id = exchange.requestURI.path.split('/').getOrNull(3)?.toLongOrNull()
            ?: return Response(400, JsonHolder.gson.toJson(mapOf("error" to "invalid id")))
        val bytes = readBodyBytes(exchange)
        if (bytes.isEmpty()) return Response(400, JsonHolder.gson.toJson(mapOf("error" to "图片为空")))
        if (bytes.size > MAX_IMAGE_BYTES) return Response(413, JsonHolder.gson.toJson(mapOf("error" to "图片大小不能超过 16MB")))
        val saved = recipeDao.saveImage(id, bytes)
        if (!saved) return Response(404, JsonHolder.gson.toJson(mapOf("error" to "菜谱不存在")))
        return Response(200, JsonHolder.gson.toJson(mapOf("ok" to true)))
    }

    private fun serveRecipeImage(exchange: HttpExchange): Response {
        val id = exchange.requestURI.path.split('/').getOrNull(3)?.toLongOrNull()
            ?: return Response(400, JsonHolder.gson.toJson(mapOf("error" to "invalid id")))
        val bytes = recipeDao.getImage(id) ?: return Response(404, JsonHolder.gson.toJson(mapOf("error" to "not found")))
        exchange.responseHeaders.set("Content-Type", imageContentType(bytes))
        return Response(200, "", raw = bytes)
    }

    // ---- Helpers ----
    private fun normalizeRecipe(
        r: com.recipe.core.model.Recipe
    ): com.recipe.core.model.Recipe = com.recipe.core.model.Recipe(
        id = r.id,
        name = r.name ?: "",
        imageUrl = r.imageUrl ?: "",
        hasImage = r.hasImage,
        category = r.category ?: "",
        foodMaterials = r.foodMaterials ?: emptyList(),
        seasonings = r.seasonings ?: emptyList(),
        steps = r.steps ?: emptyList(),
        createdAt = if (r.createdAt <= 0L) System.currentTimeMillis() else r.createdAt
    )

    /**
     * [Category] is a Kotlin data class but Gson allocates it without invoking the constructor,
     * so omitted JSON fields (e.g. colorHex) can be null at runtime. Normalize them to safe values.
     */
    private fun normalizeCategory(cat: Category, id: Long, name: String): Category = Category(
        id = id,
        name = name,
        colorHex = (cat.colorHex ?: "").ifBlank { "#FF9800" },
        sortOrder = cat.sortOrder
    )

    private fun attachImageUrl(recipe: com.recipe.core.model.Recipe, host: String): com.recipe.core.model.Recipe =
        if (recipe.hasImage) recipe.copy(imageUrl = "http://$host/api/recipes/${recipe.id}/image")
        else recipe.copy(imageUrl = "")

    private fun host(exchange: HttpExchange): String =
        exchange.requestHeaders.getFirst("Host") ?: "${config.host}:${config.port}"

    private fun imageContentType(bytes: ByteArray): String = when {
        bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size > 7 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() -> "image/png"
        bytes.size > 5 && bytes.decodeToString(0, 3) == "GIF" -> "image/gif"
        bytes.size > 11 && bytes.decodeToString(0, 4) == "RIFF" && bytes.decodeToString(8, 12) == "WEBP" -> "image/webp"
        else -> "application/octet-stream"
    }

    private fun readBody(exchange: HttpExchange): String =
        String(readBodyBytes(exchange), StandardCharsets.UTF_8)

    private fun readBodyBytes(exchange: HttpExchange): ByteArray {
        val out = ByteArrayOutputStream()
        exchange.requestBody.use { input -> input.copyTo(out) }
        return out.toByteArray()
    }

    private fun String.parseQuery(): Map<String, String> {
        if (isBlank()) return emptyMap()
        return split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            val key = java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name())
            val value = java.net.URLDecoder.decode(parts.getOrNull(1) ?: "", StandardCharsets.UTF_8.name())
            key to value
        }.toMap()
    }

    data class Response(val status: Int, val body: String, val raw: ByteArray? = null)
}

/**
 * Starts the [HttpServer] on a background thread with a shared executor.
 */
class RecipeServer(
    private val config: ServerConfig,
    private val api: HttpApi
) {
    private var server: HttpServer? = null

    fun start() {
        val http = HttpServer.create(InetSocketAddress(config.host, config.port), 0)
        http.executor = Executors.newFixedThreadPool(8)
        http.createContext("/") { api.handle(it) }
        http.start()
        server = http
    }

    fun stop() {
        server?.stop(0)
        server = null
    }
}
