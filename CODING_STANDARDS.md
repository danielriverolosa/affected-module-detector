# Coding Standards

## Core Graph & Classification Logic
- Keep [`AffectedModuleCalculator`](src/main/kotlin/com/danielriverolosa/gradle/affected/AffectedModuleCalculator.kt) free of `org.gradle.*` imports so graph traversal and path matching remain pure Kotlin functions testable in milliseconds.
- Normalize all incoming file paths (`\` to `/`, strip leading `./`) before matching against `ignoredPaths`, `globalPaths`, or module directories.
- Sort candidate module directories by path length descending so nested modules (`:core:domain` vs `:core`) resolve to the most specific module.

## Gradle 9 & Configuration Cache Rules
- Never access `Task.project` inside `@TaskAction` methods.
- Never mutate `task.enabled` on consumer project tasks during configuration.
- Annotate every custom `DefaultTask` subclass with `@DisableCachingByDefault` or `@CacheableTask` and every task property with `@get:Input` (or appropriate input/output annotations) so `./gradlew validatePlugins` passes with `enableStricterValidation = true` and `failOnWarning = true`.
- Execute external commands (`git diff`) strictly through a `ValueSource` implementation using injected `ExecOperations`.
