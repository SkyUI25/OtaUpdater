plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Alinear toolchain de Kotlin con Java 11
kotlin {
    jvmToolchain(11)
}

configurations.implementation {
    exclude ("androidx.core",  "core")
    exclude ("androidx.core",  "core-ktx")
    exclude ("androidx.customview",  "customview")
    exclude ("androidx.coordinatorlayout",  "coordinatorlayout")
    exclude ("androidx.drawerlayout",  "drawerlayout")
    exclude ("androidx.viewpager2",  "viewpager2")
    exclude ("androidx.viewpager",  "viewpager")
    exclude ("androidx.appcompat", "appcompat")
    exclude ("androidx.fragment", "fragment")
    exclude ("androidx.preference",  "preference")
    exclude ("androidx.recyclerview", "recyclerview")
    exclude ("androidx.slidingpanelayout",  "slidingpanelayout")
    exclude ("androidx.swiperefreshlayout",  "swiperefreshlayout")
    exclude ("com.google.android.material", "material")
}

android {
    namespace = "com.lich.updater"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lich.updater"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Alinear destino de bytecode Kotlin con Java 11
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation("sesl.androidx.core:core:1.17.0+1.0.7-sesl8+rev0")
    implementation("sesl.androidx.core:core-ktx:1.17.0+1.0.0-sesl8+rev0")
    implementation("sesl.androidx.customview:customview:1.2.0-rc01+1.0.0-sesl8+rev0")
    implementation("sesl.androidx.drawerlayout:drawerlayout:1.2.0+1.0.0-sesl8+rev0")
    implementation("sesl.androidx.viewpager:viewpager:1.1.0-beta01+1.0.0-sesl8+rev0")
    implementation("sesl.androidx.coordinatorlayout:coordinatorlayout:1.3.0+1.0.0-sesl8+rev0")
    implementation("sesl.androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-alpha01+1.0.0-sesl8+rev0")
    implementation("sesl.androidx.fragment:fragment:1.8.9+1.0.5-sesl8+rev0")
    implementation("sesl.androidx.appcompat:appcompat:1.7.1+1.0.21-sesl8+rev0")
    implementation("sesl.androidx.slidingpanelayout:slidingpanelayout:1.2.0+1.0.4-sesl8+rev0")
    implementation("sesl.androidx.recyclerview:recyclerview:1.4.0+1.0.12-sesl8+rev1")
    implementation("sesl.androidx.picker:picker-basic:1.0.7+1.0.7-sesl8+rev2")
    implementation("sesl.androidx.picker:picker-app:1.0.6+1.0.6-sesl8+rev0")
    implementation("sesl.androidx.picker:picker-color:1.0.2+1.0.3-sesl8+rev0")
    implementation("sesl.androidx.indexscroll:indexscroll:1.0.0+1.0.0-sesl8+rev0")
    implementation("sesl.androidx.preference:preference:1.2.1+1.0.0-sesl8+rev0")
    implementation("sesl.androidx.viewpager2:viewpager2:1.1.0+1.0.0-sesl8+rev0")
    implementation("sesl.com.google.android.material:material:1.12.0+1.0.31-sesl8+rev2")

    // Design
    implementation("io.github.tribalfs:oneui-design:0.7.10+oneui7")

    // Icons
    implementation("io.github.oneuiproject:icons:1.1.0")
    
    // Lottie for animations
    implementation("com.airbnb.android:lottie:6.1.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.6.0")

    // Local broadcasts
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
}