plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.recipe.server.MainKt")
}

/**
 * Forward `-Drecipe.env=...` from the Gradle invocation into the application JVM.
 * Gradle does not pass `-D` through to the forked process by default, so
 * `./gradlew :server:run -Drecipe.env=prod` would otherwise silently be ignored.
 * The RECIPE_ENV environment variable works without this.
 *
 * 本地 `:server:run` 默认锁定 local：否则只要 `config/server.prod.properties` 存在，
 * 服务端就会自动切到生产配置（那是给服务器用的、账号密码与本机不同），连不上 MySQL 后
 * 静默降级成内存存储，重启就丢数据。部署走 jar + systemd，不受这里影响。
 */
tasks.withType<JavaExec>().configureEach {
    val explicitEnv = System.getProperty("recipe.env") ?: System.getenv("RECIPE_ENV")
    systemProperty("recipe.env", explicitEnv?.takeIf { it.isNotBlank() } ?: "local")
    // Keep the server's own environment variables (RECIPE_*) available.
    environment(System.getenv())
}

/**
 * 单测里的 MySQL 集成测试也要按 local 配置连库：否则只要 `config/server.prod.properties` 存在，
 * 测试进程就会按 prod 连（账号密码不同）→ 连不上 → Assume 静默跳过，等于白跑。
 */
tasks.withType<Test>().configureEach {
    val explicitEnv = System.getProperty("recipe.env") ?: System.getenv("RECIPE_ENV")
    systemProperty("recipe.env", explicitEnv?.takeIf { it.isNotBlank() } ?: "local")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.gson)
    implementation(libs.mysql.connector) {
        exclude(group = "com.oracle.oci.sdk")
        exclude(group = "com.google.protobuf")
    }
    // Connection pool: borrow a connection per call instead of sharing one across all threads.
    implementation(libs.hikaricp)
    implementation(libs.slf4j.api)
    // HikariCP logs through SLF4J; this binding prints pool lifecycle to stderr.
    runtimeOnly(libs.slf4j.simple)
    testImplementation(libs.junit)
    testImplementation(libs.hamcrest.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.coroutines.core)
}
