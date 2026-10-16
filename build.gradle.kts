// Plugin resolutions are declared within each module to keep offline classpath resolution small.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// The only coroutines version cached here (1.10.2) requires kotlin-stdlib:2.1.0, but that exact
// version is not available offline. kotlin-stdlib:2.1.10 (cached) is binary compatible, so we force
// every configuration to resolve to it consistently.
subprojects {
    configurations.configureEach {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:2.1.10")
        }
    }
}
