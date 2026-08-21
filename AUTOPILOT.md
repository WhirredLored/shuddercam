# AUTOPILOT — full autonomous build mission for ShudderCam
You are Claude Opus running with filesystem and shell access. This file is your standing order.
Read it fully, then execute the mission. The human is away; you will not receive answers to
questions until the end. When a decision is yours to make, make it, record it, and continue.

## MISSION
Build ShudderCam v1.0.0 by executing the task prompts in this directory IN ORDER:
C01 → C02 → C03 → C04 → C05 → C06 → C07 → C08 → C09 → C10 → C11 → C12 → C13 → C14 → C15 → C16 → C17
One task = one commit, exactly as each prompt specifies. The mission is complete when C17's
commit exists, the repo is tagged v1.0.0, and HANDBACK.md is written (see below).

## THE LOOP (repeat per task)
1. Re-read the ENTIRE task prompt fresh before starting it — including its embedded project
   context. Do not work from memory of earlier prompts; later prompts supersede your memory.
2. Implement exactly and only what the prompt asks, honoring every architecture law.
3. Verify: run `./gradlew assembleDebug` and `./gradlew test`. Both must pass. Also verify any
   acceptance criteria checkable by build, test, grep, or static inspection.
4. Commit with the verbatim commit message from the prompt.
5. Append one line to BUILDLOG.md:
   `<task> | PASS | <one-line note>` or `<task> | PASS* | deferred: <device criteria>` —
   PASS* marks tasks whose device-dependent criteria await human verification.
6. Continue to the next task without stopping.

## MILESTONE GATES
Gates that require physical devices (two different phones, a notched panel, arm's-length
TrueSight calibration, a 45-minute thermal soak) CANNOT block you — you cannot perform them.
At each milestone boundary: tag the milestone version, record the gate as DEFERRED in
BUILDLOG.md with exactly what the human must check, and proceed. Never fabricate a device
result. Emulator checks are welcome where available but count as smoke tests, not gate passes.

## FAILURE POLICY
- A failing build/test gets up to 3 distinct fix attempts. Attempts must fix the actual cause —
  never delete or weaken a test, never relax a law, never comment out functionality to go green.
- If still failing after 3 attempts: write the full diagnosis to BLOCKED.md (task, error,
  attempts, your best hypothesis, recommended human action), commit what is safe to commit as
  work-in-progress on a branch named `blocked/<task>`, leave main green at the last passing
  task, and END THE MISSION there. A clean partial build beats a corrupted complete one.
- If a later task appears to contradict earlier code, the TASK PROMPT wins. If two prompts
  genuinely contradict each other, choose the later prompt's reading, note it in DECISIONS.md,
  and continue.

## STANDING PROHIBITIONS
- Never edit any C*.md prompt file, this file, or MILESTONES.md.
- Never combine tasks into one commit; never split one task across commits except a `fix(...)`
  follow-up when a defect in an EARLIER task blocks the current one (record it in DECISIONS.md).
- Never add dependencies, features, screens, or "improvements" no prompt asked for.
- Never claim a device-verified result you did not observe.
- Every judgment call you make gets a DECISIONS.md line. When in doubt, decide conservatively
  and log it — do not wait for the human.

## HANDBACK.md (write this after C17, or after BLOCKED.md if the mission ends early)
A single document the human reads first on return:
1. STATUS — finished at which task/tag; link BUILDLOG.md and BLOCKED.md if present
2. DEVICE TEST PLAN — every deferred criterion compiled into an ordered 30-minute checklist
   (install steps included), sequenced so the most foundational checks (C04 caps probe on two
   devices) come first
3. DECISIONS DIGEST — the five most consequential judgment calls you made, one line each
4. KNOWN GAPS — anything implemented to spec but unverifiable, and anything you'd flag as risk
