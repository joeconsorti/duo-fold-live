# 1.8.1-alpha.6: native content during a fixed-mapping hold

Alpha.5 preserved a cover-shaped preview during ACTIVE, then remapped on exit.
That did not demonstrate a full inner-screen experience without blackout.

Alpha.6 keeps the same 20-second, one-shot mapping hold and tries one real task
transfer from display 0 to display 1 at 98 degrees. For Home, it focuses an
existing matching inner Home or asks Android to move the actual Home root.
Samsung may prohibit either operation. For an app it launches the existing task
on the destination display. The mirror is removed only after task placement
AND focus on the owning inner root are observed. These are software checks,
not proof of visible pixels, native layout, touch or navigation.

At 94 degrees or exit it attempts to return only the task/root owned by the
experiment. Cleanup checks physical identities before using display IDs.
It does not launch an app while locked. Return failures are reported; normal
mapping restoration remains the fallback. The probe does not force panel power,
inject input, or change system security policy. One transfer per armed test.

Reports distinguish Stop, close, stale angle, lost request, disabled/noninteractive
state and deadline, and retain display samples for two seconds after exit.
The ordinary handoff remains unchanged. Exit and fold blackout remain unresolved
until handset tests establish otherwise. The normal inner expansion animation
is disabled during the hold so it cannot cover native content.

Validation: policy exit-reason tests; fake-framework Home routing, cleanup,
rejection, unfulfilled move and unrelated-focus tests; GitHub Actions runs all
release unit tests, lint, build, permanent-key signing and signature verification.
