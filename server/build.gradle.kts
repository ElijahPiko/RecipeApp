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
