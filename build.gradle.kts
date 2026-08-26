plugins {
    java
    id("net.neoforged.moddev") version "2.0.144"
}

version = property("mod.version") as String
group = property("mod.group") as String

base {
    archivesName = property("mod.archive_name") as String
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    withSourcesJar()
}

repositories {
    mavenCentral()

    maven("https://maven.neoforged.net/releases") {
        name = "NeoForge"
    }

    maven("https://maven.sinytra.org") {
        name = "Sinytra"
    }

    maven("https://jitpack.io") {
        name = "JitPack"
    }

    exclusiveContent {
        forRepository {
            maven("https://api.modrinth.com/maven") {
                name = "Modrinth"
            }
        }

        filter {
            includeGroup("maven.modrinth")
        }
    }
}

neoForge {
    version = property("neo_version") as String

    runs {
        create("client") {
            client()
        }

        create("server") {
            server()
        }

        create("data") {
            data()
        }
    }

    mods {
        create(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation("maven.modrinth:AANobbMI:${property("sodium_version")}")

    compileOnly(
        "com.github.bawnorton.mixinsquared:mixinsquared-common:${property("mixinsquared_version")}"
    )
    annotationProcessor(
        "com.github.bawnorton.mixinsquared:mixinsquared-common:${property("mixinsquared_version")}"
    )

    implementation(
        "com.github.bawnorton.mixinsquared:mixinsquared-neoforge:${property("mixinsquared_version")}"
    )
    jarJar(
        "com.github.bawnorton.mixinsquared:mixinsquared-neoforge:${property("mixinsquared_version")}"
    )

}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val metadataProperties = mapOf(
        "license" to project.property("mod.license"),
        "github" to project.property("mod.github"),
        "name" to project.property("mod.name"),
        "id" to project.property("mod.id"),
        "modversion" to project.property("mod.version"),
        "display_name" to project.property("mod.display_name"),
        "author" to project.property("mod.author"),
        "description" to project.property("mod.description"),
        "namespace" to project.property("mod.namespace"),
        "mc" to "[${project.property("minecraft_version")}]"
    )

    inputs.properties(metadataProperties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(metadataProperties)
    }
}
