plugins { id("com.android.application") }
android {
    namespace = "com.adamdelisi.bridgefixture"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.adamdelisi.bridgefixture"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
