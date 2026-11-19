plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

import com.android.build.gradle.internal.tasks.L8DexDesugarLibTask
import java.io.File
import java.util.Properties

fun parseBooleanProperty(value: String?): Boolean {
    val normalized = value?.trim()?.lowercase() ?: return false
    return normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on"
}

fun resolveProperty(dev: Properties, local: Properties, key: String, fallback: String = ""): String {
    return dev.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
        ?: local.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
        ?: fallback
}

fun buildConfigString(value: String): String {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

fun cmakePath(path: String): String {
    if (path.isBlank()) return ""
    val file = File(path)
    val resolved = if (file.isAbsolute) file else rootProject.file(path)
    return resolved.absolutePath.replace("\\", "/")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

val devProperties = Properties().apply {
    val devPropertiesFile = rootProject.file("local.dev.properties")
    if (devPropertiesFile.exists()) {
        load(devPropertiesFile.inputStream())
    }
}

val enableDoviNative = parseBooleanProperty(
    resolveProperty(devProperties, localProperties, "DOVI_NATIVE_ENABLED")
)
val doviExtractorHookReady = parseBooleanProperty(
    resolveProperty(devProperties, localProperties, "DOVI_EXTRACTOR_HOOK_READY")
)
val doviEnableRealLink = parseBooleanProperty(
    resolveProperty(devProperties, localProperties, "DOVI_ENABLE_REAL_LINK")
)
val doviStaticLibPath = resolveProperty(devProperties, localProperties, "DOVI_LIBDOVI_STATIC_LIB")
val doviIncludeDirPath = resolveProperty(devProperties, localProperties, "DOVI_LIBDOVI_INCLUDE_DIR")
val doviPrebuiltRootPath = resolveProperty(devProperties, localProperties, "DOVI_LIBDOVI_PREBUILT_ROOT")

fun env(name: String): String? = providers.environmentVariable(name).orNull

fun truthy(value: String?): Boolean {
    return value.equals("true", ignoreCase = true) ||
        value.equals("1", ignoreCase = true) ||
        value.equals("yes", ignoreCase = true)
}

// BalooTV: plantillas de las fuentes de la edicion casa (fuera de Git; ver CLAUDE.md).
val fuentesDeCasa: String = File(System.getProperty("user.home"), ".balootv/fuentes-casa.txt")
    .takeIf { it.isFile }
    ?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") }
    ?.joinToString("\\n") // escapado: va dentro de una cadena de BuildConfig.java
    .orEmpty()

// BalooTV: applicationId de la edicion casa. La tele de casa lleva instalado su paquete de siempre, que llega de fuera
// de Git (`paquete=` en %USERPROFILE%/.balootv/casa.properties, o BALOOTV_PAQUETE_CASA del entorno). Quien compile
// por su cuenta sin ese fichero obtiene uno neutro que se instala al lado de la publica. Lo lee tambien scripts/balootv/casa.py.
val paquetePublica = "app.balootv.tv"
val paqueteCasa: String = env("BALOOTV_PAQUETE_CASA")?.trim()?.takeIf { it.isNotEmpty() }
    ?: File(System.getProperty("user.home"), ".balootv/casa.properties").takeIf { it.isFile }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } }.getProperty("paquete")?.trim() }
        ?.takeIf { it.isNotEmpty() }
    ?: "$paquetePublica.casa"

// BalooTV: de dónde baja la edición pública sus actualizaciones (GitHub Releases del repositorio público,
// "organizacion/repositorio" en gradle.properties). Vacío: sin actualizador. BALOOTV_ACTUALIZACIONES_URL en
// local.properties lo cambia por otra dirección (pruebas con una publicación de mentira).
// TMDB de la edicion publica: TMDB_API_KEY_PUBLICA. Sin ella, la publica de release no se compila (nunca cae en la de casa).
val tmdbPublica: String = localProperties.getProperty("TMDB_API_KEY_PUBLICA", "").trim()
// Trakt de la edicion publica: TRAKT_CLIENT_ID_PUBLICA (el id de la app de Trakt del proyecto, cuando Trakt la abra a
// todos). Sin ella, la de siempre: cambiar de id es solo poner este valor en secrets.properties.
val traktPublica: String = localProperties.getProperty("TRAKT_CLIENT_ID_PUBLICA", "").trim()
    .ifEmpty { localProperties.getProperty("TRAKT_CLIENT_ID", "").trim() }
gradle.taskGraph.whenReady {
    if (tmdbPublica.isEmpty() && allTasks.any { it.path.startsWith(":app:") && it.name.contains("PublicaRelease") }) {
        throw GradleException(
            "Falta TMDB_API_KEY_PUBLICA en local.properties: la edicion publica no se reparte con la clave de casa. " +
                "Ponla en %USERPROFILE%/.balootv/secrets.properties y ejecuta scripts/balootv/local_properties.py " +
                "(si compilas por tu cuenta, pon tu propia clave de TMDB en local.properties)."
        )
    }
}
val repoPublico: String = providers.gradleProperty("balootvRepoPublico").orNull?.trim().orEmpty()
// Para probar el actualizador contra una publicacion servida en local: -PbalootvActualizacionesUrl=http://10.0.2.2:8000/latest.json
val urlActualizaciones: String = (providers.gradleProperty("balootvActualizacionesUrl").orNull ?: localProperties.getProperty("BALOOTV_ACTUALIZACIONES_URL"))?.trim()?.takeIf { it.isNotEmpty() }
    ?: if (repoPublico.isNotEmpty()) "https://api.github.com/repos/$repoPublico/releases/latest" else ""

val abisCompiladas: List<String> = providers.gradleProperty("abis").orNull
    ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
    ?: listOf("armeabi-v7a", "arm64-v8a")

val buildingAppBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
val useDebugReleaseSigning = env("CI_USE_DEBUG_SIGNING").equals("true", ignoreCase = true)
val useLocalFfmpegDecoder = truthy(
    providers.gradleProperty("useLocalFfmpegDecoder").orNull
        ?: env("USE_LOCAL_FFMPEG_DECODER")
        ?: localProperties.getProperty("USE_LOCAL_FFMPEG_DECODER")
)
// BalooTV: firma propia. La clave vive fuera del repositorio (ver local.properties y CLAUDE.md).
val releaseStoreFilePath = env("BALOOTV_RELEASE_STORE_FILE")
    ?: localProperties.getProperty("BALOOTV_RELEASE_STORE_FILE")
val releaseKeyAliasValue = env("BALOOTV_RELEASE_KEY_ALIAS")
    ?: localProperties.getProperty("BALOOTV_RELEASE_KEY_ALIAS", "balootv")
val releaseKeyPasswordValue = env("BALOOTV_RELEASE_KEY_PASSWORD")
    ?: localProperties.getProperty("BALOOTV_RELEASE_KEY_PASSWORD")
val releaseStorePasswordValue = env("BALOOTV_RELEASE_STORE_PASSWORD")
    ?: localProperties.getProperty("BALOOTV_RELEASE_STORE_PASSWORD")

android {
    namespace = "com.nuvio.tv"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        // Paquete propio: se instala al lado de Nuvio sin tocarlo. La edicion casa lo cambia por el suyo (paqueteCasa).
        applicationId = paquetePublica
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 24
        targetSdk = 36
        versionCode = 48
        versionName = "1.1.1"
        // Solo castellano (y el inglés por defecto como reserva de textos).
        androidResources.localeFilters += listOf("es")

        buildConfigField("String", "TRAKT_API_URL", "\"${localProperties.getProperty("TRAKT_API_URL", "https://api.trakt.tv/")}\"")
        buildConfigField("String", "TRAKT_REDIRECT_URI", "\"${localProperties.getProperty("TRAKT_REDIRECT_URI", "urn:ietf:wg:oauth:2.0:oob")}\"")
        buildConfigField("boolean", "DOVI_NATIVE_ENABLED", enableDoviNative.toString())
        buildConfigField("boolean", "DOVI_EXTRACTOR_HOOK_READY", doviExtractorHookReady.toString())
        if (enableDoviNative) {
            externalNativeBuild {
                cmake {
                    arguments(
                        "-DDOVI_ENABLE_LIBDOVI=${if (doviEnableRealLink) "ON" else "OFF"}",
                        "-DDOVI_LIBDOVI_STATIC_LIB=${cmakePath(doviStaticLibPath)}",
                        "-DDOVI_LIBDOVI_INCLUDE_DIR=${cmakePath(doviIncludeDirPath)}",
                        "-DDOVI_LIBDOVI_PREBUILT_ROOT=${cmakePath(doviPrebuiltRootPath)}"
                    )
                }
            }
        }

        // BalooTV: sin plugins, sin servidores multimedia propios ni reproductores externos.
        buildConfigField("boolean", "FEATURE_EXTERNAL_PLAYBACK_KEEP_ALIVE_ENABLED", "false")
    }

    // BalooTV: dos ediciones de la misma app, con la misma firma y distinto applicationId.
    // - publica: la que se reparte (app.balootv.tv). Sin clave de TorBox, sin fuentes, sin secreto de Trakt y sin SF Pro.
    // - casa: la de la tele de casa (paqueteCasa). Su clave de TorBox, sus fuentes y su paquete llegan de
    //   %USERPROFILE%/.balootv (fuera de Git).
    flavorDimensions += "edicion"
    productFlavors {
        create("publica") {
            dimension = "edicion"
            buildConfigField("String", "EDICION", "\"publica\"")
            // La clave de TMDB del proyecto (o la tuya si compilas por tu cuenta), nunca la de casa.
            buildConfigField("String", "TMDB_API_KEY", buildConfigString(tmdbPublica))
            buildConfigField("String", "TORBOX_API_KEY", "\"\"")
            buildConfigField("String", "TRAKT_CLIENT_ID", buildConfigString(traktPublica))
            buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"\"")
            buildConfigField("String", "FUENTES_DE_SERIE", "\"\"")
            buildConfigField("String", "ACTUALIZACIONES_URL", buildConfigString(urlActualizaciones))
            // Servicios externos de casa (saltar intros, trailers): fuera de la publica, que no habla con nadie mas.
            buildConfigField("String", "INTRODB_API_URL", "\"\"")
            buildConfigField("String", "TRAILER_API_URL", "\"\"")
        }
        create("casa") {
            dimension = "edicion"
            applicationId = paqueteCasa
            buildConfigField("String", "EDICION", "\"casa\"")
            buildConfigField("String", "TMDB_API_KEY", buildConfigString(localProperties.getProperty("TMDB_API_KEY", "")))
            // La clave de TorBox con la que sale la APK; Ajustes puede cambiarla (balootv/cuenta/ClaveTorbox.kt).
            buildConfigField("String", "TORBOX_API_KEY", buildConfigString(localProperties.getProperty("TORBOX_API_KEY", "")))
            buildConfigField("String", "TRAKT_CLIENT_ID", buildConfigString(localProperties.getProperty("TRAKT_CLIENT_ID", "")))
            buildConfigField("String", "TRAKT_CLIENT_SECRET", buildConfigString(localProperties.getProperty("TRAKT_CLIENT_SECRET", "")))
            // Plantillas de fuentes, una por linea, con {torbox} donde va la clave (balootv/fuentes/PlantillaFuente.kt).
            buildConfigField("String", "FUENTES_DE_SERIE", "\"$fuentesDeCasa\"")
            // Casa se actualiza a mano (tele.py instalar), como siempre.
            buildConfigField("String", "ACTUALIZACIONES_URL", "\"\"")
            buildConfigField("String", "INTRODB_API_URL", buildConfigString(localProperties.getProperty("INTRODB_API_URL", "")))
            buildConfigField("String", "TRAILER_API_URL", buildConfigString(localProperties.getProperty("TRAILER_API_URL", "")))
        }
    }

    if (enableDoviNative) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }

    signingConfigs {
        create("release") {
            keyAlias = releaseKeyAliasValue
            keyPassword = releaseKeyPasswordValue
            storeFile = releaseStoreFilePath?.let(::file)
            storePassword = releaseStorePasswordValue
        }
    }

    buildTypes {
        debug {
            // Paquete de la edicion + ".debug" (casa: el de siempre con .debug; publica: app.balootv.tv.debug).
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = parseBooleanProperty(providers.gradleProperty("debuggable").orNull)
            isMinifyEnabled = false

            buildConfigField("boolean", "IS_DEBUG_BUILD", "true")

        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (useDebugReleaseSigning) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }

            buildConfigField("boolean", "IS_DEBUG_BUILD", "false")

        }
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "IS_DEBUG_BUILD", "true")
            applicationIdSuffix = ".debug"
            matchingFallbacks += "release"
        }
    }

    splits {
        abi {
            isEnable = !buildingAppBundle
            reset()
            // Una APK por arquitectura: la de 32 bits (la tele de casa y muchas teles) y la de 64 bits (Google TV,
            // Fire TV recientes, Shield). Para el emulador: -Pabis=x86_64.
            include(*abisCompiladas.toTypedArray())
            isUniversalApk = false
        }
    }

    bundle {
        language {
            // Keep all string resources in the
            // base install so Play Store installs can switch languages at runtime.
            // https://developer.android.com/guide/app-bundle/configure-base
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
        getByName("casa") {
            // Tipografia de Apple, solo en casa: fuera de Git, en %USERPROFILE%/.balootv/assets
            // (scripts/balootv/fuentes_apple.py). La publica usa Inter (balootv/ui/componentes/Tipografia.kt).
            val assetsLocales = File(System.getProperty("user.home"), ".balootv/assets")
            if (assetsLocales.isDirectory) assets.srcDir(assetsLocales)
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            // Keep one consistent native set across dependencies.
            pickFirsts += listOf(
                "lib/*/libc++_shared.so",
                "lib/*/libavcodec.so",
                "lib/*/libavdevice.so",
                "lib/*/libavfilter.so",
                "lib/*/libavformat.so",
                "lib/*/libavutil.so",
                "lib/*/libswscale.so",
                "lib/*/libswresample.so"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

afterEvaluate {
    tasks.withType<L8DexDesugarLibTask>().configureEach {
        if (name.endsWith("AndroidTest")) {
            keepRulesConfigurations.add("-keep class j\$.** { *; }")
        }
    }
}

composeCompiler {
    // Enable Compose compiler metrics for performance analysis
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
    reportsDestination = layout.buildDirectory.dir("compose_reports")
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability_config.conf"))
}

// Globally exclude stock media3 modules — replaced by local :nuvio-exoplayer-engine module
configurations.all {
    exclude(group = "androidx.media3", module = "media3-exoplayer")
    exclude(group = "androidx.media3", module = "media3-common")
    exclude(group = "androidx.media3", module = "media3-datasource")
    exclude(group = "androidx.media3", module = "media3-datasource-okhttp")
    exclude(group = "androidx.media3", module = "media3-exoplayer-hls")
    exclude(group = "androidx.media3", module = "media3-extractor")
}

baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
    baselineProfileOutputDir = "generated/baselineProfiles"
    filter {
        include("com.nuvio.tv.**")
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")

    // Source-retention nullness annotations (MonotonicNonNull / RequiresNonNull /
    // EnsuresNonNull) used by the vendored Matroska extractor in
    // com.nuvio.tv.core.player.dvmkv. Media3 keeps these compileOnly in its own
    // build, so they aren't on our classpath via the prebuilt AARs.
    compileOnly("org.checkerframework:checker-qual:3.43.0")

    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.profileinstaller)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.activity:activity-compose:1.11.0")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.network.cache.control)

    // Navigation
    implementation(libs.navigation.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // ViewModel
    implementation(libs.lifecycle.viewmodel.compose)

    // Media3 — remaining stock modules from Maven (not forked)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.smoothstreaming)
    implementation(libs.media3.decoder)
    implementation(libs.media3.session)
    implementation(libs.media3.container)

    // Transitive dependencies required by forked local AARs (not bundled in AARs):
    // - Guava: needed by lib-common (ImmutableList/ImmutableSet in Tracks, Player API)
    // - media3-database: needed by lib-datasource (cache/storage layer)
    // - annotation-experimental: needed by lib-common (OptIn annotations)
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("androidx.media3:media3-database:1.8.0")
    implementation("androidx.annotation:annotation-experimental:1.3.1")

    // Nuvio Engine local AARs (replaces lib-exoplayer, lib-common, lib-datasource, lib-datasource-okhttp, lib-exoplayer-hls, lib-extractor)
    implementation(files(
        "libs/lib-common-release.aar",
        "libs/lib-datasource-release.aar",
        "libs/lib-datasource-okhttp-release.aar",
        "libs/lib-exoplayer-release.aar",
        "libs/lib-exoplayer-hls-release.aar",
        "libs/lib-extractor-release.aar"
    ))
    implementation(libs.media3.ui)

    // Local decoder AARs (AV1, MPEG-H)
    implementation(files(
        "libs/lib-decoder-av1-release.aar",
        "libs/lib-decoder-mpegh-release.aar"
    ))
    if (useLocalFfmpegDecoder) {
        implementation(project(":ffmpeg-decoder-downmix"))
    } else {
        implementation(files("libs/lib-decoder-ffmpeg-release.aar"))
    }

    // libass-android for ASS/SSA subtitle support (from Maven Central)
    implementation("io.github.peerless2012:ass-media:0.4.0")
    // Local nextlib-mediainfo fork (static FFmpeg; no libav*.so in final AAR)
    implementation(files("libs/nextlib-mediainfo-local.aar"))
    implementation("io.github.abdallahmehiz:mpv-android-lib:0.1.12")

    implementation(libs.gson)

    // QR code + local server for addon management
    implementation(libs.zxing.core)


    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Performance profiling
    implementation("androidx.metrics:metrics-performance:1.0.0-rc01")  // JankStats
    // BalooTV: nombres de composables en las trazas también en release (perfilado en la tele). Coste nulo sin traza activa.
    implementation("androidx.compose.runtime:runtime-tracing")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
