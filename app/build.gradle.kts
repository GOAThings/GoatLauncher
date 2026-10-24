import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Chave de assinatura do release: keystore.properties na raiz (fora do git). Sem ele, o release sai sem assinatura.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "cc.slokker.goatlauncher"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cc.slokker.goatlauncher"
        // As bibliotecas AndroidX atuais (Compose, tv-material) exigem minSdk 23.
        minSdk = 23
        targetSdk = 34
        versionCode = 26
        versionName = "0.8.5"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    // Sem sufixo no debug: é o ecrã inicial, e o `set-home-activity` aponta sempre para o mesmo pacote.
    buildTypes {
        release {
            // Sem keystore.properties assina com a chave de debug: instala-se por cima do debug sem perder as definições.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // Otimização da app inteira (ver a GoatTV: `packageScope` partiu classes no Android 11 da SHIELD).
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.appcompat)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.tvprovider)

    testImplementation(libs.junit)
}
