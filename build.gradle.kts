import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.1.1"
}

group = "com.danielriverolosa.gradle"
version = providers.gradleProperty("version").orElse("0.2.0").get()

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation(gradleTestKit())
}

tasks.validatePlugins {
    enableStricterValidation.set(true)
    failOnWarning.set(true)
}

gradlePlugin {
    website.set("https://github.com/danielriverolosa/affected-module-detector")
    vcsUrl.set("https://github.com/danielriverolosa/affected-module-detector.git")
    plugins {
        register("affectedModules") {
            id = "com.danielriverolosa.affected-modules"
            implementationClass = "com.danielriverolosa.gradle.affected.AffectedModulesPlugin"
            displayName = "Affected Module Detector Plugin"
            description = "Lightweight Gradle 9+ plugin that computes affected unit test, Roborazzi screenshot, androidTest, and Lint tasks across a multi-module dependency graph without breaking the Gradle Configuration Cache or Build Cache."
            tags.set(
                listOf(
                    "android",
                    "affected-modules",
                    "configuration-cache",
                    "build-cache",
                    "ci",
                    "monorepo",
                    "roborazzi",
                    "kotlin",
                )
            )
            compatibility {
                features {
                    configurationCache.set(true)
                }
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Affected Module Detector Gradle Plugin")
            description.set("Gradle 9+ Configuration-Cache and Build-Cache safe affected module task selector.")
            url.set("https://github.com/danielriverolosa/affected-module-detector")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("danielriverolosa")
                    name.set("Daniel Rivero Losa")
                    email.set("danielrl.drl@protonmail.com")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/danielriverolosa/affected-module-detector.git")
                developerConnection.set("scm:git:ssh://github.com:danielriverolosa/affected-module-detector.git")
                url.set("https://github.com/danielriverolosa/affected-module-detector")
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/danielriverolosa/affected-module-detector")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orNull
            }
        }
    }
}
