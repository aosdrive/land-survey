plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-parcelize")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    id("androidx.navigation.safeargs.kotlin")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {

    compileSdk = AppConfig.compileSdkVersion

    defaultConfig {
        applicationId = AppConfig.applicationId
        minSdk = AppConfig.minSdkVersion
        targetSdk = AppConfig.targetSdkVersion
        versionCode = AppConfig.versionCode
        versionName = AppConfig.versionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    kapt {
        useBuildCache = false
        correctErrorTypes = true
    }

    buildTypes {
        debug {

            ndk {
                abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
            }

            isMinifyEnabled = false
            isDebuggable = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        release {
            ndk {
                abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
            }
            isMinifyEnabled = true
            isDebuggable = false
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += mutableSetOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/licenses/ASM",
                "META-INF/DEPENDENCIES",
                "META-INF/DEPENDENCIES.txt",
                "META-INF/LICENSE",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "META-INF/LGPL2.1",
                "META-INF/proguard/androidx-annotations.pro",
            )
            // Fixes conflicts caused by ByteBuddy library used in
            // coroutines-debug and mockito
            pickFirsts += mutableSetOf(
                "win32-x86-64/attach_hotspot_windows.dll",
                "win32-x86/attach_hotspot_windows.dll",
                "META-INF/LICENSE.txt"
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("${projectDir}/src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }


    namespace = AppConfig.applicationId

}

dependencies {

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation(Dependencies.coreKtx)
    implementation(Dependencies.appCompat)
    implementation(Dependencies.material)
    implementation(Dependencies.lifecycleRuntimeKtx)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Navigation
    implementation(Dependencies.lifecycleNavigationFragmentKtx)
    implementation(Dependencies.lifecycleNavigationUiKtx)

    // Retrofit
    implementation(Dependencies.retrofit)
    implementation(Dependencies.retrofitConverterGson)

    // Okhttp
    implementation(Dependencies.Okhttp)
    implementation(Dependencies.OkhttpLoggingIntercepter)

    // Hilt
    implementation(Dependencies.hilt)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.hilt:hilt-common:1.2.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    kapt(Dependencies.hiltCompiler)

    // Room database
    implementation(Dependencies.roomRuntime)
    implementation(Dependencies.roomKtx)
    kapt(Dependencies.roomCompiler)
    //implementation(Dependencies.roomCoroutines)

    // Splash Screen
    implementation(Dependencies.splashScreenCore)

    // External Libraries
    implementation(Dependencies.loadingButton)
    implementation("com.github.telichada:SearchableMultiSelectSpinner:2.0")
    implementation("com.karumi:dexter:6.2.3")

    // ESRI MAPS
    implementation("com.esri.arcgisruntime:arcgis-android:100.15.5")
//    implementation ("com.esri:arcgis-maps-kotlin:200.2.0")

    implementation("com.jakewharton.threetenabp:threetenabp:1.3.1")

    implementation("com.google.android.gms:play-services-location:21.3.0")


    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))

    // Add the dependencies for the Crashlytics and Analytics libraries
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

}
