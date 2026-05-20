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

## OQ-20 — Pattern matching and destructuring under ownership

**Surfaced when:** noting that Rust's `match` exhaustively destructures sum types and binds each field with a move, while Java's pattern switch (sealed types + record patterns, JEP 441) leaves move-vs-borrow implicit.

**The issue.** Laterita inherits Java's pattern `switch` and record patterns. But the borrow checker has to attribute each binding produced by a record pattern: is `case Point(var x, var y)` moving `x` and `y` out of the scrutinee, borrowing them for the case body, or partially moving (DROP-04 / MOVE-07)? Sealed hierarchies (Rust-style ADTs) make this acute — the natural Rust idiom is to consume the scrutinee and rebind owned fields per arm.

**The question.**
- Do record-pattern bindings default to borrow (consistent with MOVE-01) or to move (consistent with the Rust idiom)?
- Is there an opt-in `@take` form on a pattern binding to switch arms between borrow and consume?
- How does exhaustiveness interact with partial moves: if one arm moves a field and another does not, is the scrutinee considered moved after the `switch`?
- Do guards (`case P when cond`) re-borrow across the guard expression?

**Why it matters.** Sealed-type dispatch is the Java-shaped replacement for Rust enums; without a clear ownership story for patterns, `switch` becomes a borrow-checker hole.

**Related codes:** MOVE-01, MOVE-03, MOVE-07, DROP-04.

## OQ-22 — Restoring checked exceptions for compiler-enforced error totality

**Surfaced when:** revisiting EXC-05 (all exceptions unchecked) against the observation that Rust's `Result<T, E>` + `?` is functionally checked exceptions — values whose handling the compiler enforces — and that Laterita's natural Java-shaped equivalent is `throws` + `try`/`catch` rather than a parallel `Result` machinery.

**The issue.** EXC-05 was adopted because Java's checked exceptions are a known ergonomic burden — they propagate badly through `Function<T, R>` and the rest of `java.util.function`, which declare no `throws`. The cost of that choice is that Laterita has no compile-time totality check on recoverable failures: any method may throw any unchecked exception, and the compiler cannot tell a caller "you forgot to handle `FileNotFoundException`." This is the property Rust users rely on, and the reason a Java-shaped language adopting Rust ownership should consider whether to restore it.

The literature on the original Java pain isolates it to one specific spot: the JDK's `Function`/`Consumer`/`Supplier`/`Predicate` SAMs declare no `throws`, so a lambda body calling e.g. `Files.readString` (which `throws IOException`) cannot satisfy `Stream.map(Function<T, R>)`. See *Exceptions in Java Lambda Expressions* (Baeldung), *Handling checked exceptions in Java streams* (O'Reilly), *Handling Exceptions in Java Lambdas* (Foojay). Outside of generic SAM-based APIs, checked exceptions work fine — direct method calls, try-with-resources, ordinary control flow all carry them without friction.

Laterita has a structural lever Java does not: FN-01 anonymous functional interfaces. A functional-interface type written `(P1, …, Pn) -> R` could be extended to `(P1, …, Pn) -> R throws E1, E2`, with the throws set being part of the structural type. A library API written generically over the throws set could then accept lambdas that throw, without each library declaring a parallel `ThrowingFunction` interface as Apache Commons `FailableStream` does today.

**The question.**
- Is EXC-05 reversed: does Laterita restore Java's distinction between checked and unchecked exceptions, with `throws` declarations required on method signatures for checked exceptions?
- Does FN-01 admit a structural throws clause (`(P1, …, Pn) -> R throws E1, E2`), and is the throws set part of FI subtype identity? Without this, restoring checked exceptions reintroduces the Java pain that motivated EXC-05.
- Can library APIs be generic over the throws set the way Rust APIs are generic over the error type — e.g. a Laterita `Stream<T>.map((T) -> R throws E)` parametric in `E` — or is the structural form limited to one-shot use sites with throws polymorphism left out?
- Does `InterruptedException` (THR-08) become checked again, or stay unchecked as a cancellation signal (the most-cited single case of checked-exception fatigue in Java)? A principled rule is needed.
- How does the restoration interact with EXC-01 (Java exception syntax preserved) and Java interop: imported Java methods declaring `throws IOException` would, under restored semantics, propagate the checked obligation into Laterita callers — restoring exactly the burden EXC-05 erased. Is that acceptable, or does the boundary auto-unchecks?
- Is OQ-22's original `Result<T, E>` proposal then dropped entirely, or kept as a non-stdlib idiom for the cases where errors-as-data is genuinely preferable (parser combinators, validation pipelines)?

**Why it matters.** Compile-time totality for recoverable failures is one of the central reasons Java developers reach for Rust. The Java-shaped delivery is checked exceptions, not a parallel `Result` type — provided FN-01 absorbs the throws clause so that generic functional APIs survive. If FN-01 cannot, the restoration is back to the original Java ergonomic dead-end and `Result<T, E>` becomes the only viable answer.

**Related codes:** EXC-01, EXC-05, FN-01, THR-08, COMP-06.

## OQ-23 — Channels and message-passing for inter-thread communication

**Surfaced when:** observing that §16 specifies threads, interruption, joining, and `Mutex<T>`, but no channel primitive — yet Rust's primary thread-communication idiom is `std::sync::mpsc` / `crossbeam` channels with move-on-send.

**The issue.** Shared-state concurrency via `Arc<Mutex<T>>` is covered. Message-passing concurrency — sender moves a `@take` value into the channel, receiver gets ownership on the other side, no aliasing across threads — is not. The ownership model maps to channels especially cleanly: `Sender<T>.send(@take T)` and `Receiver<T>.recv() → T` are simply moves across a queue, with `@local` (STD-07) gating which `T` may be sent.

**The question.**
- Is `Channel<T>` (or `Sender<T>` / `Receiver<T>` pair) part of the required stdlib (§15, Reserved Names §18) or a third-party library?
- Bounded vs unbounded? SPSC vs MPSC vs MPMC? Does the stdlib commit to a single shape, or expose a hierarchy?
- Is `send` an interruption point (THR-04)? Does dropping the last `Sender` close the channel (analog of Rust's `RecvError`)?
- How does back-pressure surface — `BlockingQueue`-style `put`/`offer`, or a structured `trySend` returning the value back on full?

**Why it matters.** Without a channel primitive, Laterita programs that want Rust-style "share by communicating" fall back to hand-rolled `Arc<Mutex<Queue<T>>>` and lose the static guarantee that a sent value is uniquely owned by the receiver.

**Related codes:** STD-07, STD-09, THR-01, THR-04, MOVE-03.

## OQ-24 — Operator overloading for arithmetic value types

**Surfaced when:** considering value-typed math (`BigDecimal`, `Vec3`, `Money`, `Duration`) which in Rust uses `Add`/`Sub`/`Mul`/`Div` traits.

**The issue.** Java has no operator overloading except `+` on `String`. Rust does, by trait. Without overloading, Laterita value types read `a.plus(b).times(c)` where Rust reads `(a + b) * c`. For a language whose elevator pitch is "Rust ergonomics in Java syntax," math-heavy code stays verbose.

**The question.**
- Does Laterita introduce a fixed-set annotation surface — e.g. `@operator(PLUS)` on a method named `plus` — that the parser desugars `a + b` into the annotated call, with overload resolution unchanged?
- Which operators are eligible? At minimum `+ - * / %` and `== !=` (the latter via `Object.equals` already); what about `< <= > >=` (Rust's `PartialOrd`) and `[]` indexing (Rust's `Index`)?
- How does ownership interact: do operator methods take `@bound this` by default (like Rust's `&self` reference forms), with `@take` versions opted in?
- Does the form coexist with `String`'s built-in `+`, or replace it?

**Why it matters.** Value-typed arithmetic is one of the most visible everyday differences between Rust and Java code. The decision affects how libraries like `java.math`, vector math, and unit-typed quantities look in Laterita.

**Related codes:** BIND-07, MOVE-03, MOVE-09.

## OQ-26 — Newtype wrappers as zero-cost value classes

**Surfaced when:** considering the Rust idiom `struct Meters(f64)` / `struct UserId(u64)` — a one-field tuple struct that the compiler erases to the inner representation but the type system treats as distinct.

**The issue.** Java records always allocate (unless Valhalla value-classes apply). Laterita could offer the Rust newtype pattern as a guaranteed zero-overhead wrapper: `record Meters(double value) {}` compiles to the same memory layout as `double` when used as a field or parameter, with no separate object header, but the type system rejects mixing `Meters` with `Seconds`.

**The question.**
- Is there an annotation, e.g. `@newtype` or `@valuewrapper`, on single-field records that obligates the compiler to elide allocation and treat the wrapper as the inner type at the ABI level?
- Does it inherit the inner type's ownership semantics automatically: `@newtype record Owned(String inner) {}` behaves as an owned `String` in move/borrow analysis?
- How does it interact with sealed hierarchies — can a sealed type with all `@newtype` variants compile as a tagged union with discriminant + inner data, à la Rust enums?
- Does it require Valhalla in the JVM-targeting story, or does AOT native compilation (COMP-01) sidestep that?

**Why it matters.** The newtype pattern is the most cited Rust ergonomic improvement over Java for domain modelling ("type aliases are not types; newtype wrappers are"). Without a zero-cost form, Laterita developers will skip the pattern for performance and lose the type-safety benefit.

**Related codes:** BIND-04, COMP-01, COMP-02.

## OQ-27 — `From`/`Into`-style conversions and implicit-coercion control

**Surfaced when:** noting that Rust's `From<T>`/`Into<U>` traits provide an ergonomic but controlled conversion surface (`let s: String = my_str.into();`), used heavily for error conversion in conjunction with the `?` operator.

**The issue.** Java relies on explicit constructors and static factory methods (`String.valueOf`, `Integer.parseInt`) plus a fixed set of compiler-blessed primitive widenings. There is no extension point for "this type converts to that one in one well-defined step." Combined with OQ-22 (`Result`-style errors), the lack of `From` means error-type composition across libraries requires hand-written boilerplate per call site.

**The question.**
- Does Laterita introduce a `Conversion<F, T>` interface (or `@from` annotation on a constructor / static method) that the compiler may invoke implicitly in specific positions — at minimum on `?`-style propagation of `Result<_, E1>` into a function returning `Result<_, E2>`?
- Are implicit conversions limited to error-propagation sites, or also available on assignment / argument passing? (Scala's experience suggests "only at error-propagation sites" is the safe choice.)
- How does conversion interact with ownership: must `From::from` always be `@take`, or are borrowed conversions (`Into<&str>` analog) part of the surface?

**Why it matters.** Without a conversion mechanism, the OQ-22 `Result` story is stunted: every error boundary needs an explicit `.mapErr(MyError::wrap)` call. With it, library composition tightens substantially.

**Related codes:** MOVE-03, OQ-22.
