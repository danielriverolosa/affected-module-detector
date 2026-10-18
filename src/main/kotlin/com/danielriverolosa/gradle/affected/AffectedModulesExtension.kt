package com.danielriverolosa.gradle.affected

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class AffectedModulesExtension {
    abstract val baseRef: Property<String>
    abstract val ignoredPaths: ListProperty<String>
    abstract val globalPaths: ListProperty<String>
    abstract val androidUnitTestTaskName: Property<String>
    abstract val jvmTestTaskName: Property<String>
    abstract val lintTaskName: Property<String>

    init {
        baseRef.convention("origin/master")
        ignoredPaths.convention(
            listOf(
                "docs/",
                ".md",
                ".gitignore",
                ".github/",
            ),
        )
        globalPaths.convention(
            listOf(
                "build-logic/",
                "gradle/libs.versions.toml",
                "gradle/wrapper/",
                "build.gradle.kts",
                "settings.gradle.kts",
                "gradle.properties",
            ),
        )
        androidUnitTestTaskName.convention("testDebugUnitTest")
        jvmTestTaskName.convention("test")
        lintTaskName.convention("lintRelease")
    }
}
