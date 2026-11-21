import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.StringReader
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

val versionPropsContent =
  providers.fileContents(layout.projectDirectory.file("version.properties")).asText.orNull.orEmpty()
val versionProps =
  Properties().apply {
    if (versionPropsContent.isNotBlank()) {
      load(StringReader(versionPropsContent))
    }
  }
val baseMajor = versionProps.getProperty("VERSION_MAJOR")?.toIntOrNull() ?: 1
val baseMinor = versionProps.getProperty("VERSION_MINOR")?.toIntOrNull() ?: 0
val basePatch = versionProps.getProperty("VERSION_PATCH")?.toIntOrNull() ?: 1
val baseVersionCode = versionProps.getProperty("VERSION_CODE")?.toIntOrNull() ?: 2
val ciRunNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 0
val resolvedVersionCode = baseVersionCode + ciRunNumber
val resolvedVersionName = "$baseMajor.$baseMinor.${basePatch + ciRunNumber}"

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.leadxpro.vkmqrx"
    minSdk = 24
    targetSdk = 36
    versionCode = resolvedVersionCode
    versionName = resolvedVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  // Produce a single universal .apk file for direct sharing & installation (never split APKs)
  splits {
    abi { isEnable = false }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.okhttp)
  implementation(libs.zxing.core)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.work.testing)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}

abstract class IncrementVersionTask : org.gradle.api.DefaultTask() {
  @get:org.gradle.api.tasks.Internal
  abstract val versionPropsFile: org.gradle.api.file.RegularFileProperty

  @get:org.gradle.api.tasks.Input
  abstract val ciBuild: org.gradle.api.provider.Property<Boolean>

  @org.gradle.api.tasks.TaskAction
  fun run() {
    if (ciBuild.get()) return
    val file = versionPropsFile.get().asFile
    val props = Properties()
    if (file.exists()) {
      file.inputStream().use { props.load(it) }
    }
    val major = props.getProperty("VERSION_MAJOR")?.toIntOrNull() ?: 1
    val minor = props.getProperty("VERSION_MINOR")?.toIntOrNull() ?: 0
    val nextPatch = (props.getProperty("VERSION_PATCH")?.toIntOrNull() ?: 1) + 1
    val nextCode = (props.getProperty("VERSION_CODE")?.toIntOrNull() ?: 2) + 1
    props.setProperty("VERSION_MAJOR", major.toString())
    props.setProperty("VERSION_MINOR", minor.toString())
    props.setProperty("VERSION_PATCH", nextPatch.toString())
    props.setProperty("VERSION_CODE", nextCode.toString())
    file.outputStream().use {
      props.store(it, "Auto-incremented by Gradle assemble/bundle")
    }
    logger.lifecycle("LeadX Pro version incremented to $major.$minor.$nextPatch (code $nextCode) for next build.")
  }
}

val incrementVersion =
  tasks.register<IncrementVersionTask>("incrementVersion") {
    versionPropsFile.set(layout.projectDirectory.file("version.properties"))
    ciBuild.set(ciRunNumber > 0)
  }

