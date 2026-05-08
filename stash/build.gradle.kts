plugins {
    id("java")
    id("io.github.pgatzka.docker") version "1.0.0"
    id("io.freefair.lombok") version "9.5.0"
    id("org.springframework.boot") version "4.0.6"
    id("com.apollographql.apollo") version "4.4.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.pgatzka"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")
    implementation("com.apollographql.java:client:0.0.2")
    implementation("com.google.guava:guava:33.6.0-jre")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}

val schema = layout.buildDirectory.file("schema.json")

apollo {
    service("service") {
        generateKotlinModels.set(false)
        packageName.set("io.github.pgatzka.gunsmith.apollo")
        schemaFile.set(schema)
        introspection {
            endpointUrl.set("https://api.tarkov.dev/graphql")
            schemaFile.set(schema)
        }
    }
}

tasks {
    "generateServiceApolloSources" {
        dependsOn("downloadServiceApolloSchemaFromIntrospection")
    }
    bootJar {
        manifest {
            attributes("Implementation-Version" to project.version)
        }
    }
}
