/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

plugins {
    jacoco
    kotlin("jvm")
    id("org.jetbrains.dokka")
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

// Every public declaration must carry KDoc. Dokka reports an undocumented one as a warning, a warning fails the
// HTML publication, and the publication is part of `check`, so missing documentation fails the same gate as a
// failing test.
dokka {
    dokkaPublications.html {
        failOnWarning.set(true)
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    }
    dokkaSourceSets.configureEach {
        reportUndocumented.set(true)
    }
}

tasks.check { dependsOn(tasks.dokkaGenerateHtml) }
