package com.recipe.server

/**
 * Runtime configuration, read from environment variables with sensible defaults.
 * The defaults point at the local MySQL and bind the API on 0.0.0.0:8080.
 */
data class ServerConfig(
    val host: String = env("RECIPE_SERVER_HOST", "0.0.0.0"),
    val port: Int = env("RECIPE_SERVER_PORT", "8080").toInt(),
    val dbHost: String = env("RECIPE_DB_HOST", "127.0.0.1"),
    val dbPort: Int = env("RECIPE_DB_PORT", "3306").toInt(),
    val dbName: String = env("RECIPE_DB_NAME", "recipe_app"),
    val dbUser: String = env("RECIPE_DB_USER", "root"),
    val dbPassword: String = env("RECIPE_DB_PASSWORD", "123456"),
    val uploadDir: String = env("RECIPE_UPLOAD_DIR", defaultUploadDir()),
    val useInMemory: Boolean = env("RECIPE_IN_MEMORY", "false").equals("true", ignoreCase = true)
) {
    companion object {
        private fun env(name: String, default: String): String =
            System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

        private fun defaultUploadDir(): String =
            System.getProperty("user.dir") + "/data/uploads"
    }
}
