# Laterita — Open Questions

This document records questions and issues that surfaced during the design conversation but were *not* resolved. It is intentionally minimal — only items actually raised in the discussion are listed. Each entry references the spec code(s) it relates to, where applicable.

Resolving any of these requires a separate decision; the spec deliberately leaves them open.

## OQ-06 — Spring DI and compile-time annotation processing details

**Surfaced when:** discussing how Spring-style dependency injection would work without runtime reflection.

**The issue.** We sketched a model where `@Component` and friends are processed at compile time, generating a wiring class — analogous to Dagger and Quarkus. We discussed compile-time proxy generation for `@Transactional`, conditional beans, and `ContextLocal<T>` for request scope.

**The question.** This was an *implications* discussion, not a language-spec discussion. None of it appears in the spec because it's library-and-tooling territory. But several pieces remain unresolved:
- How conditional beans whose conditions depend on runtime config are handled.
- Whether `ContextLocal<T>` should be a standard library type or framework-specific. The first candidates to evaluate are Java's existing `ThreadLocal<T>` and `ScopedValue<T>` (JEP 446) — `ScopedValue<T>` in particular is dynamically scoped and integrates cleanly with structured concurrency, which fits Laterita's ownership-bound thread model. A new `ContextLocal<T>` should only be introduced if these prove insufficient.
- How proxy generation interacts with `final` and `private` access.

**Why it matters.** Determines whether the Spring ecosystem can port to Laterita with reasonable effort.

**Related codes:** none directly; touches COMP-02.

## OQ-10 — JavaBean migration story

**Surfaced when:** enumerating what Java code requires rewriting.

**The issue.** Setter-heavy POJOs with `@Autowired` fields are pervasive in Java. We agreed the Laterita answer is records and constructor injection, with mechanical refactoring tooling. We didn't specify what that tooling looks like or whether it's part of the language project.

**Why it matters.** Determines the migration effort for existing Spring/Jakarta EE applications.

**Related codes:** BIND-04.

## OQ-11 — Bean scopes beyond singleton

**Surfaced when:** discussing Spring DI implications.

**The issue.** Singleton beans map to `Arc<T>`. Prototype beans are method calls. Request scope and session scope require per-request storage that we sketched as `ContextLocal<T>`. We agreed deterministic cleanup of request-scoped resources is a real win compared to Spring's GC-dependent model. We didn't specify the API.

**Why it matters.** Determines whether web frameworks like Spring MVC port cleanly.

**Related codes:** OQ-06.

## OQ-15 — Migration tooling for existing Java code

Items 1 and 2 below are no longer migration scaffolding — they were absorbed into the main spec (§17). Laterita's surface *is* Java + annotations + stdlib static methods, with no new keywords. What remains open is the tooling.

1. ~~Java Annotations like `@mut` or `@take` that reflect the new keywords.~~ Resolved — these are the spec (§17, BIND-02, MOVE-03, LIFE-02, DROP-06, UNS-01, STD-07).
2. ~~Class with static function `T give(@take T x) { return x; }` as a temporary drop in for the `give` keyword in expressions.~~ Resolved — declared as `laterita.lang.Intrinsics.give` per §17.
3. A tool that enhances java classes with the spec's annotations best-effort. It adds `@Nullable` wherever required.
4. A java compiler plugin that simulates the borrow checker based on the annotations above, so developers can improve their Java code before fully migrating.
5. A tool that converts annotated Java code to a `.lt` source (or keeps it as `.java`; both are valid laterita source). It assumes items 3 and 4 have already been executed.
6. A laterita formatter that formats laterita always in the same manner. It should allow only very few formatting freedoms to developers (e.g. it wouldn't remove some additional line breaks).

## OQ-19 — Ownership splitting of mut arrays: required, and what shape?

**Surfaced when:** resolving OQ-17. Once `String` was shown not to need a public splitting API (STR-07 makes every `bound String` read-only, so repeated `substring` calls implement `String.split` / `Pattern.split` / `String.lines` / `URI` getters without any disjointness obligation), the remaining splitting question is about **mut** arrays: two simultaneous `mut T[]` slices over the same backing storage for parallel in-place algorithms. MOVE-06 currently posits a stdlib `splitAt` for this case using `unsafe` internally, but the design has not been settled.

**The issue.** Two sub-questions.

1. **Is the primitive required at all?** MOVE-06 already permits two disjoint mut slices when the compiler can prove disjointness from constant or simple-arithmetic ranges. Pushing the prover slightly further — pairs `[0, mid)` and `[mid, length)` sharing a midpoint, ranges related by a chain of `≤` comparisons — would cover the bulk of real divide-and-conquer code without any explicit `splitAt` call. The remaining cases (slices indexed by independent variables with no compiler-visible ordering) may be rare enough not to justify a stdlib primitive.

2. **If required, what is its shape?** Three candidates:

   a. **Continuation-passing.** A single method taking the split point and a lambda receiving the two slices:
   ```laterita
   class T[] {
       <R> R splitAt(int mid, mut (mut T[], mut T[]) -> R body);
   }
   ```
   Uses FN-01 anonymous functional interfaces already in the language. Needs no multi-return feature, no record specialization per primitive element type, and no `unsafe` — the body is two `slice(0, mid)` / `slice(mid, length)` calls whose disjointness MOVE-06 already covers. The two slices cannot escape the lambda because they are typed `bound`. The divide-and-conquer pattern fits directly: each slice is `give`n to a different consumer (one to a thread, one processed locally) inside the body, and the lambda returns a single result.

   b. **Record return.** `record ArraySplit<T>(bound mut T[] left, bound mut T[] right)` plus `ArraySplit<T> splitAt(int mid)`. More Java-idiomatic at the call site (two named bindings in the surrounding scope), but costs a record per primitive element type to avoid boxing, plus a rule for `bound` to propagate through a record's fields out to its consumers.

   c. **Multi-return language feature.** A tuple-like return shape introduced solely to make `splitAt` natural. Adds the most language surface for the narrowest benefit.

**Why it matters.** Determines whether parallel-decomposition over **mut** arrays (in-place parallel sort, parallel fold over a working buffer, partition-based numerics) is expressible in safe code, and at what cost to the language surface. Also determines whether MOVE-06's "`splitAt` uses `unsafe` internally" framing survives, or whether the operation reduces to two ordinary `slice` calls.

**Related codes:** MOVE-06, FN-01.

# Resolved Questions

* OQ-01 — Panic safety and lock poisoning
* OQ-02 — Exception ergonomics beyond what ownership forces
* OQ-03 — Reflection model
* OQ-04 — Cross-thread safety marker (resolved as `local`, STD-07)
* OQ-05 — Closure interface names (dissolved by anonymous functional interfaces, FN-01)
* OQ-07 — Method-level `mut` syntax
* OQ-08 — Owned-vs-borrowed strings: one type or two
* OQ-09 — Iterator.remove and ConcurrentModificationException
* OQ-12 — Doubly-linked structures and graph data
* OQ-13 — User-invoked `close()` and early cleanup
* OQ-14 — Ownership of Strings (resolved by STR-06 literal-borrow rule, STR-07 closing the door on stdlib `String` mut methods, STR-08 borrow-by-default receiver; a remaining question on public buffer splitting is deferred to OQ-17)
* OQ-16 — Mutable `String`: which methods belong where (resolved by STR-07: stdlib `String` exposes no mut methods at all; bulk construction stays on `StringBuilder`)
* OQ-17 — Public expression of buffer splitting for `String` (resolved by STR-07: `bound String` is read-only, so substring views are ordinary shared borrows under MOVE-04; mut-array splitting is OQ-19)
* OQ-18 — `onDrop()` reaching already-dropped subclass state via virtual dispatch (resolved by DROP-09: `onDrop()` bodies only on `final` classes)
