# Laterita — Open Questions

This document records questions and issues that surfaced during the design conversation but were *not* resolved. It is intentionally minimal — only items actually raised in the discussion are listed. Each entry references the spec code(s) it relates to, where applicable.

Resolving any of these requires a separate decision; the spec deliberately leaves them open.

---

## OQ-01 — Panic safety and lock poisoning — resolved

---

## OQ-02 — Exception ergonomics beyond what ownership forces — resolved

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
- Whether `ContextLocal<T>` should be a standard library type or framework-specific. The first candidates to evaluate are Java's existing `ThreadLocal<T>` and `ScopedValue<T>` (JEP 446) — `ScopedValue<T>` in particular is dynamically scoped and integrates cleanly with structured concurrency, which fits Laterita's ownership-bound thread model. A new `ContextLocal<T>` should only be introduced if these prove insufficient.
- How proxy generation interacts with `final` and `private` access.

**Why it matters.** Determines whether the Spring ecosystem can port to Laterita with reasonable effort.

**Related codes:** none directly; touches COMP-02.

---

## OQ-08 — Owned-vs-borrowed strings: one type or two — resolved

---

## OQ-09 — Iterator.remove and ConcurrentModificationException — resolved

---

## OQ-10 — JavaBean migration story

**Surfaced when:** enumerating what Java code requires rewriting.

**The issue.** Setter-heavy POJOs with `@Autowired` fields are pervasive in Java. We agreed the Laterita answer is records and constructor injection, with mechanical refactoring tooling. We didn't specify what that tooling looks like or whether it's part of the language project.

**Why it matters.** Determines the migration effort for existing Spring/Jakarta EE applications.

**Related codes:** BIND-04.

---

## OQ-11 — Bean scopes beyond singleton

**Surfaced when:** discussing Spring DI implications.

**The issue.** Singleton beans map to `Arc<T>`. Prototype beans are method calls. Request scope and session scope require per-request storage that we sketched as `ContextLocal<T>`. We agreed deterministic cleanup of request-scoped resources is a real win compared to Spring's GC-dependent model. We didn't specify the API.

**Why it matters.** Determines whether web frameworks like Spring MVC port cleanly.

**Related codes:** OQ-06.

---

## OQ-12 — Doubly-linked structures and graph data

**Surfaced when:** discussing what Java code is hard to port.

**The issue.** Cyclic data structures (doubly-linked lists, parent-pointer trees) are genuinely harder under ownership than under GC. We agreed the answer is `Rc<T>` for forward references plus `WeakReference<T>` for back references, with the caveat that this is more code than the GC version.

**Why it matters.** Affects how textbook data structures get taught and used in Laterita. The example I sketched (Node<T> with Rc next and WeakReference parent) is a real implementation pattern, but no broader migration story for graph-shaped Java code was developed.

**Related codes:** STD-01, STD-03.
