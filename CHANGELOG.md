## 1.5.4 — Regional Fold 8 setup

- Enable the SM-F971 regional family on Android 17, including unlocked U1 and Canadian W, in wallpaper setup and both display controllers.
- Keep Samsung component/API checks, named concurrent-display-state discovery, and wallpaper configuration readback. Regional firmware compatibility remains experimental.
- Report model/OS eligibility separately from Shizuku authorization and verified wallpaper configuration.
- Refresh helper process versions after upgrade. No animation, capture, timing, or settings migration changes.
- SM-F966 / Fold 7 on Android 16 remains outside this wallpaper profile.

# Changelog

## v1.5.1 — privacy-clean public release (versionCode 24)

- Neutral application ID, code namespaces, component authorities, diagnostics, and signing certificate.
- Duo Fold Live branding throughout app interfaces and internal app identifiers.
- Installs separately from older private builds; old app data is not overwritten or migrated.
- Retains guided first-install setup, both animations, custom photo wallpaper, and mandatory Samsung hinge wallpaper configuration.
- Upstream license and attribution notices remain intact.

The rendering/handoff pipeline is unchanged. The custom-wallpaper unlock flash remains unresolved. Setup and the renamed helper components require handset verification.
