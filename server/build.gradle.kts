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
 */
tasks.withType<JavaExec>().configureEach {
    System.getProperty("recipe.env")?.let { systemProperty("recipe.env", it) }
    // Keep the server's own environment variables (RECIPE_*) available.
    environment(System.getenv())
}

dependencies {
    implementation(project(":core"))
    implementation(libs.gson)
    implementation(libs.mysql.connector) {
        exclude(group = "com.oracle.oci.sdk")
        exclude(group = "com.google.protobuf")
    }
    testImplementation(libs.junit)
    testImplementation(libs.hamcrest.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.coroutines.core)
}
