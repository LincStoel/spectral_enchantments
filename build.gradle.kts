plugins {
    id("java-library")
    id("net.neoforged.moddev") version "2.0.148"
}

version = property("mod_version") as String
group = property("mod_group_id") as String

base {
    archivesName = property("mod_id") as String
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

// Dev-only compat harness (see tools/compat/README.md): a second mod loaded by the
// compat runs alongside spectral_enchantments. It is never part of the jar.
val harness: SourceSet = sourceSets.create("harness")

neoForge {
    enable {
        version = property("neo_version") as String
        // Run the original Minecraft classes instead of recompiled sources: recompiling renumbers
        // lambdas, which breaks other mods' mixins (e.g. Accessories via The Aether) in dev runs.
        isDisableRecompilation = true
    }

    parchment {
        mappingsVersion = property("parchment_mappings_version") as String
        minecraftVersion = property("parchment_minecraft_version") as String
    }

    accessTransformers.from("src/main/resources/META-INF/accesstransformer.cfg")

    mods {
        register(property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
        register("spectral_enchantments_harness") {
            sourceSet(harness)
        }
    }

    runs {
        register("client") {
            client()
            loadedMods = setOf(mods["spectral_enchantments"])
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        register("server") {
            server()
            loadedMods = setOf(mods["spectral_enchantments"])
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        register("gameTestServer") {
            type = "gameTestServer"
            // Own directory, so the tests never pick up whatever client mods sit in run/mods.
            gameDirectory = layout.projectDirectory.dir("run-gametest")
            loadedMods = setOf(mods["spectral_enchantments"])
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        // Compat runs: game directories under run-compat/, mods copied there by tools/compat/setup.py.
        register("compatServer") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-compat/server")
            sourceSet = harness
            loadedMods = setOf(mods["spectral_enchantments"], mods["spectral_enchantments_harness"])
            programArgument("--nogui")
            jvmArguments.addAll("-Xms2G", "-Xmx6G")
        }
        for ((name, bot) in listOf("compatClient" to "Bot1", "compatClient2" to "Bot2")) {
            register(name) {
                client()
                gameDirectory = layout.projectDirectory.dir("run-compat/" + bot.lowercase())
                sourceSet = harness
                loadedMods = setOf(mods["spectral_enchantments"], mods["spectral_enchantments_harness"])
                programArguments.addAll("--quickPlayMultiplayer", "127.0.0.1:25565", "--username", bot,
                        "--width", "1280", "--height", "720")
                jvmArguments.addAll("-Xms2G", "-Xmx6G")
            }
        }
        register("compatGameTest") {
            type = "gameTestServer"
            gameDirectory = layout.projectDirectory.dir("run-compat/gametest")
            loadedMods = setOf(mods["spectral_enchantments"])
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
            jvmArguments.addAll("-Xms2G", "-Xmx6G")
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    addModdingDependenciesTo(harness)
}

dependencies {
    "harnessImplementation"(sourceSets.main.get().output)
}

val modMetadataProperties = listOf(
    "minecraft_version",
    "minecraft_version_range",
    "neo_version",
    "neo_version_range",
    "loader_version_range",
    "mod_id",
    "mod_name",
    "mod_license",
    "mod_version",
    "mod_authors",
    "mod_description",
).associateWith { project.property(it) as String }

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val replaceProperties = modMetadataProperties
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}

sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
