# Affected Module Detector - Agent Context

Standalone Gradle 9 plugin (`com.danielriverolosa.affected-modules`) that computes affected module test and lint tasks across multi-module Android and JVM builds without breaking the Gradle Configuration Cache or Build Cache.

## Context Pointers

- **Architecture & Cache Safety:** [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — reach when modifying graph traversal, file classification rules, or Gradle `ValueSource` / Task inputs.
- **Coding Standards:** [`CODING_STANDARDS.md`](CODING_STANDARDS.md) — reach when editing plugin classes, tasks, or tests.
- **Usage & DSL Reference:** [`README.md`](README.md) — reach when updating configuration properties or CI integration examples.
- **Issue Tracker & Triage:** [`docs/agents/issue-tracker.md`](docs/agents/issue-tracker.md) and [`docs/agents/triage-labels.md`](docs/agents/triage-labels.md) — reach when querying, creating, or triaging GitHub Issues via `gh`.

## Verification & Build Commands

Every change must include unit tests in `AffectedModuleCalculatorTest` and/or Gradle TestKit tests in `AffectedModulesPluginTest`:

```bash
# Full verification (strict plugin validation + unit tests + Gradle TestKit configuration-cache tests)
./gradlew check

# Publish to local Maven (~/.m2/repository) for consumer testing
./gradlew publishToMavenLocal
```

## Git & Pull Request Conventions

### Commit & PR Title Format
- **Features:** `Feature: <Description in English starting with Capital Letter>`
- **Technical / Architecture / Refactor / Dependencies:** `Tech: <Description in English starting with Capital Letter>`
- **Bug Fixes:** `Fix: <Description in English starting with Capital Letter>`

### Author Credentials
Git author and committer must always be:
`Daniel Rivero Losa <danielrl.drl@protonmail.com>`
Never include corporate/third-party emails or `Co-authored-by` trailers.
