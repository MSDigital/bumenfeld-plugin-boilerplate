import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Jar
import java.io.ByteArrayOutputStream
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
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jspecify)

    implementation(libs.snakeyaml)
    implementation(libs.jackson.databind)

    val enableHytalePlugin = project.findProperty("enable_hytale_plugin")?.toString()?.toBooleanStrictOrNull() ?: false
    if (enableHytalePlugin) {
        compileOnly("com.hypixel.hytale:Server:latest.release")
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
    archiveBaseName.set("bumenfeld-boilerplate")
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

tasks.named("build") {
    dependsOn(appJar)
}

tasks.register("release") {
    dependsOn(appJar)
}

tasks.named("assemble") {
    dependsOn(appJar)
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
