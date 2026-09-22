# Alpha.8: native inner window and focus diagnosis

Alpha.7 handset evidence: the selected app stayed on display 1, but global focus
stayed on cover Home. The native Presentation failed repeatedly with
InvalidDisplayException. Visible task placement was not an interactive handoff.

Use a display-specific accessibility overlay for the native inner animation,
matching the working primary animation window type. It is transparent, does
not receive focus/touch, and continues to use display-1 capture during the hold.
A task on an available display does not guarantee that TYPE_PRESENTATION is
supported; an InvalidDisplayException alone does not prove the display vanished.

After root focus, also request focus for the actual task. Record focus again
later, display flags, navigation-bar presence, system-decoration support and IME
policy. These capability queries are read-only; no persistent display policy or
power changes are introduced. This is not a claim that navigation is fixed.
The existing bounded hold/restore and signer remain unchanged. Version 1007.
