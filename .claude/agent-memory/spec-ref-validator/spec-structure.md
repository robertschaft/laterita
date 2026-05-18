---
name: Laterita spec structure and ID conventions
description: File locations, section numbering, all known ID prefix families, tombstone conventions, and recurring mistake patterns for the Laterita spec
type: project
---

## File locations (all under /home/user/laterita/doc/)
- `laterita-spec.md` — normative requirements; defines all spec IDs
- `laterita-reasoning.md` — design rationale; mirrors spec sections, references IDs but rarely defines new ones
- `laterita-open-questions.md` — OQ-NN entries (open + resolved tombstone list at end)
- `terminology.md` — alphabetical glossary; references IDs, never defines them

## Section numbering (as of 2026-05-17, after OQ-19 resolution adding §13 Arrays)
| § | Title |
|---|-------|
| 1 | Bindings |
| 2 | Mutability Rules |
| 3 | Move and Borrow |
| 4 | Lifetimes |
| 5 | Scope-Exit Cleanup |
| 6 | Unreachability |
| 7 | Copying |
| 8 | Optionality |
| 9 | Exceptions |
| 10 | Functional Interfaces |
| 11 | Closures |
| 12 | Strings |
| 13 | Arrays  ← NEW (OQ-19 resolution) |
| 14 | Unsafe |
| 15 | Standard Library Types |
| 16 | Threads |
| 17 | Compilation Model |
| 18 | Reserved Names |

## Known ID prefix families and their canonical section
| Prefix | Domain | Defined in |
|--------|--------|------------|
| BIND | Local/field bindings, mutation/consumption | §1 |
| MUT | Mutability rules (transitivity, Cell<T>) | §2 |
| MOVE | Ownership transfer, borrowing | §3 |
| LIFE | Lifetime inference, borrow boundaries | §4 |
| DROP | Scope-exit cleanup, onDrop() | §5 |
| UNR | Unreachable paths (broken()) | §6 |
| OBJ | Copying, clone semantics | §7 |
| NULL | Nullable types, null safety | §8 |
| EXC | Exception handling and unwind | §9 |
| FN | Functional interfaces | §10 |
| CLO | Closures and lambda capture | §11 |
| STR | String ownership and slicing | §12 |
| ARR | Array methods, laterita.lang.Arrays | §13 (added 2026-05-17) |
| UNS | Unsafe code | §14 |
| STD | Standard library types | §15 |
| THR | Threading | §16 |
| COMP | Compilation model | §17 |
| OQ | Open questions (non-normative) | laterita-open-questions.md |

## ARR prefix (added with OQ-19 resolution)
- ARR-01: Array methods on T[] (.lat surface) — spec line 965
- ARR-02: laterita.lang.Arrays static surface (.java surface) — spec line 994
- ARR-03: MutableConsumer<T> and MutableReducer<T,R> — spec line 1028
- ARR is listed in the codes-group header line (line 5 of laterita-spec.md)
- ARR is listed in terminology.md prefix table

## OQ conventions
- Open questions: numbered OQ-NN, full entry in open section
- Resolved: tombstone bullet in "# Resolved Questions" section at end of laterita-open-questions.md
- Resolved questions also get a section in laterita-reasoning.md
- Do not renumber existing OQ entries; new ones use unused numbers
- OQ-19: resolved (ARR-01/02/03), tombstoned, cross-thread case deferred to OQ-21
- OQ-20: does not exist (number intentionally skipped or unused)
- OQ-21: currently open (cross-thread ownership of split mut-slices)
- Highest open OQ as of 2026-05-17: OQ-21

## Recurring patterns / mistakes to watch
- After section renumbering, §-refs in reasoning and open-questions can be left stale (§17→§18 style)
- ARR prefix not in spec line-5 codes header (minor omission, not a broken reference)
- ArraySplit<T> record is defined inside ARR-02 body but used in ARR-01 — this is intentional (forward reference within same section)
- MOVE-06 originally had no ARR cross-reference; the OQ-19 resolution added it correctly
