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

Items 1 and 2 below are no longer migration scaffolding — they were absorbed into the main spec (§18). Laterita's surface *is* Java + annotations + stdlib static methods, with no new keywords. What remains open is the tooling.

1. ~~Java Annotations like `@mut` or `@take` that reflect the new keywords.~~ Resolved — these are the spec (§18, BIND-02, MOVE-03, LIFE-02, DROP-06, UNS-01, STD-07).
2. ~~Class with static function `T give(@take T x) { return x; }` as a temporary drop in for the `give` keyword in expressions.~~ Resolved — declared as `laterita.lang.Intrinsics.give` per §18.
3. A tool that enhances java classes with the spec's annotations best-effort. It adds `@Nullable` wherever required.
4. A java compiler plugin that simulates the borrow checker based on the annotations above, so developers can improve their Java code before fully migrating.
5. A tool that converts annotated Java code to a `.lat` source (or keeps it as `.java`; both are valid laterita source per COMP-06). It assumes items 3 and 4 have already been executed.
6. A laterita formatter that formats laterita always in the same manner. It should allow only very few formatting freedoms to developers (e.g. it wouldn't remove some additional line breaks).

## OQ-21 — Cross-thread ownership of split mut-slices

**Surfaced when:** resolving OQ-19. The single-thread splitting story (ARR-01, ARR-02) covers in-place divide-and-conquer where both halves are processed in the same thread, possibly via callbacks (`Arrays.forEachChunk`, `splitAt` followed by structured-concurrency scoped work). It does not cover the case where the two halves are sent to **different** threads with independent ownership and independent drop.

**The issue.** A `@bound @mut T[]` slice is a non-owning borrow tied to some upstream owner. To move it across a thread boundary it must escape that bound — but the bound is exactly what guarantees the slice cannot outlive its source. Plain `Arc<T[]>` (STD-02) does not help: both handles reach the *whole* allocation, not disjoint ranges, so mut access through them would require per-element `Mutex<T>` and defeat the disjointness story.

What is needed is a refcounted **segmented owning slice**: each handle carries `(allocation_ptr, offset, length)` plus a share of the refcount on the backing allocation. The type-system invariant guarantees no two live handles overlap; mut access through a handle is then safe because the disjointness is structural. The allocation is freed when the last handle drops. This is the shape of Rust's `BytesMut::split_off`, and it underpins the zero-copy network buffer crates (`hyper`, `tokio-util`, `quinn`) and shared-arena patterns in DSP and image processing.

**The candidates.**

a. **Defer to per-element `Mutex<T>` and `Arc<T[]>`** — the conservative answer. Two threads `share()` the same `Arc<T[]>` and lock each element individually as they touch it. Correct, simple, slow (atomic per element), and forces synchronization on writes that are statically disjoint.

b. **A dedicated `SharedSlice<T>` stdlib type** — refcounted segmented owning slice, with a `splitAt` that consumes the receiver and returns two `SharedSlice<T>` halves over disjoint ranges of the same backing allocation. Each half is movable across thread boundaries (non-`@local` per STD-07), borrowable as a `@bound @mut T[]` for the duration of work, and drops the allocation cooperatively via atomic refcount.

c. **An owning-array generalization** — extend `Arc<T[]>` with range metadata and a splitting API, fusing (b) into the existing stdlib type rather than adding a new one. Simpler surface, complicates `Arc<T>`'s semantics for non-array `T`.

**Why it matters.** Determines whether thread-spawn-and-join parallelism over a single owned array — the rayon idiom in its full form — is expressible without per-element locking. The single-thread story (OQ-19 resolution) already covers callback-based parallelism via `Arrays.forEachChunk` and structured-concurrency-style scoped workers. The unresolved case is true `Thread.start(...)` with each thread fully owning its half of the data for the duration.

**Related codes:** ARR-01, ARR-02, STD-02 (Arc), STD-09 (Mutex), STD-07 (`@local`), THR-01.

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
* OQ-17 — Public expression of buffer splitting for `String` (resolved by STR-07: `bound String` is read-only, so substring views are ordinary shared borrows under MOVE-04; mut-array splitting resolved by OQ-19 → ARR-01/02)
* OQ-18 — `onDrop()` reaching already-dropped subclass state via virtual dispatch (resolved by DROP-09: `onDrop()` bodies only on `final` classes)
* OQ-19 — Ownership splitting of mut arrays (resolved by ARR-01/02/03: methods on `T[]` and `laterita.lang.Arrays`, with `MutableConsumer`/`MutableReducer` for the `.java` surface; cross-thread independent-ownership case deferred to OQ-21)
