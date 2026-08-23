# DECISIONS
| Date | Commit | Decision | Rationale |
|------|--------|----------|-----------|
| 2026-08-21 | C08 | Manual WB disabled if COLOR_CORRECTION_MODE_TRANSFORM_MATRIX / AWB_OFF is unsupported | Hardware levels below FULL/LIMITED vary in manual WB support; per Law 1, MANUAL chip is absent if unsupported |
| 2026-08-23 | fix(C08) | Kelvin gains reproduce the selected illuminant's tint (3200K warm, 8000K cool) instead of compensating for it | C08 was committed with 4 failing ColorTemperatureTest cases; C08's prompt specifies "3200K warmer than 5600K" and a slider that visibly warms/cools, so the prompt and its tests win over the compensating-WB reading the code had implemented |
