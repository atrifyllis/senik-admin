import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDepManagement)
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinPluginSpring)
    alias(libs.plugins.kotlinPluginJpa)
    alias(libs.plugins.gradlePrettyLogger)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.kotlinKapt)
    alias(libs.plugins.openapi)
    alias(libs.plugins.versionChecker)
    alias(libs.plugins.kover)
    //    alias(libs.plugins.native)
//    alias(libs.plugins.jooqDocker)

    alias(libs.plugins.gitProperties) // expose git info under /actuator/info

    jacoco
}
group = "gr.senik"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

extra["testcontainersVersion"] = "1.18.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")
//    implementation("org.springframework.boot:spring-boot-starter-jooq")


    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

//    implementation("org.flywaydb:flyway-core")
    implementation("org.postgresql:postgresql")

    implementation(libs.oktaSpring)
    implementation(libs.hibernateTypes)
    implementation(libs.kotlinLogging)
    implementation(libs.mapStruct)
//    implementation(libs.jooq)

    implementation(libs.bundles.jmolecules)
    implementation(libs.bundles.openApi)

//    implementation(libs.bundles.ff4j)

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation(libs.micrometerDatasource)
    implementation(libs.bundles.micrometerTracing)
    // For Latency Visualization
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")

    implementation(libs.lokiLogbackAppender)

//    jooqCodegen("org.postgresql:postgresql")


    // check settings.gradle.kts to see how we import this module. it is a composite build, so we use it here like a normal library dependency!
    implementation("gr.alx:common")


    runtimeOnly("org.postgresql:postgresql")
//    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
//    annotationProcessor("org.projectlombok:lombok")
    kapt(libs.mapStructProcessor)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter")
//    testImplementation("org.testcontainers:postgresql")
    testImplementation(libs.kafkaTestContainers)
    testImplementation(libs.bundles.archUnit)
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}


tasks.test {
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true) // enable xml reports required by sonarqube
    }
}

tasks.sonarqube {
    dependsOn(tasks.test)
}

// expose build info under /actuator/info
springBoot {
    buildInfo()
}
