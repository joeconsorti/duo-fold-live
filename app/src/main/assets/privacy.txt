# Duo Fold Live privacy policy

Effective October 29, 2026. Developer: Joe Consorti. This policy covers the GitHub and Google Play editions of Duo Fold Live (org.duofold.live).

## Screen processing and accessibility

After separate affirmative consent, Duo uses an authorized Shizuku or existing-root helper to capture and mirror displayed app content while fold animation is enabled and the phone is unlocked. Screen content can include personal information. Frames are processed locally in memory for glass effects and transitions; Duo does not save screen recordings or upload captured frames. Protected/secure captures are rejected. This privileged route does not use Android's MediaProjection permission dialog.

The AccessibilityService hosts non-interactive animation overlays across displays, including over other apps. During unlocked Home transitions with custom wallpaper, it captures the focused launcher window using Android's window screenshot API. This separates launcher pixels from wallpaper and other overlays. It does not retrieve node text, perform gestures, or automate taps. Ordinary app capture continues through the privileged helper. Duo is not designed as an accessibility tool for users with disabilities. The optional custom-wallpaper instrumentation helper uses UiAutomation window events, root package names, focus and geometry to identify Home and system windows and position the photo, including beneath lock-screen UI. It does not traverse node text or automate user actions. This metadata stays local and may appear in diagnostic reports you explicitly share. No accessibility data is automatically transmitted to the developer.

## Privileged access and device changes

Shizuku+ is the recommended connection. Users install and authorize it separately. Existing root access is an optional alternative; Duo does not root the device. The helper reads filtered Samsung wallpaper/hinge diagnostics and controls supported display handoffs. Setup changes both HOME wallpaper bindings after explanation and user authorization. A custom photo is optional. The custom-wallpaper host uses Android instrumentation and privileged window APIs. Optional keep-awake changes Samsung's fold-lock setting and uses a bounded closing wake hold. These capabilities operate locally.

## Photos, settings and diagnostics

Photos selected through Android's document picker are copied into app-private storage for the wallpaper. Originals in your photo library are not deleted. Preferences, consent choices, wallpaper photos/crops, and wallpaper diagnostic reports remain locally until replaced, cleared, or the app is uninstalled. Routine animation frames are held in memory rather than retained as photos.

Arming image diagnostics explicitly saves bounded local compositor samples and source images. Home inner-display sequences can include the isolated launcher image before wallpaper composition. Exported ZIPs can contain visible icons and widget content; inspect them before sharing. Nothing is automatically uploaded.

Diagnostics can contain device/build information, hinge angles, display state, helper errors, app task/component names and timing. The health history is bounded to 288 samples; samples older than 24 hours are removed when a new sample is added. Other diagnostics use bounded histories or local report files. Reports remain local unless you explicitly copy, export or share them. Review reports before sharing; copies in your clipboard, Downloads, or another app must be deleted separately.

Duo Mode (screenshots) records bounded transition
history and local app/task, display and brightness diagnostics while enabled.
Recovery evidence includes a bounded previous-session journal, boot identity,
helper resource counts and recent system crash-log entries (which can include
other app/component names). Review copied reports before sharing.
It uses temporary in-memory screen buffers and privileged display/brightness
overrides to protect automatic hinge-driven handoffs. Shell-owned diagnostics
are retained under `/data/local/tmp/duo-live-b8-*`; older session folders are
pruned when a new session starts. Clearing Duo app storage does not delete those
shell-owned files. Status reports are copied or shared only on request.
Older manual Lab recordings may remain under `/data/local/tmp/fold-power-lab-*`.

## Collection, sharing and external services

Duo has no account system, advertising SDK, analytics service, or automatic server-upload feature. It does not sell personal data. Local processing is necessary for the features you enable. Links open external services such as GitHub, Shizuku documentation and, in the GitHub edition, optional Ko-fi support. Those services have their own privacy practices. Google handles Play purchases; Duo does not receive your payment-card details. The Play edition does not show donation prompts.

GitHub issues and attachments you submit are public unless GitHub indicates otherwise. Please do not post sensitive screen content or personal information in public reports.

## Your choices and deletion

Open Privacy & controls to decline or withdraw either consent, stop all Duo background work, or change optional keep-awake. Stop all disables animation, custom wallpaper and keep-awake supervision; it retains your photos and settings. If authorized access is available, Duo attempts to restore the fold-lock setting saved before its first opted-in change, provided another app/user has not changed it meanwhile. Builds predating this policy did not save that original setting; check Samsung's Lock when folded setting manually after upgrading. If restoration is pending, reconnect Shizuku and reopen Privacy & controls, or change the setting in Samsung Settings.

Revoke Shizuku authorization in Shizuku and accessibility/overlay permissions in Android Settings if desired. Stopping does not restore the previous Samsung home wallpaper automatically: select another wallpaper in Samsung Settings. Clear Duo's app storage or uninstall it to delete its private photos, preferences and diagnostic files. Separately delete any reports/photos you exported or shared. No developer-held account data needs deletion because Duo has no accounts or automatic data uploads.

## Contact and updates

For privacy questions or deletion requests concerning a report you previously shared with the developer, use https://github.com/joeconsorti/duo-fold-live/issues and identify the report without reposting sensitive content. The developer can remove material under their control; third-party copies are subject to the service's policies. Policy changes will be published here and included in the app.
