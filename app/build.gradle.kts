plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gabriel.tvlimpia"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gabriel.tvlimpia"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }

    // Clave fija: así cada versión nueva se instala encima de la anterior
    signingConfigs {
        getByName("debug") {
            storeFile = file("tvlimpia.keystore")
            storePassword = "tvlimpia"
            keyAlias = "tvlimpia"
            keyPassword = "tvlimpia"
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/versions/9/previous-compilation-data.bin"
            )
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Cliente ADB en Kotlin: habla con la TV por la Wi-Fi, sin PC ni root
    implementation("dev.mobile:dadb:1.2.10")
}
