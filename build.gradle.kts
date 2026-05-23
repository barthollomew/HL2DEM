import java.net.URI

plugins {
    application
    id("com.google.protobuf") version "0.9.4"
}

group = "hl2dem"
version = "1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

application {
    mainClass.set("hl2dem.Main")
}

repositories { mavenCentral() }

dependencies {
    implementation("com.google.protobuf:protobuf-java:3.25.3")
    implementation("org.xerial.snappy:snappy-java:1.1.10.5")
}

val protoOutDir = layout.buildDirectory.dir("proto/cs2")

val protoBaseUrl = "https://raw.githubusercontent.com/SteamDatabase/GameTracking-CS2/master/Protobufs"

// Core demo and network message protos needed for CS2 demo parsing.
val protoFilesToFetch = listOf(
    "demo.proto",
    "netmessages.proto",
    "networkbasetypes.proto",
    "cstrike15_usermessages.proto",
    "usermessages.proto",
    "gameevents.proto",
    "cs_usercmd.proto",
    "steammessages.proto",
    "clientmessages.proto",
    "connectionless_netmessages.proto",
    "networksystem_protomessages.proto",
    "valveextensions.proto",
    "network_connection.proto",
    "source2_steam_stats.proto",
    "usercmd.proto",
    "cstrike15_gcmessages.proto",
    "base_gcmessages.proto",
    "econ_gcmessages.proto",
    "gcsdk_gcmessages.proto",
    "gcsystemmsgs.proto",
    "engine_gcmessages.proto",
    "steammessages_gc.proto"
)

val fetchProtos by tasks.registering {
    outputs.dir(protoOutDir)
    doLast {
        val dir = protoOutDir.get().asFile
        dir.mkdirs()
        protoFilesToFetch.forEach { name ->
            val dest = File(dir, name)
            if (!dest.exists()) {
                try {
                    println("Fetching proto: $name")
                    URI("$protoBaseUrl/$name").toURL().openStream().use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    println("Warning: could not fetch $name (${e.message}) - skipping")
                }
            }
        }
    }
}

// Inject java_package and java_outer_classname into each proto so generated classes
// land in the valve.pb package and can be imported from named packages.
val patchProtos by tasks.registering {
    dependsOn(fetchProtos)
    inputs.dir(protoOutDir)
    outputs.dir(protoOutDir) // in-place patch; Gradle tracks modification times
    doLast {
        val dir = protoOutDir.get().asFile
        dir.listFiles()?.filter { it.name.endsWith(".proto") }?.forEach { f ->
            val text = f.readText()
            if (!text.contains("option java_package")) {
                val outerClass = f.nameWithoutExtension
                    .split("_").joinToString("") { w -> w.replaceFirstChar { it.uppercase() } }
                val inject = "option java_package = \"valve.pb\";\n" +
                             "option java_outer_classname = \"${outerClass}Proto\";\n"
                f.writeText(inject + text)
            }
        }
    }
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.3" }
}

sourceSets {
    main {
        proto {
            srcDir(protoOutDir)
        }
    }
}

tasks.named("generateProto") { dependsOn(patchProtos) }
tasks.named("processResources") { dependsOn(patchProtos) }

tasks.withType<JavaCompile> {
    options.release.set(21)
    options.compilerArgs.add("--enable-preview")
}

tasks.named<JavaExec>("run") {
    jvmArgs("--enable-preview")
}
