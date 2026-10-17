import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Loads an environment-specific client config from `config/`.
 *
 * Resolution order per key: `config/client.<env>.properties`, then
 * `local.properties` (machine-private overrides, never committed).
 *
 * This keeps IP addresses and endpoints out of the source code, so switching
 * between local and production is a build-type decision rather than a code edit.
 */
fun loadClientConfig(env: String): Properties {
    val props = Properties()
    listOfNotNull(
        rootProject.file("config/client.$env.properties").takeIf { it.exists() },
        rootProject.file("local.properties").takeIf { it.exists() }
    ).forEach { file ->
        file.inputStream().use { props.load(it) }
        println("[config] loaded ${file.name} for env '$env'")
    }
    return props
}

/**
 * Resolves the client config for [env] into BuildConfig field triples
 * (type, name, value). Kept as plain data so it can be applied inside
 * `defaultConfig`, where `buildConfigField` is reliably in scope.
 */
fun clientFields(env: String, defaultLogging: Boolean): List<Triple<String, String, String>> {
    val c = loadClientConfig(env)
    val baseUrl = c.getProperty("api.baseUrl") ?: "http://10.0.2.2:8080"
    val logging = (c.getProperty("debug.logging") ?: defaultLogging.toString()).toBoolean()
    return listOf(
        Triple("String", "API_BASE_URL", "\"$baseUrl\""),
        Triple("boolean", "DEBUG_LOGGING", logging.toString()),
        Triple("String", "ENV_NAME", "\"$env\"")
    )
}

// Release defaults to the prod config; override with -PrecipeEnv=local to
// produce a release build that still talks to a local backend.
val releaseEnv = providers.gradleProperty("recipeEnv").orNull ?: "prod"

// Both sets are computed eagerly so a missing file is reported during
// configuration rather than halfway through a build.
val debugFields = clientFields("local", defaultLogging = true)
val releaseFields = clientFields(releaseEnv, defaultLogging = false)

android {
    namespace = "com.recipe.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.recipe.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Inject per-build-type config after buildTypes is declared: the debug /
    // prod split is the whole point, and doing it here keeps the values in one
    // place instead of duplicating them per buildType.
    flavorDimensions += "env"
    productFlavors {
        create("local") {
            dimension = "env"
            debugFields.forEach { (type, name, value) -> buildConfigField(type, name, value) }
        }
        create("prod") {
            dimension = "env"
            releaseFields.forEach { (type, name, value) -> buildConfigField(type, name, value) }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.hamcrest.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.coroutines.core)

    debugImplementation(libs.compose.ui.tooling)
}
