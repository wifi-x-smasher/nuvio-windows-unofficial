import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

val runtimeConfigOverrideKeys = listOf(
    "SUPABASE_URL",
    "SUPABASE_ANON_KEY",
    "TRAKT_CLIENT_ID",
    "TRAKT_CLIENT_SECRET",
    "TRAKT_REDIRECT_URI",
    "INTRODB_API_URL",
    "IMDB_RATINGS_API_BASE_URL",
    "IMDB_TAPFRAME_API_BASE_URL",
    "PREMIUMIZE_CLIENT_ID",
    "CONTRIBUTIONS_URL",
    "DONATIONS_BASE_URL",
    "DONATIONS_DONATE_URL",
)

val bundledVlcVersion = "3.0.21"
val bundledVlcDownloadUrl = "https://get.videolan.org/vlc/$bundledVlcVersion/win64/vlc-$bundledVlcVersion-win64.zip"
val bundledVlcZipSha256 = "a0b7ec02b50adf6417eed014fb8df50af39690505a4225b85b3dc2ed17d14843"
val bundledVlcZip = layout.buildDirectory.file("downloads/vlc/vlc-$bundledVlcVersion-win64.zip")
val bundledVlcResourcesDir = layout.buildDirectory.dir("desktop-runtime-resources/vlc")
val bundledMpvVersion = "0.41.0"
val bundledMpvDownloadUrl = "https://github.com/mpv-player/mpv/releases/download/v$bundledMpvVersion/mpv-v$bundledMpvVersion-x86_64-pc-windows-msvc.zip"
val bundledMpvZipSha256 = "4e197f729f5071c6772f35fffd96e0f36e3e8a044bd9479b136bb09b7c6a80ff"
val bundledMpvZip = layout.buildDirectory.file("downloads/mpv/mpv-v$bundledMpvVersion-x86_64-pc-windows-msvc.zip")
val bundledMpvResourcesDir = layout.buildDirectory.dir("desktop-runtime-resources/mpv")
val bundledMpvNativeResourcesDir = layout.buildDirectory.dir("desktop-runtime-resources/native")
val mediampRootDir = rootProject.file("vendor/mediamp-nuvio")
val mediampNativeBuildDir = mediampRootDir.resolve("mediamp-mpv/build-ci")
val mediampNativeReleaseDir = mediampNativeBuildDir.resolve("Release")

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

abstract class GenerateRuntimeConfigsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Optional
    @get:InputFile
    abstract val localPropertiesFile: RegularFileProperty

    @get:Input
    abstract val appVersionName: Property<String>

    @get:Input
    abstract val appVersionCode: Property<Int>

    @get:Input
    abstract val runtimeConfigOverrides: MapProperty<String, String>

    @TaskAction
    fun generate() {
        val props = Properties()
        localPropertiesFile.asFile.orNull?.takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
        val overrides = runtimeConfigOverrides.get()
        fun configValue(key: String, defaultValue: String = "") =
            overrides[key]?.takeIf { it.isNotBlank() }
                ?: props.getProperty(key)?.takeIf { it.isNotBlank() }
                ?: defaultValue

        val outDir = outputDir.get().asFile
        outDir.resolve("com/nuvio/app/core/network").apply {
            mkdirs()
            resolve("SupabaseConfig.kt").writeText(
                """
                |package com.nuvio.app.core.network
                |
                |object SupabaseConfig {
                |    const val URL = "${configValue("SUPABASE_URL")}"
                |    const val ANON_KEY = "${configValue("SUPABASE_ANON_KEY")}"
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/tmdb/TmdbConfig.kt").delete()

        outDir.resolve("com/nuvio/app/features/trakt").apply {
            mkdirs()
            resolve("TraktConfig.kt").writeText(
                """
                |package com.nuvio.app.features.trakt
                |
                |object TraktConfig {
                |    const val CLIENT_ID = "${configValue("TRAKT_CLIENT_ID")}"
                |    const val CLIENT_SECRET = "${configValue("TRAKT_CLIENT_SECRET")}"
                |    const val REDIRECT_URI = "${configValue("TRAKT_REDIRECT_URI", "nuvio://auth/trakt")}"
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/player/skip").apply {
            mkdirs()
            resolve("IntroDbConfig.kt").writeText(
                """
                |package com.nuvio.app.features.player.skip
                |
                |object IntroDbConfig {
                |    const val URL = "${configValue("INTRODB_API_URL")}"
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/details").apply {
            mkdirs()
            resolve("ImdbEpisodeRatingsConfig.kt").writeText(
                """
                |package com.nuvio.app.features.details
                |
                |object ImdbEpisodeRatingsConfig {
                |    const val IMDB_RATINGS_API_BASE_URL = "${configValue("IMDB_RATINGS_API_BASE_URL")}"
                |    const val IMDB_TAPFRAME_API_BASE_URL = "${configValue("IMDB_TAPFRAME_API_BASE_URL")}"
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/debrid").apply {
            mkdirs()
            resolve("PremiumizeConfig.kt").writeText(
                """
                |package com.nuvio.app.features.debrid
                |
                |object PremiumizeConfig {
                |    const val CLIENT_ID = "${configValue("PREMIUMIZE_CLIENT_ID")}"
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/core/build").apply {
            mkdirs()
            resolve("AppVersionConfig.kt").writeText(
                """
                |package com.nuvio.app.core.build
                |
                |object AppVersionConfig {
                |    const val VERSION_NAME = "${appVersionName.get()}"
                |    const val VERSION_CODE = ${appVersionCode.get()}
                |}
                """.trimMargin()
            )
        }

        outDir.resolve("com/nuvio/app/features/settings").apply {
            mkdirs()
            resolve("CommunityConfig.kt").writeText(
                """
                |package com.nuvio.app.features.settings
                |
                |object CommunityConfig {
                |    const val CONTRIBUTIONS_URL = "${configValue("CONTRIBUTIONS_URL")}"
                |    const val DONATIONS_BASE_URL = "${configValue("DONATIONS_BASE_URL")}"
                |    const val DONATIONS_DONATE_URL = "${configValue("DONATIONS_DONATE_URL")}"
                |}
                """.trimMargin()
            )
        }
    }
}

abstract class ValidatePackagedRuntimeConfigsTask : DefaultTask() {
    @get:Optional
    @get:InputFile
    abstract val localPropertiesFile: RegularFileProperty

    @get:Input
    abstract val runtimeConfigOverrides: MapProperty<String, String>

    @TaskAction
    fun validate() {
        val props = Properties()
        localPropertiesFile.asFile.orNull?.takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
        val overrides = runtimeConfigOverrides.get()
        fun configValue(key: String): String =
            overrides[key]?.takeIf { it.isNotBlank() }
                ?: props.getProperty(key)?.takeIf { it.isNotBlank() }
                ?: ""

        val supabaseUrl = configValue("SUPABASE_URL")
        val supabaseAnonKey = configValue("SUPABASE_ANON_KEY")
        val normalizedUrl = supabaseUrl.trim().trimEnd('/').lowercase()
        val host = normalizedUrl
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
        val isValidPackagedSupabaseConfig = normalizedUrl.startsWith("https://") &&
            host != "localhost" &&
            host != "127.0.0.1" &&
            host != "::1" &&
            !normalizedUrl.contains("your_supabase_url_here") &&
            supabaseAnonKey.isNotBlank() &&
            !supabaseAnonKey.contains("your_supabase_anon_key_here", ignoreCase = true)

        if (!isValidPackagedSupabaseConfig) {
            error(
                "Cannot build a Windows installer without production Supabase configuration. " +
                    "Set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties, Gradle properties, or environment variables.",
            )
        }
    }
}

fun readXcconfigValue(file: File, key: String): String? {
    if (!file.exists()) return null
    return file.readLines()
        .asSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .map { line ->
            val separatorIndex = line.indexOf('=')
            line.substring(0, separatorIndex).trim() to line.substring(separatorIndex + 1).trim()
        }
        .firstOrNull { (entryKey, _) -> entryKey == key }
        ?.second
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

val supabaseProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}
val releaseStoreFile = supabaseProps.getProperty("NUVIO_RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }
val releaseStorePassword = supabaseProps.getProperty("NUVIO_RELEASE_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = supabaseProps.getProperty("NUVIO_RELEASE_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val releaseKeyPassword = supabaseProps.getProperty("NUVIO_RELEASE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeystore = releaseStoreFile?.let(rootProject::file)
val appVersionConfigFile = rootProject.file("iosApp/Configuration/Version.xcconfig")
val releaseAppVersionName = readXcconfigValue(appVersionConfigFile, "MARKETING_VERSION")
    ?: error("MARKETING_VERSION is missing from ${appVersionConfigFile.path}")
val releaseAppVersionCode = readXcconfigValue(appVersionConfigFile, "CURRENT_PROJECT_VERSION")
    ?.toIntOrNull()
    ?: error("CURRENT_PROJECT_VERSION is missing or invalid in ${appVersionConfigFile.path}")
val iosDistribution = (
    providers.gradleProperty("nuvio.ios.distribution").orNull
        ?: System.getenv("NUVIO_IOS_DISTRIBUTION")
        ?: supabaseProps.getProperty("NUVIO_IOS_DISTRIBUTION")
        ?: "appstore"
    ).trim().lowercase()
require(iosDistribution == "appstore" || iosDistribution == "full") {
    "NUVIO_IOS_DISTRIBUTION must be 'appstore' or 'full'."
}
val iosDistributionSourceDir = if (iosDistribution == "full") {
    "src/iosFull/kotlin"
} else {
    "src/iosAppStore/kotlin"
}
val desktopJavaHomeOverride = (
    providers.gradleProperty("nuvio.desktop.javaHome").orNull
        ?: System.getenv("NUVIO_DESKTOP_JAVA_HOME")
        ?: System.getenv("NUVIO_DESKTOP_PACKAGING_JDK")
        ?: supabaseProps.getProperty("NUVIO_DESKTOP_JAVA_HOME")
    )?.trim()?.takeIf { it.isNotBlank() }
val iosFrameworkBundleId = "com.nuvio.media"
val fullCommonSourceDir = project.file("src/fullCommonMain/kotlin")
val generatedRuntimeConfigDir = layout.buildDirectory.dir("generated/runtime-config/kotlin")

fun configureRuntimeConfigOverrides(overrides: MapProperty<String, String>) {
    runtimeConfigOverrideKeys.forEach { key ->
        overrides.put(
            key,
            providers.gradleProperty(key)
                .orElse(providers.environmentVariable(key))
                .orElse(""),
        )
    }
}

val generateRuntimeConfigs = tasks.register<GenerateRuntimeConfigsTask>("generateRuntimeConfigs") {
    outputDir.set(generatedRuntimeConfigDir)
    rootProject.layout.projectDirectory.file("local.properties").asFile
        .takeIf { it.exists() }
        ?.let { localPropertiesFile.set(it) }
    appVersionName.set(releaseAppVersionName)
    appVersionCode.set(releaseAppVersionCode)
    configureRuntimeConfigOverrides(runtimeConfigOverrides)
}

val validatePackagedRuntimeConfigs = tasks.register<ValidatePackagedRuntimeConfigsTask>("validatePackagedRuntimeConfigs") {
    rootProject.layout.projectDirectory.file("local.properties").asFile
        .takeIf { it.exists() }
        ?.let { localPropertiesFile.set(it) }
    configureRuntimeConfigOverrides(runtimeConfigOverrides)
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateRuntimeConfigs)
}

tasks.matching {
    it.name in setOf(
        "createDistributable",
        "packageDistributionForCurrentOS",
        "packageExe",
        "packageMsi",
        "runDistributable",
    )
}.configureEach {
    dependsOn(validatePackagedRuntimeConfigs)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64()
    )

    iosTargets.forEach { iosTarget ->
        iosTarget.compilations.getByName("main") {
            cinterops {
                create("commoncrypto") {
                    defFile(project.file("src/nativeInterop/cinterop/commoncrypto.def"))
                    compilerOpts("-I${project.projectDir}/src/nativeInterop/cinterop")
                }
            }

            if (iosDistribution == "full") {
                defaultSourceSet.kotlin.srcDir(fullCommonSourceDir)
            }
            defaultSourceSet.kotlin.srcDir(project.file(iosDistributionSourceDir))
            defaultSourceSet.dependencies {
                implementation(libs.ktor.client.darwin)
                if (iosDistribution == "full") {
                    implementation(libs.quickjs.kt)
                    implementation(libs.ksoup)
                }
            }
        }

        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            freeCompilerArgs += listOf("-Xbinary=bundleId=$iosFrameworkBundleId")
        }
    }
    
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generatedRuntimeConfigDir)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.work.runtime)
            implementation(libs.coil.gif)
            implementation("androidx.recyclerview:recyclerview:1.4.0")
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
            implementation("com.google.code.gson:gson:2.11.0")
            implementation("io.github.peerless2012:ass-media:0.4.0-beta01")
            implementation(libs.ktor.client.android)
            implementation(libs.androidx.media3.exoplayer.hls)
            implementation(libs.androidx.media3.exoplayer.dash)
            implementation(libs.androidx.media3.exoplayer.smoothstreaming)
            implementation(libs.androidx.media3.exoplayer.rtsp)
            implementation(libs.androidx.media3.datasource)
            implementation(libs.androidx.media3.datasource.okhttp)
            implementation(libs.androidx.media3.decoder)
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.common)
            implementation(libs.androidx.media3.container)
            implementation(libs.androidx.media3.extractor)
            implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("lib-*.aar"))))
        }
        commonMain.dependencies {
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.coil.svg)
            implementation("dev.chrisbanes.haze:haze:1.7.2")
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kermit)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.functions)
            implementation(libs.reorderable)
        }
        val desktopMain by getting {
            kotlin.srcDir(fullCommonSourceDir)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.cio)
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.vlcj)
                implementation(libs.jna)
                implementation(libs.jna.platform)
                implementation(libs.slf4j.simple)
                implementation(libs.quickjs.kt)
                implementation(libs.ksoup)
                implementation("org.openani.mediamp:mediamp-api:0.1.0-dev-1")
                implementation("org.openani.mediamp:mediamp-mpv:0.1.0-dev-1")
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

afterEvaluate {
    dependencies {
        add("fullImplementation", files("libs/quickjs-kt-android-1.0.5-nuvio.aar"))
        add("fullImplementation", libs.ksoup)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    debugImplementation(libs.compose.uiTooling)
}

val downloadVlcRuntime by tasks.registering {
    group = "distribution"
    description = "Downloads the 64-bit VLC runtime used by the Windows internal player."
    outputs.file(bundledVlcZip)

    doLast {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return@doLast

        val destination = bundledVlcZip.get().asFile
        destination.parentFile.mkdirs()
        if (!destination.isFile || sha256Hex(destination) != bundledVlcZipSha256) {
            logger.lifecycle("Downloading VLC runtime $bundledVlcVersion")
            URI.create(bundledVlcDownloadUrl).toURL().openStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }

        val actualSha256 = sha256Hex(destination)
        check(actualSha256 == bundledVlcZipSha256) {
            "VLC runtime checksum mismatch. Expected $bundledVlcZipSha256 but got $actualSha256"
        }
    }
}

val downloadMpvRuntime by tasks.registering {
    group = "distribution"
    description = "Downloads the 64-bit MPV runtime used by the Windows internal player."
    outputs.file(bundledMpvZip)

    doLast {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return@doLast

        val destination = bundledMpvZip.get().asFile
        destination.parentFile.mkdirs()
        if (!destination.isFile || sha256Hex(destination) != bundledMpvZipSha256) {
            logger.lifecycle("Downloading MPV runtime $bundledMpvVersion")
            URI.create(bundledMpvDownloadUrl).toURL().openStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }

        val actualSha256 = sha256Hex(destination)
        check(actualSha256 == bundledMpvZipSha256) {
            "MPV runtime checksum mismatch. Expected $bundledMpvZipSha256 but got $actualSha256"
        }
    }
}

val prepareDesktopRuntimeResources by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Prepares bundled desktop player runtimes for native Windows packages."
    dependsOn(downloadVlcRuntime)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(zipTree(bundledVlcZip)) {
        eachFile {
            val rootPrefix = "vlc-$bundledVlcVersion/"
            if (path.startsWith(rootPrefix)) {
                path = path.removePrefix(rootPrefix)
            }
        }
        includeEmptyDirs = false
    }
    into(bundledVlcResourcesDir)
}

val prepareMpvRuntimeResources by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Prepares bundled standalone MPV runtime for the Windows external/fallback path."
    dependsOn(downloadMpvRuntime)
    from(zipTree(bundledMpvZip)) {
        include("mpv.exe", "vulkan-1.dll")
    }
    into(bundledMpvResourcesDir)
}

val buildMediampMpvRuntime by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds the MediaMP/libmpv JNI runtime used by the Windows internal player."
    inputs.dir(mediampRootDir.resolve("mediamp-mpv/src/cpp"))
    inputs.file(mediampRootDir.resolve("mediamp-mpv/CMakeLists.txt"))
    outputs.file(mediampNativeReleaseDir.resolve("mediampv.dll"))
    onlyIf {
        System.getProperty("os.name").contains("Windows", ignoreCase = true) &&
            mediampRootDir.resolve("gradlew.bat").isFile
    }
    workingDir = mediampRootDir
    val taskJavaHome = desktopJavaHomeOverride
        ?: System.getenv("JAVA_HOME")
        ?: System.getProperty("java.home")
    val localAndroidSdk = System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: File(System.getProperty("user.home"), "AppData/Local/Android/Sdk").absolutePath
    val portableCmakeBin = rootProject.file("../.tmp/tools/cmake-4.3.3-windows-x86_64/bin")
        .takeIf { it.isDirectory }
        ?.absolutePath
    environment("JAVA_HOME", taskJavaHome)
    environment("ANDROID_HOME", localAndroidSdk)
    environment("ANDROID_SDK_ROOT", localAndroidSdk)
    environment(
        "PATH",
        listOfNotNull(
            File(taskJavaHome, "bin").absolutePath,
            portableCmakeBin,
            System.getenv("PATH"),
        ).joinToString(File.pathSeparator),
    )
    commandLine(
        "cmd.exe",
        "/c",
        "gradlew.bat",
        ":mediamp-mpv:buildCMakeDesktop",
        "--no-daemon",
        "--no-configuration-cache",
    )
}

val prepareMediampMpvRuntimeResources by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Prepares bundled MediaMP/libmpv native runtime for the Windows internal player."
    dependsOn(buildMediampMpvRuntime)
    from(mediampNativeReleaseDir) {
        include("*.dll", "*.exe", "*.com")
    }
    into(bundledMpvNativeResourcesDir)
}

val prepareAllDesktopRuntimeResources by tasks.registering {
    group = "distribution"
    description = "Prepares all bundled desktop player runtimes for native Windows packages."
    dependsOn(prepareDesktopRuntimeResources, prepareMpvRuntimeResources, prepareMediampMpvRuntimeResources)
}

compose.desktop {
    application {
        mainClass = "com.nuvio.app.DesktopMainKt"
        desktopJavaHomeOverride?.let { javaHome = it }
        jvmArgs("-Dskiko.renderApi=OPENGL")
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            appResourcesRootDir.set(layout.buildDirectory.dir("desktop-runtime-resources"))
            packageName = "Nuvio"
            packageVersion = releaseAppVersionName
            description = "Unofficial Nuvio media hub for Windows"
            vendor = "wifi-x-smasher"
            modules("java.instrument", "java.management", "java.naming", "jdk.unsupported")
            windows {
                menuGroup = "Nuvio"
                iconFile.set(project.file("src/desktopMain/resources/app-icon.ico"))
                upgradeUuid = "8B1D3C65-4F85-4AE9-9AE4-5E66A8C95B67"
            }
        }
    }
}

tasks.register<JavaExec>("runPlayerRenderProof") {
    group = "verification"
    description = "Runs a local proof window for Compose-owned desktop video rendering."
    dependsOn("compileKotlinDesktop", "desktopProcessResources")
    mainClass.set("com.nuvio.app.dev.PlayerRenderProofMainKt")
    val desktopCompilation = kotlin.targets.getByName("desktop").compilations.getByName("main")
    classpath = desktopCompilation.output.allOutputs + configurations.getByName("desktopRuntimeClasspath")
    systemProperty(
        "compose.application.resources.dir",
        layout.buildDirectory.dir("desktop-runtime-resources").get().asFile.absolutePath,
    )
}

tasks.matching { it.name == "packageMsi" }.configureEach {
    dependsOn(prepareAllDesktopRuntimeResources)
    inputs.file(project.file("scripts/brand-windows-msi.ps1"))
    inputs.dir(bundledVlcResourcesDir)
    inputs.dir(bundledMpvResourcesDir)
    inputs.dir(bundledMpvNativeResourcesDir)
    inputs.files(
        project.file("src/desktopMain/installer/WixUIDialogBmp.bmp"),
        project.file("src/desktopMain/installer/WixUIBannerBmp.bmp"),
        project.file("src/desktopMain/resources/app-icon.ico"),
    )

    doLast {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return@doLast

        val msiPath = layout.buildDirectory
            .file("compose/binaries/main/msi/Nuvio-$releaseAppVersionName.msi")
            .get()
            .asFile
        val brandScript = project.file("scripts/brand-windows-msi.ps1")
        val installerAssetsDir = project.file("src/desktopMain/installer")
        if (!msiPath.isFile || !brandScript.isFile || !installerAssetsDir.isDirectory) return@doLast

        exec {
            commandLine(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                brandScript.absolutePath,
                "-MsiPath",
                msiPath.absolutePath,
                "-AssetsDir",
                installerAssetsDir.absolutePath,
                "-WixToolsetDir",
                rootProject.file("build/wix311").absolutePath,
            )
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(prepareAllDesktopRuntimeResources)
    (this as Sync).from(bundledVlcResourcesDir)
    (this as Sync).from(bundledMpvResourcesDir)
    (this as Sync).from(bundledMpvNativeResourcesDir) {
        into("native")
    }
}

tasks.matching { it.name == "packageExe" || it.name == "createDistributable" }.configureEach {
    dependsOn(prepareAllDesktopRuntimeResources)
    inputs.dir(bundledVlcResourcesDir)
    inputs.dir(bundledMpvResourcesDir)
    inputs.dir(bundledMpvNativeResourcesDir)
}

configurations.all {
    exclude(group = "androidx.media3", module = "media3-exoplayer")
    exclude(group = "androidx.media3", module = "media3-ui")
}

android {
    namespace = "com.nuvio.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    signingConfigs {
        create("release") {
            if (releaseKeystore != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.nuvio.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = releaseAppVersionCode
        versionName = releaseAppVersionName
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
        }
        create("playstore") {
            dimension = "distribution"
        }
    }
    sourceSets.getByName("full") {
        manifest.srcFile("src/androidFull/AndroidManifest.xml")
        java.srcDir(fullCommonSourceDir)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += listOf(
                "lib/*/libc++_shared.so",
                "lib/*/libavcodec.so",
                "lib/*/libavutil.so",
                "lib/*/libswscale.so",
                "lib/*/libswresample.so"
            )
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
