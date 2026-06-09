# Laterita — Open Questions

This document records unresolved **language-design** questions. Each entry references the spec code(s) it relates to, where applicable.

Resolving any of these requires a separate decision; the spec deliberately leaves them open. On resolution, follow the workflow in `CLAUDE.md`: document the reasoning in `doc/laterita-reasoning.md`, remove the entry here, and tombstone it in `doc/resolved-questions.md`.

Non-language-design items — tooling, migration, and roadmap work — are tracked as GitHub issues, not here:

- Migration tooling for existing Java code — [#13](https://github.com/robertschaft/laterita/issues/13)
- Spring DI and compile-time annotation processing — [#14](https://github.com/robertschaft/laterita/issues/14)
- JavaBean migration story — [#15](https://github.com/robertschaft/laterita/issues/15)
- Bean scopes beyond singleton — [#16](https://github.com/robertschaft/laterita/issues/16)

## OQ-20 — Pattern matching and destructuring under ownership

**Surfaced when:** noting that Rust's `match` exhaustively destructures sum types and binds each field with a move, while Java's pattern switch (sealed types + record deconstruction patterns, JEP 440) leaves move-vs-borrow implicit.

**The issue.** Laterita inherits Java's pattern `switch` and record patterns. But the borrow checker has to attribute each variable produced by a record pattern: is `case Point(var x, var y)` moving `x` and `y` out of the scrutinee, borrowing them for the case body, or destructing (DROP-04 / OWN-06)? Sealed hierarchies (Rust-style ADTs) make this acute — the natural Rust idiom is to consume the scrutinee and rebind owned fields per arm.

**The question.**
- Do record-pattern variables default to borrow (consistent with OWN-02) or to move (consistent with the Rust idiom)?
- Is there an opt-in `@take` form on a pattern variable to switch arms between borrow and consume?
- How does exhaustiveness interact with destruction: if one arm moves a field and another does not, is the scrutinee considered moved after the `switch`?
- For a scrutinee whose class implements `onDrop()`, DROP-08 forbids moving any field out, so move-binding patterns on it must either be rejected or consume the whole scrutinee at once. Which of these is the rule?
- Do guards (`case P when cond`) re-borrow across the guard expression?

**Naming.** The verb *deconstruct* and the noun *deconstruction* are reserved for the record-pattern feature in this question.
A JEP 440 record deconstruction pattern reads a value through its named components, and the borrow-or-move choice listed above is exactly what such a pattern must decide.
That is distinct from `DES` destruction, which is the unconditional move form: it always moves an owned object's fields out by `give(p.x)`.
Keeping the two terms separate is why the move-based take-apart operation was renamed from deconstruct to destruct, leaving deconstruct free for the JEP 440 borrow-into-parts reading.

**Why it matters.** Sealed-type dispatch is the Java-shaped replacement for Rust enums; without a clear ownership story for patterns, `switch` becomes a borrow-checker hole.

**Related codes:** OWN-02, OWN-13, OWN-06, DES, DROP-04.

## OQ-22 — Restoring checked exceptions for compiler-enforced error totality

**Surfaced when:** revisiting EXC-05 (all exceptions unchecked) against the observation that Rust's `Result<T, E>` + `?` is functionally checked exceptions — values whose handling the compiler enforces — and that Laterita's natural Java-shaped equivalent is `throws` + `try`/`catch` rather than a parallel `Result` machinery.

**The issue.** EXC-05 was adopted because Java's checked exceptions are a known ergonomic burden — they propagate badly through `Function<T, R>` and the rest of `java.util.function`, which declare no `throws`. The cost of that choice is that Laterita has no compile-time totality check on recoverable failures: any method may throw any unchecked exception, and the compiler cannot tell a caller "you forgot to handle `FileNotFoundException`." This is the property Rust users rely on, and the reason a Java-shaped language adopting Rust ownership should consider whether to restore it.

The literature on the original Java pain isolates it to one specific spot: the JDK's `Function`/`Consumer`/`Supplier`/`Predicate` SAMs declare no `throws`, so a lambda body calling e.g. `Files.readString` (which `throws IOException`) cannot satisfy `Stream.map(Function<T, R>)`. See *Exceptions in Java Lambda Expressions* (Baeldung), *Handling checked exceptions in Java streams* (O'Reilly), *Handling Exceptions in Java Lambdas* (Foojay). Outside of generic SAM-based APIs, checked exceptions work fine — direct method calls, try-with-resources, ordinary control flow all carry them without friction.

Laterita has a structural lever Java does not: FN-01 anonymous functional interfaces. A functional-interface type written `(P1, …, Pn) -> R` could be extended to `(P1, …, Pn) -> R throws E1, E2`, with the throws set being part of the structural type. A library API written generically over the throws set could then accept lambdas that throw, without each library declaring a parallel `ThrowingFunction` interface as Apache Commons `FailableStream` does today. FN-01 restricts the anonymous form to parameter and return positions; this does not narrow the lever, because the throws-polymorphic use cases are exactly those positions — a generic API accepting a throwing lambda as a parameter — and a throws-extended form would inherit the same restriction.

**The question.**
- Is EXC-05 reversed: does Laterita restore Java's distinction between checked and unchecked exceptions, with `throws` declarations required on method signatures for checked exceptions?
- Does FN-01 admit a structural throws clause (`(P1, …, Pn) -> R throws E1, E2`), and is the throws set part of FI subtype identity? Without this, restoring checked exceptions reintroduces the Java pain that motivated EXC-05.
- A structural throws clause must attach to the functional-interface *semantics* (FN-01), so that the `.java` surface carries it via a nominal interface whose SAM declares `throws` — not only to the inline `.lat` spelling (LAT-05). If it attached to the spelling alone, `.java` and `.lat` sources would type-check throwing lambdas differently, violating LAT-00 (the `.lat` layer adds no semantics).
- Can library APIs be generic over the throws set the way Rust APIs are generic over the error type — e.g. a Laterita `Stream<T>.map((T) -> R throws E)` parametric in `E` — or is the structural form limited to one-shot use sites with throws polymorphism left out?
- Does `InterruptedException` (THR-08) become checked again, or stay unchecked as a cancellation signal (the most-cited single case of checked-exception fatigue in Java)? A principled rule is needed.
- How does the restoration interact with EXC-01 (Java exception syntax preserved) and Java interop: imported Java methods declaring `throws IOException` would, under restored semantics, propagate the checked obligation into Laterita callers — restoring exactly the burden EXC-05 erased. Is that acceptable, or does the boundary auto-unchecks?
- Is OQ-22's original `Result<T, E>` proposal then dropped entirely, or kept as a non-stdlib idiom for the cases where errors-as-data is genuinely preferable (parser combinators, validation pipelines)?

**Why it matters.** Compile-time totality for recoverable failures is one of the central reasons Java developers reach for Rust. The Java-shaped delivery is checked exceptions, not a parallel `Result` type — provided FN-01 absorbs the throws clause so that generic functional APIs survive. If FN-01 cannot, the restoration is back to the original Java ergonomic dead-end and `Result<T, E>` becomes the only viable answer.

**Related codes:** EXC-01, EXC-05, FN-01, THR-08, COMP-06.

## OQ-23 — Channels and message-passing for inter-thread communication

**Surfaced when:** observing that the `THR` topic specifies threads, interruption, joining, and `Mutex<T>`, but no channel primitive — yet Rust's primary thread-communication idiom is `std::sync::mpsc` / `crossbeam` channels with move-on-send.

**The issue.** Shared-state concurrency via `Arc<Mutex<T>>` is covered. Message-passing concurrency — sender moves a `@take` value into the channel, receiver gets ownership on the other side, no aliasing across threads — is not. The ownership model maps to channels especially cleanly: `Sender<T>.send(@take T)` and `Receiver<T>.recv() → T` are simply moves across a queue, with `@local` (STD-07) gating which `T` may be sent.

**The question.**
- Is `Channel<T>` (or `Sender<T>` / `Receiver<T>` pair) part of the required stdlib (`STD`, Reserved Names `RESV`) or a third-party library?
- Bounded vs unbounded? SPSC vs MPSC vs MPMC? Does the stdlib commit to a single shape, or expose a hierarchy?
- Is `send` an interruption point (THR-04)? Does dropping the last `Sender` close the channel (analog of Rust's `RecvError`)?
- How does back-pressure surface — `BlockingQueue`-style `put`/`offer`, or a structured `trySend` returning the value back on full?

**Why it matters.** Without a channel primitive, Laterita programs that want Rust-style "share by communicating" fall back to hand-rolled `Arc<Mutex<Queue<T>>>` and lose the static guarantee that a sent value is uniquely owned by the receiver.

**Related codes:** STD-07, STD-09, THR-01, THR-04, OWN-13.

## OQ-27 — `From`/`Into`-style conversions and implicit-coercion control

**Surfaced when:** noting that Rust's `From<T>`/`Into<U>` traits provide an ergonomic but controlled conversion surface (`let s: String = my_str.into();`), used heavily for error conversion in conjunction with the `?` operator.

**The issue.** Java relies on explicit constructors and static factory methods (`String.valueOf`, `Integer.parseInt`) plus a fixed set of compiler-blessed primitive widenings. There is no extension point for "this type converts to that one in one well-defined step." Combined with OQ-22 (`Result`-style errors), the lack of `From` means error-type composition across libraries requires hand-written boilerplate per call site.

**The question.**
- Does Laterita introduce a `Conversion<F, T>` interface (or `@from` annotation on a constructor / static method) that the compiler may invoke implicitly in specific positions — at minimum on `?`-style propagation of `Result<_, E1>` into a function returning `Result<_, E2>`?
- Are implicit conversions limited to error-propagation sites, or also available on assignment / argument passing? (Scala's experience suggests "only at error-propagation sites" is the safe choice.)
- How does conversion interact with ownership: must `From::from` always be `@take`, or are borrowed conversions (`Into<&str>` analog) part of the surface?

**Why it matters.** Without a conversion mechanism, the OQ-22 `Result` story is stunted: every error boundary needs an explicit `.mapErr(MyError::wrap)` call. With it, library composition tightens substantially.

**Related codes:** OWN-13, OQ-22.

## OQ-30 — Runtime-initialized statics (lazy / once-init primitive)

**Surfaced when:** STAT-01 restricted static initializers to const expressions and pointed runtime-initialized statics at "a once-init wrapper held in the static slot," without specifying the wrapper.

**The issue.** Const-only static initialization keeps the AOT story (COMP-01) honest — no classloader, no static-init-order fiasco, no observable initialization race. But it leaves a real case unspecified: statics whose value genuinely requires runtime work — a compiled regex, a config loaded from disk, a precomputed table, a service registry. Java handles these in `static {}` blocks under the classloader's per-class init lock; Rust uses `LazyLock<T>` / `OnceLock<T>` from `std::sync`. Laterita has neither yet, so every such case must hand-roll a `Mutex<T?>` and a first-access check at every read site.

**The question.**
- Does the stdlib provide a `Lazy<T>` (eager-first-access initialization with a supplier captured at construction), an `OnceLock<T>` (settable once at any later time, observed via `get()` returning `T?`), or both?
- Is the first-access work serialized by an internal `Mutex<T>`, by double-checked-locking over an atomic slot, or by a one-time CAS? The choice determines whether two threads racing on first access both run the supplier or whether the loser blocks.
- Does the supplier's exception poison the slot (subsequent `get()` re-throws, mirroring THR-10), retry on the next call (Rust's `LazyLock` behavior), or terminate the program?
- Is the supplier captured as a `@take () -> T` closure (consumed on success, dropped) or held for retry? Falls out of the previous answer.
- How does this compose with `@local` (STAT-03)? A `static Lazy<L>` where `L` is `@local` puts the `L` cross-thread on first access — STAT-03 presumably extends through the wrapper.

**Why it matters.** Without a runtime-init primitive, every Laterita program that needs a compiled regex, a parsed config, or any other not-quite-const startup value hand-rolls the same `Mutex<T?>` + first-access check at every read site. The pattern is universal; the shape of the stdlib carrier is what's open.

**Related codes:** STAT-01, STAT-03, STD-09, THR-10, COMP-01.

---

## OQ-31 — `val` and `var` as first-class aliases

**Surfaced when:** GEN-14 noted that Lombok's `val` (immutable inferred local) and `var` (reassignable inferred local) want a laterita spelling.

**The issue.**
Under MUT-02 a laterita `var` is already reassignable, exactly like Java's `var` and Lombok's `var`, so no divergence remains on the reassignment axis and a Lombok-using source keeps its `var` locals unchanged.
The remaining gap is `val`: Lombok's immutable inferred local is laterita's `final var`, two tokens where Lombok writes one.
Accepting `val` as sugar for `final var` would let Lombok sources migrate without rewriting `val` declarations.
The tension is that `val` is not a Java keyword, so admitting it in a `.java` file widens the surface beyond what `javac` parses.

**The question.**
- Should `val` be accepted as sugar for `final var` (immutable inferred local)?
- If so, only in `.lat` files (a LAT-topic form), or in `.java` too?

**Related codes:** MUT-02, MUT-03, GEN-14, LAT-00.

## OQ-33 — Primitives in the ownership and mutability system

**Surfaced when:** thinking through the `OWN` framing of variables as Java-variable slots carrying an ownership discipline.
The framing maps cleanly onto reference types (each slot points at a heap value, with one owner among the slots) but is awkward for `int`, `long`, `double`, `boolean`, etc.
Primitives have no heap identity: there is nothing to point at, nothing to drop, and a "borrow" of an `int` has no observable difference from a copy.

**The issue.**
A `@mut int x` parameter in Laterita can be made to behave like Rust's `&mut i32` — the compiler passes a pointer to the caller's int slot and the callee mutates through it.
This is implementable (Laterita compiles natively per COMP-01) but unusual.
The Rust idiom for shared mutation of primitives is *not* `&mut i32` but `AtomicI32` with interior mutability; `&mut <primitive>` is rare even in Rust stdlib (it shows up generically through `mem::swap` / `mem::replace`, not as a deliberate out-parameter).
The C analog (`int *`) is used in libc (`waitpid(int *wstatus, ...)`) but is the minority pattern; struct and array out-pointers dominate.
Java itself has no equivalent — primitives are pass-by-value.

If primitives sit outside the borrow system entirely, two follow-on rules need to be specified.

**The question.**

- *Are `@mut` parameters of primitive type rejected?*
  The proposal: yes, since a primitive cannot be borrowed in a way distinguishable from a copy, and the few cases that genuinely want pass-by-pointer (shared counters, atomic flags) are served better by `AtomicInt` / `AtomicBoolean` (STD-04 territory) or by `Cell<int>` (STD-05).
- *Do primitive returns default to `@mut`?*
  A returned primitive is a pure rvalue — there is no source for a borrow and no owner for a move; it is simply a value the caller may bind however they like.
  Marking it `@mut` by default means `@mut int n = computeCount();` works without an explicit annotation on the signature, where the alternative would force every primitive-returning method to spell `@mut int computeCount()` or face an owned/`@mut` mismatch at the call site.
- *What about `@bound` on primitive returns and `@borrow` on primitive fields?*
  The proposal: both rejected for the same reason — there is no storage to bind a lifetime to.
  A primitive field is always its own owner (the enclosing instance holds the bits inline).
- *Does any of this carry across `Nullable` (NULL-02)?*
  An `int?` is encoded as a tagged union, not a primitive pointer.
  Borrow rules might apply to the storage of the nullable wrapper even when the underlying type is primitive.

**Why it matters.**
The `OWN` model of variables-as-ownership-disciplined-slots only holds for reference types.
Without explicit rules excluding primitives from the borrow surface, every reader has to derive separately whether `@mut int x`, `@bound int foo()`, and `@borrow int x;` make sense.
The natural answer for all three is "no, primitives are pass-by-value", but the spec should say so once rather than leave it implicit.

**Related codes:** OWN-01, OWN-13, OWN-16, MUT-04, MUT-07, STD-04, STD-05.
