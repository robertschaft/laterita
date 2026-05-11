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

## OQ-15 - Migratability Java Code

This is a hand added entry.

Migration tools:
1. Java Annotations like `@mut` or `@take` that reflect the new keywords.
2. Class with static function `T give(@take T x) { return x; }` as a temporary drop in for the `give` keyword in expressions.
3. A tools that enhances java classes with given annotations and functions best effort. It adds `@Nullable` wherever required.
4. A java compiler plugin that tries to simulate the borrow checker based on above annotations. Developers can improve their java code.
5. A tool that converts java code to laterita code. It assumes that the previous two steps are already executed.
6. A laterita formatter that formats laterita always in the same manner. If should allow only very few formatting freedom to developers (e.g. it wouldn't remove some additional line breaks).

## OQ-17 — Public expression of buffer splitting for `String`

**Surfaced when:** discussing how `String`'s internal buffer-splitting capability surfaces in user code (alongside OQ-14's resolution).

**The issue.** Internally, two non-overlapping `bound String` slices over the same backing storage are sound. MOVE-06 already establishes the analogous rule for arrays via a stdlib `splitAt`. The natural fit for `String` is the same shape — but `splitAt` returns two values, which Java has no native multiple-return syntax for.

**The question.** Three sub-options:

1. Add `splitAt` returning a tuple-like multi-value, accepting "multiple returns" as a new language feature.
2. Use a record (`record StringSplit(bound String left, bound String right)`) to avoid native multi-return.
3. Omit a public splitting form entirely; leave buffer splitting as an `unsafe` internal mechanism behind `String`'s own methods (`substring`, `trim`, `splitOn`, etc.).

Option 3 is the smallest language surface but loses parity with MOVE-06's `splitAt` for arrays. Option 2 reuses the records feature already in the language and stays Java-native. Option 1 adds language surface but yields the cleanest call-site syntax.

**Why it matters.** Determines whether parallel-decomposition algorithms over strings (e.g., divide-and-conquer parsing) are expressible without dropping into `unsafe`.

**Related codes:** STR-02, STR-03, MOVE-06.

## OQ-18 — `onDrop()` reaching already-dropped subclass state via virtual dispatch

**Surfaced when:** working through DROP-08 (the moved-out-field restriction) and noticing the adjacent hazard on the inheritance axis.

**The issue.** DROP-05 runs `onDrop()` subclass-first, then the compiler-appended `super.onDrop()` runs each superclass body in turn. By the time a superclass's `onDrop()` body executes, the subclass's `onDrop()` has already run and the subclass's fields may already be dropped or freed. If the superclass body calls a method that the subclass overrides, the dynamically-dispatched override can touch subclass fields that no longer hold valid values — a use-after-free that DROP-08 does not cover (DROP-08 is about *this class's* moved-out fields, not a subclass's torn-down state).

The symmetric direction is fine: a subclass `onDrop()` calling up into superclass methods sees superclass state that is still live, because superclass cleanup runs *after*. The dangerous direction is a superclass `onDrop()` body dispatching *down* into an override.

C++ handles this by static dispatch inside destructors: while `~Base()` runs, the vtable slot resolves to `Base`'s version, never a derived override, precisely because the derived part is already gone. Java has no analog because it has no deterministic destruction.

**The question.** What discipline makes `onDrop()` bodies safe at compile time?

1. **Static dispatch inside `onDrop()`.** Mirror C++: any method call on `this` (or `super`) from within an `onDrop()` body — and transitively from methods it calls on `this` — resolves statically to the version defined in the class currently being dropped or one of its supers, never to a subclass override. No syntax change; a dispatch-mode rule for the `onDrop()` call tree.
2. **Restrict `onDrop()` to `final` / same-or-super methods.** The `onDrop()` body (and its `this`-callees) may invoke only `final` methods or methods declared on the same class or a superclass — nothing a subclass could override. Simpler to specify and diagnose, but more restrictive: it bans calling a non-`final` method even when no subclass actually overrides it.
3. **Forbid calling overridable methods on `this` from `onDrop()` entirely**, requiring such logic to be inlined or factored into `private`/`final` helpers. Strictest; pushes the burden onto the author.

Option 1 is the most permissive and matches an established precedent; option 2 is the easiest to teach ("`onDrop()` calls only `final` things"). Whichever is chosen, the rule must compose with DROP-08 and with THR-05's ban on interruption points inside `onDrop()`.

**Why it matters.** Without a rule, an ordinary-looking base-class `onDrop()` that calls a hook method a subclass customizes becomes a use-after-free the moment someone subclasses it. Deterministic destruction with inheritance is exactly where C++ had to grow a special rule; Laterita needs the equivalent before the cleanup model can be called sound.

**Related codes:** DROP-01, DROP-05, DROP-08.

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
