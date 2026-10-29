plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 휴대폰 앱과 같은 서명 키·같은 앱 아이디여야 서로 알아보고 메시지를 주고받습니다.
val releaseStore = System.getenv("RUNMATE_KEYSTORE")
val hasReleaseKey = !releaseStore.isNullOrBlank() && file(releaseStore).exists()
// 버전은 휴대폰 앱을 따라감(versionCode는 Play에 함께 올릴 때 겹치지 않게 100만을 더함)
val phone = rootProject.file("app/build.gradle.kts").readText()
val phoneCode = Regex("versionCode = (\\d+)").find(phone)!!.groupValues[1].toInt()
val phoneName = Regex("versionName = \"([^\"]+)\"").find(phone)!!.groupValues[1]

android {
    namespace = "io.github.sby1234ai.runmate.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.sby1234ai.runmate"
        minSdk = 30          // 웨어 OS 3 이상
        targetSdk = 34
        versionCode = 1_000_000 + phoneCode
        versionName = phoneName
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStore!!)
                storePassword = System.getenv("RUNMATE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RUNMATE_KEY_ALIAS")
                keyPassword = System.getenv("RUNMATE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
