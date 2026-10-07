plugins {
    id("java-library")
    id("io.freefair.lombok") version "9.0.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.nightexpressdev.com/releases") {
        content { includeGroupByRegex("su\\.nightexpress.*") }
    }
    maven("https://repo.rosewooddev.io/repository/public/") {
        content { includeGroup("org.black_ixx") }
    }
    maven("https://jitpack.io") {
        content { includeGroup("com.github.MilkBowl") }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.j256.ormlite:ormlite-jdbc:6.1")
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("org.apache.logging.log4j:log4j-core:2.25.3") { isTransitive = false }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    compileOnly("su.nightexpress.excellenteconomy:ExcellentEconomy:2.8.0") { isTransitive = false }
    compileOnly("org.black_ixx:playerpoints:3.3.4") { isTransitive = false }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

lombok {
    version = "1.18.42"
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
