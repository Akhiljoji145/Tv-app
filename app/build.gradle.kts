plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.akhil.tvdocumentviewer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.akhil.tvdocumentviewer"
        minSdk = 21
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }
}
