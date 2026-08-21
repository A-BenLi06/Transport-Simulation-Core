# MTR 1.20.1 Performance Walkthrough

## 2026-08-21T18:42:00+08:00 — Neutralize downstream naming

- Replaced private deployment branding in branch documentation, artifact references, release
  links, and branch links with neutral performance terminology.
- Moved the companion mod's Java namespace to `io.github.abenli06.mtrcoreperf`, updated its Mixin
  package declaration, and replaced manifest vendor fields with the repository owner identity.
- The mod ID, configuration property, Mixin targets, and patched simulation behavior are unchanged.
  This preserves user configuration and Forge mod compatibility while removing the old package
  namespace.
- The first compile attempt correctly failed because the branch-local, gitignored MTR compile-only
  dependency was absent from the new worktree. Downloaded the exact official Forge 4.0.5 for
  Minecraft 1.20.1 artifact from Modrinth version `R05lh1Ys`, verified its published SHA-1
  `5209416ccc9105d6175f4a0498d5eb6e0db714a8`, and reran the build.
- `gradlew build --no-daemon` then completed successfully. The companion artifact SHA-256 is
  `932401F850CD1B5B5B7B9608712272F37AF3F6762DE01A34EC823AB7957833CE`; extracted inspection found
  no removed branding token and confirmed the neutral manifest vendor.
