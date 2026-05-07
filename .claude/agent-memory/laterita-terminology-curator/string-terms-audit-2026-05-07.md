---
name: String-related terminology audit (2026-05-07)
description: Audit completed after STR-06 through STR-08 spec additions; new terms added to terminology.md
type: project
---

**Date:** 2026-05-07

**Context:** Audit following the addition of STR-06, STR-07, and STR-08 to the specification (string literal ownership, mut method absence, borrow-by-default receiver mode).

**Terms Added:**
1. **buffer splitting** — dividing a borrowed region into non-overlapping views; appears in OQ-17 and reasoning for STR-02/STR-03/MOVE-06
2. **static borrow** — borrow with static lifetime; string literals are static borrows per STR-06
3. **static lifetime** — lifetime spanning program execution; string literals have this per STR-06
4. **string literal** — quoted string expression with type `bound String`; appears throughout STR-06 and examples

**Existing Terms Reviewed:** 
- "mut" entry (BIND-02) already sufficient; no context-specific update needed for STR-07
- "String" not added (junior Java developers know strings; focus on terminology unfamiliar to them)

**Orphan Audit Result:** All 57 entries verified against spec documents. No orphans found. Each entry is used at least once in specification, reasoning, or open questions documents.

**Next Action:** Edit counter at 4/5. One more edit before next audit cycle.
