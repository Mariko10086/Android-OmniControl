import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Read local.properties for secrets (never committed to VCS)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
fun prop(key: String): String = localProps.getProperty(key, "")

android {
    namespace = "com.omnicontrol.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.omnicontrol.agent"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SERVER_BASE_URL",  "\"https://api.omnicontrol.internal/api/v1/\"")
        buildConfigField("String", "MQTT_BROKER_HOST", "\"api.omnicontrol.internal\"")
        buildConfigField("String", "MQTT_BROKER_PORT", "\"1883\"")
        buildConfigField("String", "DEVICE_API_USERNAME", "\"${prop("DEVICE_API_USERNAME")}\"")
        buildConfigField("String", "DEVICE_API_PASSWORD", "\"${prop("DEVICE_API_PASSWORD")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            buildConfigField("String", "SERVER_BASE_URL",  "\"http://192.168.133.94:8080/api/v1/\"")
            buildConfigField("String", "MQTT_BROKER_HOST", "\"192.168.133.94\"")
            buildConfigField("String", "MQTT_BROKER_PORT", "\"1883\"")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)

    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.ktx)

    implementation(libs.workmanager.ktx)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    implementation(libs.coroutines.android)

    implementation(libs.hivemq.mqtt.client)
    implementation(libs.androidx.security.crypto)
}
