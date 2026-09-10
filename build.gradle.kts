/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.1
 */

plugins {
    base
    kotlin("jvm") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.11.0" apply false
}

allprojects {
    group = "com.progmasoft.xide"
    version = "0.1.0"

    repositories {
        google()
        mavenCentral()
    }
}
