plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "bd.shanto.browser"
    compileSdk = 35
    defaultConfig {
        applicationId = "bd.shanto.browser.preview05"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "1.9.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // GitHub Actions build: the password comes from the repository secret SHANTO_KEY_PASSWORD.
    // Without that variable (e.g. in Android Studio) nothing below changes and the usual debug build still works.
    val keyPass: String? = System.getenv("SHANTO_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
    signingConfigs {
        create("shanto") {
            if (keyPass != null) {
                storeFile = rootProject.file("keystore/shanto-release.jks")
                storePassword = keyPass
                keyAlias = "shanto"
                keyPassword = keyPass
            }
        }
    }
    buildTypes {
        release {
            if (keyPass != null) signingConfig = signingConfigs.getByName("shanto")
        }
    }
    lint { checkReleaseBuilds = false }   // a lint hiccup must not stop the APK from being built
}

dependencies { implementation("com.google.zxing:core:3.5.3") }
