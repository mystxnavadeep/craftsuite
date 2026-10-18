plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.storytold.craftsuite"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.8.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "appType"
    productFlavors {
        create("wordcraft") {
            dimension = "appType"
            applicationId = "com.storytold.craftsuite.wordcraft"
            manifestPlaceholders["appName"] = "WordCraft"
            buildConfigField("String", "FLAVOR_TARGET", "\"wordcraft\"")
        }
        create("gridcraft") {
            dimension = "appType"
            applicationId = "com.storytold.craftsuite.gridcraft"
            manifestPlaceholders["appName"] = "GridCraft"
            buildConfigField("String", "FLAVOR_TARGET", "\"gridcraft\"")
        }
        create("deckcraft") {
            dimension = "appType"
            applicationId = "com.storytold.craftsuite.deckcraft"
            manifestPlaceholders["appName"] = "DeckCraft"
            buildConfigField("String", "FLAVOR_TARGET", "\"deckcraft\"")
        }
        create("suite") {
            dimension = "appType"
            applicationId = "com.storytold.craftsuite"
            manifestPlaceholders["appName"] = "CraftSuite"
            buildConfigField("String", "FLAVOR_TARGET", "\"suite\"")
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (!keystoreFile.isNullOrBlank() && file(keystoreFile).exists()) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:fileaccess"))
    implementation(project(":core:safety"))
    implementation(project(":core:enginebridge"))
    implementation(project(":core:tools"))
    implementation(project(":core:ai"))
    implementation(project(":feature-wordcraft"))
    implementation(project(":feature-gridcraft"))
    implementation(project(":feature-deckcraft"))

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}
