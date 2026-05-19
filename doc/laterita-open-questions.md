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
