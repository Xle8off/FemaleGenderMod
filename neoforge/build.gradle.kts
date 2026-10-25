plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev")
    id("me.modmuss50.mod-publish-plugin")
}

val webrtcNatives by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    implementation("dev.onvoid.webrtc:webrtc-java:0.19.0") { isTransitive = false }
    jarJar("dev.onvoid.webrtc:webrtc-java:0.19.0") { isTransitive = false }
    for (platform in listOf("windows-x86_64", "windows-aarch64", "linux-x86_64", "linux-aarch64", "linux-aarch32", "macos-x86_64", "macos-aarch64")) {
        webrtcNatives("dev.onvoid.webrtc:webrtc-java:0.19.0:$platform") { isTransitive = false }
    }
    implementation("javazoom:jlayer:1.0.1")
    jarJar("javazoom:jlayer:1.0.1")
}
tasks.processResources {
    // Recovered common assets overlap generated locale files; prefer the loader's
    // own/generated resources, which are visited first.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(provider { webrtcNatives.files.map { zipTree(it) } }) {
        include("*.dll", "*.so", "*.dylib", "META-INF/licenses/**")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

sourceSets.main {
    resources.srcDir("src/generated/resources")
}

neoForge {
    version = sc.properties["dependencies.neo_version"]

    val common = sc.tree["common"]!!.project
    val at = common.file("src/main/resources/META-INF/accesstransformer.cfg")
    //Use the corresponding common project's StoneCutter to process the path, so that it points at the identical absolute path
    // and MDG is able to more reliably re-use the recompiled minecraft
    val commonProject = sc.node.sibling("common")!!.project
    accessTransformers.from(commonProject.sc.process(at, "build/dev.at").absolutePath)
    validateAccessTransformers = true

    mods {
        register(sc.properties["mod_id"]) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        configureEach {
            systemProperty("neoforge.enabledGameTestNamespaces", sc.properties["mod_id"])
            ideName = "NeoForge ${name.replaceFirstChar(Char::titlecase)} ($path)"
            gameDirectory = file("../../run")

            val forceAnsi = providers.gradleProperty("forge_force_ansi")
            if (forceAnsi.isPresent) {
                // Force ANSI if declared as a Gradle property, as the auto detection
                // doesn't detect IntelliJ properly or Eclipse's ANSI console plugin.
                systemProperties.put("terminal.ansi", forceAnsi.get())
            }
        }

        register("client") {
            client()
            devLogin = providers.gradleProperty("mc_devlogin")
                    .map(String::toBoolean)
                    .getOrElse(false)
        }
        register("clientAlt") {
            client()
            programArguments.addAll("--username", "AltDev")
            devLogin = false
        }

        register("data") {
            clientData()

            programArguments.addAll("--all", "--output", file("src/generated/resources").absolutePath,
                "--mod", sc.properties["mod_id"], "--existing", file("src/main/resources").absolutePath,
                "--existing", common.file("src/main/resources").absolutePath
            )
        }

        register("server") {
            server()
        }
    }
}

tasks.named("createMinecraftArtifacts") {
    dependsOn("stonecutterGenerate")
}

rootProject.tasks.named("runData").configure {
    dependsOn(tasks.named("runData"))
}

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)

listOf(
    "apiElements",
    "runtimeElements",
    "sourcesElements",
    "javadocElements"
).forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, sc.branch.project.property("loader") as String)
        }
    }
}

sourceSets.configureEach {
    listOf(
        compileClasspathConfigurationName,
        runtimeClasspathConfigurationName,
        getTaskName(null, "jarJar")
    ).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, sc.branch.project.property("loader") as String)
            }
        }
    }
}

publishMods {
    dryRun = performDryRun
    modrinth {
        from(modrinthOps, basePublishingOps)
        additionalFile(tasks.sourcesJar.flatMap { it.archiveFile }) { type.set(SOURCES_JAR) }
        additionalFile(tasks.javadocJar.flatMap { it.archiveFile }) { type.set(JAVADOC_JAR) }
    }
    curseforge {
        from(cfOps, basePublishingOps)
    }
}
