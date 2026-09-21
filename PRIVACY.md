# Privacy

Duo uses accessibility screen capture and overlays to render fold transitions. Captured screen frames are processed locally for the animation. Protected content may be unavailable. A selected custom photo is copied into app-private storage. Diagnostics contain device/build details, hinge measurements, display state, and local errors; inspect a report before sharing it.

Shizuku grants the helper ADB-shell privileges. The helper reads filtered Samsung wallpaper logs, requests wallpaper commands, and controls the supported display handoff. First-run setup changes both HOME wallpaper bindings after the setup explanation and Shizuku authorization. The custom-wallpaper host uses Android instrumentation and privileged window APIs. These are powerful capabilities, so review the code before granting access.

The application does not include analytics or a server upload feature. Download/help links open your browser. GitHub issue reports and any attachments you publish there are public unless GitHub indicates otherwise.

Disable the animation and custom wallpaper to stop their effects. Samsung wallpaper settings restore a different underlying wallpaper. Shizuku authorization can be revoked in Shizuku; accessibility and overlay access can be revoked in Android Settings.
