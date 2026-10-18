# Architecture & Internal Design

This document describes the internal architecture of `affected-module-detector` (`com.danielriverolosa.affected-modules`) and explains how it achieves full compatibility with **Gradle 9**, **Configuration Cache**, and **Build Cache**.

---

## 1. Design Principles & Seam Separation

The plugin is built around a deep, framework-agnostic core (`AffectedModuleCalculator`) surrounded by a thin Gradle 9 adapter layer:

<p align="center">
  <img src="assets/component-seam.svg" alt="Component Seam &amp; Boundary Architecture" width="100%"/>
</p>

### Component Responsibilities

1. **[`AffectedModuleCalculator`](../src/main/kotlin/com/danielriverolosa/gradle/affected/AffectedModuleCalculator.kt)**:
   - Pure Kotlin object with zero `org.gradle.*` imports.
   - Accepts plain collections (`List<String>`, `Map<String, String>`, `Map<String, Set<String>>`) and returns `AffectedAnalysisResult` (`affectedTestModules`, `affectedAndroidTestModules`, `affectedScreenshotModules`, `affectedLintModules`).
   - Tested directly via fast JVM unit tests in [`AffectedModuleCalculatorTest`](../src/test/kotlin/com/danielriverolosa/gradle/affected/AffectedModuleCalculatorTest.kt).

2. **[`GitChangedFilesValueSource`](../src/main/kotlin/com/danielriverolosa/gradle/affected/GitChangedFilesValueSource.kt)**:
   - Implements Gradle's `ValueSource<List<String>, Parameters>`.
   - Executes `git diff --name-only <baseRef>...HEAD` (falling back to `git diff --name-only <baseRef>`) via injected `ExecOperations`, throwing a descriptive `GradleException` if neither git revision is reachable.

3. **[`PrintAffectedTasksTask`](../src/main/kotlin/com/danielriverolosa/gradle/affected/PrintAffectedTasksTask.kt)**:
   - Annotated with `@DisableCachingByDefault` because its sole output is a single line printed to `stdout`.
   - Declares only primitive/collection `@Input` properties (`ListProperty`, `MapProperty`, `Property`). Never references `Project` at execution time.

4. **[`AffectedModulesPlugin`](../src/main/kotlin/com/danielriverolosa/gradle/affected/AffectedModulesPlugin.kt)**:
   - Inspects subprojects once in `gradle.projectsEvaluated`, extracting module directories, production `ProjectDependency` edges (`!config.name.lowercase().contains("test")`), and module capabilities (`com.android.*`, `org.jetbrains.kotlin.jvm`, `io.github.takahirom.roborazzi`, `src/androidTest`).
   - Registers `printAffectedTestTasks`, `printAffectedAndroidTestTasks`, `printAffectedScreenshotTasks`, and `printAffectedLintTasks` on the root project.

---

## 2. Cache-Safety Architecture

<p align="center">
  <img src="assets/cache-architecture.svg" alt="Cache-Safety Architecture Comparison" width="100%"/>
</p>

### Why Legacy Detectors Break Gradle 9

| Anti-Pattern in Legacy Plugins | Consequence in Gradle 9 | How `affected-module-detector` Avoids It |
| :--- | :--- | :--- |
| Running `ProcessBuilder("git", "diff")` inside `afterEvaluate {}` | External process execution during configuration invalidates Configuration Cache on every commit | Uses `ValueSource` (`GitChangedFilesValueSource`) wired lazily to task `@Input` |
| Setting `subproject.tasks.configureEach { enabled = false }` | Mutates the global task graph based on git state; breaks Configuration Cache and prevents running explicit tasks | Never mutates any existing task; outputs task paths so CI invokes standard tasks directly |
| Referencing `project.subprojects` inside `@TaskAction` | Fails Gradle 9 Configuration Cache serialization (`Task.project` access forbidden) | Pre-computes `Map<String, String>` and `Map<String, Set<String>>` into serializable `MapProperty` task inputs |

---

## 3. Reverse Dependency Graph & File Classification

<p align="center">
  <img src="assets/dag-propagation.svg" alt="Reverse Dependency Graph Propagation" width="100%"/>
</p>

### Longest-Prefix Module Resolution
In nested multi-module hierarchies (e.g., `:core` at `core/` and `:core:domain` at `core/domain/`), `AffectedModuleCalculator` sorts candidate modules by directory path length descending before matching. A file at `core/domain/src/main/UseCase.kt` deterministically resolves to `:core:domain` rather than `:core`.

### Test-Only Isolation (`src/test` vs `src/androidTest`)
- Changes inside `<module>/src/test/**` mark `<module>` in `affectedTestModules` and `affectedScreenshotModules` only—without propagating to parent modules in `reverseDependencies` and without triggering `androidTest` or `lint`.
- Changes inside `<module>/src/androidTest/**` mark `<module>` in `affectedAndroidTestModules` only—without triggering unit tests, screenshot tests, or lint.
- Changes inside `<module>/src/main/**` or `<module>/build.gradle.kts` traverse `reverseDependencies` via Breadth-First Search (BFS), marking `<module>` and all direct/transitive dependents across all four task selectors.
