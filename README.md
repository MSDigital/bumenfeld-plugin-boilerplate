# Bumenfeld Java Boilerplate

This repository is a small Java/Kotlin Gradle template that bundles common tooling for future Hytale-related projects. It keeps the modern build logic (version resolution, fat JAR creation, and toolchain control) while leaving the actual product code up to you.

## Getting started

1. `git clone https://github.com/your-org/bumenfeld-plugin-boilerplate.git new-plugin` and `cd new-plugin`. Remove the inherited Git metadata (`rm -rf .git` or `rmdir /s /q .git` on Windows) and run `git init`, then point the repo at your own remote so pushes go to your plugin project.
2. Update `settings.gradle.kts`/`gradle.properties` with your project name, `project_version`, and plugin metadata (group, author, description) so artifacts carry the right identity.
3. Edit `src/main/resources/manifest.json` to describe your plugin (`Title`, `Version`, `Description`, `MainEntryPoint`) and copy it into `mods/<your-plugin-id>/manifest.json` along with any other runtime assets.
4. Copy `src/main/resources/config/example.yml` and `src/main/resources/localization/en.json` into your plugin data folder (`mods/<your-plugin-id>/config.yml` and `mods/<your-plugin-id>/localization/en.json`) and modify the content to reflect your plugin’s behavior.
5. Decide if this plugin should include the Hytale API (`-Penable_hytale_plugin=true`) or keep the lightweight boilerplate defaults. Adjust the GitHub workflows by uncommenting their `on:` blocks when you want CI/release automation, then run `./gradlew clean release` to produce the jar you’ll ship.

Sample resource files live under `src/main/resources`. `manifest.json` holds the metadata placeholders that every plugin must replace, while `config/example.yml` and `localization/en.json` provide a paired hello-world config/localization example—keep them aligned whenever you customize them.

## Customizing the template

1. Replace `src/main/java/com/bumenfeld/Application.java` with your actual entry point logic.
2. Update `gradle/libs.versions.toml` and `build.gradle.kts` with the dependencies and tasks your plugin needs.
3. Adjust `project_version` (or pass `-PlocalVersion=<value>`) whenever you want to control versioning for a release.
4. Rename `settings.gradle.kts` or add subprojects if your plugin introduces additional modules.

## Build & release

- `./gradlew clean release` or `./gradlew.bat clean release` (Windows) performs a clean build, packaging the fat jar at `build/libs/bumenfeld-boilerplate-<version>.jar`. Releases now publish the jar alone instead of a ZIP.
- `./gradlew build` still works and ensures the `appJar` task runs before `build`.

You can toggle fat-jar bundling via the `fatJar` property (default `true`). Use `./gradlew build -PfatJar=false` if you prefer to ship a thin jar and manage dependencies separately.

> **Note:** The GitHub workflows currently run only on `workflow_dispatch`. Uncomment and customize their `on:` sections (push/pull_request/release) when you are ready to enable automated CI/releases for your plugin.

## Next steps

1. Add runtime resources under `src/main/resources` (configs, localization, assets) once your plugin behavior is defined.
2. Revisit the `release` task if you need to ship extra artifacts.
3. Extend the CI workflows in `.github/workflows/` when you integrate automated builds or publishing.
