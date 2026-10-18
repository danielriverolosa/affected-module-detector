package com.danielriverolosa.gradle.affected

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AffectedModulesPluginTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `plugin computes affected tasks and reuses configuration cache in Gradle 9`() {
        val projectDir = tempFolder.root
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "sample"
            include(":domain", ":feature", ":app")
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.danielriverolosa.affected-modules")
            }
            """.trimIndent(),
        )

        val domainDir = projectDir.resolve("domain").apply { mkdirs() }
        domainDir.resolve("build.gradle.kts").writeText(
            """
            plugins { `java-library` }
            """.trimIndent(),
        )

        val featureDir = projectDir.resolve("feature").apply { mkdirs() }
        featureDir.resolve("build.gradle.kts").writeText(
            """
            plugins { `java-library` }
            dependencies { implementation(project(":domain")) }
            """.trimIndent(),
        )

        val appDir = projectDir.resolve("app").apply { mkdirs() }
        appDir.resolve("build.gradle.kts").writeText(
            """
            plugins { `java-library` }
            dependencies { implementation(project(":feature")) }
            """.trimIndent(),
        )

        // 1. Run with feature src/main change + configuration cache
        val firstRun = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "printAffectedTestTasks",
                "-PchangedFiles=feature/src/main/kotlin/Feature.kt",
                "--configuration-cache",
                "-q",
            )
            .build()

        assertEquals(":app:test :feature:test", firstRun.output.trim().lines().last().trim())

        // 2. Run again with same arguments -> configuration cache should be reused
        val secondRun = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "printAffectedTestTasks",
                "-PchangedFiles=feature/src/main/kotlin/Feature.kt",
                "--configuration-cache",
            )
            .build()

        assertEquals(TaskOutcome.SUCCESS, secondRun.task(":printAffectedTestTasks")?.outcome)
        assertTrue(secondRun.output.contains("Reusing configuration cache."))

        // 3. Docs-only change -> empty output
        val docsRun = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "printAffectedTestTasks",
                "-PchangedFiles=docs/ROADMAP.md,README.md",
                "-q",
            )
            .build()

        assertEquals("", docsRun.output.trim())
    }

    @Test
    fun `plugin detects git diff changes against baseRef and updates output when new files change`() {
        val projectDir = tempFolder.root
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "sample-git"
            include(":domain", ":feature", ":app")
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.danielriverolosa.affected-modules")
            }
            affectedModules {
                baseRef.set("master")
            }
            """.trimIndent(),
        )

        projectDir.resolve("domain").apply { mkdirs() }
            .resolve("build.gradle.kts").writeText("plugins { `java-library` }")
        projectDir.resolve("feature").apply { mkdirs() }
            .resolve("build.gradle.kts").writeText("plugins { `java-library` }\ndependencies { implementation(project(\":domain\")) }")
        projectDir.resolve("app").apply { mkdirs() }
            .resolve("build.gradle.kts").writeText("plugins { `java-library` }\ndependencies { implementation(project(\":feature\")) }")

        fun git(vararg args: String) {
            val process = ProcessBuilder(listOf("git") + args)
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $out" }
        }

        git("init", "-b", "master")
        git("config", "user.name", "Test")
        git("config", "user.email", "test@example.com")
        git("add", ".")
        git("commit", "-m", "Initial commit")

        git("checkout", "-b", "feature/branch")
        val appSrc = projectDir.resolve("app/src/main/kotlin").apply { mkdirs() }
        appSrc.resolve("App.kt").writeText("class App")
        git("add", ".")
        git("commit", "-m", "Modify app")

        val runAppOnly = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("printAffectedTestTasks", "--configuration-cache", "-q")
            .build()

        assertEquals(":app:test", runAppOnly.output.trim().lines().last().trim())

        // Now modify domain and commit -> should recompute to :app:test :domain:test :feature:test
        val domainSrc = projectDir.resolve("domain/src/main/kotlin").apply { mkdirs() }
        domainSrc.resolve("Domain.kt").writeText("class Domain")
        git("add", ".")
        git("commit", "-m", "Modify domain")

        val runDomain = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("printAffectedTestTasks", "--configuration-cache", "-q")
            .build()

        assertEquals(":app:test :domain:test :feature:test", runDomain.output.trim().lines().last().trim())
    }
}
