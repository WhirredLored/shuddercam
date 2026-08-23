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
