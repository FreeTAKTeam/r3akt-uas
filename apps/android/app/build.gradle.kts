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
        // Test build only (D-4): the simulator plus RCH publication, behind -Pr3aktPublish=true.
        // Hub address and destination come from Gradle properties outside the repository.
        create("simNet") {
            dimension = "aircraft"
            val hub = { name: String -> "\"" + providers.gradleProperty(name).getOrElse("") + "\"" }
            buildConfigField("String", "HUB_ADDRESS", hub("r3aktHubAddress"))
            buildConfigField("String", "HUB_DESTINATION", hub("r3aktHubDestination"))
        }
        create("dji4") {
            dimension = "aircraft"
            manifestPlaceholders["djiApiKey"] = providers.gradleProperty("djiApiKey").getOrElse("")
        }
    }

    buildFeatures { buildConfig = true }
    sourceSets {
        getByName("sim") { java.srcDir("src/simCommon/kotlin") }
        getByName("simNet") { java.srcDir("src/simCommon/kotlin") }
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

// One build holds either the default variants (no publisher in the native library)
// or simNet (with it), never both: the native bridge is a single unflavored module.
val publish = providers.gradleProperty("r3aktPublish").map(String::toBoolean).getOrElse(false)
androidComponents {
    beforeVariants { variant ->
        variant.enable = (variant.productFlavors.single().second == "simNet") == publish
    }
}

dependencies {
    implementation(project(":native-bridge"))
    "dji4Implementation"(project(":dji4"))
}
