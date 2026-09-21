# Publish Duo Fold Live from your own GitHub account

Use the GitHub account you want to own this project. It does not need to match your ChatGPT email. Nothing in this package has been published automatically.

## Package contents

- `source/`: complete repository contents, including README, MIT license, third-party notices, build wrapper, and tests. Publish the **contents** of this folder as the repository root.
- `release/Duo-Fold-Live-v1.5.0.apk`: signed installable APK.
- `release/RELEASE-NOTES.md`: text to paste into the GitHub release.
- `release/SHA256SUMS.txt`: APK checksum.
- `release/VALIDATION.txt`: build/test/signature verification and handset-test limits.

Only `source/` goes into the source repository. The APK goes in a GitHub **Release**. Do not upload old private source archives, signing keys, device logs, or your own photos.

## Recommended: GitHub Desktop

1. Install [GitHub Desktop](https://desktop.github.com/) and sign into the account that should own the project. Check the account under Settings → Accounts.
2. Choose File → New repository. Name it `duo-fold-live`, choose a local folder, and create it. Do not add a license or README; this package already contains them.
3. Open the newly created repository folder. Copy everything **inside** this package's `source/` folder into that repository folder, including `.gitignore`. Do not nest the whole `source` folder inside it.
4. In GitHub Desktop, review the changed files. Commit them with the summary `Initial public release v1.5.0`.
5. Click Publish repository, select the intended owner/account, and uncheck “Keep this code private” to make it open source. Publish.

All source files should appear on GitHub as real browsable files, rather than as one ZIP attachment. On Windows, build with `gradlew.bat`; on Linux/macOS, use `sh gradlew` if executable permissions were not retained while extracting the ZIP.

## Create the release

1. Open the repository on GitHub, then Releases → Draft a new release.
2. Create tag **v1.5.0**, targeting the main branch.
3. Set the title to **Duo Fold Live v1.5.0**.
4. Paste `release/RELEASE-NOTES.md` into the description.
5. Attach `Duo-Fold-Live-v1.5.0.apk` and `SHA256SUMS.txt` from `release/`.
6. Save as a draft until you have checked this APK on your phone. New-install onboarding also needs a supported-phone test; your existing installation intentionally skips it. Do not uninstall your working copy just to test onboarding.
7. When satisfied, publish the release. You may mark it as a pre-release while collecting first-install feedback.

The internal version code is **23**, so this public v1.5.0 APK can update the earlier private v1.6.0 APK (code 22). Existing users should install over the old app, not uninstall it.

## Future releases and signing

The repository builds an unsigned release unless you supply the signing variables documented in README. The delivered APK uses the same certificate as your previous working build. Keep the original signing keystore privately backed up; it is intentionally absent from this publication kit. Your earlier private 1.6.0 source archive contained `signing/standalone.jks`; do **not** publish that archive or key. If that private key is lost, a new signer cannot directly update these existing installations.

Increase `versionCode` beyond 23 for the next release, regardless of the displayed version name. Keep package name `com.consorti.foliofold` unchanged for compatible updates.

## Official references

- [GitHub: adding files](https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository)
- [GitHub: creating releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
- [GitHub Desktop](https://desktop.github.com/)
