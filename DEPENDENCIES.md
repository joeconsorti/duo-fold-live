# Dependencies

The root build no longer vendors any Shizuku client binaries (`app/libs/` was removed).

| Dependency | Version | Purpose | License |
| --- | --- | --- | --- |
| `com.github.topjohnwu.libsu:core` | 6.0.0 | Root shell access (KernelSU / Magisk) | Apache-2.0 |
| `com.github.topjohnwu.libsu:service` | 6.0.0 | `RootService`: runs Duo's privileged Binder helpers in a root process | Apache-2.0 |

libsu is resolved from JitPack (`https://jitpack.io`, configured in `settings.gradle.kts`).
