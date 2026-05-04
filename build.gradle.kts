buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.3.10")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.6")
    }
}

plugins {
    id("com.android.application") version "9.0.1" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
