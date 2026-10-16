import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.BaseExtension
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.nav.safeargs.kotlin) apply false
    alias(libs.plugins.com.github.aerathstuff.zygoteloader) apply false
}

fun String.execute(currentWorkingDir: File = file("./")): String {
    val out = providers.exec {
        workingDir = currentWorkingDir
        commandLine = split("\\s".toRegex())
    }
    return out.standardOutput.asText.get().trim()
}

val localProperties = Properties()
val localPropFile = file("local.properties")
if (localPropFile.exists()) {
    localProperties.load(localPropFile.inputStream())
}

val ciBuild = providers.environmentVariable("CI").isPresent
val officialBuild by extra(localProperties.getProperty("officialBuild", "false") == "true")

@Suppress("unused")
val crowdinProjectId: String by extra(localProperties.getProperty("crowdinProjectId", ""))

@Suppress("unused")
val crowdinApiKey: String by extra(localProperties.getProperty("crowdinApiKey", ""))

fun getUncommittedSuffix(): String {
    if (officialBuild) return ""

    val shortRef = try {
        "git rev-parse --short HEAD".execute()
    } catch (_: Throwable) {
        "custom"
    }

    if (ciBuild) {
        val headRefVal = providers.environmentVariable("GITHUB_HEAD_REF").orElse("HEAD").get()
        return "$headRefVal-$shortRef"
    }

    var returnedVal = ""

    try {
        val branch = "git rev-parse --abbrev-ref HEAD".execute().split("/").last()
        if (branch != "master") {
            returnedVal += "$branch-"
        }
    } catch (_: Throwable) {}

    returnedVal += shortRef

    val result = try { "git status -s".execute() } catch (_: Throwable) { "" }
    if (result.isEmpty()) {
        return returnedVal
    }

    return "$returnedVal+${result.count { it == '\n' } + 1}"
}

val gitCommitCount = try {
    "git rev-list refs/remotes/origin/master --count".execute().toInt()
} catch (_: Throwable) {
    500
}

val gitCommitCountAfterOss = gitCommitCount - 432

val minSdkVer by extra(29)
val targetSdkVer by extra(37)

val appVerCode by extra(gitCommitCount + 0x6f7373)

val gitVersionName: String get() {
    val suffix = try {
        getUncommittedSuffix()
    } catch (_: Throwable) {
        "custom"
    }

    return suffix.ifEmpty {
        "oss-$gitCommitCountAfterOss"
    }
}

val appVerName by extra(gitVersionName)

@Suppress("unused")
val configVerCode by extra(93)

@Suppress("unused")
val serviceVerCode by extra(105)

@Suppress("unused")
val minBackupVerCode by extra(65)

// Zygote module aur manager app dono ka syncing package
@Suppress("unused")
val appPackageName by extra("com.android.core.settings")

@Suppress("unused")
val localBuild by extra(localProperties.getProperty("localBuild", "false") == "true")

val androidSourceCompatibility = JavaVersion.VERSION_21
val androidTargetCompatibility = JavaVersion.VERSION_21

tasks.register("clean", Delete::class) {
    description = "Clean the build directory"
    delete(rootProject.layout.buildDirectory)
}

fun Project.configureBaseExtension() {
    extensions.findByType<BaseExtension>()?.run {
        compileSdkVersion(targetSdkVer)

        defaultConfig {
            minSdk = minSdkVer
            targetSdk = targetSdkVer
            versionCode = appVerCode
            versionName = appVerName

            consumerProguardFiles("proguard-rules.pro")
        }

        buildTypes {
            all {
                signingConfig = signingConfigs.getByName("debug")
            }
            named("release") {
                isMinifyEnabled = false
            }
        }

        compileOptions {
            sourceCompatibility = androidSourceCompatibility
            targetCompatibility = androidTargetCompatibility
        }
    }

    extensions.findByType<ApplicationExtension>()?.run {
        buildTypes {
            named("release") {
                isShrinkResources = false
            }
        }

        dependenciesInfo {
            includeInApk = false
            includeInBundle = false
        }
    }
}

subprojects {
    plugins.withId("com.android.application") {
        configureBaseExtension()
    }
    plugins.withId("com.android.library") {
        configureBaseExtension()
    }
}
