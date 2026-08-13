/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

plugins {
    base
    kotlin("jvm") version "2.4.10" apply false
}

allprojects {
    group = "org.progmasoft.xide"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}
