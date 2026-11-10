// Generated UniFFI Kotlin bindings plus the Rust cdylib for each ABI.
// Nothing here is hand-written FFI; regenerate by building this module.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val repoRoot = rootDir.resolve("../..")
val generated = layout.buildDirectory.dir("generated/rust").get().asFile

val cargoBuild by tasks.registering(Exec::class) {
    description = "Builds the Rust integration and generates Kotlin bindings."
    inputs.dir(repoRoot.resolve("crates"))
    inputs.dir(repoRoot.resolve("tools/uniffi-bindgen"))
    inputs.files(
        repoRoot.resolve("Cargo.toml"), repoRoot.resolve("Cargo.lock"),
        repoRoot.resolve("rust-toolchain.toml"), repoRoot.resolve("scripts/build-android-native.sh"),
    )
    outputs.dir(generated)
    commandLine(repoRoot.resolve("scripts/build-android-native.sh").path, generated.path)
}

android {
    namespace = "org.r3akt.uas.bridge"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    sourceSets["main"].java.srcDir(generated.resolve("kotlin"))
    sourceSets["main"].jniLibs.srcDir(generated.resolve("jniLibs"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

tasks.named("preBuild") { dependsOn(cargoBuild) }

dependencies {
    api(libs.jna) { artifact { type = "aar" } }
}
