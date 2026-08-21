# ShudderCam — Standalone Project Pack

Each numbered file is ONE complete, self-contained prompt: project context, architecture laws,
capability rules, and the exact commit message are all built in. Open a file, copy the whole
thing, paste into a fresh AI coding session. Nothing else to paste.

Order: C01 → C17, one prompt = one commit. Do not combine. Milestone gates in MILESTONES.md —
do not start a milestone until the previous gate passes.

## Modes
- SUPERVISED: paste one C-file per session, review each commit. Original workflow.
- AUTOPILOT: give an agent the whole directory and point it at AUTOPILOT.md. It builds
  C01→C17 unattended, self-verifies what code can verify, defers device checks into
  HANDBACK.md, and stops cleanly on unresolvable failure. Read HANDBACK.md first when you return.
