# DECISIONS
| Date | Commit | Decision | Rationale |
|------|--------|----------|-----------|
| 2026-08-21 | C08 | Manual WB disabled if COLOR_CORRECTION_MODE_TRANSFORM_MATRIX / AWB_OFF is unsupported | Hardware levels below FULL/LIMITED vary in manual WB support; per Law 1, MANUAL chip is absent if unsupported |
