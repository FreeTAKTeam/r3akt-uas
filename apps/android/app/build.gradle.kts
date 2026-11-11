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
    // Hub settings for the publishing test builds come from Gradle properties outside the repository.
    val hub = { name: String -> "\"" + providers.gradleProperty(name).getOrElse("") + "\"" }
    val djiApiKey = providers.gradleProperty("djiApiKey").getOrElse("")
    productFlavors {
        // Simulator: no DJI SDK, no registration credentials, no hardware (UAS-SCF-002).
        create("sim") { dimension = "aircraft" }
        // Test build only (D-4): the simulator plus RCH publication, behind -Pr3aktPublish=true.
        // Hub address and destination come from Gradle properties outside the repository.
        create("simNet") {
            dimension = "aircraft"
            buildConfigField("String", "HUB_ADDRESS", hub("r3aktHubAddress"))
            buildConfigField("String", "HUB_DESTINATION", hub("r3aktHubDestination"))
            // Optional RCH team UID (field 11): with it the hub relays the aircraft to that team's REM clients.
            buildConfigField("String", "TEAM_UID", hub("r3aktTeamUid"))
        }
        create("dji4") {
            dimension = "aircraft"
            manifestPlaceholders["djiApiKey"] = djiApiKey
        }
        // Test build only (D-18): the DJI aircraft plus RCH publication, behind -Pr3aktPublish=true.
        create("dji4Net") {
            dimension = "aircraft"
            manifestPlaceholders["djiApiKey"] = djiApiKey
            buildConfigField("String", "HUB_ADDRESS", hub("r3aktHubAddress"))
            buildConfigField("String", "HUB_DESTINATION", hub("r3aktHubDestination"))
            buildConfigField("String", "TEAM_UID", hub("r3aktTeamUid"))
        }
    }

    buildFeatures { buildConfig = true }
    sourceSets {
        getByName("sim") { java.srcDir("src/simCommon/kotlin") }
        getByName("simNet") {
            java.srcDir("src/simCommon/kotlin")
            java.srcDir("src/netCommon/kotlin")
        }
        getByName("dji4") { java.srcDir("src/dji4Common/kotlin") }
        getByName("dji4Net") {
            java.srcDir("src/dji4Common/kotlin")
            java.srcDir("src/netCommon/kotlin")
            // The same DJI manifest entries (key meta-data, USB accessory activity, INTERNET).
            manifest.srcFile("src/dji4/AndroidManifest.xml")
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

// One build holds either the default variants (no publisher in the native library)
// or the publishing test variants (with it), never both: the native bridge is a single unflavored module.
val publish = providers.gradleProperty("r3aktPublish").map(String::toBoolean).getOrElse(false)
androidComponents {
    beforeVariants { variant ->
        variant.enable = (variant.productFlavors.single().second in setOf("simNet", "dji4Net")) == publish
    }
}

dependencies {
    implementation(project(":native-bridge"))
    "dji4Implementation"(project(":dji4"))
    "dji4NetImplementation"(project(":dji4"))
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
