# Laterita — Resolved Questions

Tombstones for open questions that have been resolved. Each entry names the OQ, summarizes its resolution, and points at the spec codes and reasoning section that record the decision.

The full reasoning lives in `laterita-reasoning.md`; the original open-question wording (when still useful) can be found in this file's git history. Active (unresolved) questions live in `laterita-open-questions.md`.

* **OQ-01** — Panic safety and lock poisoning.
* **OQ-02** — Exception ergonomics beyond what ownership forces.
* **OQ-03** — Reflection model. Resolved as "none": no runtime reflection in the language or stdlib.
* **OQ-04** — Cross-thread safety marker. Resolved as `local` (STD-07).
* **OQ-05** — Closure interface names. Dissolved by anonymous functional interfaces (FN-01): there are no closure-interface names to fix because there are no closure interfaces.
* **OQ-07** — Method-level `mut` syntax.
* **OQ-08** — Owned-vs-borrowed strings: one type or two.
* **OQ-09** — `Iterator.remove` and `ConcurrentModificationException`.
* **OQ-12** — Doubly-linked structures and graph data. Resolved by `Rc<T>` / `Arc<T>` on forward edges plus `WeakReference<T>` (STD-03) on back edges; no dedicated graph type is added.
* **OQ-13** — User-invoked `close()` and early cleanup.
* **OQ-14** — Ownership of Strings. Resolved by STR-06 (literal-borrow rule), STR-07 (no stdlib `String` mut methods), and STR-08 (borrow-by-default receiver); the buffer-splitting remainder was tracked under OQ-17.
* **OQ-16** — Mutable `String`: which methods belong where. Resolved by STR-07: stdlib `String` exposes no mut methods at all; bulk construction stays on `StringBuilder`.
* **OQ-17** — Public expression of buffer splitting for `String`. Resolved by STR-07: `bound String` is read-only, so substring views are ordinary shared borrows under MOVE-04; mut-array splitting resolved by OQ-19 → ARR-01.
* **OQ-18** — `onDrop()` reaching already-dropped subclass state via virtual dispatch. Resolved by DROP-09: `onDrop()` bodies only on `final` classes, so no down-dispatch into freed subclass storage can occur.
* **OQ-19** — Ownership splitting of mut arrays. Resolved by ARR-01/03/04: methods on `T[]` and `laterita.lang.Arrays`, `MutableConsumer` for the `.java` surface, with the result wrapped in the general-purpose `BoundPair<L, R>` record (instantiated as `BoundPair<T[], T[]>`). The cross-thread independent-ownership case is resolved separately under OQ-21.
* **OQ-21** — Cross-thread ownership of split mut-slices. Resolved by ARR-01/02/05: consuming `T[].splitOff(int)` returns two owning halves backed by a shared refcounted allocation, wrapped in the general-purpose `OwnedPair<L, R>` record (instantiated as `OwnedPair<T[], T[]>`) for partial-move extraction; `Arrays.stream(@bound T[])` produces a JDK `Stream<@bound T>` whose `.parallel()` form covers read-only data-parallel processing via the standard `Spliterator` mechanism. In-place parallel mutation stays on the `splitOff` path.
