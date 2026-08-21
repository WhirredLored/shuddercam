# ShudderCam Milestones & Exit Gates

## M1 — Foundation [C01–C04] → tag v0.1.0
Scaffold, brand, live preview, and the capability probe that everything else depends on.
EXIT GATE: app runs on two physically different devices; diagnostics screen correctly reports
different supported ranges on each; no control UI exists yet.

## M2 — Manual Control Engine [C05–C11] → tag v0.2.0
Every modifiable camera function exposed, capability-gated, built on the override architecture.
EXIT GATE: on a FULL-level device, every supported control visibly changes the live preview;
on a LIMITED device, unsupported controls are absent (not grayed, absent); photo capture works
with manual settings applied.

## M3 — Presets [C12–C13] → tag v0.3.0
Built-in presets as immutable code constants + user-saved presets as a separate layer.
EXIT GATE: preset switch, per-control reset, reset-all, and save-as-custom all behave per spec
after an app restart.

## M4 — Edgeless / TrueSight [C14–C15] → tag v0.4.0
True edge-to-edge fullscreen and the see-through-screen illusion.
EXIT GATE: preview fills the entire panel including cutout and gesture areas on a notched
device; TrueSight calibration makes a hand held behind the phone appear life-size and aligned
at arm's length; calibration survives restart.

## M5 — Hardening & Ship [C16–C17] → tag v1.0.0
Thermal/battery behavior for long sessions, then release packaging.
EXIT GATE: 45-minute continuous preview session without thermal shutdown; assembleRelease
installs and runs the full flow; listing and privacy policy match actual behavior.
