package com.recipe.server

import java.util.Properties

/**
 * Runtime configuration, resolved from three layers (later layers win):
 *
 *  1. Built-in defaults
 *  2. A properties file from `config/` (see `RecipeConfigFile`)
 *  3. Environment variables
 *
 * Environment variables keep the highest priority so that production secrets
 * (e.g. `RECIPE_DB_PASSWORD`) never have to be written to disk.
 *
 * Defaults alone still describe the local setup, so `ServerConfig()` with no
 * arguments keeps working exactly as before.
 */
data class ServerConfig(
    val host: String = resolve("server.host", "RECIPE_SERVER_HOST", "0.0.0.0"),
    val port: Int = resolve("server.port", "RECIPE_SERVER_PORT", "8080").toInt(),
    /**
     * HTTP worker threads. Keep it in step with `db.pool.maxSize`: every request holds at
     * most one pooled connection, so a much larger thread count only queues on checkout.
     */
    val serverThreads: Int = resolve("server.threads", "RECIPE_SERVER_THREADS", "8").toInt(),
    val dbHost: String = resolve("db.host", "RECIPE_DB_HOST", "127.0.0.1"),
    val dbPort: Int = resolve("db.port", "RECIPE_DB_PORT", "3306").toInt(),
    val dbName: String = resolve("db.name", "RECIPE_DB_NAME", "recipe_app"),
    val dbUser: String = resolve("db.user", "RECIPE_DB_USER", "root"),
    val dbPassword: String = resolve("db.password", "RECIPE_DB_PASSWORD", "123456"),
    val uploadDir: String = resolve("upload.dir", "RECIPE_UPLOAD_DIR", defaultUploadDir()),
    val useInMemory: Boolean = resolve("db.inMemory", "RECIPE_IN_MEMORY", "false").toBoolean(),

    // ---- Connection pool (HikariCP) ----
    /** Upper bound on simultaneously borrowed connections; keep it close to the HTTP worker count. */
    val dbPoolMaxSize: Int = resolve("db.pool.maxSize", "RECIPE_DB_POOL_MAX_SIZE", "10").toInt(),
    /** Connections kept warm when idle. */
    val dbPoolMinIdle: Int = resolve("db.pool.minIdle", "RECIPE_DB_POOL_MIN_IDLE", "2").toInt(),
    /** How long a request waits for a free connection before failing. */
    val dbPoolConnectionTimeoutMs: Long =
        resolve("db.pool.connectionTimeoutMs", "RECIPE_DB_POOL_CONNECTION_TIMEOUT_MS", "5000").toLong(),
    /**
     * Hard cap on a connection's age. MUST stay below MySQL's `wait_timeout`
     * (default 28800s / 8h), otherwise the server kills connections the pool
     * still believes are healthy — exactly the failure this pool exists to stop.
     */
    val dbPoolMaxLifetimeMs: Long =
        resolve("db.pool.maxLifetimeMs", "RECIPE_DB_POOL_MAX_LIFETIME_MS", "1500000").toLong(),
    /** Idle connections beyond [dbPoolMinIdle] are closed after this long. */
    val dbPoolIdleTimeoutMs: Long =
        resolve("db.pool.idleTimeoutMs", "RECIPE_DB_POOL_IDLE_TIMEOUT_MS", "600000").toLong(),
    /**
     * Background ping interval that keeps idle connections from being reaped by
     * MySQL / NAT `wait_timeout`. Set to 0 to disable.
     */
    val dbPoolKeepaliveMs: Long =
        resolve("db.pool.keepaliveMs", "RECIPE_DB_POOL_KEEPALIVE_MS", "120000").toLong()
) {
    /** Which configuration file was actually used, for startup logging. */
    val sourceDescription: String get() = RecipeConfigFile.describe()

    companion object {
        private fun env(name: String, default: String): String =
            System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

        /**
         * Looks up [key] in this order: environment variable, then the loaded
         * properties file, then [default].
         */
        private fun resolve(key: String, envName: String, default: String): String {
            System.getenv(envName)?.takeIf { it.isNotBlank() }?.let { return it }
            RecipeConfigFile.value(key)?.takeIf { it.isNotBlank() }?.let { return it }
            return default
        }

        private fun defaultUploadDir(): String =
            System.getProperty("user.dir") + "/data/uploads"
    }
}

/**
 * Loads the environment-specific properties file from `config/`.
 *
 * The active environment is chosen by, in order:
 *  1. `-Drecipe.env=prod` (JVM arg), or the `RECIPE_ENV` environment variable
 *  2. the presence of `config/server.prod.properties`
 *  3. `config/server.local.properties`
 *
 * Missing files are not an error: the built-in defaults then apply.
 */
object RecipeConfigFile {

    private val loaded: Properties by lazy { load() }

    fun value(key: String): String? = loaded.getProperty(key)?.trim()

    /** Active environment name, e.g. `local` or `prod`. */
    val envName: String by lazy {
        val explicit = System.getProperty("recipe.env")?.takeIf { it.isNotBlank() }
            ?: System.getenv("RECIPE_ENV")?.takeIf { it.isNotBlank() }
        when {
            explicit != null -> explicit
            rootFile("server.prod.properties").exists() -> "prod"
            else -> "local"
        }
    }

    fun describe(): String = loaded.getProperty("__source") ?: "defaults"

    private fun load(): Properties {
        val file = rootFile("server.$envName.properties")
        val props = Properties()
        if (!file.exists()) {
            System.err.println(
                "[config] WARNING: ${file.path} not found for env '$envName'. " +
                    "Using built-in defaults (which match the local setup). " +
                    "Copy config/server.local.properties or " +
                    "config/server.prod.example.properties into the config/ directory."
            )
            return props
        }
        file.inputStream().use { props.load(it) }
        props.setProperty("__source", file.path)
        return props
    }

    /**
     * Locates `config/<name>` by walking up from the working directory until a
     * directory containing `config/` is found. This keeps both
     * `./gradlew :server:run` (cwd = repo root) and
     * `./gradlew -p server run` (cwd = server/) working, because the lookup is
     * anchored on the `config/` folder rather than on a guessed project root.
     */
    private fun rootFile(name: String): java.io.File {
        val start = System.getProperty("user.dir").let { java.io.File(it).absoluteFile }
        var dir: java.io.File? = start
        while (dir != null) {
            val candidate = java.io.File(java.io.File(dir, CONFIG_DIR), name)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        // Not found anywhere: report where the user most likely expects it.
        return java.io.File(java.io.File(start, CONFIG_DIR), name)
    }

    private const val CONFIG_DIR = "config"
}
