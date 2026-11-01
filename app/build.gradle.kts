plugins {
    id("com.android.application")
}

// Sin bloque `dependencies` a propósito: ni AndroidX, ni Material, ni Compose, ni Kotlin.
// Todo lo que se carga en el proceso es el framework de Android (que ya está en memoria del sistema).
android {
    namespace = "app.minilauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.minilauncher"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Firmado con la clave debug para que se pueda instalar directo (uso personal).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
