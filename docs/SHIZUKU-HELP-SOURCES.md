# In-app troubleshooting sources and corrections

The user-supplied shizuku_troubleshooting.txt was reviewed in full. Its practical steps were reorganized into the in-app guide, but unsupported causal claims and guarantees were not reproduced as facts.

- Official Shizuku setup/recovery: https://shizuku.rikka.app/guide/setup/
- Android ADB and TCP startup: https://developer.android.com/tools/adb
- Shizuku+ project (independent fork; version-specific controls): https://github.com/thejaustin/ShizukuPlus
- Samsung background usage limits: https://www.samsung.com/us/support/answer/ANS10003442/
- Samsung Auto Blocker and USB command restrictions: https://www.samsung.com/sg/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/

Corrections: adb tcpip starts TCP listening, not Shizuku, and does not bind the listener exclusively to loopback. The phone's 127.0.0.1 is not the PC's 127.0.0.1. ADB connect is not a wake-lock operation. Doze does not establish the claimed universal localhost shutdown diagnosis. Debugging-only USB configuration and child-process settings vary by firmware; they are optional troubleshooting, not guaranteed fixes. Global phantom-process overrides and speculative one-second watchdog scripts are not offered as reliable solutions. Restrict battery exceptions to the necessary apps before disabling global sleep policies. Auto Blocker tradeoffs are explained instead of blanket instructions to disable security.

Non-root service startup normally needs repeating after reboot; saved pairing and battery settings generally do not. Offline operation must be tested on the user's actual firmware and Shizuku+ build.
