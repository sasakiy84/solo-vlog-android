plugins {
    id("com.android.application")
}

val releaseVersion = providers.gradleProperty("releaseVersion").orElse("1.0.10").get()
require(Regex("(0|[1-9][0-9]{0,3})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})").matches(releaseVersion)) {
    "releaseVersion must be MAJOR.MINOR.PATCH without leading zeros"
}
val (major, minor, patch) = releaseVersion.split('.').map(String::toInt)
require(major <= 1999 && major + minor + patch > 0) { "Version must be between 0.0.1 and 1999.999.999" }
val releaseCode = major * 1_000_000 + minor * 1_000 + patch
val releaseStore = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releasePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull

android {
    namespace = "com.sasakiy84.solovlog"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sasakiy84.solovlog"
        minSdk = 36
        targetSdk = 36
        versionCode = releaseCode
        versionName = releaseVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (releaseStore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = releasePassword
                keyAlias = "solo-vlog-release"
                keyPassword = releasePassword
            }
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.camera:camera-camera2:1.5.0")
    implementation("androidx.camera:camera-lifecycle:1.5.0")
    implementation("androidx.camera:camera-video:1.5.0")
    implementation("androidx.camera:camera-view:1.5.0")
    implementation("androidx.media3:media3-transformer:1.8.0")
    implementation("androidx.media3:media3-effect:1.8.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-container:1.8.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("com.google.truth:truth:1.4.4")
}

// A distributable release must never silently fall back to unsigned/debug signing.
val verifyReleaseSigning by tasks.registering {
    doLast {
        require(!releaseStore.isNullOrBlank() && !releasePassword.isNullOrBlank()) {
            "Set ANDROID_KEYSTORE_PATH and ANDROID_KEYSTORE_PASSWORD to build a signed release"
        }
        require(file(releaseStore).isFile) { "Release keystore does not exist" }
    }
}
tasks.matching { it.name in setOf("packageRelease", "validateSigningRelease", "bundleRelease") }.configureEach {
    dependsOn(verifyReleaseSigning)
}
