package com.danielriverolosa.gradle.affected

import org.junit.Test
import kotlin.test.assertEquals

class AffectedModuleCalculatorTest {

    private val moduleDirs = mapOf(
        ":app" to "app",
        ":domain" to "domain",
        ":data" to "data",
        ":core:domain" to "core/domain",
        ":core:data" to "core/data",
        ":core:ui" to "core/ui",
        ":feature:home" to "feature/home",
        ":feature:alarms" to "feature/alarms",
        ":feature:settings" to "feature/settings",
    )

    private val reverseDependencies = mapOf(
        ":core:domain" to setOf(":domain", ":core:data", ":data", ":feature:home", ":feature:alarms", ":feature:settings", ":app"),
        ":domain" to setOf(":data", ":feature:alarms", ":feature:settings", ":app"),
        ":core:data" to setOf(":data", ":app"),
        ":data" to setOf(":app"),
        ":core:ui" to setOf(":feature:home", ":feature:alarms", ":feature:settings", ":app"),
        ":feature:home" to setOf(":app"),
        ":feature:alarms" to setOf(":app"),
        ":feature:settings" to setOf(":app"),
        ":app" to emptySet(),
    )

    private val defaultIgnoredPaths = listOf("docs/", ".md", ".gitignore", ".github/")
    private val defaultGlobalPaths = listOf(
        "build-logic/",
        "gradle/libs.versions.toml",
        "gradle/wrapper/",
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
    )

    @Test
    fun `when only ignored documentation files change then zero modules are affected`() {
        val result = AffectedModuleCalculator.calculate(
            changedFiles = listOf("README.md", "CODING_STANDARDS.md", "docs/ROADMAP.md", ".gitignore"),
            moduleDirs = moduleDirs,
            reverseDependencies = reverseDependencies,
            ignoredPaths = defaultIgnoredPaths,
            globalPaths = defaultGlobalPaths,
        )

        assertEquals(emptySet(), result.affectedTestModules)
        assertEquals(emptySet(), result.affectedAndroidTestModules)
        assertEquals(emptySet(), result.affectedScreenshotModules)
        assertEquals(emptySet(), result.affectedLintModules)
    }

    @Test
    fun `when global build configuration changes then all modules are affected`() {
        val result = AffectedModuleCalculator.calculate(
            changedFiles = listOf("gradle/libs.versions.toml"),
            moduleDirs = moduleDirs,
            reverseDependencies = reverseDependencies,
            ignoredPaths = defaultIgnoredPaths,
            globalPaths = defaultGlobalPaths,
        )

        assertEquals(moduleDirs.keys, result.affectedTestModules)
        assertEquals(moduleDirs.keys, result.affectedAndroidTestModules)
        assertEquals(moduleDirs.keys, result.affectedScreenshotModules)
        assertEquals(moduleDirs.keys, result.affectedLintModules)
    }

    @Test
    fun `when build-logic file changes then all modules are affected`() {
        val result = AffectedModuleCalculator.calculate(
            changedFiles = listOf("build-logic/convention/src/main/java/FeaturePlugin.kt"),
            moduleDirs = moduleDirs,
            reverseDependencies = reverseDependencies,
            ignoredPaths = defaultIgnoredPaths,
            globalPaths = defaultGlobalPaths,
        )

        assertEquals(moduleDirs.keys, result.affectedTestModules)
        assertEquals(moduleDirs.keys, result.affectedAndroidTestModules)
        assertEquals(moduleDirs.keys, result.affectedScreenshotModules)
        assertEquals(moduleDirs.keys, result.affectedLintModules)
    }

    @Test
    fun `when only src test in a module changes then only unit and screenshot tests for that module are affected`() {
        val result = AffectedModuleCalculator.calculate(
            changedFiles = listOf("data/src/test/java/com/locationalarm/data/dao/LocationAlarmDaoTest.kt"),
            moduleDirs = moduleDirs,
            reverseDependencies = reverseDependencies,
            ignoredPaths = defaultIgnoredPaths,
            globalPaths = defaultGlobalPaths,
        )

        assertEquals(setOf(":data"), result.affectedTestModules)
        assertEquals(setOf(":data"), result.affectedScreenshotModules)
        assertEquals(emptySet(), result.affectedAndroidTestModules)
        assertEquals(emptySet(), result.affectedLintModules)
    }

    @Test
    fun `when only src androidTest in a module changes then only androidTest for that module is affected`() {
        val result = AffectedModuleCalculator.calculate(
            changedFiles = listOf("app/src/androidTest/java/com/locationalarm/AppInstrumentedTest.kt"),
            moduleDirs = moduleDirs,
            reverseDependencies = reverseDependencies,
            ignoredPaths = defaultIgnoredPaths,
            globalPaths = defaultGlobalPaths,
        )

        assertEquals(emptySet(), result.affectedTestModules)
        assertEquals(emptySet(), result.affectedScreenshotModules)
        assertEquals(setOf(":app"), result.affectedAndroidTestModules)
        assertEquals(emptySet(), result.affectedLintModules)
    }

    @Test
    fun `when leaf feature src main changes then only that feature and app are affected across all task types`() {
        val result = AffectedModuleCalculator.calculate(
            changedFiles = listOf("feature/settings/src/main/java/com/locationalarm/feature/settings/SettingsScreen.kt"),
            moduleDirs = moduleDirs,
            reverseDependencies = reverseDependencies,
            ignoredPaths = defaultIgnoredPaths,
            globalPaths = defaultGlobalPaths,
        )

        val expected = setOf(":feature:settings", ":app")
        assertEquals(expected, result.affectedTestModules)
        assertEquals(expected, result.affectedAndroidTestModules)
        assertEquals(expected, result.affectedScreenshotModules)
        assertEquals(expected, result.affectedLintModules)
    }

    @Test
    fun `when domain src main changes then domain and all direct and transitive dependents are affected`() {
        val result = AffectedModuleCalculator.calculate(
            changedFiles = listOf("domain/src/main/java/com/locationalarm/domain/model/LocationAlarm.kt"),
            moduleDirs = moduleDirs,
            reverseDependencies = reverseDependencies,
            ignoredPaths = defaultIgnoredPaths,
            globalPaths = defaultGlobalPaths,
        )

        val expected = setOf(":domain", ":data", ":feature:alarms", ":feature:settings", ":app")
        assertEquals(expected, result.affectedTestModules)
        assertEquals(expected, result.affectedAndroidTestModules)
        assertEquals(expected, result.affectedScreenshotModules)
        assertEquals(expected, result.affectedLintModules)
    }

    @Test
    fun `when nested module file changes it matches longest module directory prefix`() {
        val result = AffectedModuleCalculator.calculate(
            changedFiles = listOf("core/domain/src/test/java/com/locationalarm/core/domain/UseCaseTest.kt"),
            moduleDirs = moduleDirs,
            reverseDependencies = reverseDependencies,
            ignoredPaths = defaultIgnoredPaths,
            globalPaths = defaultGlobalPaths,
        )

        assertEquals(setOf(":core:domain"), result.affectedTestModules)
        assertEquals(emptySet(), result.affectedAndroidTestModules)
        assertEquals(emptySet(), result.affectedLintModules)
    }
}
