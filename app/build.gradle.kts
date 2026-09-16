plugins {
    id("com.android.application")
}

android {
    namespace = "mobi.vxd.aequilibrium"
    compileSdk = 36

    defaultConfig {
        applicationId = "mobi.vxd.aequilibrium"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/aequilibrium/AndroidManifest.xml")
            java.setSrcDirs(listOf("src/aequilibrium/java"))
            res.setSrcDirs(listOf("src/aequilibrium/res"))
            assets.setSrcDirs(listOf("src/aequilibrium/assets"))
        }
        getByName("test") { java.setSrcDirs(emptyList<String>()) }
        getByName("androidTest") { java.setSrcDirs(emptyList<String>()) }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
