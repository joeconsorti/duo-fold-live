# Saved wallpaper investigation idea

Joe's requested idea (2026-09-26): inject the user's selected custom image into Samsung's existing FoldInteractive live wallpaper, or use that wallpaper as the base for a custom version, instead of covering its image with separate surfaces. His hypothesis is that rendering the image inside the wallpaper itself could eliminate the unlock/fold layering flash while retaining everything else.

Keep this idea for future work. It is not implemented or proven. Investigate a supported image/asset configuration route first. Repackaging a Samsung APK invalidates its signature, and a separate implementation must preserve the angle feed currently supplied by the active Samsung wallpaper. Do not claim the flash will necessarily disappear until tested.

Current decision: alpha.16 is the user's best available release and official main/latest build, still labeled alpha. Occasional immediate and delayed unlock flashing remains. The original alpha.13 rollback release must remain available. Do not start the injection experiment without a clear decision to pursue it.

## Next build: Support the Project reminder cadence

User-requested on 2026-09-26; saved for implementation in the next app build, not implemented by this documentation change.

- First invitation: 3 days after the existing recorded first-use/start timestamp, replacing the current one-week delay. Download time is not currently recorded; preserve the existing successful-use eligibility checks.
- Subsequent invitations: 7 days after the first, 14 days after the second, 30 days after the third, then every 30 days. Nominal cumulative days: 3, 10, 24, 54, 84, 114, and so on. Treat a month as 30 days for this interval schedule.
- Existing eligible users already past the first threshold may receive one invitation at the next eligible app/background check after upgrading, subject to notification permission, snooze and opt-out. Do not promise delivery at package-install time. If no usable start timestamp exists, initialize it once and wait 3 days.
- Persist schedule stage, last successful invitation and next due time across updates. Migrate existing weekly history once; users recently prompted must not be prompted again merely because they update. Never reset clocks on each release, replay missed invitations in a burst, or issue multiple catch-up notifications.
- Preserve permanent dismissal and active snooze. Remove the old three-weekly-invitation cap only for users who have not opted out; distinguish automatic completion from explicit opt-out where existing data permits, otherwise preserve suppression.
- Coordinate notification and in-app prompt history to avoid duplicate invitations for the same schedule stage. Record delivery only when it actually succeeds.
- Update public wording that currently says weekly after one week. Verify migration, missing history, repeated upgrades, snooze/opt-out, denied notifications and missed intervals before shipping.
