import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.Copy
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
plugins {
    java
    `maven-publish`
}

group = "com.bumenfeld"
val baseVersion = project.findProperty("project_version")?.toString()?.takeIf { it.isNotBlank() }
    ?: "0.1.0"

fun resolveGitVersion(): String? {
    val process = ProcessBuilder(
        "git",
        "describe",
        "--tags",
        "--long",
        "--dirty"
    )
        .directory(projectDir)
        .redirectErrorStream(true)
        .start()

    val finished = process.waitFor(5, TimeUnit.SECONDS)
    if (!finished || process.exitValue() != 0) {
        return null
    }

    val describe = process.inputStream.bufferedReader().readText().trim()
    if (describe.isBlank()) {
        return null
    }

    val dirty = describe.endsWith("-dirty")
    val cleanDescribe = if (dirty) describe.removeSuffix("-dirty") else describe
    val segments = cleanDescribe.split('-')
    if (segments.size < 3) {
        return segments.firstOrNull()?.removePrefix("v")
    }

    val baseTag = segments[0].removePrefix("v")
    val commitCount = segments[1].toIntOrNull() ?: 0
    return if (commitCount == 0 && !dirty) {
        baseTag
    } else {
        val suffix = if (commitCount > 0) "-$commitCount" else ""
        "$baseTag-dev$suffix"
    }
}

val outputVersion = project.findProperty("localVersion")?.toString()?.takeIf { it.isNotBlank() }
    ?: resolveGitVersion()

val computedVersion = outputVersion ?: baseVersion
version = computedVersion

val javaVersion = 25
repositories {
    mavenCentral()
    maven {
        name = "hytale-release"
        url = uri("https://maven.hytale.com/release")
    }
    maven {
        name = "hytale-pre-release"
        url = uri("https://maven.hytale.com/pre-release")
    }
}

val serverVersionProperty = project.findProperty("server_version")?.toString()?.takeIf { it.isNotBlank() }
val serverVersionResolved = resolveServerVersion(serverVersionProperty)
val serverVersionManifest = formatManifestServerVersion(serverVersionResolved ?: serverVersionProperty)

fun resolveServerVersion(raw: String?): String? {
    if (raw == null) {
        return null
    }
    if (raw != "*") {
        return raw
    }
    return fetchLatestServerRelease()
}

fun fetchLatestServerRelease(): String? {
    val metadataUrl = "https://maven.hytale.com/release/com/hypixel/hytale/Server/maven-metadata.xml"
    return try {
        val xml = URL(metadataUrl).readText()
        Regex("<release>([^<]+)</release>").find(xml)?.groupValues?.get(1)
            ?: Regex("<latest>([^<]+)</latest>").find(xml)?.groupValues?.get(1)
    } catch (ex: IOException) {
        logger.warn("Unable to fetch Hytale server metadata from $metadataUrl: ${ex.message}")
        null
    }
}

fun formatManifestServerVersion(value: String?): String? {
    if (value == null) {
        return null
    }
    val trimmed = value.trim()
    if (trimmed == "*") {
        return "*"
    }
    if (trimmed.startsWithAny(listOf("<", ">", "=", "~", "^", "[", "(", "x", "X")) || trimmed.contains(" ")) {
        return trimmed
    }
    return ">=$trimmed"
}

fun String.startsWithAny(prefixes: List<String>): Boolean {
    return prefixes.any { this.startsWith(it) }
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jspecify)

    implementation(libs.snakeyaml)
    implementation(libs.jackson.databind)

    val enableHytalePlugin = project.findProperty("enable_hytale_plugin")?.toString()?.toBooleanStrictOrNull() ?: false
    if (enableHytalePlugin) {
        val dependencyVersion = serverVersionResolved ?: "latest.release"
        compileOnly("com.hypixel.hytale:Server:$dependencyVersion")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

val buildTimestamp = OffsetDateTime.now(ZoneOffset.UTC)
    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
val gitRevision = runCatching {
    ByteArrayOutputStream().use { buffer ->
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.copyTo(buffer)
        if (process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0) {
            buffer.toString().trim().takeIf { it.isNotBlank() } ?: "unknown"
        } else {
            "unknown"
        }
    }
}.getOrDefault("unknown")
val buildId = "$computedVersion-$buildTimestamp-$gitRevision"

tasks.named<Copy>("processResources") {
    val replaceProperties = mapOf(
        "plugin_group" to findProperty("plugin_group"),
        "plugin_maven_group" to findProperty("plugin_maven_group"),
        "plugin_name" to findProperty("plugin_name"),
        "plugin_version" to computedVersion,
        "server_version" to serverVersionManifest,
        "plugin_description" to findProperty("plugin_description"),
        "plugin_website" to findProperty("plugin_website"),
        "plugin_main_entrypoint" to findProperty("plugin_main_entrypoint"),
        "plugin_author" to findProperty("plugin_author"),
        "build_id" to buildId,
        "git_revision" to gitRevision,
        "build_timestamp" to buildTimestamp
    )

    filesMatching("manifest.json") {
        expand(replaceProperties)
    }

    inputs.properties(replaceProperties)
}

tasks.withType<Jar> {
    manifest {
        attributes["Specification-Title"] = rootProject.name
        attributes["Specification-Version"] = version
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] =
            providers.environmentVariable("COMMIT_SHA_SHORT")
                .map { "${version}-${it}" }
                .getOrElse(version.toString())
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}

val includeDependenciesInJar = project.findProperty("fatJar")?.toString()?.toBooleanStrictOrNull() ?: true

val appJar = tasks.register<Jar>("appJar") {
    archiveClassifier.set("")
    archiveBaseName.set("bumenfeld-death-announcer")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    from(sourceSets.main.get().output)
    dependsOn(tasks.named("classes"))

    if (includeDependenciesInJar) {
        val runtimeClasspath = configurations.named("runtimeClasspath")
        inputs.files(runtimeClasspath)
        from({
            runtimeClasspath.get().files.flatMap { file ->
                when {
                    !file.exists() -> emptyList()
                    file.isDirectory -> listOf(file)
                    else -> listOf(zipTree(file))
                }
            }
        })
    }
}

tasks.named<Copy>("processResources") {
    // UI assets are loaded from resources/Common/UI/Custom as documented.
}

val deployOutputPath = providers.gradleProperty("deployOutputPath").orNull?.takeIf { it.isNotBlank() }
val fallbackDeployDirectory = layout.buildDirectory.dir("deploy").get().asFile

val deployJar = tasks.register<Copy>("deployJar") {
    group = "build"
    description = "Copy the packaged plugin jar to the directory provided by -PdeployOutputPath."
    dependsOn(appJar)
    onlyIf { deployOutputPath != null }
    from(appJar.flatMap { it.archiveFile })
    into(deployOutputPath?.let(::file) ?: fallbackDeployDirectory)
    doFirst {
        val jarFile = appJar.get().archiveFile.get().asFile
        logger.lifecycle("Copying {} -> {}", jarFile.absolutePath, deployOutputPath ?: fallbackDeployDirectory)
    }
}

tasks.named("build") {
    dependsOn(appJar)
}

tasks.register("release") {
    dependsOn(appJar)
}

tasks.named("assemble") {
    dependsOn(appJar)
}

listOf("build", "release", "assemble").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn(deployJar)
    }
}
tasks.named("jar") {
    enabled = false
}

tasks.findByName("sourcesJar")?.let { it.enabled = false }

publishing {
    repositories {}
    publications {
        create<MavenPublication>("maven") {
            artifact(appJar) {
                classifier = null
            }
        }
    }
}
