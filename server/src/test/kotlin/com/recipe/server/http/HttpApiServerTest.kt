package com.recipe.server.http

import com.recipe.server.ServerConfig
import com.recipe.server.db.InMemoryCategoryDao
import com.recipe.server.db.InMemoryMenuDao
import com.recipe.server.db.InMemoryProfileDao
import com.recipe.server.db.InMemoryRecipeDao
import com.recipe.server.db.InMemoryUserDao
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

/**
 * Round-trip tests over a real HTTP server with in-memory DAOs.
 *
 * Since the auth feature, every business endpoint requires a Bearer token, so the
 * fixture registers+logs in a test user up front and requests default to that token.
 */
class HttpApiServerTest {

    companion object {
        private lateinit var server: RecipeServer
        private lateinit var recipeDao: RecipeDao
        private var port: Int = 0
        private lateinit var baseUrl: String
        private lateinit var token: String

        private fun freePort(): Int = ServerSocket(0).use { it.localPort }

        @BeforeClass
        @JvmStatic
        fun startServer() {
            port = freePort()
            val config = ServerConfig(host = "127.0.0.1", port = port)
            recipeDao = InMemoryRecipeDao()
            val api = HttpApi(config, recipeDao, InMemoryMenuDao(), InMemoryProfileDao(), InMemoryCategoryDao(), InMemoryUserDao())
            server = RecipeServer(config, api)
            server.start()
            baseUrl = "http://127.0.0.1:$port"

            // Register + log in a fixture user so tests can authenticate.
            request(
                "POST", "/api/auth/register",
                """{"username":"chef_test","password":"secret66","nickname":"测试厨师"}""",
                auth = null
            )
            val login = request(
                "POST", "/api/auth/login",
                """{"username":"chef_test","password":"secret66"}""",
                auth = null
            )
            token = Regex("\"token\":\"([^\"]+)\"").find(login.second)!!.groupValues[1]
        }

        @AfterClass
        @JvmStatic
        fun stopServer() {
            server.stop()
        }

        private fun request(method: String, path: String, body: String? = null, auth: String? = token): Pair<Int, String> {
            val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (auth != null) conn.setRequestProperty("Authorization", "Bearer $auth")
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

        private fun rawRequest(method: String, path: String, body: ByteArray?, auth: String? = token): Pair<Int, String> {
            val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            if (auth != null) conn.setRequestProperty("Authorization", "Bearer $auth")
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

    // ---- Auth ----

    @Test
    fun `register without token is allowed and returns session`() {
        val created = request(
            "POST", "/api/auth/register",
            """{"username":"fresh_user","password":"secret66","nickname":"新人"}""",
            auth = null
        )
        assertEquals(200, created.first)
        assertTrue(created.second.contains("\"token\":\""))
        assertTrue(created.second.contains("fresh_user"))
    }

    @Test
    fun `duplicate registration returns 409`() {
        val first = request("POST", "/api/auth/register", """{"username":"dup_user","password":"secret66"}""", auth = null)
        assertEquals(200, first.first)
        val second = request("POST", "/api/auth/register", """{"username":"dup_user","password":"other77"}""", auth = null)
        assertEquals(409, second.first)
    }

    @Test
    fun `login rejects wrong password and unknown user with same message`() {
        val wrongPassword = request("POST", "/api/auth/login", """{"username":"chef_test","password":"nope"}""", auth = null)
        assertEquals(401, wrongPassword.first)
        assertTrue(wrongPassword.second.contains("用户名或密码错误"))

        val unknown = request("POST", "/api/auth/login", """{"username":"ghost","password":"whatever123"}""", auth = null)
        assertEquals(401, unknown.first)
        assertEquals(wrongPassword.second, unknown.second)
    }

    @Test
    fun `short password and bad username are rejected`() {
        assertEquals(400, request("POST", "/api/auth/register", """{"username":"ab","password":"secret66"}""", auth = null).first)
        assertEquals(400, request("POST", "/api/auth/register", """{"username":"okname","password":"123"}""", auth = null).first)
    }

    @Test
    fun `me returns the logged in user for a valid token`() {
        val (code, body) = request("GET", "/api/auth/me")
        assertEquals(200, code)
        assertTrue(body.contains("chef_test"))
    }

    @Test
    fun `logout invalidates the token and subsequent calls get 401`() {
        // Use a dedicated account: a fresh login overwrites the user's stored token,
        // so reusing chef_test here would break other tests that rely on the fixture token.
        request("POST", "/api/auth/register", """{"username":"logout_user","password":"secret66"}""", auth = null)
        val login = request("POST", "/api/auth/login", """{"username":"logout_user","password":"secret66"}""", auth = null)
        val fresh = Regex("\"token\":\"([^\"]+)\"").find(login.second)!!.groupValues[1]

        assertEquals(200, request("POST", "/api/auth/logout", "{}", auth = fresh).first)
        assertEquals(401, request("GET", "/api/auth/me", auth = fresh).first)
    }

    @Test
    fun `business endpoints reject missing and invalid tokens`() {
        assertEquals(401, request("GET", "/api/recipes", auth = null).first)
        assertEquals(401, request("GET", "/api/recipes", auth = "not-a-real-token").first)
        assertEquals(401, request("GET", "/api/profile", auth = null).first)
        assertEquals(401, request("POST", "/api/menus", "{}", auth = null).first)
        // Image download stays public (Coil cannot attach headers).
        assertEquals(404, getRaw("/api/recipes/999999/image").first)
    }

    @Test
    fun `users only see their own menus`() {
        // Register a second user.
        val other = request(
            "POST", "/api/auth/register",
            """{"username":"other_user","password":"secret66"}""",
            auth = null
        )
        val otherToken = Regex("\"token\":\"([^\"]+)\"").find(other.second)!!.groupValues[1]

        // chef_test creates a menu record.
        val body = """{"items":[{"recipeId":1,"recipeName":"A","quantity":1}],"totalCost":88.5}"""
        assertEquals(200, request("POST", "/api/menus", body).first)

        // The other user's list must be empty.
        val otherList = request("GET", "/api/menus", auth = otherToken)
        assertEquals(200, otherList.first)
        assertEquals("[]", otherList.second)
    }

    // ---- Recipes ----

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
    fun `profile saves and reads back per user`() {
        val saved = request("PUT", "/api/profile", """{"nickname":"大厨","signature":"好好吃饭","location":"北京","favoriteCategory":"家常菜"}""")
        assertEquals(200, saved.first)
        assertTrue(saved.second.contains("大厨"))

        val readBack = request("GET", "/api/profile")
        assertEquals(200, readBack.first)
        assertTrue(readBack.second.contains("好好吃饭"))
    }

    @Test
    fun `unhandled route returns 404`() {
        assertEquals(404, request("GET", "/api/nope").first)
    }

    private fun gsonRecipe(name: String): String =
        """{"id":0,"name":"$name","imageUrl":"","category":"家常菜","foodMaterials":[{"name":"鱼","amount":"1条"}],"seasonings":[{"name":"盐","amount":"1勺"}],"steps":["蒸"]}"""
}
