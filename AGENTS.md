# DuofoldX development guardrails

- This repository is an independent fork of Duo Fold Live v3.6.0. Preserve the upstream MIT license, copyright headers, bundled license files and THIRD_PARTY_NOTICES.md in all distributions.
- The approved first setup commit may be pushed to the dedicated `duofoldx/bootstrap` branch. This is not permission for subsequent pushes, merges, releases, APK publication, Play publication or deployment. Obtain explicit approval for each later remote publication action.
- GitHub Actions must remain test-only with `contents: read`. Never add signing, artifact upload, release creation, deployment or other publishing permissions without explicit approval.
- Keep the upstream stable branch and release history unchanged. Do all product development on DuofoldX branches.
- Do not unlock the bootloader, root the target device, flash firmware, modify system partitions or replace Samsung System UI. Use non-root workflows and separately authorized Shizuku access only where necessary.
- Keep signing keys and credentials private; never commit them, include them in archives or print them in logs.
- Preserve complete source, tests, build configuration, licensing and recovery notes. Do not discard uncommitted work.
- Distinguish software tests and submitted frames from actual optical panel behavior. Do not claim physical FPS, flicker elimination or SM-F976B compatibility without measured handset evidence.

---

# Duo Fold source preservation

- Public stable promotion approved **2026-11-11**: publish alpha.111's unchanged runtime as **3.6.0-reconstructed**, tag **v3.6.0**, on **main**, with the existing public package/signer and GitHub Latest status. Include prominent in-app rotation-repair instructions in the README. See `docs/3.6.0-reconstructed-CHANGELOG.md`. Preserve all earlier release and rollback references.

- Public stable promotion approved **2026-11-09**: publish alpha.109's runtime as **3.5.9-reconstructed**, tag **v3.5.9**, public package **org.duofold.live**, with the existing public signer and GitHub Latest status. Preserve the historical alpha.107 rollback reference below. See `docs/3.5.9-reconstructed-CHANGELOG.md`.

- Latest user-approved stable rollback reference: **3.5.9-alpha.107-reconstructed-local**, versionCode **1360**, approved **2026-11-09**. See `STABLE_REFERENCE.json` and `docs/STABLE-REFERENCE.md` for the exact code commit, artifact hashes, original APK/source identities, and observed settings.
- Preserve the `stable-reference/3.5.9-alpha.107` tag at the tested code commit. Future experiments must use a new version and must not replace the archived alpha.107 APK/source or silently promote a new stable reference.
- Recovery target is alpha.20 (3.5.9-alpha.20-local), not alpha.18.
- Reconstructed releases must identify themselves as reconstructed; do not claim the original source or identical handset behavior was recovered.
- Before a delivery build, commit the complete source, tests, build configuration and recovery notes. Never discard uncommitted work.
- Use tools/build_local_test.py. It refuses a dirty tree and emits a source archive and Git bundle with the APK and a manifest tying their hashes to the source commit.
- Before handing an APK to the user, save both APK and source archive outside this transient workspace. Verify each save succeeded. A local Git commit or download link alone is not a durable backup.
- Keep the debug signing key private and separately backed up; never include it in source archives or logs.
- Do not push to GitHub or publish releases unless the user explicitly requests it.
- Software commits, HAS_DRAWN and screenshots are not optical panel proof. Distinguish missing measurements, historical sessions, and current-build evidence.
