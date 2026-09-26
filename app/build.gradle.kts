plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "org.duofold.live"
 compileSdk = 36
 defaultConfig { applicationId = "org.duofold.live"; minSdk = 34; targetSdk = 36; versionCode = 1048; versionName = "3.0.4-alpha.25" }
 val releaseKey = System.getenv("DUO_KEYSTORE")
 signingConfigs { if (releaseKey != null) create("standalone") {
  storeFile=file(releaseKey); storePassword=System.getenv("DUO_STORE_PASSWORD")
  keyAlias=System.getenv("DUO_KEY_ALIAS"); keyPassword=System.getenv("DUO_KEY_PASSWORD")
 } }
 buildTypes { getByName("release") { isMinifyEnabled=false; isShrinkResources=false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")); if (releaseKey != null) signingConfig=signingConfigs.getByName("standalone") } }
 buildFeatures { compose=true; buildConfig=true }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}
val wallpaperStubs by tasks.registering(JavaCompile::class) {
 source(rootProject.fileTree("wallpaper-stubs") { include("**/*.java") })
 classpath = files(android.bootClasspath)
 destinationDirectory.set(layout.buildDirectory.dir("wallpaper-stubs"))
 sourceCompatibility = "17"
 targetCompatibility = "17"
}
val wallpaperStubJar by tasks.registering(Jar::class) {
 dependsOn(wallpaperStubs)
 from(wallpaperStubs.map { it.destinationDirectory })
 archiveFileName.set("wallpaper-framework-stubs.jar")
 destinationDirectory.set(layout.buildDirectory.dir("compile-only"))
}
dependencies {
 compileOnly(files(wallpaperStubJar))
 implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
 implementation(fileTree("libs") { include("*.jar") })
 implementation("androidx.window:window:1.5.1")
 implementation("androidx.profileinstaller:profileinstaller:1.4.1")
 implementation(platform("androidx.compose:compose-bom:2025.06.01"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.material3:material3")
 testImplementation("junit:junit:4.13.2")
}
