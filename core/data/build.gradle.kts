plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.aggregator.shell.core.data"
    compileSdk = 35

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.room.testing)
    implementation(libs.room.rxjava3)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.retrofit)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
}
