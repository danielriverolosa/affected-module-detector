package com.danielriverolosa.gradle.affected

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Outputs affected task paths to standard output.")
abstract class PrintAffectedTasksTask : DefaultTask() {

    @get:Input
    abstract val changedFiles: ListProperty<String>

    @get:Input
    abstract val moduleDirs: MapProperty<String, String>

    @get:Input
    abstract val reverseDependencies: MapProperty<String, Set<String>>

    @get:Input
    abstract val moduleTaskMap: MapProperty<String, String>

    @get:Input
    abstract val ignoredPaths: ListProperty<String>

    @get:Input
    abstract val globalPaths: ListProperty<String>

    @get:Input
    abstract val taskMode: Property<String>

    @TaskAction
    fun printTasks() {
        val result = AffectedModuleCalculator.calculate(
            changedFiles = changedFiles.get(),
            moduleDirs = moduleDirs.get(),
            reverseDependencies = reverseDependencies.get(),
            ignoredPaths = ignoredPaths.get(),
            globalPaths = globalPaths.get(),
        )

        val targetModules = when (taskMode.get()) {
            MODE_LINT -> result.affectedLintModules
            MODE_ANDROID_TEST -> result.affectedAndroidTestModules
            MODE_SCREENSHOT -> result.affectedScreenshotModules
            else -> result.affectedTestModules
        }

        val tasksByModule = moduleTaskMap.get()
        val taskPaths = targetModules.mapNotNull { modulePath ->
            val taskName = tasksByModule[modulePath]
            if (!taskName.isNullOrBlank()) "$modulePath:$taskName" else null
        }

        println(taskPaths.joinToString(" "))
    }

    companion object {
        const val MODE_TEST = "test"
        const val MODE_ANDROID_TEST = "androidTest"
        const val MODE_SCREENSHOT = "screenshot"
        const val MODE_LINT = "lint"
    }
}
