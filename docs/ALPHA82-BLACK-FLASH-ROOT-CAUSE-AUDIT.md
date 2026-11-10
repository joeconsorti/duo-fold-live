# Alpha.82 black-flash investigation

Investigation date: 2026-11-02. Device reported by the user: SM-F971U / Android 17.
Runtime under investigation: 3.5.9-alpha.82-reconstructed-local, commit
`49fbd43486adb39680e34a7056942467a0d5bb38`. No runtime change or new APK is part of
this investigation. Alpha.82 itself only changed diagnostic export from alpha.81.

## Finding

The strongest evidence points to the native primary-screen exchange during the
moving animation. Two separate mechanisms require attention:

1. A power transition accompanies primary requests in the software observations,
   including while both-ON and physical brightness holds are active. Longer
   endpoint holds do not address that event.
2. Duo's moving layers follow logical layer stacks. They are not permanently
   attached to physical panels. The alpha.80 geometry guard can retain the wrong
   assignment if Android changes physical routing before its DisplayInfo geometry
   settles. An unchanged-production-code replay reproduces black guard exposure
   in that ordering. The reverse ordering also matters: alpha.51/79 can map too
   early. Neither heuristic provides continuous physical coverage in both cases.

The replay establishes a code-level counterexample, not that every observed phone
flash is caused by it. The current logs do not record SurfaceFlinger's actual
physical routing alongside every layer transaction. They cannot determine the
ordering for each flash, its optical duration, or whether all flashes share one
cause. Black guard exposure in this replay is partial-screen; it does not explain
away the separately reported whole-screen shutoff.

## Historical baseline recovered

The preserved alpha.51 notes record glitch-free slow folds on alpha.50, with some
fast-fold glitches. The alpha.52 notes record the user's description of ordinary
app folding on alpha.51 as flawless. Home remained a separate problem. Those are
useful user reports, not proof of flawless Home or every possible motion.

Recovered `Duo-Fold-Live-alpha51-build-manifest.json` ties alpha.51 to commit
`95b41538df3a646605102badfa7dc1a308f50a68`, versionCode 1304, APK SHA-256
`a5458ae1a8a5bbc95b6f7e0a0059b22b2be31f3150ac66429026e20a220d4c34`.
The production source at that commit was compared directly with alpha.82.
Recovered `Duo-composition-1793243289033.zip` is CRC-clean and contains 16 Home
composition samples from four sessions. It lacks a complete power timeline and
cannot serve as a no-OFF baseline. The original Part 5 messages were not recovered
reliably by conversation search; the comparison relies on these preserved notes,
manifest, source history and diagnostic archive. The old APK was not run here.

Relevant source history:

| Build | Relevant behavior |
| --- | --- |
| alpha.50 / `a69296d` | Independent moving app layers replace static handoff shields. |
| alpha.51 / `95b4153` | Native Home route and corrected opaque-guard teardown; Home still uses the normal handoff band. |
| alpha.55 / `032d89f` | Home first primary request at the saved 85-degree handoff. |
| alpha.56 / `0432c229` | Home first primary request moves to 20 degrees opening / 152 degrees closing with a 172-degree open threshold, after preparation. |
| alpha.76 / `784dcb7` | Runtime rolled back to alpha.73 after reports of real screen shutoff. The notes do not establish alpha.73 as a verified last-good power baseline. |
| alpha.80 / `c755bb2` | Root remapping now waits for both physical identities, saved geometry and rotation, plus a second consistent read. |
| alpha.81 / `378d176` | Bounded active Home brightness preparation and serialized closing power tail. |
| alpha.82 / `49fbd43` | Diagnostic export only; retains the runtime behavior above. |

The normal both-ON and primary request methods in LiveB8Power are unchanged from
alpha.51 to alpha.82. A later pause-only OFF lease exists, but the four latest
sessions do not enter that pause route. This is not evidence of a newly changed
normal ON API. Scheduling and layer mapping changed around the same primary path.

## Latest four Home sessions

Source: `Duo-composition-1793588038685.zip`, `fold-timeline.tsv`, and each session's
`live-journal.txt` / `physical-brightness-hold.txt`. All times below are elapsed
realtime milliseconds. First presentation is a software callback, not first light.

| Session / direction | First presentation callback | Primary request | Request after callback | Immediate observation |
| --- | ---: | ---: | ---: | --- |
| 524225460 / opening | 524225814 | 524226544 | 730 ms | D1 OFF callback at 524226576, 32 ms later. |
| 524229765 / closing | 524229992 | 524230405 | 413 ms | D0 and D1 OFF callbacks at 524230445, 40 ms later. |
| 524233468 / opening | 524233713 | 524234027 | 314 ms | Mixed physical identity/geometry at 524234107; no OFF callback captured in this handoff. |
| 524236956 / closing | 524237280 | 524237392 | 112 ms | D1 OFF callback at 524237429, 37 ms later. |

The OFF callbacks have unknown physical identity during remapping. Do not turn
their intervals into physical blackout measurements. An earlier alpha.82 export
also associates both physical IDs with OFF at 521659963 and ON at 521660001 after
the app-session primary request; those sequential samples include mixed geometry
and are still software evidence. Another Home close records cover OFF at 521669606
and ON at 521669642. Brightness protection was active during those exchanges.

In the latest four sessions the Home brightness holds begin immediately before
the primary requests. None had already expired at that moment. The opening holds
do end 191–256 ms before their endpoints, but that cannot explain a flash just
after animation startup or around the primary exchange.

The request angles in the latest Home traces are 37, 152, 20 and 152 degrees.
Alpha.56's earlier Home promotion and variable preparation time explain why this
exchange can occur shortly after startup. Ordinary apps retain the later
85 +/- 20 degree handoff band. This is a scheduling explanation, not a claim that
alpha.56 alone introduced every flash.

## Reproduced layer-mapping race

`LiveB8Scene` creates one root and a large opaque black backing for each panel.
`B8BufferLayer` places an opaque panel-sized buffer above that backing. Roots
follow the layer stacks obtained from separate D0/D1 DisplayInfo reads.

At 524234107, the latest log reports D0 as the inner physical ID with the cover's
1248x1972 geometry. D1 identifies the cover with the same cover geometry. At
524234165, D0 reports the proper 2448x1848 inner geometry. These are observations
58 ms apart; they do not measure how long the scene retained a wrong mapping.

The new replay runs the unchanged production LiveB8Scene and SceneMapping from
alpha.51, .79, .80 and .82 against the same mixed-row shape. Logical stack values
are normalized to 0 and 1. It separately models physical output routing in both
possible orders. Buffer rectangles remain fixed, opaque and correctly sized;
there are no rendering stalls, missing buffers, power changes or shader effects.

| Ordering of the same mixed metadata | alpha.51 / .79 | alpha.80 / .82 |
| --- | --- | --- |
| Physical routing already changed; geometry lags | Matching buffers | Wrong roots; black backing visible at both selected sample points |
| Metadata leads the physical routing change | Wrong roots; black backing visible at both selected sample points | Matching buffers |
| Final routing and metadata agree | Matching buffers | Matching buffers |

The inner sample is (1800,900), outside a misplaced 1248-wide cover buffer. The
cover sample is (600,1900), below a misplaced 1848-high inner buffer. Both fall
inside the 2448-square backing. The replay does not render a whole-screen blackout.
It proves that retaining the old mapping is not equivalent to retaining correct
physical coverage, and that immediately trusting the new mapping is not a general
fix either. The earlier scene tests only checked root stack assignments and
visibility; they did not model physical display routing independently.

Validation: the existing production scene suite still passes all 30 checks. The
new replay compiles and runs all four historical implementations, exercises both
orders for each, and checks initial and final coverage. The exported replay was
also executed standalone with Java 17. No handset was connected or modified.

## What the evidence does not support

- Increasing endpoint timers or repeatedly waking the phone as the remedy for
  these early/middle events. No sleep/wake event aligns with the latest exchanges.
- Treating repeated idle ROTATION_STATUS text as repeated rotation restoration.
  Actual restore traces occur at cleanup; status publication is separate.
- Blaming the historical ChatGPT OOM entries. They are a different process and
  are not evidence of a Duo crash during these sessions.
- Blaming a blank buffer fallback. The latest sessions have no fence timeout or
  renderer error; one close has buffer misses, which retain the previous image.
- Calling an alpha.51 rollback a verified complete fix. It has its own ordering
  counterexample, and its positive ordinary-app report does not cover current Home.
- Declaring the flashes universally unavoidable. The preserved firmware audit
  identifies a normal remap path that waits for OFF and can reject ON while
  transitioning. It does not exhaust every Samsung mechanism or invalidate the
  user's earlier successful observations.

## Correction target

The correction target is synchronization between physical routing and Duo's
layer assignment, plus the separate power transition triggered by native primary
exchange. More capture-readiness gates cannot certify either of those facts.

For layers, use an authoritative physical-display composition state if the supplied
firmware exposes an accessible route, or synchronize with the display-routing
transaction. Do not merely replace the alpha.80 guard with the older heuristic.
Even querying actual compositor state is not atomic with a later layer transaction;
the remaining race must be accounted for. AOSP's native SurfaceFlinger
getDisplayState reads the display's layer stack and projection, which identifies
the relevant source of truth, but that is not verification of a usable Java or
Shizuku API on this phone. The current AOSP SurfaceControl Java source inspected
here did not expose that method.

For power, a drawn screenshot cannot conceal a panel that is actually off. The
native transition route must be evaluated separately from brightness submission
success. The available records establish where to focus, not a proven zero-OFF
replacement API. No speculative alpha.83 APK is presented as a fix.

Supporting AOSP sources (architectural context, not Samsung handset verification):

- https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android14-gsi/services/surfaceflinger/SurfaceFlinger.cpp — getDisplayState.
- https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/SurfaceControl.java — layer hierarchy and API inspection.

## Reproduction

In the repository: `python3 tools/replay_b8_physical_routing.py`.
In the supplied audit ZIP: extract, then run
`python3 replay/replay_b8_physical_routing.py` with Java 17 on PATH.
See `replay/replay-results.txt` for recorded output. Source snapshots retain their
full Git commit IDs. `evidence/` contains selected original timeline rows and
brightness/request journals; the manifest hashes the packaged files.
