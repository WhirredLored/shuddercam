# ShudderCam — Play Store listing

## Title (≤30 characters)

`ShudderCam: Manual Camera` *(25 characters)*

## Short description (≤80 characters)

`Full manual camera. Every control your phone actually supports.` *(63 characters)*

## Full description

ShudderCam is a manual camera for people who want the controls, not the
filters. It exposes every modifiable camera function your particular phone
supports — and nothing it does not.

**Controls that match your hardware**

Every control is read from your camera at runtime: ISO and shutter range,
exposure compensation, autofocus modes and manual focus distance, white balance
modes and manual colour temperature, zoom, frame rate, stabilisation, torch and
flash. If your camera does not support something, that control is simply not
there — no greyed-out buttons, no invented ranges, no settings that quietly do
nothing. Two different phones will show two different panels, and both will be
telling the truth.

**Presets you cannot break, and changes you can always undo**

Five built-in presets ship as part of the app and can never be modified:

- **Device Auto** — everything left to the camera's own automatic behaviour
- **Night Hand-held** — auto exposure pulled down, steadied, at the fastest
  steady frame rate
- **Long Exposure** — one-second shutter at base ISO, focused at infinity
- **Daylight Crisp** — daylight balance, neutral exposure, biased to the
  fastest shutter
- **UV Hunt** — for 365nm fluorescence work

Anything you change sits in a separate layer on top of the preset. A small reset
mark appears beside every control you have altered, so you can see at a glance
what you changed and undo exactly that one thing — or all of them at once. Press
and hold the preset chip to see the preset on its own for as long as you hold,
then release to get your adjustments back. Save your own recipes as named
presets; they live in their own layer and never touch the built-in ones.

Preset values adapt to the phone they land on. A preset saved on a camera with
a huge ISO range still works on one without — values are clamped to what your
hardware can actually do.

**TrueSight edgeless mode**

TrueSight fills the entire display with the live preview — through the notch,
past the gesture bar, edge to edge — and lets you calibrate its scale so the
image on screen continues the real scene across the phone's edge when held at
arm's length. Done well, the phone reads as a pane of glass.

To be clear about what this is: TrueSight is an **optical illusion produced by
zoom calibration**, not transparent hardware. Your phone is not see-through and
this app cannot make it so. The effect works because a camera's field of view is
much wider than the slice of world the phone's body hides from your eye;
calibration corrects that mismatch. It depends on your arm length and how you
hold the phone, which is why you set it by eye, once per orientation.

**Also in the box**

- Still capture that honours the manual settings you are previewing, saved
  straight to your normal photo library
- A capability diagnostics screen showing what your cameras actually report
- A thermal governor that eases off frame rate and torch if your phone gets hot
  during long sessions, and restores your settings when it cools
- Battery readout for long shoots
- Dark, night-friendly interface throughout

**What ShudderCam does not do**

No accounts. No cloud. No analytics. No ads. No network access whatsoever — the
app does not request internet permission. No filters and no beautification: this
app controls the camera, it does not paint over the picture. Video recording is
not included in this version.

---

## Screenshot shot list

One per milestone feature. TrueSight leads, since it is the app's signature.

1. **TrueSight edgeless demo** — phone held at arm's length in front of a scene
   with strong horizontal lines (a railing, a shelf, a horizon). The lines on
   screen continue the real lines either side of the phone. Caption: *"TrueSight:
   calibrated so the screen continues the scene."*
2. **TrueSight calibration screen** — the drag interface mid-calibration with the
   edge rulers visible and the factor readout showing. Caption: *"Calibrate once
   per orientation, by eye."*
3. **Manual exposure in use** — control panel open on the Exposure tab in manual
   mode, ISO and shutter sliders visible, live readout chip showing the values.
   Caption: *"Manual ISO and shutter, with the values on screen."*
4. **Capability gating, side by side** — the same Motion tab on two different
   phones, one showing controls the other lacks. Caption: *"Only the controls
   your camera actually has."*
5. **Presets and the override layer** — preset chips across the top with a
   built-in selected, and two reset marks visible beside changed controls.
   Caption: *"Presets you cannot break. Changes you can always undo."*
6. **User preset save** — the save dialog with a name typed in, user preset chips
   in Slate visible behind it. Caption: *"Save your own recipes."*
7. **Night Hand-held at work** — a low-light frame with the preset active and the
   readout chip showing the negative EV bias. Caption: *"Built-in presets, tuned
   per device."*
8. **Capture with manual settings** — the viewfinder just after a long-exposure
   shot, corner thumbnail populated. Caption: *"What you preview is what you
   capture."*
9. **Diagnostics screen** — scrolled to show ISO range, focus distance and frame
   rate ranges for a real camera. Caption: *"See exactly what your cameras
   report."*

Shoot all screenshots on a real device in dark surroundings, matching the app's
dark theme. Do not composite or retouch the preview content: what the camera saw
is what the screenshot shows.

---

## Data Safety answers (draft)

These match the code as built. Verify against the shipping binary before
submitting.

**Does your app collect or share any of the required user data types?**
No.

**Data collected:** none.
**Data shared:** none.

**Is all of the user data collected by your app encrypted in transit?**
Not applicable — the app has no network permission and transmits nothing.

**Do you provide a way for users to request that their data be deleted?**
Not applicable — no data is collected. Settings and saved presets live only on
the device and are removed when the app is uninstalled. Photos the user takes
belong to their own media library and are deleted like any other photo.

**Photos and videos:**
Not collected and not shared. Photos are written to the user's own device media
library only when the user presses the shutter, and are never read back except
to draw the corner thumbnail.

**Location:** not collected. The app does not request location permission and
does not tag photos with location.

**Personal info, financial info, health, messages, contacts, calendar, app
activity, crash logs, diagnostics, advertising ID:** none collected.

**Permissions declared** (verified against the merged release manifest):
- `android.permission.CAMERA` — required for the viewfinder and capture.
- `android.permission.WRITE_EXTERNAL_STORAGE` (maxSdkVersion 28) — required only
  on Android 9 and older to write into Pictures/ShudderCam.
- `com.huestone.shuddercam.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — added
  automatically by AndroidX when registering the battery-level receiver. It is
  app-private and signature-level: it exists to stop other apps reaching that
  receiver, and grants ShudderCam nothing.

There is deliberately no `android.permission.INTERNET`, which is why the "no
data transmitted" answers above are structural rather than a promise.

**Privacy policy URL:** host `store/privacy-policy.md` at a public URL and put it
here before submitting.
