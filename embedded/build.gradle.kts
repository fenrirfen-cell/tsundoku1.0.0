// Library build of the Tsundoku app, used to embed Tsundoku inside the Nuvio app ("Read" mode).
//
// It compiles the exact same sources and resources as :app (no copies), but as an Android
// library so another app can depend on it. Standalone Tsundoku builds keep using :app and are
// not affected by this module.
//
// Differences from :app:
//  - its own AndroidManifest.xml (no launcher entry, no <application> attributes, own FileProvider)
//  - BuildConfig fields that an application module would generate are declared by hand, and
//    APPLICATION_ID points at the *host* app's id (overridable with Gradle properties)
//  - no signing, splits, baseline profile, shortcuts placeholder task, telemetry or spotless

plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)

    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.kotlin.serialization)
}

// Keep in sync with :app.
val tsundokuVersionCode = 24
val tsundokuVersionName = "0.3.4"

val hostApplicationIdRelease = providers.gradleProperty("tsundoku.host.applicationId")
    .getOrElse("com.nuvio.app")
val hostApplicationIdDebug = providers.gradleProperty("tsundoku.host.applicationId.debug")
    .getOrElse("com.nuviodebug.com")

android {
    // Same namespace as :app so generated R and BuildConfig match the imports in the shared sources.
    namespace = "eu.kanade.tachiyomi"

    defaultConfig {
        buildConfigField("int", "VERSION_CODE", "$tsundokuVersionCode")
        buildConfigField("String", "VERSION_NAME", "\"$tsundokuVersionName\"")
        buildConfigField("String", "COMMIT_COUNT", "\"0\"")
        buildConfigField("String", "COMMIT_SHA", "\"embedded\"")
        buildConfigField("String", "BUILD_TIME", "\"\"")
        buildConfigField("boolean", "TELEMETRY_INCLUDED", "false")
        buildConfigField("boolean", "UPDATER_ENABLED", "false")

        consumerProguardFiles("../app/proguard-rules.pro")
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "APPLICATION_ID", "\"$hostApplicationIdDebug\"")
        }
        getByName("release") {
            buildConfigField("String", "APPLICATION_ID", "\"$hostApplicationIdRelease\"")
        }
    }

    sourceSets {
        getByName("main") {
            // Compile :app's sources, resources, assets and AIDL directly.
            setRoot("../app/src/main")
            // ...but use this module's manifest, plus a few host-only classes.
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.directories.add("src/host/java")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=coil3.annotation.ExperimentalCoilApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

// Same dependency list as :app (minus test, baseline-profile and telemetry plugins).
// `api` for the modules the host needs to see (it subclasses App).
dependencies {
    api(projects.i18n)
    api(projects.i18nNovel)
    api(projects.core.archive)
    api(projects.core.common)
    api(projects.core.viewmodel)
    api(projects.coreMetadata)
    api(projects.sourceApi)
    api(projects.sourceLocal)
    api(projects.data)
    api(projects.domain)
    api(projects.presentationCore)
    api(projects.presentationWidget)
    api(projects.telemetry)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.materialIcons)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animationGraphics)
    implementation(libs.androidx.compose.uiUtil)

    implementation(libs.androidx.interpolator)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.sqlite.bundled)

    implementation(libs.kotlin.reflect)

    implementation(libs.bundles.kotlinx.coroutines)

    implementation(libs.sqldelight.async)

    implementation(libs.kotlinx.datetime)

    // AndroidX libraries
    implementation(libs.androidx.annotation)
    api(libs.androidx.appCompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.constraintLayout)
    api(libs.androidx.core)
    implementation(libs.androidx.coreSplashScreen)
    implementation(libs.androidx.recyclerView)
    implementation(libs.androidx.viewPager)
    implementation(libs.androidx.profileInstaller)

    implementation(libs.bundles.androidx.lifecycle)

    implementation(libs.androidx.work)
    implementation(libs.rxJava)

    // Networking
    implementation(libs.bundles.okhttp)
    implementation(libs.okio)
    implementation(libs.conscrypt)

    implementation(libs.bundles.serialization)
    implementation(libs.jsoup)
    // JavaScript engine. Inside Nuvio's "full" Android build this is replaced by Nuvio's own
    // quickjs-kt build (Nuvio's androidApp excludes this artifact), since only one can be packaged.
    implementation("io.github.dokar3:quickjs-kt:1.0.0-alpha13")

    // Disk
    implementation(libs.diskLruCache)
    implementation(libs.unifile)

    implementation(libs.androidx.preference)

    // Dependency injection
    api(libs.injekt)

    // Image loading (api: the host builds one combined Coil ImageLoader)
    api(libs.bundles.coil)
    implementation(libs.subsamplingScaleImageView) {
        exclude(module = "image-decoder")
    }
    implementation(libs.image.decoder)

    // UI libraries
    implementation(libs.material)
    implementation(libs.flexibleAdapter)
    implementation(libs.photoView)
    implementation(libs.directionalViewPager) {
        exclude(group = "androidx.viewpager", module = "viewpager")
    }
    implementation(libs.composeRichEditor)
    implementation(libs.aboutLibraries.compose)
    implementation(libs.bundles.voyager)
    implementation(libs.composeMaterialMotion)
    implementation(libs.swipe)
    implementation(libs.composeWebview)
    implementation(libs.composeGrid)
    implementation(libs.reorderable)
    implementation(libs.bundles.markdown)
    implementation(libs.materialKolor)

    implementation(libs.logcat)
    implementation(libs.bundles.shizuku)
    implementation(libs.stringSimilarity)
    implementation(libs.leakCanary.plumber)
    implementation(libs.androidx.media)
}
