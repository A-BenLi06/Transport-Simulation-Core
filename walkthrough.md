# MTR 1.21.1 Yunniverse Performance Walkthrough

This file records evidence, design changes, validation, and deployment findings for the
Yunniverse MTR performance branch. It intentionally documents auditable engineering reasoning
and results rather than private chain-of-thought.

## 2026-08-01T18:47:25+08:00 — Reduce simulation allocation and persistence pressure

- Source review found repeated temporary collections in the rail simulation tick and repeated
  state serialization even when the payload had not changed.
- Reused stable simulation state where ownership permits and avoided redundant persistence work.
- The change targets server tick allocation rate and retained heap; it does not alter route or
  timetable semantics.
- Source commit: `3b5d219 Optimize simulation state updates and persistence`.

## 2026-08-01T19:08:21+08:00 — Package the Core repair without modifying Steam 'n' Rails

- Steam 'n' Rails uses Create rail APIs and bogey-style registries; it does not call MTR Core.
  No direct MTR/SNR ownership conflict was found.
- Added a reproducible allowlisted assembler for replacing only reviewed Core classes inside the
  NeoForge MTR shell. Signed targets, missing classes, and unexpected inputs are rejected.
- Source commit: `8b857f2 Add reproducible MTR Core patch assembler`.

## 2026-08-01T21:25:03+08:00 — Replace circular timetable replay loops

- Timetable calculations replayed interval additions until reaching the current time. Long
  uptime and missed intervals made the work proportional to elapsed history.
- Replaced the replay with overflow-safe constant-time circular arithmetic while preserving the
  same boundary behavior.
- Source commit: `37bbc41 Optimize circular timetable arithmetic`.

## 2026-08-10T19:36:36+08:00 — Prevent siding departures from feeding an occupied route

- The simulator previously started automatic departures without checking the departure envelope
  against jammed and already deployed vehicles. Multiple sidings could also make the decision in
  the same tick before occupancy became visible.
- Added congestion-aware startup admission, previous-tick jam carryover, path-index-zero
  occupancy, and same-tick reservation after a successful dispatch.
- This is admission backpressure: a congested route stops receiving new trains while existing
  movement and timetable state remain intact.
- Source commit: `741e362 Add MTR departure congestion backpressure`.

## 2026-08-10T20:02:19+08:00 — Bound simulation catch-up work

- `Simulator.tickUntilCaughtUp()` used an unbounded replay loop. If one simulated second cost more
  than one real second, the worker accumulated lag faster than it consumed it and could stop
  yielding indefinitely.
- Limited one catch-up invocation to five one-second steps. Excess elapsed time is skipped with a
  warning, while vehicle timestamps are shifted so existing jam age is preserved.
- This complements departure admission: one control bounds new train count, and the other
  guarantees the simulation worker yields under overload.
- Selected vehicle-deployment and utility tests passed.
- Source commit: `00931bb Bound MTR simulation catch-up work`.

## 2026-08-10T20:27:34+08:00 — Preserve the relocated MTR ABI

- An initial assembled artifact used the plain Core JAR and failed at runtime with
  `NoSuchMethodError`: its Gson return type was `com.google.gson.JsonObject`, while the NeoForge
  MTR shell expects relocated `org.mtr.libraries.com.google.gson.JsonObject`.
- Extended the assembler allowlist for `Siding` and `Vehicle`, required a relocated Gson marker,
  and documented `shadowJar` as the only valid embedding input.
- `javap` confirmed the corrected Gson and FastUtil descriptors.
- Source commits: `d57d484 Include deployment classes in MTR assembler` and
  `4bd5771 Reject unrelocated MTR patch artifacts`.

## 2026-08-10T20:44:57+08:00 — Production smoke test

- Corrected MTR v3 reached the dedicated-server ready state and remained alive through two full
  five-minute autosave cycles.
- No `NoSuchMethodError`, tick-loop exception, or `Can't keep up!` warning occurred during the
  controlled window. Working set remained approximately 4.66 GiB with no connected clients.
- This validates packaging, startup and short-run save behavior. A long congested timetable soak
  remains necessary to measure real-world pile-up frequency.

## 2026-08-10T20:52:51+08:00 — Standardize the performance version

### Version decision

- Standardized this branch on
  `4.1.0-beta.2-mc1.21.1-yunniverse-perf-v4`:
  `<upstream MTR version>-mc<Minecraft version>-<downstream iteration>`.
- Iteration `v4` is shared with the corresponding Create build. Advancing from the previous
  `v2`/`v3` artifact suffixes avoids release-name collisions and makes the pair unambiguous.

### Build-system changes

- Updated the Core Gradle version so the relocated Shadow artifact, generated runtime version,
  Maven coordinates and documentation use the same identifier.
- Added a mandatory `-ModVersion` assembler parameter. The assembler now updates the `mtr` entry
  in `META-INF/neoforge.mods.toml` and verifies the embedded value after packaging; renaming a JAR
  without updating runtime metadata is no longer possible.

### Validation

- `gradlew test --tests org.mtr.core.data.VehicleDeploymentTests --tests
  org.mtr.core.tool.UtilitiesTests shadowJar` completed with `BUILD SUCCESSFUL`; compilation
  reported four existing deprecation warnings.
- The independent clone has no prebuilt Angular `website/dist` directory, so the upstream
  non-failing `setupWebserver` task printed a missing-directory stack trace. It did not affect the
  tested Java classes or relocated Shadow artifact used by the MTR assembler.
- Built `Transport-Simulation-Core-4.1.0-beta.2-mc1.21.1-yunniverse-perf-v4.jar`, then assembled
  `MTR-4.1.0-beta.2-mc1.21.1-yunniverse-perf-v4.jar` from the last runtime-validated NeoForge
  shell.
- The assembler verified all allowlisted classes, the relocated Gson marker, and the final
  `version = "4.1.0-beta.2-mc1.21.1-yunniverse-perf-v4"` metadata entry.
- Assembled validation JAR SHA-256:
  `3DC09909BACD480A7833FC61BDEDFF3F76660DE4F27D230FEE3B7B50A061F9D1`.

## 2026-08-10T21:04:26+08:00 — Derive the version from independent components

- The first standardized configuration stored the complete version as one Gradle property. That
  made the visible output correct but did not give build logic separate authoritative fields for
  the upstream MTR release, Minecraft compatibility, and downstream iteration.
- Replaced the combined literal with `upstream_mtr_version = 4.1.0-beta.2`,
  `minecraft_version = 1.21.1`, and `modification_version = yunniverse-perf-v4`.
- Gradle now constructs the project version once from those three properties. Generated runtime
  version files, Shadow artifacts and Maven coordinates continue to receive
  `4.1.0-beta.2-mc1.21.1-yunniverse-perf-v4` without duplicating any component.
- `gradlew shadowJar` completed with `BUILD SUCCESSFUL` and reproduced the expected standardized
  Core artifact name and SHA-256
  `FB5E52EE245DA971B1A8511CF42EBDD5D5404E065F1A2E578A6D30B6565D587B`.

## 2026-08-21T16:58:50+08:00 — Prepare upstream MTR Core performance contributions

### Tracking issue and contribution boundary

- Created upstream issue
  https://github.com/Minecraft-Transit-Railway/Transport-Simulation-Core/issues/32 to document the
  overload feedback loop, production symptoms, source-level causes, and the independent patch
  scopes.
- Rebased each contribution onto official
  `Minecraft-Transit-Railway/Transport-Simulation-Core:master` head `ee09ec5`.
- Excluded Yunniverse versioning, the MTR shell assembler, relocated-class allowlists, deployment
  artifacts, the configurable 10 ms simulator cadence experiment, and the unrelated persistence
  truncation fix. The three upstream branches contain only Core source and focused tests.
- All PRs are drafts and GitHub reports them mergeable. No upstream automated checks were attached
  at the time of this entry.

### Rail signal-state hot path

- Draft PR: https://github.com/Minecraft-Transit-Railway/Transport-Simulation-Core/pull/33
- Branch: `A-BenLi06:perf/simulation-hot-path`
- Before the patch, every rail created its own array snapshot of the same client set, used generic
  collection comparison, copied four AVL maps, and streamed boxed reservation values every
  simulation tick.
- The simulator now snapshots clients once. Rails rotate current/previous reservation maps in O(1),
  record key additions while reserving, detect removals by size, and scan values with a primitive
  iterator. The required client visibility scan remains O(rails × clients), but temporary client
  references fall from O(rails × clients) to O(clients), and reservation history no longer copies
  O(entries) when rotating.
- A regression test verifies that reservation visibility remains correct after map rotation.

### Constant-time circular timetable arithmetic

- Draft PR: https://github.com/Minecraft-Transit-Railway/Transport-Simulation-Core/pull/34
- Branch: `A-BenLi06:perf/circular-time-arithmetic`
- The original clamp/difference helpers repeatedly added or subtracted one period. Work was
  O(abs(offset) / period), so very large persisted or external time values could monopolize the
  simulation thread or overflow during normalization.
- Floor-mod arithmetic makes normalization O(1), retains the old half-period tie direction and NaN
  behavior, and rejects non-positive periods rather than entering a non-terminating loop.
- Equivalence tests compare the new implementation with the former iterative semantics over
  representative ranges and add Long.MIN_VALUE/Long.MAX_VALUE coverage.

### Congestion admission and bounded catch-up

- Draft PR: https://github.com/Minecraft-Transit-Railway/Transport-Simulation-Core/pull/35
- Branch: `A-BenLi06:perf/congestion-catchup-control`
- `tickUntilCaughtUp()` previously had no work bound. When one simulated second cost at least one
  wall-clock second, it could never converge; a gap over one hour could synchronously replay up to
  3,600 slices.
- One scheduler call now performs at most five one-second slices. Remaining elapsed time is
  explicitly skipped and logged, and vehicle last-movement timestamps shift by the same amount so
  load shedding alone cannot create false jams. This intentionally trades missed elapsed service
  for guaranteed scheduler yield under overload.
- A departure now checks current occupancy and previous/current/next route jams before startup.
  Successful deployment is inserted into the current snapshot immediately, preventing another
  siding later in the same tick from making the same admission decision. Jam state remains visible
  for one handoff tick so siding iteration order cannot hide it. Segment index zero is included.
- Tests cover blocked deployment, immediate reservation, and exactly one tick of jam-state handoff.

### Validation

- Each branch passed `gradlew test --no-daemon`; the complete suite reported 113 tests.
- The first parallel run caused two independent `RuntimeTests` workers to contend for fixed port
  8889. The hot-path and circular branches still completed successfully; the congestion branch was
  rerun alone after updating the intentional one-tick jam lifecycle assertion and completed with
  `BUILD SUCCESSFUL`.
- The upstream non-failing `setupWebserver` task also printed its known missing
  `website/dist/website/browser` stack trace in fresh worktrees. Java compilation and tests were
  unaffected.
