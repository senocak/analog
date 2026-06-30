---
name: static-analysis-gates
description: "The build-failing static analysis gates in this project — Checkstyle, SpotBugs, and JaCoCo — their exact config, the project's non-obvious conventions (e.g. EI_EXPOSE_REP is excluded), and how to run or temporarily disable them. Use before committing, when a build fails on checkstyle/spotbugs, or when adding suppressions."
---

# Static Analysis Gates Skill

This project gates the Maven build on three analyzers. They run in the normal build and **fail it on violation** — green local compile is not enough.

## When to Use
- Before committing / "why did the build fail" on style or bug-pattern violations
- Adding or justifying a suppression
- Questions about line length, Javadoc requirements, coverage, or defensive copying
- Deciding whether to disable a gate for a quick local run

## The three gates (versions are pinned in pom.xml)
| Gate | Plugin / version | Config file | Effect |
|------|------------------|-------------|--------|
| Checkstyle | `maven-checkstyle-plugin 3.6.0` (Checkstyle `10.26.0`) | `checkstyle.xml` (+ `checkstyle-exclude.xml`) | `severity=error` → any violation **fails the build** |
| SpotBugs | `spotbugs-maven-plugin 4.9.8.2` | `spotbugs-exclude.xml` | bug-pattern matches fail the build |
| JaCoCo | `jacoco-maven-plugin 0.8.14` | pom | coverage report / rules |

## Checkstyle rules that bite most (from `checkstyle.xml`)
- **`LineLength` max 100** for code (a separate 200 limit applies to specific contexts). Wrap long lines; don't fight it with `//NOPMD`-style hacks.
- **Javadoc is mandatory on methods** (`JavadocMethod` + `MissingJavadocMethod`, `SummaryJavadoc`, `SingleLineJavadoc`, `JavadocParagraph`). New public/protected methods need a real Javadoc block see existing classes (e.g. the constructor Javadoc on `IpCheckInterceptor`).
- Brace and whitespace rules are strict: `NeedBraces`, `LeftCurly`/`RightCurly`, `WhitespaceAround`, `SeparatorWrap`, `OperatorWrap`, `ParenPad`, `NoWhitespaceBefore/After`.
- Naming: `ConstantName`, `StaticVariableName`, `TypeName`, `PatternVariableName`, record names.
- `UnusedImports` / `RedundantImport` / `RedundantModifier` / `TodoComment` are enforced.

Suppress narrowly when justified: the config already wires `SuppressionFilter`, `SuppressionCommentFilter`, `SuppressWithNearbyCommentFilter`, `SuppressWarningsFilter`(via `@SuppressWarnings`), and `SuppressWithNearbyTextFilter`. Prefer a scoped suppression over weakening `checkstyle.xml`.

## SpotBugs — the key project convention
`spotbugs-exclude.xml` excludes **`EI_EXPOSE_REP`** and **`EI_EXPOSE_REP2`**. That means **this project intentionally does NOT defensively-copy** arrays/collections/dates in getters and setters(DTOs expose internal references directly). Do not "fix" a getter to return a copy to satisfy SpotBugs — it's a deliberate, codebase-wide choice. Add new excludes only for a genuine false-positive, and keep them pattern-scoped.

## Running & disabling
- Full gate run: `mvn verify` (or the relevant `mvn checkstyle:check` / `mvn spotbugs:check`).
- Each gate has a kill switch property — handy for a fast local iteration, **never** for CI: `-Ddisable.checkstyle=true`, `-Ddisable.spotbugs=true`, `-Ddisable.jacoco=true`.
- These default to `false` in pom.xml; don't commit them flipped.

## Rules
1. Fix violations at the source; line-length and Javadoc are the usual offenders.
2. Don't add defensive copies to silence `EI_EXPOSE_REP*` — it's excluded on purpose.
3. Suppressions are scoped (`@SuppressWarnings`, nearby-comment, or `*-exclude.xml` with a precise
   pattern) and justified in a comment — never blanket-disable a rule in `checkstyle.xml`.
4. The `-Ddisable.*` flags are for local speed only; CI runs all gates.
5. Keep plugin/tool versions on the existing pinned properties; bump deliberately, not incidentally.
