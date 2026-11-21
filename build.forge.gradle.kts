plugins {
    java
    id("net.minecraftforge.gradle") version "[7.0.17,8)"
}

version = "${sc.properties["mod.version"]}+${sc.current.version}"
base.archivesName = "tides-of-war-forge"

val requiredJava = JavaVersion.VERSION_25

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:${sc.properties["deps.forge"]}"))
}

minecraft {
    runs {
        configureEach {
            workingDir = rootProject.layout.projectDirectory.dir("run")
        }
        register("server") {
            args("--nogui")
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
            "forge_major" to sc.properties["deps.forge_major"],
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching("META-INF/mods.toml") { expand(props) }
        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
    }

    withType<Jar> {
        from("../../LICENSE") { rename { "${it}_tides-of-war" } }
    }

    register("printClasspath") {
        val cp = sourceSets.main.get().compileClasspath
        doLast { println("CLASSPATH=" + cp.asPath) }
    }
}
