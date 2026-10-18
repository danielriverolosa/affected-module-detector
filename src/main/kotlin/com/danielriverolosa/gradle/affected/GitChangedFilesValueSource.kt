package com.danielriverolosa.gradle.affected

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class GitChangedFilesValueSource : ValueSource<List<String>, GitChangedFilesValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        val rootDir: DirectoryProperty
        val baseRef: Property<String>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): List<String> {
        val workDir = parameters.rootDir.get().asFile
        val base = parameters.baseRef.get()

        val threeDotOutput = runGitDiff(workDir, "$base...HEAD")
        if (threeDotOutput != null) {
            return parseLines(threeDotOutput)
        }

        val twoDotOutput = runGitDiff(workDir, base)
        if (twoDotOutput != null) {
            return parseLines(twoDotOutput)
        }

        return emptyList()
    }

    private fun runGitDiff(workDir: java.io.File, diffSpec: String): String? {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val result = execOperations.exec {
            workingDir = workDir
            commandLine("git", "diff", "--name-only", diffSpec)
            standardOutput = stdout
            errorOutput = stderr
            isIgnoreExitValue = true
        }
        return if (result.exitValue == 0) stdout.toString(Charsets.UTF_8) else null
    }

    private fun parseLines(output: String): List<String> {
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
}
