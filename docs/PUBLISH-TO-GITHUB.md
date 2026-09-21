# Publish Duo Fold Live from your own GitHub account

Use the GitHub account you want to own this project. It does not need to match your ChatGPT email. Nothing in this package has been published automatically.

## Package contents

- `source/`: complete repository contents, including README, MIT license, third-party notices, build wrapper, and tests. Publish the **contents** of this folder as the repository root.
- `release/Duo-Fold-Live-v1.5.1.apk`: signed installable APK.
- `release/RELEASE-NOTES.md`: text to paste into the GitHub release.
- `release/SHA256SUMS.txt`: APK checksum.
- `release/VALIDATION.txt`: build/test/signature verification and handset-test limits.

Only `source/` goes into the source repository. The APK goes in a GitHub **Release**. Do not upload old private source archives, signing keys, device logs, or your own photos.

## Recommended: GitHub Desktop

1. Install [GitHub Desktop](https://desktop.github.com/) and sign into the account that should own the project. Check the account under Settings → Accounts.
2. Choose File → New repository. Name it `duo-fold-live`, choose a local folder, and create it. Do not add a license or README; this package already contains them.
3. Open the newly created repository folder. Copy everything **inside** this package's `source/` folder into that repository folder, including `.gitignore`. Do not nest the whole `source` folder inside it.
4. In GitHub Desktop, review the changed files. Commit them with the summary `Initial public release v1.5.1`.
5. Click Publish repository, select the intended owner/account, and uncheck “Keep this code private” to make it open source. Publish.

All source files should appear on GitHub as real browsable files, rather than as one ZIP attachment. On Windows, build with `gradlew.bat`; on Linux/macOS, use `sh gradlew` if executable permissions were not retained while extracting the ZIP.

## Create the release

1. Open the repository on GitHub, then Releases → Draft a new release.
2. Create tag **v1.5.1**, targeting the main branch.
3. Set the title to **Duo Fold Live v1.5.1**.
4. Paste `release/RELEASE-NOTES.md` into the description.
5. Attach `Duo-Fold-Live-v1.5.1.apk` and `SHA256SUMS.txt` from `release/`.
6. Save as a draft until you have checked this APK on your phone. This new identity runs onboarding on first launch. Keep your old working app installed but disabled while testing the new app.
7. When satisfied, publish the release. You may mark it as a pre-release while collecting first-install feedback.

This release has a new neutral package identity and signer. It installs separately from earlier private builds. Stop the old app's animation/photo layer before enabling this one. It does not overwrite the old app's settings.

## Signing

No private signing key is included in this publishing kit. Keep the new release signing key privately backed up for future releases. Do not upload any signing key or older private source archives. Future updates to this new application must retain `org.duofold.live` and the new signing identity and increase versionCode above 24.

## Replacing already-published files

Old commits and release assets can retain the previous identifying text even after you replace files. For a newly created repository with no work to preserve, publish this clean source in a fresh repository and remove the old repository and old release assets. Copies already downloaded by others cannot be recalled. Never upload the old publishing kit alongside this one.

## Official references

- [GitHub: adding files](https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository)
- [GitHub: creating releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
- [GitHub Desktop](https://desktop.github.com/)
