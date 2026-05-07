---
name: Terminology Document Created
description: First comprehensive terminology document added to the Laterita spec on 2026-05-07
type: project
---

**Fact:** A new file `terminology.md` was created in `/workspace/jdk/doc/laterita/` containing 60+ terms and concepts needed by junior Java developers reading the spec.

**Why:** The Laterita specification introduces many Rust-derived concepts (ownership, borrowing, lifetimes, move semantics) and project-specific vocabulary (OQ, `give`/`take`, `bound`, `local`, etc.) that junior Java developers would not recognize. No terminology document existed.

**How to apply:** On future runs, use `terminology.md` as the authoritative reference. Update it when:
- New terms are used in spec revisions
- Terminology is clarified based on user feedback
- Obsolete entries (terms no longer used in any spec document) are discovered during orphan audits

**Structure of terminology.md:**
- Alphabetical terms section (60+ entries covering Rust concepts, Java analogies, compiler terminology)
- Notation and abbreviations section (spec code prefixes like `BIND`, `MOVE`, etc.)
- Code notation legend (symbols used in the spec like `T?`, `(T) -> R`)
- Java analogs table (maps Rust/Laterita to familiar Java concepts)
- Links to spec sections for further reading

**Edit counter:** 1/5 toward next orphan audit (per agent instructions).
