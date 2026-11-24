plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.maslak.gps"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "brand"
    productFlavors {
        create("maslak") {
            dimension = "brand"
            applicationId = "com.maslak.gps"
            resValue("string", "app_name", "مسلك GPS")
        }
        create("marwani") {
            dimension = "brand"
            applicationId = "com.marwani.gps"
            resValue("string", "app_name", "Marwani")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // موقّع بمفتاح debug حتى يمكن تثبيته مباشرة. استبدله بمفتاحك قبل النشر.
            signingConfig = signingConfigs.getByName("debug")
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
        resValues = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // الخريطة: OpenStreetMap بدون مفتاح API
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // Fused Location (لضمان وصول الموقع الوهمي للتطبيقات التي تستخدم خدمات Google)
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
