---
name: Laterita spec structure
description: File layout, topic order, prefix families, cross-reference conventions, and stale-pattern flags for the Laterita spec under /home/user/laterita/doc/
type: project
---

# Laterita spec structure

**Why:** Institutional knowledge for fast future audits without re-reading every file from scratch.
**How to apply:** Use this as the baseline when scanning for broken references, stale `§N` citations, or missing prefix definitions.

## Document paths

| File | Role |
|---|---|
| `/home/user/laterita/doc/laterita-spec.md` | Normative spec. Defines all spec IDs. |
| `/home/user/laterita/doc/laterita-reasoning.md` | Explains why each rule holds. References spec IDs but defines none. |
| `/home/user/laterita/doc/laterita-open-questions.md` | Unresolved design questions (OQ-NN). Defines OQ IDs. |
| `/home/user/laterita/doc/resolved-questions.md` | Tombstones for closed OQ items + rejected-alternatives table. |
| `/home/user/laterita/doc/terminology.md` | Defined terms; cites spec IDs but defines none. |

## Topic order in `laterita-spec.md` (verified 2026-06-05)

OWN, LIFE, MUT, HIER, TARG, STAT, DROP, UNR, **DEC**, OBJ, NULL, EXC, FN, CLO, STR, ARR, UNS, STD, THR, COMP, **RESV**, LAT, NABI, GEN.

Rule of thumb in the spec intro: every topic except `LAT` is the Java-compatible surface.

## Section numbering: ABOLISHED

Topics no longer carry ordinal section numbers (`§1`, `## 1.`, etc.).
Each top-level topic is identified by its code prefix in the heading, e.g. `## OWN — Ownership`.
A prefix is a stable ID, so inserting or moving a topic never renumbers anything.

### Stale references to flag as errors

Any `§N` / `§N–M` citation, or textual "section(s) N" / "sections 1–18" range, is stale and must be flagged.
Correct replacements:
- `§21` → "the `LAT` topic"
- `§20` → "`RESV`"
- "§1–18 surface" / "§1–20" / "sections 1–18" → "the Java-compatible surface"

As of branch `claude/adoring-fermat-d3KkY` all such references were removed across spec, reasoning, open-questions, resolved-questions, terminology, README, and CLAUDE.md. Any reappearance is a regression.

## Prefix families

| Prefix | Domain |
|---|---|
| OWN | Ownership, parameter modes; OWN-06 is a pointer to DEC |
| LIFE | Lifetimes and borrow lifetime intersection |
| MUT | Mutability |
| HIER | Class hierarchy, override variance |
| TARG | Annotations in generic type arguments |
| STAT | Static storage |
| DROP | Scope-exit cleanup, onDrop() |
| UNR | Unreachability (broken()) |
| DEC | Deconstruction (DEC-01…DEC-03) |
| OBJ | Object copying |
| NULL | Optionality |
| EXC | Exceptions |
| FN | Functional interfaces |
| CLO | Closures |
| STR | Strings |
| ARR | Arrays |
| UNS | Unsafe |
| STD | Standard library types |
| THR | Threads |
| COMP | Compilation model |
| RESV | Reserved names + the annotation/intrinsic surface (prose + tables, no numbered sub-rules) |
| LAT | `.lat` surface forms (LAT-00…LAT-08) |
| NABI | Native ABI guarantees |
| GEN | Code generation annotations |
| OQ | Open design questions |

## DEC topic — key facts

- Own topic between UNR and OBJ since branch `claude/adoring-fermat-d3KkY`.
- OWN-06 is now a one-sentence pointer to DEC.
- DROP-04 and DROP-08 reference deconstruction (DROP-04: "When deconstruction (OWN-06) has left fields moved out…"; DROP-08: "A class with `onDrop()` cannot be deconstructed").
- Reasoning-doc heading: "Why deconstruction is restricted to direct field access (DEC)".

## RESV topic — key facts

- Prefix newly assigned to the formerly-unnamed "Reserved Names" section.
- No numbered sub-rules; one prose section with annotation and intrinsics tables.
- Referenced by COMP-06 and LAT-00 as "the annotation and intrinsic surface of the `RESV` topic".

## Terminology changes (branch `claude/adoring-fermat-d3KkY`)

- "partial move" → retired; now "deconstruction".
- "partially deconstructed" → retired; an object is "deconstructed" from its first field move onward.
- Reasoning-doc headings renamed from "partial move" wording: "Why deconstruction is restricted to direct field access", "Why a class with `onDrop()` cannot be deconstructed".

## Obsolete code groups

- `BIND-NN`, `MOVE-NN` — old codes, superseded by OWN/LIFE/MUT/HIER/TARG/STAT. Any reference is stale.
- `§N` section numbers — abolished; any occurrence is stale.
