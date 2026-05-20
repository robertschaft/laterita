# Laterita — Resolved Questions & Settled Decisions

This document is the registry of closed decisions. It has two parts:

1. **Rejected alternatives** — design options that were evaluated and discarded, indexed by the name a contributor is likely to reach for. **If a mechanism is listed here, the decision is closed: do not reopen it, re-propose it, or raise it as an open question without new evidence that directly contradicts the recorded reasoning.**
2. **Resolved open questions** — tombstones for `OQ-NN` entries that have been answered.

The full reasoning lives in `laterita-reasoning.md`. Active (unresolved) questions live in `laterita-open-questions.md`.

## Rejected alternatives

| Proposed mechanism | Decision | Where |
|---|---|---|
| `Send` / `Sync` marker traits (separate cross-thread move vs. share markers) | Rejected. A single negative marker, `@local`, covers both move and borrow restriction. The `Send`-but-not-`Sync` distinction is rare and unusable without fine-grained borrow reasoning Java developers do not expect. | STD-07; OQ-04; reasoning "Why `@local`, not Send" |
| Runtime reflection / dynamic class loading | Rejected. No runtime reflection exists in the language or stdlib; the use cases are served by compile-time annotation processing. | COMP-05; OQ-03; reasoning "Why no reflection" |
| `Optional<T>` / Rust `Option<T>` as the optionality type | Rejected. Optionality is a nullable type (`@Nullable T` / `T?`) with flow narrowing, not a wrapper type. | NULL-01..NULL-10; reasoning "Why no separate Optional<T>" |
| `finalize()` for cleanup | Rejected. Cleanup is the compiler-orchestrated `onDrop()`; finalizers are non-deterministic and GC-coupled. | DROP-01; reasoning "Why not `finalize()`" |
| `synchronized`, intrinsic object monitors, `Object.wait`/`notify`/`notifyAll` | Removed. Mutual exclusion is provided exclusively through `Mutex<T>`. | §18; THR-10; reasoning "Why `synchronized` is removed" |
| Rust-style lifetime syntax (`'a`) or a `from` keyword | Rejected. Borrow sources are marked with the `@bound` annotation; named lifetime variables are not part of the surface. | LIFE-02; reasoning "Why `@bound` instead of `'a` or `from`" |
| Cycle collector / tracing GC for reference cycles | Rejected. `Rc`/`Arc` cycles leak; `WeakReference<T>` breaks back-edges. Laterita matches Rust's memory model here rather than exceeding it. | STD-01; STD-03; reasoning "Why cycles leak" |
| A single ownership sigil for both giving and taking | Rejected. Giving and taking are inverse roles; `give(...)` at use sites and `@take` on signatures keep each end explicit. | MOVE-02; MOVE-03; reasoning "Why `give(...)` and `@take` instead of one shared marker" |
| Named closure / function-type interfaces | Rejected. Anonymous structural functional interfaces avoid an interface-name explosion that no naming convention survives once parameter modes enter the type system. | FN-01; OQ-05; reasoning "Why structural rather than nominal" |
| `@take` on local binding declarations | Rejected. A local's ownership is fixed by its initializer; `@take` is a published contract and lives only on parameters (including `this`) and fields. | BIND-07; reasoning "Why `give(...)` and `@take` instead of one shared marker" |
| Separate `BoundPair` / `OwnedPair` return types for array splitting | Rejected. BIND-08's generic-substitution rule lets the general-purpose `Pair<L, R>` record carry binding modifiers on its arguments, so no dedicated pair type is needed. | ARR-04; BIND-08; OQ-19; OQ-21 |
| Two-type owned/borrowed strings (`String` + `&str`) | Rejected. One `String` type, with the compiler tracking owned-vs-borrowed per binding; `clone()` is the universal owned-copy escape valve. | STR-02; OBJ-02; OQ-08; reasoning "Why owned vs. borrowed strings tracked per-binding" |

Deferred — not rejected, not specified:

| Construct | Status | Where |
|---|---|---|
| Early-cleanup statement (`drop x;`) | Not specified. Restructuring scopes covers the rare cases, and an escape hatch reintroduces the double-drop surface. Can be added later without breaking existing code. | reasoning "Why explicit `onDrop()` calls are forbidden" |

## Resolved open questions

Tombstones for `OQ-NN` entries that have been answered. Each names the OQ, summarizes its resolution, and points at the spec codes and reasoning that record the decision. The original open-question wording can be found in this file's git history.

* **OQ-01** — Panic safety and lock poisoning. Resolved by `Mutex<T>`'s closure-scoped API (STD-09) and THR-10: a mutex whose critical-section closure throws is poisoned, and later acquirers get `PoisonedException` — with no bypass.
* **OQ-02** — Exception ergonomics beyond what ownership forces. Resolved by EXC-01 (Java's `try`/`catch`/`finally` and the `Throwable` hierarchy are preserved unchanged) and EXC-05 (the checked/unchecked distinction is dropped; `throws` becomes documentary). The narrower question of restoring checked exceptions is reopened as OQ-22.
* **OQ-03** — Reflection model. Resolved as "none": no runtime reflection in the language or stdlib.
* **OQ-04** — Cross-thread safety marker. Resolved as `@local` (STD-07).
* **OQ-05** — Closure interface names. Dissolved by anonymous functional interfaces (FN-01): there are no closure-interface names to fix because there are no closure interfaces.
* **OQ-07** — Method-level `mut` syntax. Resolved by BIND-02 and BIND-05: receiver mutation is marked with the `@mut` annotation on the method. Laterita introduces no new keyword for it (§18).
* **OQ-08** — Owned-vs-borrowed strings: one type or two. Resolved as one type: the compiler tracks per-binding whether a `String` is owned or borrowed (STR-02), and `clone()` (OBJ-02) is the universal escape valve for any owned/borrowed mismatch. The two-type (`String` / `&str`) model is rejected — see the rejected-alternatives table.
* **OQ-09** — `Iterator.remove` and `ConcurrentModificationException`. Resolved by STD-08: borrow-checked iteration reuses Java's `Iterator` / `ListIterator` API. MOVE-04 makes concurrent modification a compile error, so `ConcurrentModificationException` and `modCount` leave the language.
* **OQ-12** — Doubly-linked structures and graph data. Resolved by `Rc<T>` / `Arc<T>` on forward edges plus `WeakReference<T>` (STD-03) on back edges; no dedicated graph type is added.
* **OQ-13** — User-invoked `close()` and early cleanup. Resolved by DROP-06: `onDrop()` is `@internal` and never user-invoked; a user-defined `close()` survives migration as an ordinary method, kept distinct from `onDrop()`. No early-cleanup statement is specified — see the deferred-constructs table above. (The number OQ-13 earlier tracked the `onDrop()` no-blocking rule, resolved by THR-05 and THR-06, before being reused for this question.)
* **OQ-14** — Ownership of Strings. Resolved by STR-06 (literal-borrow rule), STR-07 (no stdlib `String` mut methods), and STR-08 (borrow-by-default receiver); the buffer-splitting remainder was tracked under OQ-17.
* **OQ-16** — Mutable `String`: which methods belong where. Resolved by STR-07: stdlib `String` exposes no mut methods at all; bulk construction stays on `StringBuilder`.
* **OQ-17** — Public expression of buffer splitting for `String`. Resolved by STR-07: `bound String` is read-only, so substring views are ordinary shared borrows under MOVE-04; mut-array splitting resolved by OQ-19 → ARR-01.
* **OQ-18** — `onDrop()` reaching already-dropped subclass state via virtual dispatch. Resolved by DROP-09: `onDrop()` bodies only on `final` classes, so no down-dispatch into freed subclass storage can occur.
* **OQ-19** — Ownership splitting of mut arrays. Resolved by ARR-01/03/04: methods on `T[]` and `laterita.lang.Arrays`, `MutableConsumer` for the `.java` surface, with the result wrapped in the general-purpose `Pair<L, R>` record (instantiated as `@bound Pair<@bound @mut T[], @bound @mut T[]>` — BIND-08's generic-substitution rule makes a separate bound-pair type unnecessary). The cross-thread independent-ownership case is resolved separately under OQ-21.
* **OQ-21** — Cross-thread ownership of split mut-slices. Resolved by ARR-01/02/04: consuming `T[].splitOff(int)` returns two owning halves backed by a shared refcounted allocation, wrapped in the general-purpose `Pair<L, R>` record (instantiated as `Pair<T[], T[]>`) for partial-move extraction; `Arrays.stream(@bound T[])` produces a JDK `Stream<T>` bound to the source array by the parameter-source form of LIFE-02, whose `.parallel()` form covers read-only data-parallel processing via the standard `Spliterator` mechanism. In-place parallel mutation stays on the `splitOff` path.
