plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.r3akt.uas"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "org.r3akt.uas"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-scaffold"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    flavorDimensions += "aircraft"
    productFlavors {
        // Simulator: no DJI SDK, no registration credentials, no hardware (UAS-SCF-002).
        create("sim") { dimension = "aircraft" }
        create("dji4") {
            dimension = "aircraft"
            manifestPlaceholders["djiApiKey"] = providers.gradleProperty("djiApiKey").getOrElse("")
        }
    }

    packaging {
        // DJI SDK 4 native-library rules adapted from FreeTAKUAS 704f4aa (MIT); see THIRD_PARTY_NOTICES.md.
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += listOf("*/*/libdjivideo.so", "*/*/libSDKRelativeJNI.so", "*/*/libFlyForbid.so",
                "*/*/libduml_vision_bokeh.so", "*/*/libyuv2.so", "*/*/libGroudStation.so", "*/*/libFRCorkscrew.so",
                "*/*/libUpgradeVerify.so", "*/*/libFR.so", "*/*/libDJIFlySafeCore.so", "*/*/libdjifs_jni.so",
                "*/*/libsfjni.so", "*/*/libDJICommonJNI.so", "*/*/libDJICSDKCommon.so", "*/*/libDJIUpgradeCore.so",
                "*/*/libDJIUpgradeJNI.so", "*/*/libDJIWaypointV2Core.so", "*/*/libAMapSDK_MAP_v6_9_2.so",
                "*/*/libDJIMOP.so", "*/*/libDJISDKLOGJNI.so")
            pickFirsts += listOf("lib/*/libstlport_shared.so", "lib/*/libRoadLineRebuildAPI.so",
                "lib/*/libGNaviUtils.so", "lib/*/libGNaviMapex.so", "lib/*/libGNaviData.so",
                "lib/*/libGNaviMap.so", "lib/*/libGNaviSearch.so", "lib/*/libDJIFlySafeCore.so",
                "lib/*/libdjifs_jni.so", "lib/*/libsfjni.so")
        }
        resources { excludes += listOf("META-INF/proguard/okhttp3.pro", "META-INF/rxjava.properties") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":native-bridge"))
    "dji4Implementation"(project(":dji4"))
}
