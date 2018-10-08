import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.apache.tools.ant.filters.*
import org.jetbrains.kotlin.contracts.model.structure.UNKNOWN_COMPUTATION.type
import sun.tools.jar.resources.jar

val kotlin_version = "1.2.71"

group = "com.rnett.daogen"
version = "2.0.0-beta"

plugins {
    java
    kotlin("jvm") version "1.2.71"
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version")
    implementation("com.github.cesarferreira:kotlin-pluralizer:0.2.9")
    implementation("org.postgresql:postgresql:42.2.5")
    implementation("no.tornado:tornadofx:1.7.16")
    implementation("com.github.salomonbrys.kotson:kotson:2.5.0")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val jars = mapOf(
        "daogen" to "com.rnett.daogen.MainKt",
        "ExposedDaoGenerator" to "com.rnett.daogen.app.DaogenApp"
)


val jarTasks = jars.map {
    task("${it.key}FatJar", type = Jar::class) {
        baseName = it.key
        group = "executables"
        version = ""

        manifest {
            attributes["Implementation-Title"] = "Jar for ${it.key}"
            attributes["Implementation-Version"] = version
            attributes["Main-Class"] = it.value
        }

        from(configurations.runtimeClasspath.map { file -> if (file.isDirectory) file else zipTree(file) })
        with(tasks["jar"] as CopySpec)
    }
}
val copyExecs = tasks.create("copyExecs", Copy::class.java) {
    from("/build/libs/")
    into("libs")

    include {
        it.name.matches("[A-z]*.jar".toRegex())
    }
    group = "executables"
}

tasks {
    "build" {
        jarTasks.forEach {
            dependsOn(it)
        }
        dependsOn(copyExecs)
    }
}


