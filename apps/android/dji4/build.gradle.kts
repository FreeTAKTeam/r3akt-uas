// DJI Mobile SDK 4 adapter. Adapted FreeTAKUAS components land here only after
// their immutable revision and licence are recorded in docs/integration/reuse-matrix.md.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.r3akt.uas.dji4"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(libs.dji.sdk)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    compileOnly(libs.dji.sdk.provided)
}
