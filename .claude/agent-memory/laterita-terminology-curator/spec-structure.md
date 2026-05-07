---
name: Specification Structure
description: Layout and relationships of the four core Laterita specification documents
type: reference
---

## Document Organization

The Laterita specification is in `/workspace/jdk/doc/laterita/` and consists of four files:

### 1. README.md
Overview and entry point. Lists the three main spec documents and reading order recommendations (new to project → skim §1–3, then read matching reasoning; evaluating a design → open reasoning doc; looking for unresolved questions → open questions doc).

### 2. laterita-spec.md (~64KB)
**Normative specification.** Defines every requirement a compiler must implement. Organized in 16 sections (numbered §1–16, with §17 for reserved names):
- §1–2: Bindings, optionality
- §3–6: Move/borrow, mutability, lifetimes, cleanup
- §7–11: Copying, unreachability, strings, functional interfaces, closures
- §12–15: Exceptions, unsafe, standard library types (Rc/Arc/Cell/Heap/Mutex), threads
- §16–17: Compilation model, reserved names

Each requirement carries a mnemonic code (e.g., `BIND-01`, `MOVE-03`) for cross-reference.

### 3. laterita-reasoning.md (~90KB)
**Design rationale.** Explains *why* each spec rule reads the way it does. Section headers reference the corresponding spec codes. Includes:
- Design alternatives considered
- Trade-offs taken
- Java/Rust/Kotlin precedents followed or rejected
- Non-normative recommendations on implementation and style

### 4. laterita-open-questions.md (~5KB)
**Unresolved design decisions.** Entries that were surfaced during design but *not* resolved. Format:
- OQ-N: name
- Surfaced when: context
- The issue: what's unresolved
- Why it matters: implications
- Related codes: spec sections it touches

Current entries: OQ-06 (Spring DI), OQ-10 (JavaBean migration), OQ-11 (bean scopes), OQ-14 (string ownership), OQ-15 (Java code migratability).

Resolved questions are reduced to one-line tombstones at the end.

---

## Key Terminology Observations

**Spec code prefixes** organize requirements:
- BIND, NULL, MOVE, MUT, LIFE, DROP, OBJ, UNR, STR, FN, CLO, EXC, UNS, STD, THR, COMP

**Rust-derived vocabulary** pervades the spec:
- Ownership, borrowing, moves, lifetimes, traits (via functional interfaces), RAII (`onDrop()`)

**Java-preserving choices:**
- Types-first syntax, not postfix (Rust uses postfix)
- `mut` for mutability (single unified keyword)
- `give`/`take` instead of Rust's single `move` (reflecting Java's two-ended asymmetry)
- Anonymous functional interfaces instead of trait objects

**Project conventions:**
- Do not change existing OQ numbers
- Resolved questions must document the reasoning before becoming tombstones
- New terminology is added to terminology.md; each entry must be used in the spec

---

## Terminology Coverage Status

**Created:** 2026-05-07

**Entries included:** 60+ terms covering:
- Rust concepts (borrow, lifetime, move, ownership, etc.)
- Project-specific keywords (give, take, bound, local, nonlocal, unsafe, internal, onDrop, etc.)
- Standard library types (Rc, Arc, Cell, Heap, Mutex, MutexGuard, WeakReference, Thread)
- Advanced concepts (monomorphization, variance, interior mutability, drop flags, poison, etc.)
- Java analogies for every Rust concept

**Audit status:** Initial creation. Next audit after 4 more edits (5/5 edit threshold).
