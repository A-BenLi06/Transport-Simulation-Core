# MTR Core Performance (Forge 1.20.1)

A companion mod that applies the `perf/yunniverse-server` Transport Simulation Core optimizations
to **Minecraft Transit Railway 4.0.5 on Forge 1.20.1**, via Mixin — without rebuilding MTR.

## Why mixins instead of patching the jar

MTR 4.0.x bundles a much older TSC than the upstream `master` those patches were written against
(it matches TSC commit `ecde724`, 2025-10-14), compiled to **Java 8 bytecode** with its
dependencies relocated into `org.mtr.libraries.*`. Rebuilding TSC to produce drop-in replacement
classes means reproducing both the Java 8 target and that relocation exactly. Mixins operate on
the already-relocated, already-Java-8 classes at load time, so none of that applies.

## What it changes

| Target | Change | Kind |
|---|---|---|
| `Rail.tick1` | Swap the two reservation buffers instead of `clear()` + `putAll()` — the latter rebuilds an AVL tree, one node allocation per entry, per rail, per tick | perf |
| `Rail.tick1` | Compare the two key sets in one ordered pass instead of `Utilities.sameItems` (which is `containsAll` in *both* directions) | perf |
| `Rail.tick1` | Iterate `clients` with a plain loop instead of `forEach` + a capturing lambda (one allocation per rail per tick) | perf |
| `Rail.isNotBlocked` | `LongIterator` instead of `longStream().allMatch(...)` | perf |
| `FileLoader.writeDirtyDataToFile` | Add `TRUNCATE_EXISTING` to `Files.newOutputStream` | **bug fix** |
| `Main` (threaded mode) | Make the simulation tick interval configurable | perf, opt-in |

Behaviour is otherwise identical: `needsUpdate` is computed from the same two set comparisons,
before the buffers swap, and the same clients are updated with the same argument.

### The save-corruption fix

`Files.newOutputStream(path, CREATE)` does **not** imply `TRUNCATE_EXISTING`. The implicit
`CREATE, TRUNCATE_EXISTING, WRITE` default only applies when the options array is *empty*; pass one
option and the set becomes exactly that option plus `WRITE`. So when a station/route/depot packs to
fewer bytes than last save, the tail of the previous version survives and the MessagePack stream is
garbage past the new end. This is worth applying on its own merits.

### Simulation tick interval

With `useThreadedSimulation` enabled in `config/mtr.json`, MTR runs its simulation on a dedicated
thread at a hard-coded **10 ms — 100 TPS**, five times the rate of the server thread it feeds.

Add a JVM flag to change it:

```
-Dmtr.simulationTickMillis=50
```

Clamped to 10–1000 ms. Unset means 10 ms, i.e. stock behaviour. No effect when threaded simulation
is off, because then nothing is scheduled at all. `Main.MILLISECONDS_PER_TICK` is a compile-time
constant and is inlined at its only call site, so this is a `@Redirect` on the schedule call rather
than a field change.

## Building

```
./gradlew build
```

`libs/MTR-forge-4.0.5+1.20.1.jar` is a `compileOnly` dependency — never bundled — and must be the
exact MTR build the server runs. Output: `build/libs/mtr-core-perf-1.0.0.jar`.

## Verifying

Do **not** use a ModDevGradle dev run. A dev runtime uses official (mojmap) names while a released
MTR jar is reobfuscated to SRG, so MTR's registration fails with `NoSuchFieldError: f_279569_` long
before the classes this mod patches are ever loaded. Test on a real Forge server:

1. `java -jar forge-1.20.1-47.1.33-installer.jar --installServer`
2. Put the MTR jar and `mtr-core-perf-1.0.0.jar` in `mods/`
3. Start with `-Dmixin.debug.export=true`
4. After startup, `.mixin.out/class/` should contain `org/mtr/core/data/Rail.class`,
   `org/mtr/core/Main.class` and `org/mtr/core/simulation/FileLoader.class`
5. `javap -c -cp .mixin.out/class org.mtr.core.data.Rail` should show `mtrcoreperf$keysDiffer`
   and no `sameItems`, `longStream` or `putAll`

This was done against MTR 4.0.5 + Forge 47.1.33: all three mixins applied, and with
`useThreadedSimulation: true` the server logged
`Simulation tick interval set to 50 ms (stock is 10 ms)`.

## On upgrading MTR

`Rail.tick1` and `Rail.isNotBlocked` are `@Overwrite`s, and `MainMixin` targets a synthetic lambda
method (`lambda$new$0`). A new MTR build can change any of these. The `Main` redirect is
`require = 0` and degrades to a no-op, but the two `@Overwrite`s will fail loudly at startup — which
is the intended behaviour, since silently reverting to unpatched code would be worse. Re-verify on a
test server before upgrading MTR in production.

## Not included

The upstream fork also rewrites `Utilities.circularClamp` / `circularDifference` to use modular
arithmetic. That is **not** ported here: the version MTR 4.0.x bundles folds into the half-open
range `(-half, +half]`, while upstream `master` (and therefore the fork's rewrite) uses the closed
range `[-half, +half]`. Brute-forced over 2.81M value pairs they disagree in 2564 cases, all sign
flips at exactly half a period — e.g. `period=360, v1=-360, v2=-180` gives `180` on the bundled
version and `-180` on the fork's. Since the bundled implementation already does a divide-then-loop
rather than a pure loop, the win would have been small and the risk to timetable deviation maths
real.
