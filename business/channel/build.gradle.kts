plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
}

android {
    namespace = "com.m3u.business.channel"
    buildFeatures {
        compose = true
        buildConfig = true
    }
    defaultConfig {
        // TMDB v3 API key used to enrich VOD metadata (cast photos, crew).
        // Read calls only; safe to ship with the binary. Override via
        // -PtmdbApiKey=... at build time when rotating.
        val tmdbApiKey: String = (project.findProperty("tmdbApiKey") as? String)
            ?: System.getenv("TMDB_API_KEY")
            ?: ""
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
    }
    packaging {
        resources.excludes += "META-INF/**"
    }
}

dependencies {
    implementation(project(":core:foundation"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.media3.exoplayer)

    implementation(libs.google.dagger.hilt)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.google.dagger.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.work)

    implementation(libs.net.mm2d.mmupnp.mmupnp)

    // VOD / series metadata loader uses a plain OkHttp call + JsonObject
    // parsing instead of going through the heavier xtream parser plumbing.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.kotlinx.serialization.json)
}
