import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val releaseSigningEnvironment =
    listOf(
        "WORKLOG_RELEASE_STORE_FILE",
        "WORKLOG_RELEASE_STORE_PASSWORD",
        "WORKLOG_RELEASE_KEY_ALIAS",
        "WORKLOG_RELEASE_KEY_PASSWORD",
    )
val releaseSigningValues =
    releaseSigningEnvironment.associateWith { name ->
        providers.environmentVariable(name).orNull?.takeIf(String::isNotBlank)
    }
val hasReleaseSigning = releaseSigningValues.values.all { it != null }
val missingReleaseSigningEnvironment =
    releaseSigningValues
        .filterValues { value -> value == null }
        .keys
        .toList()
val releaseKeystorePath = releaseSigningValues["WORKLOG_RELEASE_STORE_FILE"].orEmpty()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.worklogai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.worklogai.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            // Keep development and device-test data isolated from the signed internal release installation.
            applicationIdSuffix = ".debug"
        }
        if (hasReleaseSigning) {
            signingConfigs.create("release") {
                storeFile = file(requireNotNull(releaseSigningValues["WORKLOG_RELEASE_STORE_FILE"]))
                storePassword = requireNotNull(releaseSigningValues["WORKLOG_RELEASE_STORE_PASSWORD"])
                keyAlias = requireNotNull(releaseSigningValues["WORKLOG_RELEASE_KEY_ALIAS"])
                keyPassword = requireNotNull(releaseSigningValues["WORKLOG_RELEASE_KEY_PASSWORD"])
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources.excludes +=
            setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md",
            )
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        htmlReport = true
        sarifReport = true
        xmlReport = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Checks non-secret Release signing environment before packaging."
    inputs.property(
        "missingReleaseSigningEnvironment",
        missingReleaseSigningEnvironment.joinToString(),
    )
    inputs.property("releaseKeystorePath", releaseKeystorePath)
    doLast {
        val missing =
            inputs.properties
                .getValue("missingReleaseSigningEnvironment")
                .toString()
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing configuration is incomplete. Set: $missing",
            )
        }
        val storePath: String =
            inputs.properties
                .getValue("releaseKeystorePath")
                .toString()
        if (!File(storePath).isFile) {
            throw GradleException("Release keystore file does not exist or is not a regular file.")
        }
    }
}

tasks.configureEach {
    if (name in setOf("validateSigningRelease", "packageRelease", "assembleRelease", "bundleRelease")) {
        dependsOn(verifyReleaseSigning)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

hilt {
    enableAggregatingTask = true
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    parallel = true
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        sarif.required.set(true)
        xml.required.set(true)
    }
}

ktlint {
    android.set(true)
    version.set("1.5.0")
    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
