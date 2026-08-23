# BUILDLOG
| Task | Status | Note |
|------|--------|------|
| C01 | PASS | Scaffold with Compose, Material 3, dark theme, all packages created, assembleDebug + test green |
| C02 | PASS | Brand tokens and dark-only Material 3 theme, no hex colors outside Brand.kt and theme files |
| C03 | PASS* | CameraX preview + permission flow + tap-to-focus; deferred: device verification of live preview, rotation, tap-to-focus |
| C04 | PASS* | Exhaustive capability probe + diagnostics screen; deferred: two-device verification of different ranges |
| C05 | PASS | Pure-Kotlin control model with override resolution, clamping, and 12 JVM unit tests; zero Android imports in controls/ |
| C06 | PASS* | Exposure controls: ControlApplier, ControlsSheet, DataStore Prefs, exposure readout; deferred: device verification of manual ISO/shutter changes |
| C07 | PASS* | Focus controls: AF mode cycling, manual focus distance slider, focus loupe overlay; deferred: device verification of focus racking |
| C08 | PASS* | White balance family with manual Kelvin control, RGGB gains conversion, JVM unit tests; row corrected 2026-08-23 — the commit actually left 4 ColorTemperatureTest cases failing, fixed before C09; deferred: device verification of warming/cooling |
| C09 | PASS* | Zoom (pinch + slider on one override path), torch, frame rate and stabilization gated to probed options; deferred: device verification of pinch/slider sync, torch, and FPS cadence change |
| C10 | PASS* | Still capture to Pictures/ShudderCam via MediaStore, flash-at-capture, blink + shutter sound + thumbnail handoff; JPEG-only (no DNG on CameraX 1.4.1); deferred: device verification that manual settings reach the capture request, EXIF orientation, and filename/timestamp |
| C11 | PASS* | Tabbed control panel (Exposure/Focus/WB/Motion/Capture), per-control reset glyphs, reset-all with confirmation, press-and-hold A/B preset audition; deferred: device verification of glyph behaviour and the A/B hold rendering |

| C12 | PASS* | Five immutable built-in presets (Device Auto, Night Hand-held, Long Exposure, Daylight Crisp, UV Hunt) with descriptions, selector chips, persisted active id, 15 JVM tests across FULL and LIMITED caps; deferred: device verification that each preset visibly changes the preview per its description |

| C13 | PASS* | User presets as a separate DataStore layer: save-current snapshot, rename/delete via long-press menu, Slate chips, clamp-on-load, 15 JVM tests; deferred: device verification of save/rename/delete and survival across restart |

| C14 | PASS* | Edgeless fullscreen: hidden system bars with transient-swipe behaviour, cutout ALWAYS/SHORT_EDGES, FILL_CENTER preview, tap-reveal chrome fading after 4s, double-tap exit, keep-screen-on, persisted across cold start; deferred: notched-device verification of edge-to-edge coverage, no inset jump, and rotation without a flash |

| C15 | PASS* | TrueSight: calibration composed into zoom inside the resolver, drag-to-calibrate flow with edge rulers, per-orientation persistence, TS badge, zoom reset to calibrated 1:1, Settings screen with recalibrate and brightness assist, 11 JVM tests; deferred: arm's-length verification that a hand behind the phone reads life-size and continuous |

| C16 | PASS* | Thermal governor (tier policy pure and unit-tested, applied during resolution so recovery is automatic), battery readout with one low warning, allocation audit documented in ViewfinderScreen, settings and window mode restated on resume, 16 JVM tests; deferred: 45-minute soak and observing the tiers on real hardware |

## Milestone gates

**M2 — Manual Control Engine [C05–C11] → tagged v0.2.0 (2026-08-23). GATE DEFERRED.**
The exit gate needs physical hardware and cannot be run here. A human must check:
1. On a FULL-level device, every control shown in the sheet visibly changes the live preview
   (ISO, shutter, EV, AE/AWB lock, AF mode, manual focus distance, Kelvin, zoom, torch, FPS,
   stabilization, flash).
2. On a LIMITED or LEGACY device, unsupported controls are ABSENT from the sheet — not greyed,
   not showing invented ranges — and the tab itself disappears when its whole section is absent.
3. Photo capture works with manual settings applied: a shot at an extreme setting (2s shutter,
   ISO 100) matches the preview's look, lands in Pictures/ShudderCam with the SC_yyyyMMdd_HHmmss
   name, and opens right-side-up from the corner thumbnail.

**M3 — Presets [C12–C13] → tagged v0.3.0 (2026-08-23). GATE DEFERRED.**
The exit gate is behavioural and needs a device. A human must check, after an app restart:
1. Preset switch — each built-in visibly changes the preview per its description, and any
   overrides in place survive the switch with their reset glyphs still accurate.
2. Per-control reset clears exactly one control; reset-all clears every override after the
   confirmation dialog and returns the camera to the active preset.
3. Save-as-custom names a preset from the current effective settings, it reappears after a
   restart along with the active selection, rename and delete work from the long-press menu,
   and deleting the active user preset drops back to Device Auto with overrides preserved.

**M4 — Edgeless / TrueSight [C14–C15] → tagged v0.4.0 (2026-08-23). GATE DEFERRED.**
Needs a notched or punch-hole device and an arm's-length judgement no emulator can make:
1. Edgeless fills the entire panel including the cutout and gesture areas, with zero visible
   chrome; single tap reveals the floating layer and it fades after 4s; double-tap exits.
2. Transient system bars, brought back by an edge swipe, do not shift the layout, and rotating
   while edgeless does not flash — if it does, add the ≤150ms black fade recorded in
   DECISIONS.md as the fallback.
3. TrueSight calibration: at arm's length, a hand held behind the phone appears life-size and
   continuous with the real hand across the bezel; the factor survives a restart; pinch still
   zooms on top of it and the reset returns to calibrated 1:1; portrait and landscape each hold
   their own factor.
