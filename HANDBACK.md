# HANDBACK — read this first

## 1. Status

The mission ran to completion: **C01 → C17, tagged `v1.0.0`.**

| Milestone | Tag | Gate |
|-----------|-----|------|
| M1 Foundation (C01–C04) | `v0.1.0` | deferred — needs two devices |
| M2 Manual Control Engine (C05–C11) | `v0.2.0` | deferred — needs FULL + LIMITED devices |
| M3 Presets (C12–C13) | `v0.3.0` | deferred — behavioural, needs a restart cycle |
| M4 Edgeless / TrueSight (C14–C15) | `v0.4.0` | deferred — needs a notched device and arm's-length judgement |
| M5 Hardening & Ship (C16–C17) | `v1.0.0` | deferred — needs a 45-minute soak |

Per-task detail is in [BUILDLOG.md](BUILDLOG.md), including the full text of each
deferred gate. Every judgement call is in [docs/DECISIONS.md](docs/DECISIONS.md).
There is no BLOCKED.md — nothing was abandoned.

**What is verified:** `./gradlew assembleDebug` and `./gradlew test` pass.
`./gradlew assembleRelease` passes with the lint-vital tasks excluded — R8,
resource shrinking and packaging all complete, producing a 2.1 MB APK.
**Lint itself could not run here:** its own artifacts time out downloading from
`dl.google.com` in this environment. I did not disable the check to go green;
please run one full `assembleRelease` on a networked machine. Two real defects
that check would have caught were found by inspection and fixed in `9de2e48`
(missing Android 14 receiver-export flag, and a thermal API call whose SDK guard
was invisible to the compiler).

87 JVM unit tests cover the entire `controls/` layer:
override resolution, clamping, capability gating, colour temperature, all five
built-in presets against both FULL and LIMITED mock devices, user-preset
serialization round-trips, TrueSight zoom composition, and every thermal tier.

**What is not verified:** anything requiring a physical camera. No line of this
app has ever run on a device. That is the whole of section 2.

**One correction worth knowing about:** C08 was recorded as PASS but had been
committed with four failing tests. It is fixed (commit `c46cca5`) and the
buildlog row now says so. See the digest below.

## 2. Device test plan (~30 minutes)

Install: `./gradlew installDebug` with a device on USB, or side-load
`app/build/outputs/apk/debug/app-debug.apk`.

You need **two physically different phones** for steps 1–2 — ideally one recent
flagship (Camera2 FULL, notch or punch-hole) and one older or budget handset
(LIMITED or LEGACY). Everything after step 2 runs on the flagship.

### Foundation — do this first, everything else depends on it (6 min)

1. **Caps probe, phone A.** Launch, grant camera. Tap the ⓘ icon. Confirm the
   diagnostics screen lists real values: hardware level, ISO range, exposure
   time range, min focus distance, zoom range, frame-rate ranges, flash. Note
   them down.
2. **Caps probe, phone B.** Same screen on the second phone. **The numbers must
   differ.** Identical output on two different phones means the probe is
   reporting defaults instead of hardware — stop and report that.
3. **Preview and focus.** Live preview is right-side-up. Rotate the phone: the
   image stays correct. Tap the preview — focus visibly racks.

### Manual control engine (8 min)

4. **Absence, not greyness.** On the *weaker* phone, open the control panel
   (sliders icon). Controls that phone lacks must be **missing entirely** — no
   greyed rows, no fake ranges. A whole tab disappears if its section is empty.
5. **Exposure.** Manual mode → drag ISO and Shutter. The preview visibly
   brightens and darkens; the readout chip at top-left tracks the values.
6. **Focus.** Focus tab → Manual → drag Focus Distance. Focus racks from near to
   infinity; the loupe appears mid-drag showing the distance.
7. **White balance.** WB tab → Manual → drag Colour Temp. **3200K should look
   warm/orange and 8000K cool/blue.** If it is inverted, the C08 fix went the
   wrong way — tell me and it is a one-line change.
8. **Motion.** Pinch the preview to zoom, then open the Motion tab: **the slider
   must already sit where the pinch left it** (one override path, no duplicate
   state). Toggle Torch. Change Frame Rate and watch the preview cadence change.

### Capture (4 min)

9. **Extreme manual shot.** Set 2s shutter, ISO 100, then press the shutter.
   The photo should match what the preview looked like — not an auto-exposed
   version of the scene.
10. **Where it landed.** Open your gallery: the file is in `Pictures/ShudderCam`,
    named `SC_yyyyMMdd_HHmmss.jpg`, and is **right-side-up**. Tap the corner
    thumbnail in the app — it opens in your normal gallery.
11. **Flash vs torch.** Capture tab → Flash On, take a shot: it fires at capture
    only. Torch (Motion tab) stays on continuously. They are separate controls.

### Presets and overrides (5 min)

12. **Reset marks.** Change two controls. A small reset glyph appears beside
    exactly those two. Tap one — that control alone returns to the preset value.
13. **A/B hold.** Press and hold the active preset chip. The preview should snap
    to the preset *without* your changes for as long as you hold, and snap back
    the instant you release. No lag, no animation.
14. **Preset switch.** Switch between built-ins — each should visibly change the
    preview per its description. **Your overrides must survive the switch** and
    their reset glyphs stay accurate.
15. **Reset all.** Confirm the dialog, everything returns to the preset.
16. **Save your own.** Set something distinctive, tap Save, name it. It appears
    as a Slate-coloured chip after the built-ins. Long-press it → Rename, then
    long-press → Delete. **Deleting the active one drops you to Device Auto with
    your overrides intact.**
17. **Restart.** Force-quit and relaunch. Saved presets, the active selection,
    and your overrides all come back.

### TrueSight — the signature feature (5 min)

18. **Edgeless.** Tap the fullscreen icon. The preview must reach **every
    physical edge** — through the punch-hole/notch, past the gesture bar — with
    **zero visible chrome**. Single tap reveals the floating shutter row; it
    fades after 4 seconds. Double-tap exits.
19. **No inset jump.** While edgeless, swipe from the top edge to bring the
    status bar back transiently. **The image must not shift or resize.**
20. **Rotation.** Rotate while edgeless. If you see a visible flash or jump cut,
    tell me — the ≤150ms black-fade fallback is documented in DECISIONS.md but
    was not needed in theory (rotation is handled in-process).
21. **Calibration.** Tap the eye icon → calibration opens on first run. Hold the
    phone at arm's length, look at something with strong straight lines past the
    phone's edge, and drag vertically until the on-screen scene **continues the
    real scene across the bezel**. Confirm.
22. **The actual test.** Hold your free hand behind the phone at arm's length.
    It should appear **life-size and continuous** with the real hand either side
    of the screen. This is the one thing no test can fake — if it does not land,
    the calibration range (1.0–3.0) may need widening.
23. **Zoom on top.** Pinch while in TrueSight — it zooms past 1:1 (expected).
    Tap the reset glyph in the revealed chrome to return to calibrated 1:1.
24. **Landscape.** Rotate and recalibrate; each orientation keeps its own factor.
    Restart the app — both survive.

### Hardening (2 min active, 45 min elapsed)

25. **Background/foreground.** While in TrueSight with manual settings applied,
    switch to another app and come back. **Everything must return exactly:**
    mode, calibration, overrides, and the actual camera behaviour — not just the
    UI showing the old values. This one has a real failure mode; check the image
    itself, not the labels.
26. **The soak.** Leave the preview running for 45 minutes, ideally somewhere
    warm. It must not crash or shut down. Watch for the calm banner appearing as
    the phone heats: frame rate drops, torch is held off, and **as it cools
    everything comes back on its own** — your presets and calibration untouched.
    `adb logcat -s ThermalGovernor` shows the tier transitions.
27. **Battery.** The percentage shows in the header, and in the tap-reveal layer
    while edgeless. Below 15% you get one warning, not a repeating nag.

### Release build (2 min)

28. **The minified APK.** `./gradlew assembleRelease` — this also runs the lint
    pass that could not execute here, so read its output. Then install
    `app/build/outputs/apk/release/`. Run the whole flow once: manual exposure →
    capture → save a preset → restart → **load that preset** → edgeless →
    TrueSight. The preset load is the critical one: R8 renaming would break
    saved-preset deserialization, and it only shows up at runtime.

    A first attempt at the keep rules left the `ControlId` constants renamed
    (`AE_MODE -> e`) even though the class name survived — which is exactly the
    breakage above. The rules were tightened and the mapping now preserves them.
    You can confirm without a device:
    `grep "ControlId AE_MODE" app/build/outputs/mapping/release/mapping.txt`
    must show `AE_MODE -> AE_MODE`.

## 3. Decisions digest — the five that matter most

1. **C08 was shipped broken and I inverted it to fix it.** The Kelvin conversion
   divided green by each channel, which *compensates* for the illuminant and
   made 3200K render cool. The C08 prompt says "3200K warmer than 5600K" and its
   own tests agreed, so the prompt won over the compensating-WB reading. This is
   a deliberate creative choice, not physics: check item 7 above.
2. **Capture is JPEG-only and the RAW control is absent.** CameraX 1.4.1 has no
   DNG output path. C10 explicitly sanctions JPEG-only with the control hidden
   rather than faking a RAW+JPEG option, so that is what shipped. Diagnostics
   still reports the hardware's RAW capability honestly.
3. **Zoom and torch go through CameraX, not Camera2 interop.** CameraX owns both
   knobs; writing `CONTROL_ZOOM_RATIO` or `FLASH_MODE` through interop would
   fight it. `ControlApplier` is still the single writer, so Law 4 holds.
4. **TrueSight calibration composes inside the resolver, not the UI.** Effective
   zoom is `clamp(userZoom × calibration)` computed in `EffectiveSettings`, with
   a dedicated accessor giving the UI back the user's *own* zoom. Without that
   split, every slider drag would multiply the calibration into itself.
5. **Thermal throttling never writes to stored values.** The governor constrains
   resolution only, so the user's torch and frame-rate choices survive being
   overruled and recovery as the phone cools needs no restore logic at all.

## 4. Known gaps and risks

**Implemented to spec, unverifiable here:**

- Everything in section 2. No camera has ever run this code.
- The **minified release build has never been executed.** It compiles and R8
  completes, but saved-preset deserialization under obfuscation is exactly the
  class of bug that only appears at runtime. `ControlId` enum names are the
  on-disk format and are protected by a keep rule — item 28 confirms it works.
- **`lintVitalRelease` has never run.** Its artifacts cannot be downloaded in
  this environment, so the release build was verified with those tasks excluded.
  It is the one gate in the whole mission I could not execute rather than merely
  observe, and it is a real gap: lint-vital exists to catch fatal issues before
  release, and it had already found two by the time it failed to download.
- **Thermal tiers have never been observed transitioning.** The policy is fully
  unit-tested, but the mapping from `PowerManager` status to tier has only ever
  been exercised with synthetic values.
- **The pre-Android-10 storage path is untested.** API 29+ writes via
  `RELATIVE_PATH`; older devices use a file path plus `WRITE_EXTERNAL_STORAGE`.
  If you have an Android 9 handset, capture on it once.

**Risks I would flag:**

- **The calibration range may be wrong.** 1.0–3.0 is an educated guess at what
  arm's-length 1:1 requires. If item 22 cannot reach continuity at either end,
  the bound needs moving — a one-line change in `EffectiveSettings`.
- **Resume behaviour is the subtlest thing in the app.** CameraX rebinds its use
  cases around the lifecycle but knows nothing of our Camera2 options, so
  settings are restated on every `ON_RESUME`. Item 25 is the check that matters;
  a failure here looks like "the UI is right but the image is wrong."
- **No instrumented or UI tests exist.** The `controls/` layer is thoroughly
  tested as pure Kotlin; everything in `ui/` and `camera/` is verified only by
  compilation and by reading. That is the deliberate architecture (Law 3), but
  it does mean section 2 is the only coverage those layers have.
- **The store listing needs two real values** before submission: the privacy
  policy URL and a genuine contact address, both currently placeholders in
  `store/`.
