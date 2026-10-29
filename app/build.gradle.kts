plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 서명 정보는 저장소에 넣지 않고 GitHub Secrets → 환경 변수로만 받습니다.
val releaseStore = System.getenv("RUNMATE_KEYSTORE")
val hasReleaseKey = !releaseStore.isNullOrBlank() && file(releaseStore).exists()

android {
    namespace = "io.github.sby1234ai.runmate"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.sby1234ai.runmate"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "0.3.16"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // 지도: OpenStreetMap (설정에서 끄면 만들지 않음)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")   // 단위 테스트에서 진짜 JSON 읽기
}
