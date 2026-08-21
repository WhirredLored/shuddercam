# ══════════════════════════════════════════════════════════════════════════════
# PROJECT CONTEXT (read fully before writing any code)
# ══════════════════════════════════════════════════════════════════════════════
You are working on ShudderCam, an Android app. Unless this prompt is the scaffold commit (C01),
assume the codebase described below already exists and is building green — build on it, do not
scaffold or rewrite it.

WHAT THE APP IS
ShudderCam is a manual camera app that exposes EVERY modifiable camera function the device
supports — exposure, focus, white balance, zoom, frame rate, flash, stabilization, and capture —
through a fast, dark, night-friendly UI. Its two signatures are: (1) a preset system where
built-in presets are immutable code constants and every user change is a nullable override on
top, and (2) TrueSight — an edgeless fullscreen mode where the live preview fills the entire
display panel and is calibrated to real-world scale, so the phone appears to be a pane of glass
you can see through.

STACK (fixed — do not substitute)
- Kotlin, Jetpack Compose, Material 3, dark theme only
- CameraX (Preview + ImageCapture) with Camera2 interop (Camera2CameraControl,
  CaptureRequestOptions, Camera2CameraInfo) for all manual control
- minSdk 26, latest stable target/compile SDK, single :app module, Gradle Kotlin DSL
- DataStore for persistence. No Room needed. No ML.
- applicationId / package: com.huestone.shuddercam
NON-GOALS: no accounts, no cloud, no ads, no analytics, no video recording in v1, no filters or
image processing — this app controls the camera, it does not paint over it.

PACKAGE LAYOUT (files go ONLY in these packages; do not invent new top-level dirs)
app/src/main/java/com/huestone/shuddercam/
  MainActivity.kt
  brand/      Brand.kt — ONLY source of brand strings + color tokens:
              Void #0B0B0E (bg), Bone #EDE6DC (text), ShudderRed #D93A2B (accent),
              Slate #5A5F66 (secondary), appName "ShudderCam", company "Huestone Holdings"
  camera/     CameraController.kt (bind/unbind, use cases), CameraCaps.kt (capability probe),
              ControlApplier.kt (writes CaptureRequestOptions), CaptureController.kt
  controls/   PURE KOTLIN (zero Android imports except androidx.annotation):
              ControlId.kt, ControlSpec.kt, ControlValue.kt, Preset.kt, Presets.kt,
              EffectiveSettings.kt (override resolution), plus JVM unit tests for all of it
  data/       Prefs.kt (DataStore: active preset id, nullable overrides, user presets,
              TrueSight calibration)
  ui/         ViewfinderScreen.kt, ControlsSheet.kt, DiagnosticsScreen.kt, SettingsScreen.kt,
              theme/
  util/
app/src/test/java/com/huestone/shuddercam/controls/ — JVM tests
docs/ — DECISIONS.md (append-only: `YYYY-MM-DD | <commit> | <decision> | <why>`)
store/ — listing + privacy policy (C17 only)

ARCHITECTURE LAWS (non-negotiable)
1. CAPABILITY-GATING IS LAW. Every control's availability and range comes from
   CameraCharacteristics at runtime (sensitivity range, exposure time range, min focus distance,
   zoom ratio range, AE/AF/AWB available modes, hardware level). A control the device does not
   support is ABSENT from the UI — never grayed out, never shown with fake ranges. All probing
   lives in camera/CameraCaps.kt and is exposed as one immutable caps object.
2. PRESETS ARE CODE, OVERRIDES ARE DATA. Built-in presets are `val` constants in
   controls/Presets.kt — nothing writes to them. User changes persist as NULLABLE values;
   null = "use preset." Effective value = `override ?: preset.value`, resolved in ONE pure
   function in controls/EffectiveSettings.kt, unit-tested. No screen resolves inline.
3. controls/ is pure Kotlin and fully JVM-unit-tested. Android types stay in camera/ and ui/.
4. ONE WRITER. Only camera/ControlApplier.kt translates EffectiveSettings into
   CaptureRequestOptions and applies them. UI never touches Camera2 types directly.
5. Clamp, don't crash: every applied value is clamped to the probed range; out-of-range
   persisted values (e.g. after a phone migration) clamp silently and log one line.
6. All brand strings/colors come from brand/Brand.kt + strings.xml. Never hardcode.
7. One class per file; file = class name; classes PascalCase, functions camelCase, constants
   SCREAMING_SNAKE; Compose screens end in "Screen".
8. No secrets, fully offline, keystores and captured images gitignored, no test images committed.
9. `./gradlew assembleDebug` and `./gradlew test` must pass before the commit is done.
10. Scope discipline: implement ONLY what this prompt asks. Unrelated findings become a
    DECISIONS.md line, not code. Any tradeoff you choose also gets a DECISIONS.md line.

DELIVERABLE FORMAT
If you are an agent with filesystem access: apply the changes directly, run the gradle checks
yourself, and make the commit with the exact message quoted at the end of this prompt —
verbatim, no edits. Then: if you are running under AUTOPILOT.md, append your status line to
BUILDLOG.md and continue to the next task; otherwise stop and report.
If you are a chat session without filesystem access: give me the complete contents of every new
or changed file (full files, not diffs), a one-line summary, then that same commit message.

VERIFICATION SPLIT (for agents)
Acceptance criteria that are builds, tests, greps, or code properties: verify yourself.
Criteria that require a physical device, a second device, a notched panel, field conditions, or
thermal soak: you cannot verify these — implement to spec, state plainly which criteria await
human verification, and stop. Never claim device-verified results you did not observe.

# ══════════════════════════════════════════════════════════════════════════════
# TASK — M3 commit C12: built-in presets

IMPLEMENT
1. controls/Presets.kt: `val` constants per Law 2 — nothing writes to them. Ship these five,
   each with a one-line human description shown in the UI:
   - DEVICE AUTO — everything deferred to device auto behavior (the implicit default)
   - NIGHT HAND-HELD — AE on with negative EV bias, highest stable FPS range, stabilization on
   - LONG EXPOSURE — AE off, shutter 1s, ISO minimum, MF at infinity, torch off
   - DAYLIGHT CRISP — AE on, EV 0, AWB daylight, stabilization on, fastest shutter bias the
     API allows via FPS/EV choices
   - UV HUNT — AE off, ISO high (clamped to device range), shutter ~1/15s, AWB manual ~4500K
     where supported, torch off; description: "for 365nm fluorescence work"
   Presets must express values through the ControlValue types so clamping (Law 5) adapts them
   to any device. A preset referencing an unsupported control simply has that entry ignored on
   that device — verify this path is exercised in tests.
2. Preset selector UI at the top of ControlsSheet: chip row with name + description of the
   active preset. Switching presets does NOT clear user overrides (same semantics as reset
   affordances imply — overrides shadow whatever preset is beneath). Persist active preset id.
3. JVM tests: each preset resolves cleanly against a mocked caps mirror of a LIMITED device
   and a FULL device; UV HUNT's ISO clamps correctly on a low-max-ISO device.

ACCEPTANCE CRITERIA
- Switching presets visibly changes the preview per description; overrides survive the switch
  and their reset glyphs remain accurate.
- `./gradlew assembleDebug` and `./gradlew test` pass.

COMMIT MESSAGE (use verbatim)
feat(controls): add immutable built-in presets including UV Hunt
