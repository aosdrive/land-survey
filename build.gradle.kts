// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    val agp_version by extra("8.4.2")
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
    dependencies {
        classpath(BuildPlugins.android)
        classpath(BuildPlugins.kotlin)
        classpath(BuildPlugins.daggerHilt)
        classpath(BuildPlugins.navigationSafeArgs)
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
        classpath ("com.google.firebase:firebase-crashlytics-gradle:3.0.2")
        classpath ("com.google.gms:google-services:4.4.2")
        classpath("com.android.tools.build:gradle:$agp_version")
//        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }

}

task("clean", Delete::class) {
    delete(rootProject.buildDir)
}