plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "iovi.lobby1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "iovi.lobby1"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    // appcompat сам тянет старую fragment 1.5.4 — из-за разнобоя с новыми activity/core IDE видит
    // «Ambiguous method call» на super.onCreate. Явная свежая версия согласует их.
    implementation(libs.fragment)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}