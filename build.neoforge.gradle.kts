import dev.kikugie.stonecutter.controller.task.StonecutterTaskMutexService

plugins {
    id("net.neoforged.moddev") version "2.0.148"
}

version = "${sc.properties["mod.version"]}+${sc.current.version}"
base.archivesName = "tides-of-war-neoforge"

val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

neoForge {
    version = sc.properties["deps.neo_loader"]

    mods {
        register("tidesofwar") {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("server") {
            gameDirectory = file("../../run/")
            server()
            if (System.getenv("TIDESOFWAR_SELFTEST") != null) {
                systemProperty("tidesofwar.selftest", "true")
            }
        }
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

    processResources {
        val props = mapOf(
            "id" to sc.properties["mod.id"],
            "name" to sc.properties["mod.name"],
            "version" to sc.properties["mod.version"],
            "minecraft" to sc.properties["mod.mc_compat"],
            "neo_loader" to sc.properties["deps.neo_loader"],
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
        exclude("fabric.mod.json", "META-INF/mods.toml")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
        usesService(StonecutterTaskMutexService.create(gradle, name))
    }

    withType<Jar> {
        from("../../LICENSE") { rename { "${it}_tides-of-war" } }
    }

    register("printClasspath") {
        val cp = sourceSets.main.get().compileClasspath
        doLast { println("CLASSPATH=" + cp.asPath) }
    }
}
