# ShudderCam — Standalone Project Pack

Each numbered file is ONE complete, self-contained prompt: project context, architecture laws,
capability rules, and the exact commit message are all built in. Open a file, copy the whole
thing, paste into a fresh AI coding session. Nothing else to paste.

Order: C01 → C17, one prompt = one commit. Do not combine. Milestone gates in MILESTONES.md —
do not start a milestone until the previous gate passes.

## Release signing

Release builds are signed from `keystore.properties` in the repository root.
That file is gitignored and must never be committed — it holds credentials.

Create it with these four keys:

```properties
storeFile=/absolute/path/to/shuddercam-release.jks
storePassword=<store password>
keyAlias=<key alias>
keyPassword=<key password>
```

`storeFile` may be an absolute path or one relative to the `app/` module. If the
file is absent, `assembleRelease` still builds — the APK is simply left
unsigned, so a missing keystore never blocks a local build.

The release build runs R8 with resource shrinking. Keep rules live in
`app/proguard-rules.pro`; the important one preserves `ControlId` enum names,
which are the on-disk format for saved user presets.

## Modes
- SUPERVISED: paste one C-file per session, review each commit. Original workflow.
- AUTOPILOT: give an agent the whole directory and point it at AUTOPILOT.md. It builds
  C01→C17 unattended, self-verifies what code can verify, defers device checks into
  HANDBACK.md, and stops cleanly on unresolvable failure. Read HANDBACK.md first when you return.
