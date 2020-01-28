plugins {
    kotlin("multiplatform") version "1.3.61"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

kotlin {
    /* Targets configuration omitted. 
    *  To find out how to configure the targets, please follow the link:
    *  https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html#setting-up-targets */

    macosX64 {
        compilations {
            "main" {
                dependencies {
                    api("org.jetbrains.kotlinx:atomicfu-native:0.14.1")
                }
                defaultSourceSet {
                    kotlin.srcDir("src/main/kotlin")
                    resources.srcDir("src/main/resources")
                }
            }
            "test" {
                defaultSourceSet {
                    kotlin.srcDir("src/test/kotlin")
                    resources.srcDir("src/test/resources")
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }
}
