# Laterita — Open Questions

This document records unresolved **language-design** questions.
Each entry references the spec code(s) it relates to, where applicable.

Resolving any of these requires a separate decision.
The spec deliberately leaves them open.
On resolution, follow the workflow in `CLAUDE.md`: document the reasoning in `doc/laterita-reasoning.md`, remove the entry here, and tombstone it in `doc/resolved-questions.md`.

Non-language-design items (tooling, migration, and roadmap work) are tracked as GitHub issues, not here:

- Migration tooling for existing Java code: [#13](https://github.com/robertschaft/laterita/issues/13)
- Spring DI and compile-time annotation processing: [#14](https://github.com/robertschaft/laterita/issues/14)
- JavaBean migration story: [#15](https://github.com/robertschaft/laterita/issues/15)
- Bean scopes beyond singleton: [#16](https://github.com/robertschaft/laterita/issues/16)

## OQ-20 — Pattern matching and destructuring under ownership

**Surfaced when:** noting that Rust's `match` exhaustively destructures sum types and binds each field with a move, while Java's pattern switch (sealed types + record deconstruction patterns, JEP 440) leaves move-vs-borrow implicit.

**The issue.** Laterita inherits Java's pattern `switch` and record patterns.
But the borrow checker has to attribute each variable produced by a record pattern: does `case Point(var x, var y)` move `x` and `y` out of the selector expression's value, borrow them for the case body, or destruct it (DROP-04, OWN-06)?
A sealed hierarchy, which is where a Rust program uses an enum, makes this acute: the natural Rust idiom is to consume the matched value and bind owned fields per case.

**The question.**
- Do record-pattern variables default to borrow (consistent with OWN-02) or to move (consistent with the Rust idiom)?
- Is there an opt-in `@take` form on a pattern variable to switch arms between borrow and consume?
- How does exhaustiveness interact with destruction: if one arm moves a field and another does not, is the matched value considered moved after the `switch`?
- For a matched value whose class implements `onDrop()`, DROP-08 forbids moving any field out, so a pattern that binds by move must either be rejected or consume the whole value at once.
Which of these is the rule?
- Do guards (`case P when cond`) re-borrow across the guard expression?
- What mutability does a pattern variable carry?
MUT-40 reads a local variable's mutability off its declared type, and for `var` off its initializer, but a pattern variable has neither a written type nor an initializer.
The candidates are the component's declared type, the mutability of the matched value, and an explicit `@fixed` on the pattern variable, with MUT-60's borrow-mode classification following whichever is chosen.

**Naming.** The verb *deconstruct* and the noun *deconstruction* are reserved for the record-pattern feature in this question.
A JEP 440 record deconstruction pattern reads a value through its named components, and the borrow-or-move choice listed above is exactly what such a pattern must decide.
That is distinct from `DES` destruction, which is the unconditional move form: it always moves an owned object's fields out by `give(p.x)`.
Keeping the two terms separate is why the move-based take-apart operation was renamed from deconstruct to destruct, leaving deconstruct free for the JEP 440 borrow-into-parts reading.

**Why it matters.** Sealed-type dispatch is the Java-shaped replacement for Rust enums.
Without a clear ownership story for patterns, `switch` becomes a borrow-checker hole.

**Related codes:** OWN-02, OWN-13, OWN-06, MUT-40, MUT-60, DES, DROP-04.

## OQ-22 — Restoring checked exceptions for compiler-enforced error totality

**Surfaced when:** revisiting EXC-05 (all exceptions unchecked) against the observation that Rust's `Result<T, E>` + `?` is functionally checked exceptions (values whose handling the compiler enforces) and that Laterita's natural Java-shaped equivalent is `throws` + `try`/`catch` rather than a parallel `Result` machinery.

**The issue.** EXC-05 was adopted because Java's checked exceptions are a known ergonomic burden, they propagate badly through `Function<T, R>` and the rest of `java.util.function`, which declare no `throws`.
The cost of that choice is that Laterita has no compile-time totality check on recoverable failures: any method may throw any unchecked exception, and the compiler cannot tell a caller "you forgot to handle `FileNotFoundException`." This is the property Rust users rely on, and the reason a Java-shaped language adopting Rust ownership should consider whether to restore it.

The literature on the original Java pain isolates it to one specific spot: the JDK's `Function`/`Consumer`/`Supplier`/`Predicate` SAMs declare no `throws`, so a lambda body calling e.g. `Files.readString` (which `throws IOException`) cannot satisfy `Stream.map(Function<T, R>)`.
See *Exceptions in Java Lambda Expressions* (Baeldung), *Handling checked exceptions in Java streams* (O'Reilly), *Handling Exceptions in Java Lambdas* (Foojay).
Outside of generic SAM-based APIs, checked exceptions work fine, direct method calls, try-with-resources, ordinary control flow all carry them without friction.

Laterita has a structural lever Java does not: FN-01 anonymous functional interfaces.
A functional-interface type written `(P1, …, Pn) -> R` could be extended to `(P1, …, Pn) -> R throws E1, E2`, with the throws set being part of the structural type.
A library API written generically over the throws set could then accept lambdas that throw, without each library declaring a parallel `ThrowingFunction` interface as Apache Commons `FailableStream` does today.
FN-01 restricts the anonymous form to parameter and return positions.
This does not narrow the lever, because the throws-polymorphic use cases are exactly those positions (a generic API accepting a throwing lambda as a parameter) and a throws-extended form would inherit the same restriction.

**The question.**
- Is EXC-05 reversed: does Laterita restore Java's distinction between checked and unchecked exceptions, with `throws` declarations required on method signatures for checked exceptions?
- Does FN-01 admit a structural throws clause (`(P1, …, Pn) -> R throws E1, E2`), and is the throws set part of functional-interface subtype identity?
Without this, restoring checked exceptions reintroduces the Java pain that motivated EXC-05.
- A structural throws clause must attach to the functional-interface *semantics* (FN-01), so that the `.java` surface carries it via a nominal interface whose SAM declares `throws`: not only to the inline `.lat` spelling (LAT-05).
If it attached to the spelling alone, `.java` and `.lat` sources would type-check throwing lambdas differently, violating LAT-00 (the `.lat` layer adds no semantics).
- Can library APIs be generic over the throws set the way Rust APIs are generic over the error type (e.g. a Laterita `Stream<T>.map((T) -> R throws E)` parametric in `E`) or is the structural form limited to one-shot use sites with throws polymorphism left out?
- Does `InterruptedException` (THR-08) become checked again, or stay unchecked as a cancellation signal (the most-cited single case of checked-exception fatigue in Java)?
A principled rule is needed.
- How does the restoration interact with EXC-01 (Java exception syntax preserved) and Java interop: imported Java methods declaring `throws IOException` would, under restored semantics, propagate the checked obligation into Laterita callers, restoring exactly the burden EXC-05 erased.
Is that acceptable, or does the boundary auto-unchecks?
- Is OQ-22's original `Result<T, E>` proposal then dropped entirely, or kept as an idiom outside the standard library for the cases where errors-as-data is genuinely preferable (parser combinators, validation pipelines)?

**Why it matters.** Compile-time totality for recoverable failures is one of the central reasons Java developers reach for Rust.
The Java-shaped delivery is checked exceptions, not a parallel `Result` type: provided FN-01 absorbs the throws clause so that generic functional APIs survive.
If FN-01 cannot, the restoration is back to the original Java ergonomic dead-end and `Result<T, E>` becomes the only viable answer.

**Related codes:** EXC-01, EXC-05, FN-01, THR-08, COMP-06.

## OQ-23 — Channels and message-passing for inter-thread communication

**Surfaced when:** observing that the `THR` topic specifies threads, interruption, joining, and `Mutex<T>`, but no channel primitive, yet Rust's primary thread-communication idiom is `std::sync::mpsc` / `crossbeam` channels with move-on-send.

**The issue.** Shared-state concurrency via `Arc<Mutex<T>>` is covered.
Message-passing concurrency (sender moves a `@take` value into the channel, receiver gets ownership on the other side, no aliasing across threads) is not.
The ownership model maps to channels especially cleanly: `Sender<T>.send(@take T)` and `Receiver<T>.recv() → T` are simply moves across a queue, with `@local` (STD-07) gating which `T` may be sent.

**The question.**
- Is `Channel<T>` (or `Sender<T>` / `Receiver<T>` pair) part of the required standard library (`STD`, Reserved Names `RESV`) or a third-party library?
- Bounded vs unbounded?
SPSC vs MPSC vs MPMC?
Does the standard library commit to a single shape, or expose a hierarchy?
- Is `send` an interruption point (THR-04)?
Does dropping the last `Sender` close the channel (analog of Rust's `RecvError`)?
- How does back-pressure surface: `BlockingQueue`-style `put`/`offer`, or a structured `trySend` returning the value back on full?

**Why it matters.** Without a channel primitive, Laterita programs that want Rust-style "share by communicating" fall back to hand-rolled `Arc<Mutex<Queue<T>>>` and lose the static guarantee that a sent value is uniquely owned by the receiver.

**Related codes:** STD-07, STD-09, THR-01, THR-04, OWN-13.

## OQ-27 — `From`/`Into`-style conversions and implicit-coercion control

**Surfaced when:** noting that Rust's `From<T>`/`Into<U>` traits provide an ergonomic but controlled conversion surface (`let s: String = my_str.into();`), used heavily for error conversion in conjunction with the `?` operator.

**The issue.** Java relies on explicit constructors and static factory methods (`String.valueOf`, `Integer.parseInt`) plus a fixed set of compiler-blessed primitive widenings.
There is no extension point for "this type converts to that one in one well-defined step." Combined with OQ-22 (`Result`-style errors), the lack of `From` means error-type composition across libraries requires hand-written conversion code at every call site.

**The question.**
- Does Laterita introduce a `Conversion<F, T>` interface (or `@from` annotation on a constructor / static method) that the compiler may invoke implicitly in specific positions: at minimum on `?`-style propagation of `Result<_, E1>` into a function returning `Result<_, E2>`?
- Are implicit conversions limited to error-propagation sites, or also available on assignment / argument passing? (Scala's experience suggests "only at error-propagation sites" is the safe choice.)
- How does conversion interact with ownership: must `From::from` always be `@take`, or are borrowed conversions (`Into<&str>` analog) part of the surface?

**Why it matters.** Without a conversion mechanism, the OQ-22 `Result` story is stunted: every error boundary needs an explicit `.mapErr(MyError::wrap)` call.
With it, library composition tightens substantially.

**Related codes:** OWN-13, OQ-22.

## OQ-30 — Runtime-initialized statics (lazy / once-init primitive)

**Surfaced when:** STAT-02 restricted static initializers to const expressions and pointed runtime-initialized static fields at a once-init wrapper held in the static field, without specifying the wrapper.

**The issue.** Const-only static initialization keeps the AOT story (COMP-01) honest, no classloader, no static-init-order fiasco, no observable initialization race.
But it leaves a real case unspecified: statics whose value genuinely requires runtime work, a compiled regex, a config loaded from disk, a precomputed table, a service registry.
Java handles these in `static {}` blocks under the classloader's per-class init lock, Rust uses `LazyLock<T>` / `OnceLock<T>` from `std::sync`.
Laterita has neither yet, so every such case must hand-roll a `Mutex<T?>` and a first-access check at every read site.

**The question.**
- Does the standard library provide a `Lazy<T>` (eager-first-access initialization with a supplier captured at construction), an `OnceLock<T>` (settable once at any later time, observed via `get()` returning `T?`), or both?
- Is the first-access work serialized by an internal `Mutex<T>`, by double-checked locking over an atomic field, or by a one-time CAS?
The choice determines whether two threads racing on first access both run the supplier or whether the loser blocks.
- Does an exception from the supplier poison the field (subsequent `get()` re-throws, mirroring THR-10), retry on the next call (Rust's `LazyLock` behavior), or terminate the program?
- Is the supplier captured as a `@take () -> T` closure (consumed on success, dropped) or held for retry?
Falls out of the previous answer.
- How does this compose with `@local` (STAT-03)?
A `static Lazy<L>` where `L` is `@local` puts the `L` cross-thread on first access: STAT-03 presumably extends through the wrapper.

**Why it matters.** Without a runtime-init primitive, every Laterita program that needs a compiled regex, a parsed config, or any other not-quite-const startup value hand-rolls the same `Mutex<T?>` + first-access check at every read site.
The pattern is universal.
The shape of the standard library carrier is what's open.

**Related codes:** STAT-02, STAT-03, STD-09, THR-10, COMP-01.

---

## OQ-34 — `val` and `var` as first-class aliases

**Surfaced when:** GEN-14 noted that Lombok's `val` (immutable inferred local) and `var` (reassignable inferred local) want a Laterita spelling.

**The issue.**
Under MUT-20 a Laterita `var` is already reassignable, exactly like Java's `var` and Lombok's `var`, so no divergence remains on the reassignment axis and a Lombok-using source keeps its `var` locals unchanged.
The remaining gap is `val`: Lombok's immutable inferred local is Laterita's `final var`, two tokens where Lombok writes one.
Accepting `val` as sugar for `final var` would let Lombok sources migrate without rewriting `val` declarations.
The tension is that `val` is not a Java keyword.
Lombok makes `val x = ...` compile by shipping `val` as an importable type that `javac` resolves, so a `.java`-surface `val` would need the same importable-type trick, while a `.lat`-only form is plain LAT-topic sugar for `final var`.

**The question.**
- Should `val` be accepted as sugar for `final var` (immutable inferred local)?
- If so, only in `.lat` files (a LAT-topic form), or in `.java` too through an importable `val` type the compiler special-cases?

**Related codes:** MUT-40, MUT-20, GEN-14, LAT-00.

## OQ-36 — Ownership and mutability introspection (`isMutable`, `isFixed`, `isOwned`)

**Surfaced when:** specifying MUT-17 receiver-inherited mutation, where an operation already branches implicitly on the compile-time mutability of its receiver, and generic code may want to branch on the same facts by hand.

**The issue.**
Ownership, borrow, and mutability are compile-time properties of a variable (OWN-01, OWN-03, MUT-01).
Generic or library code sometimes needs to observe them, to specialize an algorithm, assert an expectation, or drive a MUT-17-style inherited path explicitly.
Laterita currently offers no way to ask in source whether a value is mutable, fixed, owned, or borrowed.

**The question.**

- Is there a standard set of predicates over a variable: `isMutable(x)`, `isFixed(x)`, `isOwned(x)`, `isBorrowed(x)`, and perhaps `isBound(x)`?
- Are they intrinsics in the manner of `give` and `broken`, or ordinary methods, and what is the surface spelling?
- Do they observe only the declared mode of the variable, or can they narrow flow-sensitively the way a null check narrows (NULL-06)?
- Their answers are compile-time constants, so are they necessarily compile-time-evaluated (OQ-37)?
What is the result for a generic `T` whose mode is itself inherited (MUT-17)?
- Do they compose with monomorphization, so a MUT-17 `InheritFrom.RECEIVER` body could read `isMutable(this)` and specialize per instantiation?

**Why it matters.**
Compile-time mode predicates let a library author write one generic body that adapts to the caller's ownership, the manual counterpart to MUT-17's automatic inheritance, and they are the natural building block for the compile-time reflection of OQ-37.

**Related codes:** OWN-01, OWN-03, MUT-01, MUT-17, OQ-37.

## OQ-37 — Compile-time evaluation scopes (`@Macro`, `@Runtime`) and compile-time reflection

**Surfaced when:** observing that the OQ-36 mode predicates yield compile-time constants, and that Laterita removes runtime reflection (README), so any reflective capability has to be resolved at compile time.

**The issue.**
Laterita compiles ahead-of-time with monomorphization and no runtime metadata, so reflection cannot be a runtime service.
Some computations are fully determined at compile time and could be forced to evaluate there, while others depend on runtime state and must be rejected if reached from a compile-time context.

**The question.**

- Is there a `@Macro` scope marking a method whose result the compiler must compute at compile time (the `constexpr` and `comptime` analog), and a `@Runtime` scope marking a method the compiler must not compile-time-evaluate?
- Is calling a `@Runtime` method, directly or transitively, from a `@Macro` context a compile-time error, and how is the transitive purity of a `@Macro` call graph enforced?
- Are the OQ-36 predicates declared `@Macro`, so they fold to constants and can drive `@Macro`-level branching and code generation?
- What is the reflection surface reachable from `@Macro` code: type structure, fields, annotations, ownership modes?
Does it subsume the build-time annotation processing that already replaces serializers, ORM mappers, and DI wiring (README, GH #14)?
- How does a `@Macro` result interact with monomorphization: is a `@Macro` body re-evaluated per instantiation of the generic it sits in?

**Why it matters.**
A `@Macro`/`@Runtime` split gives Laterita a principled compile-time metaprogramming layer to replace the runtime reflection it drops, folds the OQ-36 predicates into zero-cost constants, and provides the substrate for the build-time code generation the language already relies on.

**Related codes:** COMP-02, RESV, OQ-36, GH #14.

---

## OQ-38 — The surface name of `@fixed`

**Surfaced when:** settling MUT-01 on a single negative annotation, which fixes the shape of the word but not the word itself.

**The issue.**
`@fixed` reads well against `final` on a local (`final @fixed List<Item> items` is two locks named by two words) and it is short.
It is less good elsewhere.
On a class declaration `@fixed class Money` says "this class cannot change" where the intended reading is "instances of this class expose no mutation", and a reader coming from Java hears `fixed` as the assignment property `final` already owns.
On a parameter, `void render(@fixed Scene s)` states a read-only lend, which is the meaning most call sites care about, and `fixed` names it only indirectly.
The annotation appears in eight positions (MUT-01), so the name is read far more often than it is written and a wrong connotation compounds.

Candidates carry different trade-offs.

- `@ro` / `@readonly` names the capability directly and matches what C# (`readonly`), C++ (`const`), and D (`const`) call it.
  It is the least ambiguous on parameters and fields.
  `@readonly class Money` is still odd, and `@ro` is terse to the point of being unguessable.
  `@readonly` is also taken: MUT-13 uses it as a method modifier, where it forbids receiver mutation.
  One word for both would state two different things in the same token, what a variable may do and what a method does to its receiver.
- `@value` names the intent on a class (`@value class Money`) and lines up with Java's own value-class vocabulary (JEP 401), which is a liability as much as an asset: a reader may take it to promise the identity and flattening semantics of a Valhalla value class, which MUT-10 does not.
  It reads poorly on a parameter, where the point is the lend mode, not the value-ness.
- `@frozen` matches the "frozen view" term the spec already uses (MUT-30, HIER-03) and carries no Java baggage, at the cost of a word Java developers do not have.
- `@const` is the C++/D spelling and would be immediately understood, but `const` is a reserved Java keyword, so `@const` and the keyword sit confusingly close.

**The question.**

- Which name does the annotation carry across all eight positions?
- Is one name for every position the right call, or does the class declaration want a different word from the variable positions, at the cost of MUT-01's single-word property?
- Is there a pair that reads as a pair, the way `@mut` and `@mutating` once did, now that a method is annotated `@readonly` (MUT-13)?
  A variable annotation built from the same root, `@ro` against `@readonly`, would let a reader carry one idea across both.
  The two are distinct (MUT-01, MUT-13), and a shared root must not become a shared meaning.

**Why it matters.**
The name is the most-read token of the mutability system, and it is cheap to change now and expensive once sources exist.

**Related codes:** MUT-01, MUT-13, MUT-30, MUT-10, TARG-03, HIER-03.

---

## OQ-39 — Per-field lifetime granularity for a `@bound` instance

**Surfaced when:** comparing the `@bound` model against Rust's struct lifetime parameters, where a struct holding two borrows can return a value tied to one of them alone.

**The issue.**
A `@borrow` field is unconditionally a source of its instance (OWN-09), the sources intersect (LIFE-03), and a returned borrow binds to `this` (OWN-18) or to a marked parameter (OWN-17), never to a field.
An instance holding two borrows therefore carries one lifetime, the intersection, and every borrow it lends is capped at that intersection even when it reaches only one of the fields.

```java
class Parser {
    @borrow String input;
    @borrow Config config;
    @bound String token() { return input.substring(0, 3); }   // bound to this
}

var input  = readFile();
var config = loadConfig();
var p      = new Parser(input, config);
var t      = p.token();
give(config);      // p is now unusable, and so is t
use(t);            // rejected, though t reaches only input
```

LIFE-02 lets an author tighten a return by removing a `@bound` marker from a parameter that does not contribute.
No such control exists on the field side: OWN-09 admits no per-field opt-out, and OWN-21 states that a retained parameter's source becomes a source of `this` rather than of the field it lands in.

**The question.**

- Can a return name a `@borrow` field as its source, rather than `this`?
- Does reading a `@borrow` field directly (OWN-04) yield a borrow of that field's original source or of the enclosing instance?
The answer decides whether direct field access is already a partial escape.
- Is the intersection the right default even with a per-field form available, so that `@bound` on a return keeps meaning "the whole instance"?
- Does a per-field form reintroduce the naming problem that `@bound` exists to avoid, given that a field already has a name to refer to?

**Why it matters.**
Every long-lived structure that holds borrows of different lifetimes is capped at its shortest one, so a parser holding a long-lived buffer and a short-lived config cannot lend anything that survives the config.
The workaround is to split the structure, which is the design pressure Rust's struct lifetime parameters exist to remove.

**Related codes:** OWN-09, OWN-17, OWN-18, OWN-21, LIFE-02, LIFE-03, LIFE-04, OWN-04.
