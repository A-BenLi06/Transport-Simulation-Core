# MTR Core Performance (Forge 1.20.1)

*[English](#english) · [中文](#中文)*

A small companion mod that applies the `perf/optimized-build-1.20.1` Transport Simulation Core
optimizations to **Minecraft Transit Railway 4.0.5 on Forge 1.20.1**, via Mixin.

> ⚠️ **This installs differently from the 1.21.1 releases.** Those ship a patched MTR jar that
> *replaces* the original. This is a separate ~7 KB mod that installs *alongside* an unmodified
> MTR. Keep your existing MTR jar exactly as it is.
>
> ⚠️ **安装方式与 1.21.1 版本不同。** 那边是替换整个 MTR jar；这里是一个约 7 KB 的独立 mod，
> **与原版 MTR 并存**。你原来的 MTR jar 不要动。

---

## English

### Install

1. Download `mtr-core-perf-1.0.0+1.20.1-performance.jar` from
   [Releases](https://github.com/A-BenLi06/Transport-Simulation-Core/releases/tag/mtr-4.0.5-1.20.1-performance-v1).
2. Drop it into `mods/`, **next to** your existing MTR jar. Do not remove or replace MTR.
3. Restart the server.

Server-side is where this matters, but the jar is safe to install on clients too. It contains no
MTR or Create content — only its own four classes.

**Requirements**: Minecraft 1.20.1, Forge 47+, MTR **4.0.5** (Forge). The mod declares MTR as a
mandatory dependency, so Forge will refuse to start without it rather than silently doing nothing.

**Uninstalling**: delete the jar. Behaviour returns to stock exactly. The one file-format effect
(truncation, below) only ever makes saves *more* correct, so there is nothing to undo.

Verify the download:

```
sha256  6e2ecfab1c193382e1f9a81ad07a74f59f28abaf593b439fe4800569ead497e7
```

### Optional: simulation tick interval

With `useThreadedSimulation` enabled in `config/mtr.json`, MTR runs its simulation on a dedicated
thread at a hard-coded **10 ms — 100 TPS**, five times the rate of the server thread it feeds. On a
large network that is the single largest CPU cost of the mod, and most of it buys nothing
observable.

Add a JVM flag to slow it down:

```
-Dmtr.simulationTickMillis=50
```

That runs the simulation at 20 TPS, matching the server thread. Clamped to 10–1000 ms. **Unset means
10 ms, i.e. stock behaviour** — the flag is opt-in, and this mod changes nothing here unless you set
it. It has no effect at all when `useThreadedSimulation` is `false`, because then nothing is
scheduled in the first place.

Where to put it depends on your launcher:

```bash
# plain server start script
java -Xmx8G -Dmtr.simulationTickMillis=50 -jar forge-1.20.1-47.1.33-server.jar nogui
```
```
# Forge's user_jvm_args.txt
-Dmtr.simulationTickMillis=50
```

Confirm it took effect — this line appears at startup:

```
[Server thread/INFO] [MtrCorePerf/]: Simulation tick interval set to 50 ms (stock is 10 ms)
```

If you do not see it, either the flag is not reaching the JVM or threaded simulation is off.

### What it changes

| Target | Change | Kind |
|---|---|---|
| `Rail.tick1` | Swaps the two reservation buffers instead of `clear()` + `putAll()` — the latter rebuilds an AVL tree, one node allocation per entry, per rail, per tick | perf |
| `Rail.tick1` | Compares the two key sets in one ordered pass instead of `Utilities.sameItems`, which is `containsAll` in *both* directions | perf |
| `Rail.tick1` | Iterates `clients` with a plain loop instead of `forEach` plus a capturing lambda (one allocation per rail per tick) | perf |
| `Rail.isNotBlocked` | `LongIterator` instead of `longStream().allMatch(...)` | perf |
| `FileLoader.writeDirtyDataToFile` | Adds `TRUNCATE_EXISTING` to `Files.newOutputStream` | **bug fix** |
| `Main` | Makes the threaded simulation tick interval configurable | perf, opt-in |

`needsUpdate` is still computed from the same two set comparisons, before the buffers swap, and the
same clients are still updated with the same argument.

#### The save-corruption fix

`Files.newOutputStream(path, CREATE)` does **not** imply `TRUNCATE_EXISTING`. The implicit
`CREATE, TRUNCATE_EXISTING, WRITE` default only applies when the options array is *empty*; pass one
option and the set becomes exactly that option plus `WRITE`. So whenever a station, route, or depot
packs to fewer bytes than it did last save, the tail of the previous version survives and the
MessagePack stream is garbage past the new end. Worth installing for this alone.

### Why a companion mod instead of a patched jar

MTR 4.0.x does not bundle this branch of the Core. It bundles a build matching commit `ecde724`
(2025-10-14), compiled to **Java 8 bytecode** with its dependencies relocated into
`org.mtr.libraries.*`. A normal build of this repository reproduces neither the bytecode target nor
the relocation, so the `tools/assemble-mtr-core-patch.ps1` route used for 1.21.1 does not carry over.
Mixins sidestep both problems by patching the already-relocated, already-Java-8 classes at load time.

The same changes expressed as source against `ecde724` are in
[`docs/tsc-ecde724-source-backport.patch`](docs/tsc-ecde724-source-backport.patch), for review or for
anyone who does want to rebuild.

### Building from source

```bash
git clone -b perf/optimized-build-1.20.1 https://github.com/A-BenLi06/Transport-Simulation-Core.git
cd Transport-Simulation-Core/mtr-core-perf
# put the exact MTR jar your server runs into libs/, then point gradle.properties at it
./gradlew build
# output: build/libs/mtr-core-perf-1.0.0.jar
```

Needs JDK 17. The MTR jar in `libs/` is a `compileOnly` dependency used only to resolve the mixin
targets — it is never bundled, and it is not committed here because it is ~81 MB and not ours to
redistribute.

### Verifying a build

Do **not** use a ModDevGradle dev run. A dev runtime uses official (mojmap) names while a released
MTR jar is reobfuscated to SRG, so MTR's registration fails with `NoSuchFieldError: f_279569_` long
before the classes this mod patches are ever loaded. Test on a real Forge server:

1. `java -jar forge-1.20.1-47.1.33-installer.jar --installServer`
2. Put the MTR jar and `mtr-core-perf-1.0.0.jar` in `mods/`
3. Start with `-Dmixin.debug.export=true`
4. `.mixin.out/class/` should contain `org/mtr/core/data/Rail.class`, `org/mtr/core/Main.class`,
   and `org/mtr/core/simulation/FileLoader.class`
5. `javap -c -cp .mixin.out/class org.mtr.core.data.Rail` should show the new key-set comparison and
   **zero** occurrences of `sameItems`, `longStream`, and `putAll`

This was done against MTR 4.0.5 + Forge 47.1.33: all three mixins applied, and with
`useThreadedSimulation: true` the server logged
`Simulation tick interval set to 50 ms (stock is 10 ms)`.

### On upgrading MTR

`Rail.tick1` and `Rail.isNotBlocked` are `@Overwrite`s, and the `Main` redirect targets a synthetic
lambda method (`lambda$new$0`). A new MTR build can move any of these. The `Main` redirect is
`require = 0` and degrades to a no-op, but the two `@Overwrite`s will fail loudly at startup — which
is intended, since silently reverting to unpatched code would be worse. Re-verify on a test server
before upgrading MTR in production.

### Not included

The `circularClamp` / `circularDifference` rewrite from the 1.21.1 branch is deliberately left out.
The implementation MTR 4.0.x bundles folds into the half-open range `(-half, +half]`; `master`, and
therefore that rewrite, uses the closed range `[-half, +half]`. Brute-forced over 2.81M value pairs
the two disagree in 2564 cases, all sign flips at exactly half a period — e.g. `period=360,
v1=-360, v2=-180` gives `180` on the bundled version and `-180` on the rewrite. Since the bundled
code already does a divide-then-loop rather than a pure loop, the win was small and the risk to
timetable-deviation maths was not.

---

## 中文

### 安装

1. 从 [Releases](https://github.com/A-BenLi06/Transport-Simulation-Core/releases/tag/mtr-4.0.5-1.20.1-performance-v1)
   下载 `mtr-core-perf-1.0.0+1.20.1-performance.jar`。
2. 放进 `mods/`，**与你现有的 MTR jar 并存**。不要删除或替换 MTR。
3. 重启服务端。

主要作用在服务端，但客户端装上也是安全的。这个 jar 不含任何 MTR 或 Create 的内容，只有它自己的 4 个类。

**依赖要求**：Minecraft 1.20.1、Forge 47 及以上、MTR **4.0.5**（Forge 版）。
mod 把 MTR 声明为强制依赖，所以缺少 MTR 时 Forge 会直接拒绝启动，而不是静默不生效。

**卸载**：删掉 jar 即可，行为完全恢复原样。唯一涉及文件格式的改动（下面的截断修复）只会让存档
*更* 正确，没有需要回滚的东西。

校验下载：

```
sha256  6e2ecfab1c193382e1f9a81ad07a74f59f28abaf593b439fe4800569ead497e7
```

### 可选：模拟 tick 间隔

当 `config/mtr.json` 里 `useThreadedSimulation` 为 `true` 时，MTR 会在独立线程上以**硬编码的 10 ms
（100 TPS）** 跑模拟，是它所服务的服务端主线程速率的 5 倍。在大型线网上这是该 mod 最大的一笔 CPU
开销，而其中大部分并不会带来可观察的差别。

加一个 JVM 参数即可放慢：

```
-Dmtr.simulationTickMillis=50
```

这样模拟以 20 TPS 运行，与服务端主线程对齐。取值被限制在 10–1000 ms。
**不设置就是 10 ms，即原版行为** —— 这是个可选开关，不设置时本 mod 在这一项上不改变任何东西。
当 `useThreadedSimulation` 为 `false` 时该参数完全无效，因为那种情况下根本没有定时任务。

写在哪里取决于你的启动方式：

```bash
# 普通启动脚本
java -Xmx8G -Dmtr.simulationTickMillis=50 -jar forge-1.20.1-47.1.33-server.jar nogui
```
```
# Forge 的 user_jvm_args.txt
-Dmtr.simulationTickMillis=50
```

确认是否生效 —— 启动日志里会出现这一行：

```
[Server thread/INFO] [MtrCorePerf/]: Simulation tick interval set to 50 ms (stock is 10 ms)
```

如果没看到，说明参数没传到 JVM，或者 threaded simulation 是关闭的。

### 改了什么

| 目标 | 改动 | 性质 |
|---|---|---|
| `Rail.tick1` | 用两个缓冲区互换替代 `clear()` + `putAll()`。后者会重建一棵 AVL 树，每个条目一次节点分配，每条轨道、每 tick 都做一遍 | 性能 |
| `Rail.tick1` | 用一次有序遍历比较两个键集，替代 `Utilities.sameItems` —— 后者是**双向** `containsAll` | 性能 |
| `Rail.tick1` | 用普通循环遍历 `clients`，替代 `forEach` + 捕获型 lambda（每条轨道每 tick 一次分配） | 性能 |
| `Rail.isNotBlocked` | 用 `LongIterator` 替代 `longStream().allMatch(...)` | 性能 |
| `FileLoader.writeDirtyDataToFile` | 给 `Files.newOutputStream` 补上 `TRUNCATE_EXISTING` | **bug 修复** |
| `Main` | 让 threaded 模拟的 tick 间隔可配置 | 性能，可选 |

`needsUpdate` 仍然由同样的两次集合比较得出，且仍在缓冲区互换之前计算；被更新的客户端和传入的参数也
完全不变。

#### 关于存档损坏的修复

`Files.newOutputStream(path, CREATE)` **并不**隐含 `TRUNCATE_EXISTING`。
只有在 options 数组**为空**时才会套用 `CREATE, TRUNCATE_EXISTING, WRITE` 这组默认值；
一旦传入任意一个选项，实际生效的就只有「该选项 + `WRITE`」。
于是只要某个车站、线路或车库序列化后的字节数比上次保存时少，上一版的尾部数据就会残留下来，
MessagePack 流在新数据结束之后就是垃圾内容。单凭这一条就值得装。

### 为什么是配套 mod 而不是打补丁的 jar

MTR 4.0.x 并没有打包本仓库这个分支的 Core。它打包的是对应提交 `ecde724`（2025-10-14）的构建，
且是 **Java 8 字节码**，依赖被重定位到 `org.mtr.libraries.*`。
本仓库的常规构建既不产出那个字节码目标，也不做那套重定位，所以 1.21.1 用的
`tools/assemble-mtr-core-patch.ps1` 那条路在这里走不通。
Mixin 在类加载时改写「已经重定位、已经是 Java 8」的类，从而绕开这两个问题。

同样的改动以源码形式针对 `ecde724` 表达的版本，见
[`docs/tsc-ecde724-source-backport.patch`](docs/tsc-ecde724-source-backport.patch)，
供审阅或供确实想重新构建的人使用。

### 从源码构建

```bash
git clone -b perf/optimized-build-1.20.1 https://github.com/A-BenLi06/Transport-Simulation-Core.git
cd Transport-Simulation-Core/mtr-core-perf
# 把服务器实际运行的那个 MTR jar 放进 libs/，再在 gradle.properties 里指向它
./gradlew build
# 产物：build/libs/mtr-core-perf-1.0.0.jar
```

需要 JDK 17。`libs/` 里的 MTR jar 是 `compileOnly` 依赖，仅用于解析 mixin 目标 ——
它不会被打进产物，也没有提交到仓库里，因为它约 81 MB 且不属于我们可以再分发的内容。

### 如何验证构建

**不要**用 ModDevGradle 的开发运行。开发环境用的是 official（mojmap）名字，而发行版 MTR jar 是
重混淆成 SRG 的，所以 MTR 的注册阶段会先抛 `NoSuchFieldError: f_279569_`，
本 mod 要改的那些类根本还没被加载。请用真实 Forge 服务端测：

1. `java -jar forge-1.20.1-47.1.33-installer.jar --installServer`
2. 把 MTR jar 和 `mtr-core-perf-1.0.0.jar` 放进 `mods/`
3. 加 `-Dmixin.debug.export=true` 启动
4. `.mixin.out/class/` 下应出现 `org/mtr/core/data/Rail.class`、`org/mtr/core/Main.class`
   和 `org/mtr/core/simulation/FileLoader.class`
5. `javap -c -cp .mixin.out/class org.mtr.core.data.Rail` 应能看到新的键集比较逻辑，
   且 `sameItems`、`longStream`、`putAll` 各出现 **0** 次

本项目已按此流程在 MTR 4.0.5 + Forge 47.1.33 上验证：三个 mixin 全部注入成功；
在 `useThreadedSimulation: true` 下服务端输出了
`Simulation tick interval set to 50 ms (stock is 10 ms)`。

### 升级 MTR 时注意

`Rail.tick1` 和 `Rail.isNotBlocked` 用的是 `@Overwrite`，`Main` 的重定向打在合成 lambda 方法
（`lambda$new$0`）上。新版 MTR 可能移动其中任何一个。
`Main` 的重定向是 `require = 0`，找不到就退化为空操作；
但两个 `@Overwrite` 会在启动时直接报错 —— 这是刻意设计的，
因为静默退回未优化的代码比报错更糟。**在生产环境升级 MTR 之前，请先在测试服重新验证。**

### 未包含的改动

1.21.1 分支里的 `circularClamp` / `circularDifference` 重写被刻意排除了。
MTR 4.0.x 打包的那个实现把结果折叠进**半开区间** `(-half, +half]`，
而 `master`（以及基于它的那份重写）用的是**闭区间** `[-half, +half]`。
对 281 万组取值暴力比对，两者在 **2564** 处不一致，全部是「恰好半个周期」处的符号翻转 ——
例如 `period=360, v1=-360, v2=-180`，打包版给出 `180`，重写版给出 `-180`。
考虑到打包版本身已经是「先除后循环」而非纯循环，收益本就有限，
而时刻表偏差计算上的风险是实打实的。
