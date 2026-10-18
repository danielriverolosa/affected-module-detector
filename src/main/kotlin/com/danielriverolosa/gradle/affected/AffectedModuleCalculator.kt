package com.danielriverolosa.gradle.affected

import java.io.Serializable

data class AffectedAnalysisResult(
    val affectedTestModules: Set<String>,
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
            return AffectedAnalysisResult(emptySet(), emptySet())
        }

        val sortedModulesByDirDepth = moduleDirs.entries
            .filter { it.value.isNotBlank() }
            .sortedByDescending { it.value.length }

        val directMainModules = linkedSetOf<String>()
        val directTestOnlyModules = linkedSetOf<String>()

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
                if (isTestOnlyPath(relativeInModule)) {
                    directTestOnlyModules += modulePath
                } else {
                    directMainModules += modulePath
                }
            } else {
                // Unknown root file not in ignoredPaths -> treat conservatively as global
                val allModules = moduleDirs.keys.toSortedSet()
                return AffectedAnalysisResult(
                    affectedTestModules = allModules,
                    affectedLintModules = allModules,
                )
            }
        }

        val transitiveMainModules = computeTransitiveClosure(directMainModules, reverseDependencies)
        val finalTestModules = (transitiveMainModules + directTestOnlyModules).toSortedSet()
        val finalLintModules = transitiveMainModules.toSortedSet()

        return AffectedAnalysisResult(
            affectedTestModules = finalTestModules,
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

    private fun isTestOnlyPath(relativeInModule: String): Boolean {
        return relativeInModule.startsWith("src/test/") ||
            relativeInModule.startsWith("src/androidTest/")
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
