package com.danielriverolosa.gradle.affected

import java.io.Serializable

data class AffectedAnalysisResult(
    val affectedTestModules: Set<String>,
    val affectedAndroidTestModules: Set<String>,
    val affectedScreenshotModules: Set<String>,
    val affectedLintModules: Set<String>,
) : Serializable

object AffectedModuleCalculator {

    fun calculate(
        changedFiles: List<String>,
        moduleDirs: Map<String, String>,
        reverseDependencies: Map<String, Set<String>>,
        ignoredPaths: List<String>,
        globalPaths: List<String>,
    ): AffectedAnalysisResult {
        if (changedFiles.isEmpty()) {
            return AffectedAnalysisResult(
                affectedTestModules = emptySet(),
                affectedAndroidTestModules = emptySet(),
                affectedScreenshotModules = emptySet(),
                affectedLintModules = emptySet(),
            )
        }

        val sortedModulesByDirDepth = moduleDirs.entries
            .filter { it.value.isNotBlank() }
            .sortedByDescending { it.value.length }

        val directMainModules = linkedSetOf<String>()
        val directUnitTestOnlyModules = linkedSetOf<String>()
        val directAndroidTestOnlyModules = linkedSetOf<String>()
        val directScreenshotTestOnlyModules = linkedSetOf<String>()

        for (rawFile in changedFiles) {
            val file = rawFile.trim().replace('\\', '/').removePrefix("./")
            if (file.isEmpty()) continue

            if (isIgnored(file, ignoredPaths)) {
                continue
            }

            if (isGlobal(file, globalPaths)) {
                val allModules = moduleDirs.keys.toSortedSet()
                return AffectedAnalysisResult(
                    affectedTestModules = allModules,
                    affectedAndroidTestModules = allModules,
                    affectedScreenshotModules = allModules,
                    affectedLintModules = allModules,
                )
            }

            val owningModuleEntry = sortedModulesByDirDepth.firstOrNull { (_, dir) ->
                val normalizedDir = dir.trimEnd('/')
                file == normalizedDir || file.startsWith("$normalizedDir/")
            }

            if (owningModuleEntry != null) {
                val (modulePath, moduleDir) = owningModuleEntry
                val relativeInModule = file.removePrefix("${moduleDir.trimEnd('/')}/")
                when {
                    isScreenshotTestOnlyPath(relativeInModule) -> directScreenshotTestOnlyModules += modulePath
                    isUnitTestOnlyPath(relativeInModule) -> directUnitTestOnlyModules += modulePath
                    isAndroidTestOnlyPath(relativeInModule) -> directAndroidTestOnlyModules += modulePath
                    else -> directMainModules += modulePath
                }
            } else {
                val allModules = moduleDirs.keys.toSortedSet()
                return AffectedAnalysisResult(
                    affectedTestModules = allModules,
                    affectedAndroidTestModules = allModules,
                    affectedScreenshotModules = allModules,
                    affectedLintModules = allModules,
                )
            }
        }

        val transitiveMainModules = computeTransitiveClosure(directMainModules, reverseDependencies)
        val finalUnitTestModules = (transitiveMainModules + directUnitTestOnlyModules).toSortedSet()
        val finalAndroidTestModules = (transitiveMainModules + directAndroidTestOnlyModules).toSortedSet()
        val finalScreenshotModules = (transitiveMainModules + directScreenshotTestOnlyModules).toSortedSet()
        val finalLintModules = transitiveMainModules.toSortedSet()

        return AffectedAnalysisResult(
            affectedTestModules = finalUnitTestModules,
            affectedAndroidTestModules = finalAndroidTestModules,
            affectedScreenshotModules = finalScreenshotModules,
            affectedLintModules = finalLintModules,
        )
    }

    private fun isIgnored(file: String, ignoredPaths: List<String>): Boolean {
        return ignoredPaths.any { pattern ->
            when {
                pattern.endsWith("/") -> file.startsWith(pattern) || file.contains("/$pattern")
                pattern.startsWith(".") -> file.endsWith(pattern) || file == pattern
                else -> file == pattern || file.startsWith("$pattern/")
            }
        }
    }

    private fun isGlobal(file: String, globalPaths: List<String>): Boolean {
        return globalPaths.any { pattern ->
            when {
                pattern.endsWith("/") -> file.startsWith(pattern)
                else -> file == pattern || file.startsWith("$pattern/")
            }
        }
    }

    private fun isScreenshotTestOnlyPath(relativeInModule: String): Boolean {
        return relativeInModule.startsWith("src/screenshotTest/") ||
            relativeInModule.startsWith("src/screenshotTestDebug/") ||
            relativeInModule.startsWith("src/screenshotTestRelease/") ||
            relativeInModule.contains("/screenshotTest/") ||
            relativeInModule.startsWith("src/test/screenshots/") ||
            relativeInModule.startsWith("src/test/snapshots/") ||
            (
                relativeInModule.startsWith("src/test/") &&
                    (relativeInModule.endsWith("ScreenshotTest.kt") || relativeInModule.endsWith("ScreenshotTest.java"))
                )
    }

    private fun isUnitTestOnlyPath(relativeInModule: String): Boolean {
        return relativeInModule.startsWith("src/test/")
    }

    private fun isAndroidTestOnlyPath(relativeInModule: String): Boolean {
        return relativeInModule.startsWith("src/androidTest/")
    }

    private fun computeTransitiveClosure(
        seeds: Set<String>,
        reverseDependencies: Map<String, Set<String>>,
    ): Set<String> {
        val visited = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        seeds.forEach {
            if (visited.add(it)) {
                queue.add(it)
            }
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val dependents = reverseDependencies[current].orEmpty()
            for (dep in dependents) {
                if (visited.add(dep)) {
                    queue.add(dep)
                }
            }
        }
        return visited
    }
}
