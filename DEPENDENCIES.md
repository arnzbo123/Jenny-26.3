# Fabric 26.3 dependency verification

Checked against publisher metadata on 2026-10-24:

| Component | Pinned version | Evidence |
| --- | --- | --- |
| Minecraft Java | 26.3 | [Official release](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3) |
| Java | 25 | [Fabric 26.3 example build](https://github.com/FabricMC/fabric-example-mod/blob/26.3/build.gradle) |
| Gradle | 9.6.0 | [Fabric 26.3 guidance](https://fabricmc.net/2026/09/15/263.html) |
| Fabric Loom | 1.17.21 | [Publisher Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml) |
| Fabric Loader | 0.20.1 | [26.3 loader metadata](https://meta.fabricmc.net/v2/versions/loader/26.3) |
| Fabric Loader JUnit (tests only) | 0.20.1 | [Publisher Maven releases](https://maven.fabricmc.net/net/fabricmc/fabric-loader-junit/), [testing guide](https://docs.fabricmc.net/develop/automatic-testing) |
| Fabric API | 0.165.0+26.3 | [Publisher Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml) |
| GeckoLib Fabric | 5.5.7 | [Official support table](https://wiki.geckolib.com/docs/geckolib5/), [publisher releases](https://modrinth.com/mod/geckolib/versions?g=26.3&l=fabric) |

Minecraft 26.x has unobfuscated Java names. This build uses `net.fabricmc.fabric-loom`, plain `implementation` dependencies, and no obsolete Yarn mappings. GeckoLib is resolved externally as `com.geckolib:geckolib-fabric-26.3:5.5.7`; the old embedded `software/bernie/geckolib3` source tree is not compiled or packaged.

The original mod ID `sexmod` is preserved. Changing the loader does not provide automatic conversion of 1.12.2 saves, inventories, entity state, or network packets.

## Local tools

Java and Gradle were downloaded into `.tooling/` only. No system installation was changed. The following downloaded archives matched publisher SHA-256 checksums:

- Temurin `OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip`: `00c847d804f4a78e9f04f2683faf14fed898535b177b7fc704486cb0284e9283`
- Gradle `gradle-9.6.0-bin.zip`: `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`

On this Windows host, run `./build-local.ps1 build` from PowerShell. It uses project-local Java, Gradle, caches and temporary files, restoring environment variables when finished. Ordinary Gradle daemon launch encountered a local Java socket failure on this host; the script runs Gradle in the current Java process. On another computer with Java 25 installed, use `./gradlew build` or `gradlew.bat build`.

This is a migration development project. Dependency resolution and compilation do not establish gameplay parity or in-game rendering correctness.

Registry-backed fire tests use Fabric Loader JUnit and Minecraft bootstrap without starting a server or creating a world. They cover the retained block ID and light, the inert scheduled tick, support removal, neighbor updates retaining the custom block and age, shared flammability, and waterlogged exclusions. These tests do not establish full world simulation behavior.
