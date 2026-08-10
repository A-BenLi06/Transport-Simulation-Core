# Build

> How to build, test, and develop Transport Simulation Core. For runtime usage of the
> resulting jar see [RUNNING.md](RUNNING.md); for the schema-driven code generation flow
> see [SCHEMA.md](SCHEMA.md).

## Prerequisites

| Tool    | Version                                                                                                                                                                                                                                                                                                             |
|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| JDK     | **21** — pinned via `java.toolchain.languageVersion = 21` in [`build.gradle.kts`](../build.gradle.kts) and `buildSrc/build.gradle.kts`. Gradle can provision a matching compile toolchain, but this repo's build logic also runs on JVM 21 (for `buildSrc`), so running Gradle itself on older JVMs can still fail. |
| Node.js | A current LTS that supports the Angular 21 toolchain.                                                                                                                                                                                                                                                               |
| npm     | Bundled with Node.                                                                                                                                                                                                                                                                                                  |
| Gradle  | Use the wrapper (`./gradlew` / `gradlew.bat`). Don't install Gradle globally.                                                                                                                                                                                                                                       |

## Backend (Gradle)

All commands run from the repository root.

```bash
./gradlew build                            # compile, run tests, package the jar
./gradlew shadowJar                        # package the relocated runtime/fat jar
./gradlew test                             # JUnit 5 only
./gradlew clean build
./gradlew publishToMavenLocal              # publish a local Maven artifact (group org.mtr)
```

Outputs land in `build/libs/`:

- `Transport-Simulation-Core-<version>.jar` — the relocated runnable fat jar produced by
  `shadowJar` (`Main-Class: org.mtr.core.Main`). The plain `jar` task is not suitable for
  embedding Core classes into an MTR mod JAR because dependency package names are not relocated.
- `Transport-Simulation-Core-<version>-sources.jar`
- `Transport-Simulation-Core-<version>-javadoc.jar`

`<version>` is derived from `upstream_mtr_version`, `minecraft_version`, and
`modification_version` in [`gradle.properties`](../gradle.properties). The `generateVersion`
task rewrites [`src/main/VersionTemplate.java`](../src/main/VersionTemplate.java) and
[`website/version-template.txt`](../website/version-template.txt) into `Version.java` and
`version.ts` respectively, so the running server and the bundled UI report the same version.

Yunniverse performance builds use `<upstream-mod-version>-mc<minecraft-version>-<iteration>`.
For example, `4.1.0-beta.2-mc1.21.1-yunniverse-perf-v4` identifies the upstream MTR release,
the target Minecraft version, and the downstream performance iteration without changing the
MTR mod ID.

When embedding the relocated Core classes into the NeoForge MTR shell, pass the same version
to the assembler. It updates and verifies `META-INF/neoforge.mods.toml` as part of the
allowlisted patch instead of relying on the output filename:

```powershell
.\tools\assemble-mtr-core-patch.ps1 `
  -JarTool <path-to-jar.exe> `
  -CoreJar .\build\libs\Transport-Simulation-Core-4.1.0-beta.2-mc1.21.1-yunniverse-perf-v4.jar `
  -OriginalMtrJar <path-to-original-mtr.jar> `
  -ModVersion 4.1.0-beta.2-mc1.21.1-yunniverse-perf-v4 `
  -OutputJar .\build\MTR-4.1.0-beta.2-mc1.21.1-yunniverse-perf-v4.jar
```

### Schema-driven code generation

```bash
./gradlew generateSchemaClasses  # regenerate Java + TypeScript schema classes
./gradlew setupWebserver         # package Angular app into WebserverResources
```

These tasks read JSON schemas from `buildSrc/src/main/resources/schema/<area>/` and write
generated Java / TypeScript into `src/main/java/org/mtr/core/generated/`,
`src/main/java/org/mtr/legacy/generated/`, and `website/src/app/entity/generated/`.
All output directories are git-ignored. See [SCHEMA.md](SCHEMA.md) for the schema mini-language.

`setupWebserver` packages the built Angular app into the `WebserverResources` Java class
served at `/`. This means the website must be built (`cd website && npm run build`) before
this Gradle task if you want the dashboard bundled into the jar.

The full pre-release sequence is therefore:

```bash
cd website && npm install && npm run build && cd ..
./gradlew generateSchemaClasses setupWebserver
./gradlew clean build
```

## Frontend (Angular)

All commands run from `website/`.

```bash
npm install                # one-off / after dependency changes
npm start                  # ng serve --host 0.0.0.0 — dev server on http://localhost:4200
npm run watch              # ng lint && ng build --watch --configuration development
npm run build              # ng lint && ng build --base-href a — production build
npm run lint               # ng lint
npm test                   # ng test
```

The dev server hits the same `/mtr/api/map/*` endpoints exposed by the running Java jar; in
development you'll typically run the jar on its default port and let the Angular dev server
proxy XHRs to it.

`npm run update-all` upgrades all npm dependencies (and `baseline-browser-mapping`) to
their latest compatible versions — useful, but always re-run `npm run build` and the lint /
type-check pipeline after it.

## Project structure

```
Transport-Simulation-Core/
├── build.gradle.kts         ← top-level Gradle script
├── settings.gradle.kts
├── gradle.properties        ← project version etc.
├── buildSrc/                ← Gradle plugin: schema generator + webserver-resources packer
│   └── src/main/resources/schema/<area>/*.json   ← authoring schemas (see SCHEMA.md)
├── src/
│   ├── main/
│   │   ├── VersionTemplate.java                  ← rewritten to Version.java each build
│   │   ├── java/org/mtr/core/                    ← backend source
│   │   └── resources/log4j2.properties
│   └── test/java/org/mtr/core/                   ← JUnit 5 tests
├── website/                 ← Angular 21 dashboard
│   ├── package.json
│   └── src/app/{component,service,pipe,entity,...}/
└── docs/                    ← this folder
```

## Continuous integration

See [`.github/workflows/build.yml`](../.github/workflows/build.yml) for the CI configuration.
The workflow builds the full stack (frontend → schema generation → Gradle build with tests)
and publishes to GitHub Packages on every push.
