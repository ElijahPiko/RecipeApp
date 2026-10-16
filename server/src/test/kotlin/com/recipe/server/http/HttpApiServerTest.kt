package com.recipe.server.http

import com.recipe.server.ServerConfig
import com.recipe.server.db.InMemoryCategoryDao
import com.recipe.server.db.InMemoryMenuDao
import com.recipe.server.db.InMemoryProfileDao
import com.recipe.server.db.InMemoryRecipeDao
import com.recipe.server.db.RecipeDao
import com.recipe.core.model.Ingredient
import com.recipe.core.model.Recipe
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class HttpApiServerTest {

    companion object {
        private lateinit var server: RecipeServer
        private lateinit var recipeDao: RecipeDao
        private var port: Int = 0
        private lateinit var baseUrl: String

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }

        @BeforeClass
        @JvmStatic
        fun startServer() {
            port = freePort()
            val config = ServerConfig(host = "127.0.0.1", port = port)
            recipeDao = InMemoryRecipeDao()
            val api = HttpApi(config, recipeDao, InMemoryMenuDao(), InMemoryProfileDao(), InMemoryCategoryDao())
            server = RecipeServer(config, api)
            server.start()
            baseUrl = "http://127.0.0.1:$port"
        }

        @AfterClass
        @JvmStatic
        fun stopServer() {
            server.stop()
        }

        private fun request(method: String, path: String, body: String? = null): Pair<Int, String> {
            val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.let {
                it.readBytes().toString(StandardCharsets.UTF_8)
            } ?: ""
            conn.disconnect()
            return code to text
        }

        private fun rawRequest(method: String, path: String, body: ByteArray?): Pair<Int, String> {
            val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/octet-stream")
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.let {
                it.readBytes().toString(StandardCharsets.UTF_8)
            } ?: ""
            conn.disconnect()
            return code to text
        }

        private fun getRaw(path: String): Pair<Int, ByteArray> {
            val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            val code = conn.responseCode
            val bytes = (if (code in 200..299) conn.inputStream else conn.errorStream)?.readBytes() ?: ByteArray(0)
            conn.disconnect()
            return code to bytes
        }
    }

    @Test
    fun `create and list recipes round trip`() {
        val created = request("POST", "/api/recipes", gsonRecipe("清蒸鲈鱼"))
        assertEquals(200, created.first)

        val list = request("GET", "/api/recipes")
        assertEquals(200, list.first)
        assertTrue(list.second.contains("清蒸鲈鱼"))
    }

    @Test
    fun `recipe may be created without imageUrl then image blob round trips`() {
        // A client that omits optional fields (imageUrl / hasImage / lists) must not 500.
        val minimal = """{"name":"蒜蓉青菜","category":"家常菜"}"""
        val created = request("POST", "/api/recipes", minimal)
        assertEquals(200, created.first)
        assertTrue(created.second.contains("蒜蓉青菜"))

        val id = Regex("\"id\":(\\d+)").find(created.second)!!.groupValues[1].toLong()

        // Upload an image BLOB.
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02, 0x03, 0x04)
        val upload = rawRequest("POST", "/api/recipes/$id/image", png)
        assertEquals(200, upload.first)

        // Listing now flags hasImage and exposes the image endpoint.
        val list = request("GET", "/api/recipes")
        assertEquals(200, list.first)
        assertTrue(list.second.contains("\"hasImage\":true"))
        assertTrue(list.second.contains("/api/recipes/$id/image"))

        // The stored bytes come back intact.
        val (code, bytes) = getRaw("/api/recipes/$id/image")
        assertEquals(200, code)
        assertTrue(bytes.contentEquals(png))

        // An oversized image must be rejected (413).
        val big = ByteArray(17 * 1024 * 1024)
        val tooBig = rawRequest("POST", "/api/recipes/$id/image", big)
        assertEquals(413, tooBig.first)
    }

    @Test
    fun `search filters recipes`() {
        recipeDao.save(Recipe(name = "红烧肉", category = "家常菜", foodMaterials = listOf(Ingredient("五花肉", "1斤")), steps = listOf("炖")))
        val encoded = URLEncoder.encode("五花肉", StandardCharsets.UTF_8.name())
        val result = request("GET", "/api/recipes?search=$encoded")
        assertEquals(200, result.first)
        assertTrue(result.second.contains("红烧肉"))
    }

    @Test
    fun `categories endpoint returns built-ins`() {
        val result = request("GET", "/api/categories")
        assertEquals(200, result.first)
        assertTrue(result.second.contains("家常菜"))
    }

    @Test
    fun `category crud round trips`() {
        val created = request("POST", "/api/categories", """{"name":"海鲜"}""")
        assertEquals(200, created.first)
        val id = Regex("\"id\":(\\d+)").find(created.second)!!.groupValues[1].toLong()

        assertTrue(request("GET", "/api/categories").second.contains("海鲜"))

        val updated = request("PUT", "/api/categories/$id", """{"name":"海鲜大餐","colorHex":"#123456","sortOrder":1}""")
        assertEquals(200, updated.first)
        assertTrue(request("GET", "/api/categories").second.contains("海鲜大餐"))

        assertEquals(200, request("DELETE", "/api/categories/$id").first)
        assertFalse(request("GET", "/api/categories").second.contains("海鲜大餐"))
    }

    @Test
    fun `recipes filter by category`() {
        recipeDao.save(Recipe(name = "红烧肉", category = "家常菜", foodMaterials = listOf(Ingredient("五花肉", "1斤")), steps = listOf("炖")))
        recipeDao.save(Recipe(name = "海鲜汤", category = "汤羹", foodMaterials = listOf(Ingredient("虾", "半斤")), steps = listOf("煮")))
        val category = URLEncoder.encode("汤羹", StandardCharsets.UTF_8.name())
        val filtered = request("GET", "/api/recipes?category=$category")
        assertEquals(200, filtered.first)
        assertTrue(filtered.second.contains("海鲜汤"))
        assertFalse(filtered.second.contains("红烧肉"))
    }

    @Test
    fun `create menu then query stats`() {
        val body = """{"items":[{"recipeId":1,"recipeName":"A","quantity":1}],"totalCost":88.5}"""
        val created = request("POST", "/api/menus", body)
        assertEquals(200, created.first)

        val stats = request("GET", "/api/menus/stats")
        assertEquals(200, stats.first)
        assertTrue(stats.second.contains("\"orderCount\""))
    }

    @Test
    fun `unhandled route returns 404`() {
        assertEquals(404, request("GET", "/api/nope").first)
    }

    private fun gsonRecipe(name: String): String =
        """{"id":0,"name":"$name","imageUrl":"","category":"家常菜","foodMaterials":[{"name":"鱼","amount":"1条"}],"seasonings":[{"name":"盐","amount":"1勺"}],"steps":["蒸"]}"""
}
