# Protected release builds

Run from the repository root with Java 25:

- Fabric: `gradlew.bat -I scripts/obfuscate.gradle :26.2:obfuscateJar`
- NeoForge: `gradlew.bat -I scripts/obfuscate.gradle :neoforge:26.2:obfuscateJar`

The protected JAR is in the target project's `build/protected/`.
Run `checkProtectedLoad` instead of `obfuscateJar` to also verify metadata and JVM class linkage.
This smoke test includes optional compile-time integrations (ModMenu, animation library).
Private crash mappings and kept-symbol reports are in `build/private-obfuscation/`.
Archive these with the precise version and JAR SHA-256. Do not include them in the distributed JAR.
Regular development builds and their tests remain unchanged.

This pass renames implementation classes/methods, flattens packages and strips local debug names.
Mixins, loader entrypoints, native/reflection and serialization contracts stay stable.
It is not string encryption, control-flow virtualization, DRM, or a guarantee against reverse engineering.
Before publication, test the protected JAR (not the development JAR) in each loader:
login, cosmetics/skins, notifications/PMs, embedded music/auth, disconnect and foreign/singleplayer gates.

Keep upstream LGPL-3.0 notices and comply with applicable corresponding-source obligations.
Obfuscation does not replace licensing obligations or grant exclusive rights to upstream code.
