---
name: Specification Structure
description: Layout and relationships of the Laterita specification documents
type: reference
---

## Document Organization

The Laterita specification lives in `doc/` at the repo root and consists of:

- `doc/laterita-spec.md` — normative spec. Every topic except `LAT` is the Java-compatible surface (expressible as annotated `.java` that `javac` parses). The `LAT` topic is the `.lat` sugar (LAT-00: pure syntactic sugar, exact desugaring, no semantics of its own).
- `doc/laterita-reasoning.md` — design rationale. Section headers reference the corresponding spec codes. Written as if the current spec were the first commit (no project history).
- `doc/laterita-open-questions.md` — unresolved questions (`OQ-NN`).
- `doc/resolved-questions.md` — closed decisions: rejected-alternatives table + resolved-OQ tombstones.
- `doc/terminology.md` — defined terms, plus a "Spec Code Prefixes" table and "Java Analogies" table.

## Topics are prefix-identified (no section numbers)

IMPORTANT: As of branch `claude/adoring-fermat-d3KkY` the spec NO LONGER uses numeric sections (`§1`, `## 1.`, etc.). Each top-level topic is identified by its code prefix in the heading, e.g. `## OWN — Ownership`. The reason: a prefix is a stable ID, so inserting or moving a topic never renumbers anything or invalidates cross-references.

- Cross-references use the prefix and rule code (`OWN-02`, `DEC-03`), never `§N`.
- Former ordinal ranges ("§1–18 surface", "§1–20") are now the named concept "the Java-compatible surface" (= every topic except `LAT`).
- If a `§N` numeric reference ever reappears, treat it as STALE and flag it.

## Current topic order (verified from `^## ` headings)

OWN, LIFE, MUT, HIER, TARG, STAT, DROP, UNR, **DEC**, OBJ, NULL, EXC, FN, CLO, STR, ARR, UNS, STD, THR, COMP, **RESV**, LAT, NABI, GEN.

## Spec code prefixes

OWN (ownership), LIFE (lifetimes), MUT (mutability), HIER (class hierarchy and override), TARG (annotations in generic type arguments), STAT (static storage), DROP (scope-exit cleanup), UNR (unreachability), **DEC (deconstruction)**, OBJ (object copying), NULL (optionality), EXC (exceptions), FN (functional interfaces), CLO (closures), STR (strings), ARR (arrays), UNS (unsafe), STD (standard library types), THR (threads), COMP (compilation model), **RESV (reserved names + the annotation/intrinsic surface)**, LAT (`.lat` surface forms), NABI (native ABI), GEN (code generation annotations).

OBSOLETE prefixes that may linger in old notes: `BIND`, `MOVE`. These were reorganized long ago into `OWN` / `MUT` / `LIFE` / `HIER` / `TARG` / `STAT` (see resolved-questions.md). Any memory referencing them is stale.

## Deconstruction (DEC) and terminology

- `DEC` is its own topic (DEC-01 through DEC-03), between `UNR` and `OBJ`. `OWN-06` remains only as a short pointer to `DEC`.
- The term "partial move" is RETIRED in favor of "deconstruction".
- "partially deconstructed" is dropped: an object is "deconstructed" as soon as its first field is moved out.
- `doc/terminology.md` glossary has `### deconstruction` (in its alphabetical slot), and the "Spec Code Prefixes" table includes `DEC` and `RESV`.

---

## Audit status

Memory refreshed on branch `claude/adoring-fermat-d3KkY` to record the prefix-as-identifier scheme, the `DEC` and `RESV` topics, the current topic order, and the deconstruction terminology. Next orphan audit per the usual 5-edit threshold.
