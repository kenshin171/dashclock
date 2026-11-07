/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
}

val versionProps = Properties().apply {
    load(FileInputStream(file("version.properties")))
}

android {
    namespace = "net.nurik.roman.dashclock"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "net.nurik.roman.dashclock"
        minSdk = 32
        targetSdk = 37

        versionName = versionProps.getProperty("name")
        versionCode = versionProps.getProperty("code").toInt()
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val localPropsFile = file("../local.properties")
    val localProps = Properties().apply {
        if (localPropsFile.exists()) {
            load(FileInputStream(localPropsFile))
        }
    }
    val keystorePropsPath = localProps.getProperty("keystore.props.file")
    val hasReleaseKeystore = !keystorePropsPath.isNullOrEmpty() && file(keystorePropsPath).exists()

    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                val keyProps = Properties().apply {
                    load(FileInputStream(file(keystorePropsPath!!)))
                }
                storeFile = file(keyProps.getProperty("store"))
                keyAlias = keyProps.getProperty("alias")
                storePassword = keyProps.getProperty("storePass")
                keyPassword = keyProps.getProperty("pass")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-project.txt")
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        create("publicBeta") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-project.txt")
            )
            versionNameSuffix = " Beta " + versionProps.getProperty("betaNumber")
        }

        create("publicDebug") {
            initWith(getByName("publicBeta"))
            matchingFallbacks += listOf("debug", "release")
            isDebuggable = true
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-project.txt")
            )
            versionNameSuffix = " Debug Beta " + versionProps.getProperty("betaNumber")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.gms:play-services-base:18.5.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.mobeta.android.dslv:drag-sort-listview:0.6.1") {
        exclude(group = "com.android.support")
    }
    implementation(project(":api"))
}
