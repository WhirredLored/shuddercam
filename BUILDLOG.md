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
