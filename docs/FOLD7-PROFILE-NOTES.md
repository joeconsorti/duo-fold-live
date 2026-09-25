# Fold7 wallpaper selection (3.0.3)

Source inspected: bunkaich/Folduo commit c9e5976cf1d5176652fcf0fc984bb5ec86751d29, tools/CoverWallpaperSetup.java and README.md. Its SM-F966Z setup checks video_002.mp4 on inner HOME and copies that profile's extras to cover HOME; sub_wallpaper_002 is the original static cover image, not the final angle-reading wallpaper.

Duo now selects video_002.mp4 for regional SM-F966 models and retains video_001.mp4 for SM-F971/SM-F976. Unknown models must select Fold7 or Fold8/Ultra in the warning area. Selection persists, invalidates previous wallpaper verification, and reaches the shell setup helper. Recognized model detection takes precedence over a saved manual choice.

Both HOME bindings are checked against the selected filename. Existing matching profile extras are preferred; the fallback Fold7 profile uses frame zero instead of assuming the Fold8 thumbnail offset. That synthesized profile still requires phone validation. Lock slots, animation rendering, and Android17 eligibility remain unchanged. The reference's Android16 compatibility is not automatically established for Duo.

Existing users can run Connection & setup → Show setup → Repair required wallpaper setup. Included in the 3.0.3 release.
