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
# TASK — M1 commit C01: project scaffold

This IS the scaffold commit — create the project from scratch exactly per the layout above.

IMPLEMENT
1. Gradle Kotlin DSL project, single :app module, applicationId com.huestone.shuddercam,
   minSdk 26, latest stable AGP/Kotlin/Compose BOM, Material 3, dark theme only.
2. MainActivity hosting a placeholder ViewfinderScreen composable ("ShudderCam" centered).
3. Create every package directory from the layout (brand, camera, controls, data, ui, ui/theme,
   util) even if empty, plus docs/DECISIONS.md with only its header line and a .gitignore
   covering Android/Gradle plus keystores, local.properties, *.zip, and captured images.
4. Version 0.1.0, versionCode 1.

ACCEPTANCE CRITERIA
- `./gradlew assembleDebug` passes; app launches to the placeholder.
- Tree matches the layout exactly.

COMMIT MESSAGE (use verbatim)
build(repo): scaffold ShudderCam with Compose and package layout
