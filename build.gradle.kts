plugins {
    id("java")
    id("io.github.pgatzka.docker") version "1.0.0"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.flywaydb.flyway") version "11.14.1"
    id("org.jooq.jooq-codegen-gradle") version "3.19.32"
    id("com.apollographql.apollo") version "4.4.3"
    id("io.freefair.lombok") version "9.5.0"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("com.google.guava:guava:33.6.0-jre")
    runtimeOnly("org.postgresql:postgresql")
    jooqCodegen("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-batch-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jooq-test")
    testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.apollographql.java:client:0.0.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:11.14.1")
    }
}

val codegenDatabase: String = "gunsmith_codegen"
val codegenUser: String = "gunsmith_codegen"
val codegenPassword: String = "gunsmith_codegen"
val codegenPort: Int = 15432
val codegenUrl: String = "jdbc:postgresql://localhost:${codegenPort}/${codegenDatabase}"

docker {
    volumes {
        register("gunsmith_codegen-postgres_data") {}
    }
    containers {
        register("gunsmith_codegen-postgres") {
            image.set("postgres:18-alpine")
            ports.set(mapOf(codegenPort to 5432))
            environment.set(
                mapOf(
                    "POSTGRES_DB" to codegenDatabase,
                    "POSTGRES_USER" to codegenUser,
                    "POSTGRES_PASSWORD" to codegenPassword
                )
            )
            mounts {
                volume("gunsmith_codegen-postgres_data", "/var/lib/postgresql")
            }
            wait.set(io.github.pgatzka.docker.dsl.waitable.Waitable.logLine(
                ".*\\[1\\] LOG.*database system is ready to accept connections.*"
            ))
        }
    }
}

flyway {
    url = codegenUrl
    user = codegenUser
    password = codegenPassword
}


jooq {
    configuration {
        jdbc {
            url = codegenUrl
            user = codegenUser
            password = codegenPassword
        }
        generator {
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                excludes = "flyway_schema_history"
                catalogVersionProvider = "select max(version) from flyway_schema_history"
                schemaVersionProvider = "select max(version) from flyway_schema_history"
            }
            generate {
                fluentSetters = true
                pojos = true
            }
            strategy {
                matchers {
                    tables {
                        table {
                            tableClass {
                                transform = org.jooq.meta.jaxb.MatcherTransformType.PASCAL
                                expression = "$0_Table"
                            }
                        }
                    }
                }
            }
            target {
                packageName = "${group}.gunsmith.jooq"
            }
        }
    }
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
    flywayMigrate {
        dependsOn("startGunsmithCodegenPostgres")
        finalizedBy("stopGunsmithCodegenPostgres")
    }
    jooqCodegen {
        dependsOn(flywayMigrate)
        finalizedBy("stopGunsmithCodegenPostgres")
    }
    compileJava {
        dependsOn(jooqCodegen)
    }
    "startGunsmithCodegenPostgres" {
        finalizedBy("stopGunsmithCodegenPostgres")
    }
    "removeVolumeGunsmithCodegenPostgresData" {
        dependsOn("removeContainerGunsmithCodegenPostgres")
    }
    clean {
        dependsOn("removeVolumeGunsmithCodegenPostgresData")
    }
    "generateServiceApolloSources" {
        dependsOn("downloadServiceApolloSchemaFromIntrospection")
    }
}