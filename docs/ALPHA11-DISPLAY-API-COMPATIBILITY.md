# Alpha.11: firmware API compatibility and recovery

Alpha.10 device report confirms inner task configuration sw704dp/w933dp but
cover-style Samsung Home, absent navigation, and cover Recents focus. The
setShouldShowSystemDecors(int,boolean) reflection lookup fails on this firmware.
This is not evidence that the same API exists with a guessed signature.

Alpha.11 checks setter availability before journaling or requesting a change.
Unsupported devices report relevant IWindowManager and IDisplayManager method
signatures once per app process instead of retrying a nonexistent setter.
Old journals are cleared only after the original value is read back on the same
physical panel, or after a supported restoration succeeds and matches readback.
Unresolved recovery is retained and retried once per minute.

Keeps always-on fold-awake supervision, working task transfer and animation.
Does not claim to enable Samsung inner Home or taskbar. A subsequent device
report is needed to identify firmware-specific supported control APIs.
