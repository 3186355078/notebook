import org.gradle.api.tasks.wrapper.Wrapper

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
}

tasks.wrapper {
    gradleVersion = "8.13"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
    validateDistributionUrl = false
}
