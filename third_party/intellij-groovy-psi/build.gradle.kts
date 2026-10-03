/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

// The Groovy lexer and parser of IntelliJ IDEA Community Edition, adapted to run on the IntelliJ core that the
// Kotlin embeddable compiler carries. UPSTREAM.md lists the source files, their commit and every change.
//
// The upstream sources are compiled as they are: this module does not apply the warning, documentation and
// coverage gates of the first-party modules, because those gates would require editing third-party code.

plugins {
    `java-library`
    kotlin("jvm")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
}

sourceSets {
    main {
        java.srcDir("src/adapter/java")
        resources.srcDir("src/adapter/resources")
    }
}

dependencies {
    // The IntelliJ core, PSI and lexer framework, relocated under org.jetbrains.kotlin.com.intellij.
    api("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
    // GeneratedParserUtilBase uses annotation members newer than the annotations the compiler depends on.
    compileOnly("org.jetbrains:annotations:26.0.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}
