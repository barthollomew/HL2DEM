import java.net.URI
import me.champeau.jmh.JmhBytecodeGeneratorTask

plugins {
    application
    id("com.google.protobuf") version "0.9.4"
    id("me.champeau.jmh") version "0.7.3"
}

group = "hl2dem"
version = "1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

application {
    mainClass.set("hl2dem.Main")
    applicationDefaultJvmArgs = listOf("--enable-preview")
}

repositories { mavenCentral() }

dependencies {
    implementation("com.google.protobuf:protobuf-java:3.25.3")
    implementation("org.xerial.snappy:snappy-java:1.1.10.5")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val protoOutDir = layout.buildDirectory.dir("proto/cs2")

val protoBaseUrl = "https://raw.githubusercontent.com/SteamDatabase/GameTracking-CS2/master/Protobufs"

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
            var text = f.readText()

            text = text.lines().filter { !it.contains("additional_includes") }.joinToString("\n")

            text = text.replace(Regex(",\\s*boxed_type\\s*=\\s*\"[^\"]*\""), "")
            text = text.replace(Regex("boxed_type\\s*=\\s*\"[^\"]*\"\\s*,?"), "")
            text = text.replace(Regex(",\\s*synthetic_default\\s*=\\s*\"[^\"]*\""), "")
            text = text.replace(Regex("synthetic_default\\s*=\\s*\"[^\"]*\"\\s*,?"), "")

            text = text.replace(Regex("\\[\\s*\\]"), "")

            if (!text.contains("option java_package")) {
                val outerClass = f.nameWithoutExtension
                    .split("_").joinToString("") { w -> w.replaceFirstChar { it.uppercase() } }
                val packageOpt = "option java_package = \"valve.pb\";\n"
                val classOpt = "option java_outer_classname = \"${outerClass}Proto\";\n"

                if (f.name == "valveextensions.proto") {
                    val target = "import \"google/protobuf/descriptor.proto\";\n"
                    if (text.contains(target)) {
                        text = text.replace(target, target + packageOpt + classOpt)
                    } else {
                        text = packageOpt + classOpt + text
                    }
                } else {
                    var inject = packageOpt + classOpt
                    if (!text.contains("import \"google/protobuf/descriptor.proto\";")) {
                        inject += "import \"google/protobuf/descriptor.proto\";\n"
                    }
                    if (!text.contains("import \"valveextensions.proto\";")) {
                        inject += "import \"valveextensions.proto\";\n"
                    }
                    text = inject + text
                }
            }
            f.writeText(text)
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
    // MemorySegment/Arena are preview APIs in Java 21 (finalized in Java 22 via JEP 454).
    // JMH benchmark source never references those APIs directly, so omitting the flag there
    // keeps the JMH class files at standard class version 65 (no preview marker).
    // The bytecode generator is configured separately to launch with --enable-preview so it
    // can still load the main/test class files that carry the preview flag.
    if (name != "compileJmhJava") {
        options.compilerArgs.add("--enable-preview")
    }
}

tasks.named<JavaExec>("run") {
    jvmArgs("--enable-preview")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
    testLogging {
        events("passed", "failed", "skipped")
    }
}

// The bytecode generator forks a plain JVM that needs --enable-preview to load class files
// compiled with preview features (MemorySegment/Arena are preview in Java 21, so main and
// test class files carry the preview flag). Also restrict scanning to JMH class files only
// — there are no @Benchmark annotations in main or test classes.
tasks.withType<JmhBytecodeGeneratorTask>().configureEach {
    jvmArgs.add("--enable-preview")
    classesDirsToProcess.setFrom(
        project.sourceSets.getByName("jmh").output.classesDirs
    )
}

jmh {
    warmupIterations.set(10)
    iterations.set(10)
    fork.set(1)
    timeUnit.set("ns")
    resultFormat.set("TEXT")
    resultsFile.set(project.file("${project.rootDir}/jmh-raw.txt"))
    jvmArgsAppend.add("--enable-preview")
}
