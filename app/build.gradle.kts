plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nusafit.app"
    compileSdk = 36

    defaultConfig {
        // Injected at build time from GitHub Secret; never commit a real API key.
        resValue("string", "maps_key", providers.gradleProperty("GOOGLE_MAPS_API_KEY").orElse("").get())
        applicationId = "com.nusafit.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
    }

    // Keep Java and Kotlin JVM targets aligned for Release builds.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Release signing values are supplied by GitHub Actions via -P properties.
    signingConfigs {
        create("release") {
            val keystorePath = project.findProperty("NUSAFIT_KEYSTORE") as String?
            val storePassword = project.findProperty("NUSAFIT_STORE_PASSWORD") as String?
            val keyAlias = project.findProperty("NUSAFIT_KEY_ALIAS") as String?
            val keyPassword = project.findProperty("NUSAFIT_KEY_PASSWORD") as String?

            if (!keystorePath.isNullOrBlank() && !storePassword.isNullOrBlank() &&
                !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
                storeFile = file(keystorePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
}
