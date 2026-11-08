# Seed — First Public Release Plan

**Prepared:** 2026-09-30  
**Starting point:** `Ref/All_Project` at `9311862`; Maven/Java 25 CLI; current version text `1.0.0`  
**Input:** [seed-review.md](seed-review.md)

## Goal and release decision

Ship a small, reliable public release that a developer can download, understand, and use to generate a buildable Spring project **without risking existing files or needing a preconfigured Git identity**.

**Current decision: NO-GO for a supported public release.** The offline build passes 58 tests and the JAR's help/version work, but review findings R1–R6 and distribution/documentation gaps must be addressed. Passing the current suite alone is not the release criterion.

### Recommended first-release scope

- **Version:** recommend `v0.1.0` as the first pre-1.0 public release. Reserve `1.0.0` for an intentional compatibility commitment. If a beta is published before all gates pass, label it a prerelease and disclose limitations; do not call it stable.
- **Artifact:** one verified runnable fat JAR with checksums and licenses. Do not upload `original-seed-*.jar` as the CLI download.
- **Runtime:** keep **Java 25+** initially if intentional; explicitly separate this from the Java version selected for a generated project.
- **Platforms:** validate Linux first. Advertise macOS/Windows support only after their artifact and file/Git behavior is tested. An operating-system-neutral JAR is not sufficient evidence of platform support.
- **Features:** interactive wizard, dependency search, output-parent selection, cache refresh, YAML/properties choice, optional `.env`, and optional Git. Retain a feature only if its release acceptance tests pass; otherwise clearly narrow/disable it before publication.
- **External service:** generation requires reachable `start.spring.io`; stale metadata does **not** provide offline project generation.
- **Distribution channel:** a repository-hosted release/download page is sufficient. Native binaries, Maven Central, Homebrew, SDKMAN, package repositories, and installer scripts are **not** first-release requirements.

These are recommendations to confirm in Phase 0, not changes already made to the source.

## Working rules

1. One focused change at a time, with a regression test for the relevant defect.
2. Preserve existing no-overwrite, ZIP containment, and secret-preservation behavior.
3. Do not introduce Spring DI, rewrite the terminal UI, migrate build systems, or bulk-upgrade dependencies to prepare this release.
4. Tests must exercise effective behavior: parsed configuration values, executable wrappers, intact existing files, complete wizard input, and real exit status—not just substring or getter checks.
5. Use temporary directories and injected endpoints/cache paths in tests. Never modify the developer's real home cache, Git identity, or unrelated projects.
6. Review and test the exact release commit/artifact; no publishing, tags, or branch changes happen as part of this planning task.

## Phase 0 — Freeze the public contract

**Effort:** small, approximately half a day. **Dependencies:** none.

### Tasks

- [ ] Confirm version, supported platform(s), runtime requirement, and initial distribution channel.
- [ ] Define exactly what `-p/--path` means: a parent directory, with a new `<artifact>` child; default parent is the user's home directory, not the current working directory.
- [ ] Define exit statuses: success, generation/config failure, cancellation, invalid CLI option, and optional Git warning. Current CLI uses 0/1/130 and Picocli uses 2 for invalid input.
- [ ] Decide whether “initialize Git” means only initialization or also an initial commit. Make the prompt/docs truthful.
- [ ] Define `.env` as imported Java-properties configuration, not shell dotenv execution; decide whether to keep generic example properties or provide matching Spring placeholders.
- [ ] Decide the supported config-conversion boundary: arbitrary properties/YAML, or only recognized newly generated template syntax with safe rejection outside it.
- [ ] Create a minimal README outline and a changelog entry for the chosen release scope.

**Acceptance gate:** an unfamiliar user can tell what will be created, where it will be written, what will be sent to the network, which prerequisites they need, and what “success” means.

## Phase 1 — Fix generated-project correctness

**Effort:** medium, approximately 2–4 focused working days. **Dependencies:** Phase 0 contract.

### Change 1: Safe, usable archive generation — R1/R2

**Primary files:** `src/main/java/dev/ayman/seed/service/ProjectGeneratorService.java`, `src/test/java/dev/ayman/seed/service/ProjectGeneratorServiceTest.java`.

- [ ] Reject non-ZIP, empty, corrupt, and incomplete project archives with an actionable error.
- [ ] Check expected build file and wrapper for the selected supported project type.
- [ ] Restore executable permissions for `mvnw` and `gradlew` on supported Unix-like systems; do not mark all extracted files executable.
- [ ] Extract to an exclusively tool-owned staging directory; complete mandatory post-processing there before publishing the project.
- [ ] Preserve a nonempty existing destination byte-for-byte. Define handling of existing empty destinations explicitly; reject symlink/ambiguous cases if ownership cannot be established safely.
- [ ] Make publication refuse a destination that appears concurrently. Do not use replace-existing behavior.
- [ ] Clean up only tool-created staging content after failure; do not delete pre-existing user directories.
- [ ] Add sensible compressed/expanded archive-size and entry-count limits, or record a justified bounded follow-up with conservative first-release behavior.

**Required tests:** executable Maven and Gradle wrappers; nested files; invalid/empty/truncated ZIP; path traversal; existing destination preservation; partial extraction/config failure; concurrent destination creation; resource-limit failure if implemented.

**Gate:** no false success for invalid content, no existing-file overwrite, no unusable Unix wrapper, and no partial project published after mandatory failure.

### Change 2: Configuration integrity — R3/R4

**Primary files:** `src/main/java/dev/ayman/seed/util/EnvFileWriter.java`, `src/test/java/dev/ayman/seed/EnvFileWriterTest.java`.

- [ ] Make YAML import addition idempotent and merge it without a second `spring:` mapping.
- [ ] Preserve existing Spring settings and imports. Do not skip/duplicate an import merely because a comment contains `spring.config.import`.
- [ ] Preserve Java-properties semantics during supported conversion; alternatively reject unsupported syntax before deleting/changing the source.
- [ ] Handle newline boundaries and existing configuration consistently; write/validate a replacement before removing original content.
- [ ] Preserve existing `.env` secrets; verify generated `.gitignore` actually excludes `.env` while allowing `.env.example`.
- [ ] Make `.env` instructions accurately describe the Java-properties import behavior.

**Required tests:** fresh properties and YAML projects; repeated YAML patching; existing nested `spring:` content; existing import; whitespace separators, escapes, continuations, and quoted values according to the chosen support boundary; safe rejection; parsed effective-value equivalence; existing secret preservation; `git check-ignore` behavior.

**Gate:** a generated YAML/properties project loads its configuration, `.env` import behaves as documented, and customization does not silently discard settings.

### Change 3: One wizard input stream — R5

**Primary files:** `WizardRunner.java`, `SelectionPrompt.java`, `DependencySelector.java`, `MetadataForm.java` under `src/main/java/dev/ayman/seed/wizard/`, plus their tests.

- [ ] Construct one buffered reader/output writer and share them through every wizard component.
- [ ] Allow the wizard's service collaborators and streams to be supplied in a test without contacting Initializr.
- [ ] Add one complete multi-step transcript test, including selected dependencies and metadata overrides.
- [ ] Verify EOF cancellation at different steps produces status 130 and no unintended generated files.
- [ ] Add numbered-choice/default tests and constrain dependency toggles to displayed results.

**Gate:** a pre-buffered sequence of answers progresses through all wizard steps correctly; isolated tests no longer hide cross-step input consumption.

### Change 4: Optional Git that cannot invalidate a good project — R6

**Primary files:** `src/main/java/dev/ayman/seed/util/GitInitializer.java`, `src/main/java/dev/ayman/seed/wizard/WizardRunner.java`, new focused Git behavior tests.

- [ ] Align the prompt with initialization/commit behavior chosen in Phase 0.
- [ ] Bound process execution; terminate/clean up timed-out subprocesses.
- [ ] Keep a limited useful stderr diagnostic instead of discarding it.
- [ ] On optional Git failure, report the completed project path and a warning/recovery action rather than only a generic generation error.
- [ ] Do not modify global Git settings or require users to disable signing/security controls.

**Required tests:** success, missing executable, missing identity, command failure, timeout, user declining Git, and `.env` not staged. Use controlled process fixtures and temporary repositories.

**Gate:** the project remains usable and its location is reported when optional Git setup cannot finish.

## Phase 2 — Make network/cache behavior testable and reliable

**Effort:** medium, approximately 1–2 days. **Dependencies:** shared wizard seams; can partly run in parallel with Phase 1.

**Primary files:** `MetadataService.java`, `ProjectGeneratorService.java`, metadata models, corresponding service tests.

- [ ] Inject cache path, HTTP transport/base URI, and time where necessary. Keep simple production defaults; avoid a new framework.
- [ ] Reject null options and missing required identifiers/names using descriptive validation errors.
- [ ] Recover from corrupt cache by refreshing; preserve valid stale fallback on fetch failure.
- [ ] Return valid fresh metadata even when cache persistence fails; emit a warning and replace caches atomically where possible.
- [ ] Tell users when stale metadata is used, including after `--refresh` falls back.
- [ ] Exercise timeout, interruption, non-200 response, malformed JSON, invalid archive, and useful error diagnostics without calling the public API.
- [ ] Validate common identifier/package mistakes when entered, and reprompt rather than fail at the end.
- [ ] Verify the relevance of Boot suffix and Gradle/unstable-version workarounds against the live service during the later pilot. Record the checked condition/date and test policy decisions directly.

**Required tests:** fresh-cache hit, expired refresh, forced refresh, corrupt cache, stale fallback, missing/unwritable cache, malformed/null metadata, encoded special characters/dependencies, generation HTTP errors, and selected version-policy cases.

**Gate:** routine cache/network failures have deterministic behavior and useful messages, while all normal test runs remain offline.

## Phase 3 — Prepare the repository and downloadable artifact

**Effort:** medium, approximately 1–2 days. **Dependencies:** version/platform decisions; artifact smoke tests depend on Phases 1–2.

### Build identity and packaging

- [ ] Set the chosen release version in Maven and expose it to Picocli from one source; derive HTTP user-agent version consistently.
- [ ] Pin a reproducible Maven toolchain using a wrapper, and document supported JDK/Maven versions. Review plugin versions/defaults; set compiler release/runtime policy deliberately.
- [ ] Verify the shaded JAR's manifest/main class and make multi-release dependency behavior intentional.
- [ ] Include Seed's MIT license in the download/JAR and intentionally preserve dependency license/notice resources.
- [ ] Inspect runtime warnings, especially Jansi native-access output on Java 25. Resolve or clearly document supported invocation requirements after testing; do not blanket-suppress unrelated warnings.
- [ ] Run a current dependency vulnerability/license check and generate an SBOM if practical. Triage findings and upgrade only the affected dependency with before/after tests.
- [ ] Scan the working tree **and full Git history** for secrets before making the repository public. Investigate hits rather than deleting unrelated history without a plan.

### CI

- [ ] Add CI for the intended release branch with clean `verify` on the supported JDK.
- [ ] Run on each advertised OS, with appropriate platform-specific assertions for permissions and Git.
- [ ] Gate packaged-JAR `--help`, `--version`, invalid-option status, Jackson metadata parsing, and a complete fixture-based wizard run.
- [ ] Keep live Initializr/generated-build checks separate from deterministic unit CI to avoid treating upstream outages as local regressions.
- [ ] Build the release from an exact reviewed commit/tag; use minimal workflow permissions and do not expose secrets in pull-request jobs.
- [ ] Produce an unambiguous fat-JAR asset, checksum, license/notice bundle, and release notes. Never publish the unshaded `original-` artifact by mistake.

### Documentation

- [ ] `README.md`: purpose, short demo, requirements, download/install/run, help options, supported platforms, output-parent semantics, overwrite refusal, first generated build commands, and troubleshooting.
- [ ] Explain network/privacy behavior: project options such as group/artifact/name/description/dependencies are sent to `start.spring.io` in the generation request; local `.env` contents are not part of that request. Do not promise privacy properties of the upstream service.
- [ ] Explain cache location (`~/.cache/seed/metadata.json`), one-hour freshness, refresh/stale fallback, and that generation still requires network access. If platform-native cache directories change, update docs/tests together.
- [ ] Document `.env` as Java-properties import, Git requirements/optional commit behavior, partial/optional failure recovery, and cancellation.
- [ ] `CHANGELOG.md`: initial supported features and known limitations.
- [ ] Add concise contribution/build/test instructions and a way to report reproducible bugs. A README section is enough initially; separate community documents are optional unless needed.
- [ ] If advertising a `seed` executable command, distribute/test the launcher. Otherwise make the documented command `java -jar ...`; a JAR download does not install a `seed` command automatically.

**Gate:** a clean CI machine builds successfully, the downloadable artifact is correctly identified/licensed/versioned, and an unfamiliar user can use it without developer assistance.

## Phase 4 — Pilot the actual user journey

**Effort:** approximately half to one day, plus time for discovered fixes. **Dependencies:** Phases 1–3.

Perform these checks using the **candidate release artifact**, a temporary parent/cache, and a clean environment. Network generation/build checks contact Initializr and dependency repositories; record failures and distinguish upstream availability from Seed defects.

| Scenario | Required evidence |
|---|---|
| Download and invoke | Artifact checksum matches; help/version display the release version; instructions match real prerequisites. |
| Maven / Java / properties, if advertised | Generated `./mvnw test` succeeds on Unix; equivalent documented Windows invocation if Windows is supported. |
| Gradle / Java / YAML, if advertised | Generated `./gradlew test` succeeds; YAML loads without duplicate-key errors. Test each retained config format even if only one build system is supported. |
| `.env` enabled | A configured example property resolves as documented; `.env` is ignored and never committed. |
| Other advertised build DSL/language | At least one generated build per advertised alternative, or clearly narrow/mark unverified alternatives before release. |
| Existing destination | Existing files stay byte-for-byte unchanged; clear refusal and non-success status. |
| Missing Git / missing identity | Generated project remains usable; warning and recovery instructions are accurate. |
| Refresh/cache/network failure | Fresh/stale/corrupt behavior matches documentation; generation failure never claims full success. |
| Cancellation and pasted input | Clean cancellation; multi-line answers don't disappear between steps. |
| Platform/terminal | Each advertised OS tested; readable normal terminal output and redirected output; path with spaces tested. |

Record artifact hash, source commit, platform/JDK, selected metadata/version/options, commands, statuses, and results. Generated-project builds are stronger evidence than inspecting ZIP contents alone.

**Gate:** all advertised core paths pass and no known P1 issue remains. Run a small external pilot with one or two users who did not develop Seed; fix onboarding failures before release.

## Phase 5 — Publish and support

**Effort:** small. **Dependencies:** all prior gates.

- [ ] Review and merge the preparation changes into the intended release branch; confirm unrelated local work was not included accidentally.
- [ ] Verify all release gates on the exact candidate commit; then create the chosen version tag through the normal human-approved release process.
- [ ] Publish the tested fat JAR, SHA-256 checksum, license/notices, and release notes from the trusted build output.
- [ ] State supported platforms/runtime, features, external-service dependency, known limitations, and installation commands in the release announcement.
- [ ] Keep a reproducible bug-report checklist: Seed version, OS/JDK, selected project options, error/exit status, and sanitized logs. Never request `.env` secrets.
- [ ] Triage first-user failures; issue a focused patch release when warranted. Retain the previous working artifact and document affected versions if a release must be withdrawn.

**Gate:** the public download and its instructions are independently usable, with a clear support path.

## Suggested implementation sequence

These are work units, not branches/commits created by this plan.

| Unit | Focus | Dependencies | Size |
|---|---|---|---|
| 1 | Public contract, version decision, README skeleton | None | Small |
| 2 | Wrapper permissions + archive validation regression tests | 1 | Small–medium |
| 3 | Staging/no-overwrite publication and failure cleanup | 2 | Medium |
| 4 | Safe config conversion and YAML idempotency | 1; integrate with 3 | Medium |
| 5 | Shared I/O + complete wizard transcript test | 1 | Small–medium |
| 6 | Optional Git timeout/diagnostics/outcome | 1, 5 | Small–medium |
| 7 | Metadata/client seams + cache/malformed-input behavior tests | 5 where needed | Medium |
| 8 | Versioned/licensed artifact + CI + dependency/security checks | 1; gates for 2–7 | Medium |
| 9 | Docs, candidate pilot, exact-artifact release | 2–8 | Medium |

Where changes overlap, keep them sequential; disjoint docs/CI work can proceed while correctness fixes are reviewed. Do not combine all units into an unreviewable refactor.

**Planning estimate:** roughly **5–10 focused working days** for a single maintainer, depending on chosen config-conversion scope and pilot findings. Native/multi-platform distribution can substantially extend this and is excluded from the minimal plan.

## Final go/no-go checklist

Publish the supported release only when all mandatory items are true:

- [ ] No known R1–R6 blocker remains for any advertised feature.
- [ ] Existing user files cannot be overwritten by generation/publication.
- [ ] Mandatory failure never leaves a falsely successful/partly published project.
- [ ] Tests cover archive permissions/validity, config semantics, whole-wizard I/O, cache failure, and optional Git outcomes.
- [ ] Clean CI passes, including packaged-artifact behavior on each supported OS.
- [ ] Real generated projects build for every advertised project type/language with the candidate artifact; both Maven and Gradle pass if both remain supported.
- [ ] Runtime/version/platform claims match evidence; native support is not claimed without validation.
- [ ] Artifact includes the required license/notices; advisory and history-secret checks are triaged.
- [ ] README/install instructions and checksum were tested from the user perspective.
- [ ] Release tag, commit, JAR version, user-agent version, notes, and checksum agree.

## Defer until after the first release

- Native-image binaries and platform installers/package-manager integration.
- A new raw-terminal/live-search UI or arrow-key navigation.
- Fully noninteractive CLI automation/config-file mode, unless explicitly added to the release scope.
- Record/Lombok migrations, package reshuffling, architecture frameworks, and broad formatting changes.
- Micro-optimizing fuzzy search without a representative performance problem.
- Broad feature expansion, offline project templates, telemetry, and plugin systems.

## Verification already completed for this plan

`mvn -o clean verify` passed **58 tests** on Linux/OpenJDK 25.0.2/Maven 3.9.11. The current `target/seed-1.0.0.jar` passed help/version/invalid-option smoke checks. Local probes reproduced the identified permission, archive, YAML/conversion, shared-input, and malformed-metadata defects.

**Not yet done:** implementation fixes, new CI/docs/installers, live generated-project builds, native compilation, multi-OS tests, vulnerability/history-secret scans, commits/tags, or publishing. This plan does not mark any future acceptance gate as passed.