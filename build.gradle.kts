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

neoForge {
    version = property("neo_version") as String

    parchment {
        mappingsVersion = property("parchment_mappings_version") as String
        minecraftVersion = property("parchment_minecraft_version") as String
    }

    accessTransformers.from("src/main/resources/META-INF/accesstransformer.cfg")

    runs {
        register("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        register("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        register("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        register(property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
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
