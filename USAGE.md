# Using the Bumenfeld Hytale Plugin Boilerplate

This repository is a reusable starting point for internal Hytale server plugins. Instead of accepting open contributions, we keep this template pristine and document the usage steps that every new plugin should follow.

## 1. Project metadata
- Replace the placeholders in `gradle.properties` (`plugin_*`, `project_version`, etc.) with the values for the plugin you are building.
- Copy `src/main/resources/manifest.json` into `mods/<your-plugin-id>/manifest.json` and update the title, version, description, and other metadata before packaging.

## 2. Configuration & localization
- `config/example.yml` and `localization/en.json` are paired hello-world samples. Edit them together so the config keys you expose have matching localization keys, then copy them into your plugin data folder.
- Add new locale files beside `en.json` whenever you support additional languages.

## 3. Enabling the Hytale API
- Set `-Penable_hytale_plugin=true` or update `gradle.properties` to bring back `com.hypixel.hytale:Server` when a plugin needs the API.
- Implement the plugin-specific listeners, event systems, and manifest entries only after the dependency is enabled so you don’t bundle unused code.

## 4. Workflows & releases
- Uncomment and adjust the `on:` blocks inside `.github/workflows/gradle.yml` and `.github/workflows/release.yml` once you are ready for CI or automated publishing.
- Verify the release workflow points at the artifact (`build/libs/*.jar` by default) you intend to ship before tagging releases.

## 5. Testing
- Run `./gradlew clean release` with the desired `fatJar`/`enable_hytale_plugin` settings before you publish a plugin build.
- Document any plugin-specific manual tests in this repo’s `docs/` folder or within the plugin project so the verification steps are easy to follow.
