/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

plugins {
    jacoco
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

jacoco {
    toolVersion = "0.8.15"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(false)
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(project(":modules:compiler"))
    implementation(project(":modules:document"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.components:components-resources:1.11.0")
    implementation("org.jetbrains.compose.material:material:1.11.0")

    testImplementation(kotlin("test"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.progmasoft.xide.app.MainKt"

        nativeDistributions {
            packageName = "Xide"
            packageVersion = project.version.toString()
            description = "Visual X# integrated development environment"
            vendor = "Progmasoft"
        }
    }
}
