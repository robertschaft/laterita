# Laterita — Open Questions

This document records questions and issues that surfaced during the design conversation but were *not* resolved. It is intentionally minimal — only items actually raised in the discussion are listed. Each entry references the spec code(s) it relates to, where applicable.

Resolving any of these requires a separate decision; the spec deliberately leaves them open.

---

## OQ-01 — Panic safety and lock poisoning

**Surfaced when:** working through `Mutex<T>` during the standard-library verification pass.

**The issue.** If a thread holding a `Mutex<T>` panics mid-operation, the data inside might be in an inconsistent state. Rust's `Mutex` returns a `PoisonError` from subsequent locks; the user must explicitly opt in to recovery. Laterita has not chosen a policy.

**Options identified in the conversation:**
- Implicit poisoning that throws on next access (simplest).
- Explicit `Result`-returning API (most Rust-like, but Laterita doesn't have `Result` as a settled concept either — see OQ-02).
- Some hybrid where some types poison and others don't.

**Why it matters.** Affects the API of every synchronization primitive. Affects whether exceptions in `mut` methods can be safely caught and continued from.

**Related codes:** STD (no specific code yet), EXC-01.

---

## OQ-02 — Exception ergonomics beyond what ownership forces

**Surfaced when:** designing exception handling, then explicitly walking back the over-reach.

**The issue.** I initially proposed sealed error types, mandatory `try` at call sites, exhaustive pattern matching in catch, no checked-vs-unchecked split, and a Rust-style panic distinction. Then I correctly noted these are *not* forced by ownership — they are independent improvements to Java's exception story. The spec preserves Java's existing exception syntax (EXC-01) and leaves the question of whether to *also* fix exception ergonomics open.

**The actual question.** Should Laterita ship with regular Java exceptions (current spec), or take this opportunity to redesign exception handling? If the latter, which of the proposed improvements get adopted?

**Why it matters.** Affects the look and feel of essentially all error-handling code. Affects whether Laterita has a `Result<T, E>` type. Affects whether checked exceptions exist.

**Related codes:** EXC-01.

---

## OQ-03 — Reflection model

**Surfaced when:** verifying the standard library design (specifically when discussing what monomorphization implies for runtime type information).

**The issue.** Java's reflection assumes runtime type information for every class, dynamic class loading, and that erased generics like `List<String>` and `List<Integer>` share runtime representation. Laterita's monomorphization (COMP-02) erases the opposite: each instantiation is a distinct compiled type with no runtime generic information. Field offsets are baked in at compile time.

**The question.** What reflection capabilities, if any, does Laterita support? Full reflection would defeat monomorphization; no reflection breaks much of the Java ecosystem (JSON serializers, ORM frameworks, mocking libraries). A middle ground exists but hasn't been specified.

**Why it matters.** Determines what fraction of the Java ecosystem ports to Laterita. Affects whether libraries like Jackson, Hibernate, Mockito have a path forward.

**Related codes:** COMP-02.

---

## OQ-04 — `Send` declaration syntax

**Surfaced when:** specifying what types are safe to move across thread boundaries.

**The issue.** STD-07 records that the language must distinguish `Send` from non-`Send` types. The conversation never settled on how a class declares its `Send`-ness. Possibilities discussed implicitly: an interface (`implements Send`), an annotation, automatic inference based on field types.

**Why it matters.** Affects every concurrency primitive's signature. Affects how user code declares whether their types are thread-safe.

**Related codes:** STD-07, UNS-02 (item 3).

---

## OQ-05 — Closure interface names

**Surfaced when:** specifying the three closure types.

**The issue.** CLO-03 requires three interfaces for the three closure capture modes (read / mutate / consume). The conversation used `Closure`, `RepeatableClosure`, `SharedClosure` as illustrative names but did not finalize them. Other reasonable names exist (`Fn`/`FnMut`/`FnOnce` from Rust, `Function`/`Consumer` from Java extended to three forms, etc.).

**Why it matters.** These names appear in every higher-order method signature. Picking them is a public-API decision.

**Related codes:** CLO-03.

---

## OQ-06 — Spring DI and compile-time annotation processing details

**Surfaced when:** discussing how Spring-style dependency injection would work without runtime reflection.

**The issue.** We sketched a model where `@Component` and friends are processed at compile time, generating a wiring class — analogous to Dagger and Quarkus. We discussed compile-time proxy generation for `@Transactional`, conditional beans, and `ContextLocal<T>` for request scope.

**The question.** This was an *implications* discussion, not a language-spec discussion. None of it appears in the spec because it's library-and-tooling territory. But several pieces remain unresolved:
- How conditional beans whose conditions depend on runtime config are handled.
- Whether `ContextLocal<T>` should be a standard library type or framework-specific.
- How proxy generation interacts with `final` and `private` access.

**Why it matters.** Determines whether the Spring ecosystem can port to Laterita with reasonable effort.

**Related codes:** none directly; touches COMP-02.

---

## OQ-07 — Method-level `mut` syntax

**Surfaced when:** writing the first examples of mutating methods.

**The issue.** BIND-05 specifies that mutating methods are marked with `mut` before the return type. The conversation used `mut void put(String key, Entry entry)` without the user explicitly confirming this is the right placement. Alternatives could be `void mut put(...)` (after return type) or a different keyword altogether.

**Why it matters.** Affects every mutating method signature in the language. Aesthetic, but pervasive.

**Related codes:** BIND-05.

---

## OQ-08 — Owned-vs-borrowed strings: one type or two

**Surfaced when:** working through the StringBuilder/String slice case.

**The issue.** STR-02 records that the compiler tracks per-binding whether a `String` is owned or borrowed. The alternative — Rust's two-type model with separate `String` and `&str` — was considered and rejected as too un-Java-like. But the chosen single-type model means the compiler must track significant per-binding state that doesn't appear in the source. We agreed on the direction; we didn't validate the implementation complexity.

The introduction of mark-borrow at signature boundaries (LIFE-02, MOVE-03) tightens the picture: the public contract is now explicit (owned vs. `bound` on returns, bare vs. `^` on parameters), so cross-method tracking is no longer hidden. What remains is per-binding tracking *inside* a method body, where the compiler still has to thread owned/borrowed state through local flow.

**The question.** Is per-binding owned/borrowed tracking inside method bodies clean enough to produce comprehensible error messages, or does intra-method confusion still push us toward the two-type model?

**Why it matters.** Affects every string-handling API. Affects how confusing the type system is to users.

**Related codes:** STR-02, STR-03, STR-04.

---

## OQ-09 — Iterator.remove and ConcurrentModificationException

**Surfaced when:** verifying the iterator pattern.

**The issue.** Real Java's `ConcurrentModificationException` is the runtime version of "you mutated the collection while iterating." Laterita's borrow rules check this at compile time. We agreed CME-requiring code is essentially nonexistent in real codebases, but didn't enumerate the few places it does exist (mostly inside library implementations of `removeIf`).

**The question.** Is there a cleaner pattern for in-iteration modification than dropping into `private unsafe`? Standard library methods like `removeIf` need *some* implementation strategy.

**Why it matters.** Determines whether common collection-mutation idioms are ergonomic or painful.

**Related codes:** UNS-01.

---

## OQ-10 — JavaBean migration story

**Surfaced when:** enumerating what Java code requires rewriting.

**The issue.** Setter-heavy POJOs with `@Autowired` fields are pervasive in Java. We agreed the Laterita answer is records and constructor injection, with mechanical refactoring tooling. We didn't specify what that tooling looks like or whether it's part of the language project.

**Why it matters.** Determines the migration effort for existing Spring/Jakarta EE applications.

**Related codes:** BIND-04.

---

## OQ-11 — Bean scopes beyond singleton

**Surfaced when:** discussing Spring DI implications.

**The issue.** Singleton beans map to `Atomic<T>`. Prototype beans are method calls. Request scope and session scope require per-request storage that we sketched as `ContextLocal<T>`. We agreed deterministic cleanup of request-scoped resources is a real win compared to Spring's GC-dependent model. We didn't specify the API.

**Why it matters.** Determines whether web frameworks like Spring MVC port cleanly.

**Related codes:** OQ-06.

---

## OQ-12 — Doubly-linked structures and graph data

**Surfaced when:** discussing what Java code is hard to port.

**The issue.** Cyclic data structures (doubly-linked lists, parent-pointer trees) are genuinely harder under ownership than under GC. We agreed the answer is `Shared<T>` for forward references plus `Weak<T>` for back references, with the caveat that this is more code than the GC version.

**Why it matters.** Affects how textbook data structures get taught and used in Laterita. The example I sketched (Node<T> with Shared next and Weak parent) is a real implementation pattern, but no broader migration story for graph-shaped Java code was developed.

**Related codes:** STD-01, STD-03.

---

## OQ-13 — User-invoked `close()` and early cleanup

**Surfaced when:** specifying scope-exit semantics.

**The issue.** DROP-01 mandates that the compiler invokes `close()` at scope exit. The conversation did not address two related questions:

1. Whether user code can call `close()` directly.
2. Whether there is any mechanism for releasing a resource before its binding goes out of scope.

The spec leaves both unanswered.

**Why it matters.** Affects the ergonomics of resource-heavy code where scope boundaries don't match resource lifetimes. Affects whether `close()` is idempotent or "consuming."

**Related codes:** DROP-01.
