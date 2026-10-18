import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.danielriverolosa.gradle"
version = providers.gradleProperty("version").orElse("0.1.0").get()

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
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
    plugins {
        register("affectedModules") {
            id = "com.danielriverolosa.affected-modules"
            implementationClass = "com.danielriverolosa.gradle.affected.AffectedModulesPlugin"
            displayName = "Affected Module Detector Plugin"
            description = "Gradle 9 Configuration-Cache and Build-Cache safe affected module task selector."
        }
    }
}

publishing {
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
