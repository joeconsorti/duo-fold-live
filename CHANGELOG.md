# Changelog

## v1.5.0 — first public release (versionCode 23)

- Renamed the default animation to **iPhone Duo Inspired**; Classic Glass remains available.
- Added resumable first-install onboarding with official Shizuku download links and wireless/USB debugging instructions.
- Added mandatory, verified Samsung interactive wallpaper setup on both inner and cover home screens through Shizuku.
- Added optional photo selection during setup and automatic photo-host launch when setup finishes.
- Added overlay/accessibility setup checks and optional notification access.
- Existing installs bypass onboarding and retain their data and configuration.
- Removed signing keys and device-specific development artifacts from public source. Release signing uses external environment variables.

The working rendering/handoff pipeline is unchanged. The custom-wallpaper unlock flash remains unresolved. Fresh-install setup requires testing on the supported handset.
