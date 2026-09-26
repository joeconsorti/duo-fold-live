# Saved wallpaper investigation idea

Joe's requested idea (2026-09-26): inject the user's selected custom image into Samsung's existing FoldInteractive live wallpaper, or use that wallpaper as the base for a custom version, instead of covering its image with separate surfaces. His hypothesis is that rendering the image inside the wallpaper itself could eliminate the unlock/fold layering flash while retaining everything else.

Keep this idea for future work. It is not implemented or proven. Investigate a supported image/asset configuration route first. Repackaging a Samsung APK invalidates its signature, and a separate implementation must preserve the angle feed currently supplied by the active Samsung wallpaper. Do not claim the flash will necessarily disappear until tested.

Current decision: alpha.16 is the user's best available release and official main/latest build, still labeled alpha. Occasional immediate and delayed unlock flashing remains. The original alpha.13 rollback release must remain available. Do not start the injection experiment without a clear decision to pursue it.
