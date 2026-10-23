plugins { id("com.android.application") }
android {
    namespace = "com.monkeyiptv.player"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.monkeyiptv.player"
        minSdk = 23
        targetSdk = 35
        versionCode = 3000057
        versionName = "1.0.0-beta.54"
        buildConfigField("String", "SERVER_URL", "\"http://tvplug.xyz:8880\"")
    }
    signingConfigs {
        create("dreamRelease") {
            val keyFile = System.getenv("DREAM_KEYSTORE_FILE")
            if (!keyFile.isNullOrBlank()) {
                storeFile = file(keyFile)
                storePassword = System.getenv("APK_STORE_PASSWORD")
                keyAlias = System.getenv("APK_KEY_ALIAS")
                keyPassword = System.getenv("APK_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("dreamRelease")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("org.videolan.android:libvlc-all:3.6.0")
}
