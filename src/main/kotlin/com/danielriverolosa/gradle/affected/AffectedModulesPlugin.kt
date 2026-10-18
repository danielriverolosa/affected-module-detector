package com.danielriverolosa.gradle.affected

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class AffectedModulesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        check(target == target.rootProject) {
            "com.danielriverolosa.affected-modules must be applied only to the root project."
        }

        val extension = target.extensions.create<AffectedModulesExtension>("affectedModules")

        val baseRefProvider = target.providers.gradleProperty("baseRef")
            .orElse(extension.baseRef)

        val gitChangedFilesProvider = target.providers.of(GitChangedFilesValueSource::class.java) {
            parameters.rootDir.set(target.layout.projectDirectory)
            parameters.baseRef.set(baseRefProvider)
        }

        val changedFilesProvider = target.providers.gradleProperty("changedFiles")
            .map { raw ->
                raw.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
            .orElse(gitChangedFilesProvider)

        val moduleDirsMap = linkedMapOf<String, String>()
        val reverseDepsMap = linkedMapOf<String, MutableSet<String>>()
        val testTaskMap = linkedMapOf<String, String>()
        val lintTaskMap = linkedMapOf<String, String>()

        target.gradle.projectsEvaluated {
            val rootFile = target.rootDir
            val androidTestTask = extension.androidUnitTestTaskName.get()
            val jvmTestTask = extension.jvmTestTaskName.get()
            val lintTask = extension.lintTaskName.get()

            target.subprojects.forEach { sub ->
                val hasMainOrTest = sub.file("src/main").exists() ||
                    sub.file("src/test").exists() ||
                    sub.file("build.gradle.kts").exists() ||
                    sub.file("build.gradle").exists()

                if (!hasMainOrTest) return@forEach

                val relDir = sub.projectDir.relativeTo(rootFile).invariantSeparatorsPath
                moduleDirsMap[sub.path] = relDir
                reverseDepsMap.putIfAbsent(sub.path, linkedSetOf())

                val isAndroid = sub.plugins.hasPlugin("com.android.application") ||
                    sub.plugins.hasPlugin("com.android.library") ||
                    sub.plugins.hasPlugin("com.android.test")
                val isJvm = sub.plugins.hasPlugin("org.jetbrains.kotlin.jvm") ||
                    sub.plugins.hasPlugin("java-library") ||
                    sub.plugins.hasPlugin("java")

                if (isAndroid) {
                    testTaskMap[sub.path] = androidTestTask
                    lintTaskMap[sub.path] = lintTask
                } else if (isJvm) {
                    testTaskMap[sub.path] = jvmTestTask
                    when {
                        sub.tasks.names.contains(lintTask) -> lintTaskMap[sub.path] = lintTask
                        sub.plugins.hasPlugin("com.android.lint") || sub.tasks.names.contains("lint") -> {
                            lintTaskMap[sub.path] = "lint"
                        }
                    }
                }

                sub.configurations.forEach { config ->
                    val nameLower = config.name.lowercase()
                    val isProductionDep = !nameLower.contains("test")
                    if (isProductionDep) {
                        config.dependencies.withType(ProjectDependency::class.java).forEach { projDep ->
                            val depPath = projDep.path
                            if (depPath != sub.path) {
                                reverseDepsMap.getOrPut(depPath) { linkedSetOf() }.add(sub.path)
                            }
                        }
                    }
                }
            }
        }

        target.tasks.register<PrintAffectedTasksTask>("printAffectedTestTasks") {
            group = "verification"
            description = "Prints space-separated unit test tasks for affected modules and their transitive dependents."
            changedFiles.set(changedFilesProvider)
            moduleDirs.set(target.provider { moduleDirsMap.toMap() })
            reverseDependencies.set(target.provider { reverseDepsMap.mapValues { it.value.toSet() } })
            moduleTaskMap.set(target.provider { testTaskMap.toMap() })
            ignoredPaths.set(extension.ignoredPaths)
            globalPaths.set(extension.globalPaths)
            taskMode.set(PrintAffectedTasksTask.MODE_TEST)
        }

        target.tasks.register<PrintAffectedTasksTask>("printAffectedLintTasks") {
            group = "verification"
            description = "Prints space-separated lint tasks for affected modules and their transitive dependents."
            changedFiles.set(changedFilesProvider)
            moduleDirs.set(target.provider { moduleDirsMap.toMap() })
            reverseDependencies.set(target.provider { reverseDepsMap.mapValues { it.value.toSet() } })
            moduleTaskMap.set(target.provider { lintTaskMap.toMap() })
            ignoredPaths.set(extension.ignoredPaths)
            globalPaths.set(extension.globalPaths)
            taskMode.set(PrintAffectedTasksTask.MODE_LINT)
        }
    }
}
