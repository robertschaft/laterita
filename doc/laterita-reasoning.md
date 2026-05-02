# Laterita — Design Reasoning

This document explains the reasoning behind each decision in the language specification. It is intended for readers who want to understand *why* Laterita is shaped the way it is, not just *what* the rules are. Section headers reference the corresponding spec codes.

---

## On the Name (Laterita)

The language is named after **laterite** — the iron-rich, rust-red tropical soil that forms on volcanic islands and is the ideal medium for growing coffee. The name carries all three of the threads that motivated the search:

- **Rust** — laterite is rust, literally. It is iron oxide weathered out of volcanic rock.
- **Island** — laterite forms on the same volcanic islands that gave us Java.
- **Coffee** — coffee grows best in laterite soils, including the famous Toraja, Kalosi, and Mandheling regions.

It also shares phonemes with *café latte*, which is a happy accident: a language that grew out of Java, made richer. The name is feminine where most language names are masculine or neutral, has no namespace conflicts, and is pronounceable in every major language without coaching.

The tagline writes itself: *the rich soil Java grew from.*

---

## Bindings (BIND-01 through BIND-06)

### Why `let` and `mut` for inference, types-first for explicit declarations

Java has always written types first: `String name = ...`. Postfix `: Type` syntax (TypeScript, Kotlin, Rust) is foreign to the Java aesthetic and there's no good reason to abandon what Java developers have internalized. A field declaration in Laterita reads like a field declaration in Java.

For type-inferred locals, modern Java has `var`. Laterita does not reuse Java's `var`: it conflates "infer the type" with "the slot may be reassigned," and we want those concepts separate. `let` is the immutable inferred form, `mut` is the mutable inferred form, and the explicit-type forms (`Type name` and `mut Type name`) cover the rest. Three primary forms mirror real Java practice — explicit-and-immutable (the dominant case), inferred-and-immutable (the convenient case), inferred-and-mutable (the modern Java case) — with explicit-type mutable bindings (`mut Type name`) provided for completeness, though rare.

### Why `mut` is the *single* mutability marker (BIND-02)

`mut` denotes mutability uniformly: local bindings, fields, methods, parameters. We considered a two-keyword model (e.g., reusing Java's `var` for slot reassignability and reserving a different word for method-receiver mutation) and rejected it. Both express the same underlying idea — "this can change" — and using one keyword for all positions means a reader can grep for `mut` and find every mutation point in the system.

The choice of `mut` over `var` is two-fold. First, `mut` reads cleanly in method positions (`mut void inc()` parses naturally as "mutable, returning void"), whereas `var void inc()` reads awkwardly because Java's `var` already carries the unrelated meaning of "infer the type of this local." Second, `mut` is Rust's vocabulary, and Rust is the closest existing language with the same ownership story Laterita is trying to bring to Java syntax — using the same keyword for the same concept is the lower-friction choice. Java's `var` keyword is therefore not reused; `let` and `mut` cover its territory.

### Why fields default to immutable (BIND-03)

Rust's transitivity insight: immutability is only meaningful if it propagates. If a `let` binding could still mutate the object's fields, "immutable" would be a hopeful suggestion rather than a guarantee. Making fields immutable by default forces an explicit choice for mutation, exactly where Effective Java has been recommending we make that choice for years (favor immutability, favor records over JavaBeans).

### Why methods declare mutation in the signature (BIND-05)

The signature of `mut void put(...)` answers a question Java developers have always had to answer informally: "does this method modify the receiver?" Today you read the body or hope the documentation is accurate. With `mut` in the signature, the compiler knows and the caller knows. It also matches Rust's `&self`/`&mut self`, expressed in Java's syntactic vocabulary.

### Why constructors are a special initialization case (BIND-04)

This is the same accommodation Rust makes for struct initialization and Java already makes for `final` fields. Immutable fields have to be assigned somewhere; the constructor is the only place that makes sense. Generalizing today's `final` to be the default for every field is straightforward.

### Why mutability is transitive (BIND-06)

If a `let` binding could call `mut` methods, immutability would mean nothing — it would just be a comment. The transitivity rule is what makes "this object is read-only" a real guarantee. It also means handing someone a `let` reference to a complex object graph is genuinely safe — they cannot change anything, anywhere, through it. This is one of the largest correctness wins in the language, and it falls out of getting one rule right.

---

## Optionality (NULL-01 through NULL-10)

### Why non-nullable by default

Java's null is, by Tony Hoare's own assessment, the most expensive single mistake in language design. NPE remains the dominant runtime failure in Java codebases despite decades of static-analysis tooling. Laterita has the option Java didn't: pick non-nullable as the default and require a syntactic marker to opt in to absence. This is the same call Kotlin made in 2011 and Swift made in 2014, and the resulting safety improvement is one of the best-validated language design decisions of the last 15 years.

The rule turns NPE from "any reference might fault at any time" into "only `T?` references might be null, and you can't dereference one without proving it isn't." The compiler does the proof.

### Why Kotlin's model rather than Optional<T> or Rust's Option<T>

Three approaches exist:

1. **Library-level `Optional<T>`** (Java 8). Wraps the value, requires `.get()` / `.orElse(...)` / `.map(...)` to use. Verbose, doesn't compose well with collections (`List<Optional<String>>`), and doesn't help fields whose underlying type is still nullable.
2. **Tagged-union `Option<T>`** (Rust). Pattern-match to extract. Sound and explicit, but Rust pays for it with a much larger pattern-matching story than Java has — `match`, irrefutable patterns, exhaustiveness — most of which Java does not have today.
3. **Type-level nullability** (Kotlin). `T` and `T?` are distinct types, null safety falls out of the type system, and flow-sensitive smart casts let `if (x != null) { ... x ... }` work naturally.

Kotlin's model fits Laterita's other choices best. It preserves Java's surface (you write `String name`, not `Option<String> name`), it doesn't require importing pattern matching to be useful, and the `?.`/`?:`/`!!` operators are short, familiar from Kotlin and Swift, and visually localize null-handling decisions. Smart-cast narrowing (NULL-06) makes the common case — null-check then use — read like ordinary Java.

### Why `onDrop()` is null-aware

NULL-09 specifies that scope-exit `onDrop()` on a `T?` skips `null` and dispatches on the contained value otherwise. This composes with DROP-04's drop-flag machinery — the compiler already had to track per-binding "still owned?" state; "is this `T?` non-null?" is the same shape of conditional cleanup. No new runtime mechanism is introduced.

### Why no separate Optional<T>

With nullable types in the language, `Optional<T>` and `T?` are isomorphic and `T?` is shorter, more idiomatic, and avoids `Optional<Optional<T>>` confusion when generics nest. STD-03's `Weak<T>::upgrade()` returns `Shared<T>?` rather than `Optional<Shared<T>>` for this reason.

---

## Move and Borrow (MOVE-01 through MOVE-07)

### Why default assignment is a borrow (MOVE-01)

Looking at real Java code, the overwhelming common case is "I want to read this, I don't want to take it away from where it lives." Defaulting to a borrow matches that intuition. The user writes ordinary Java-looking code; the compiler infers a borrow; both bindings remain usable. Making move the default would have been the Rust approach, but it would have meant `give` (or `move` keyword) on essentially every assignment — friction with no payoff.

### Why `give` and `take` instead of one shared marker (MOVE-02, MOVE-03)

Giving up ownership and taking ownership are different actions, even though they are two ends of the same transfer. Earlier drafts used a single sigil (`^`) in both positions on the theory that "ownership crosses this boundary" was a unifying meaning. That meaning is real but stretched — the sigil reads as "out of this binding" at a use site and as "into this slot" at a parameter, which are inverse roles.

Two English verbs make the asymmetry first-class. `take T name` on a parameter declares "this slot receives ownership"; `give binding` at a use site declares "this source releases ownership." The pairing is symmetric in the sense any sender/receiver pair is symmetric: same operation, two named ends, neither overloaded with the other's job.

We considered keeping ownership entirely at the call site (silent signatures) and rejected it. The function knows whether it needs ownership; that need is part of its public contract. A signature `void store(take String s, ...)` says "s is consumed" without forcing the caller to read the body. The signature is the API.

The two markers also support different inference at different positions. At a call site, the parameter declaration is canonical, so `give` on the argument is optional — `store(name, list)` is unambiguous when `store`'s signature has `take String s`. At a binding assignment, both sides are local and we want the marker on the side that *acts*: the source binding releasing ownership. So `let b = give a` is the explicit form; `take String b = give a` is allowed for documentation but adds nothing operational; `take String b = a` (bare RHS) is rejected because the bare RHS is a borrow per MOVE-01 and `take` cannot infer a move from a borrowing source.

This asymmetry — `give` optional at call sites, mandatory at assignments — is deliberate. The call site has a published contract to lean on; the assignment doesn't. Quiet by default at the boundary that has more information; explicit at the one that doesn't.

Producer/consumer asymmetry runs through the rest of the language as well: bindings default to borrow on the consumer side (MOVE-01), expressions default to owned on the producer side (return values, constructors, computed expressions). `give` and `take` only appear at genuine ownership transfers, and the surrounding code reads like Java.

### Why borrow exclusivity (MOVE-04)

This is the rule that actually buys safety. It rules out data races at compile time, iterator invalidation at compile time, and all the family of bugs that come from "two pieces of code each thought they had exclusive access to this." It is the load-bearing wall of the entire ownership system.

### Why disjoint field borrows (MOVE-05)

This was a real finding from the verification work. A red-black tree node needs to access `left` and `right` independently while the tree is mutably borrowed. The naïve borrow check rejects this even though it's trivially sound — the two fields don't alias. Without disjoint field borrows, large parts of the standard library become unwriteable in safe code (TreeMap, doubly-linked structures, swap-based algorithms). Rust solved this; Laterita inherits the solution.

### Why disjoint slice borrows with `splitAt` for hard cases (MOVE-06)

Same principle as MOVE-05, applied to arrays. The compiler can prove disjointness for trivial cases (`data.slice(0, 50)` and `data.slice(50, 100)`); for arbitrary index arithmetic, a standard library `splitAt` provides safe sub-slices using `unsafe` internally. This is the foundation for parallel divide-and-conquer, in-place sort, and any partition-based algorithm.

### Why partial-move tracking (MOVE-07)

Once you have moves out of fields, you need to know which fields are still alive at every point in the function. This is bookkeeping the compiler does silently, and it pays off both in normal control flow (use-after-move detection on partially-moved values) and during exception unwind (DROP-04, EXC-03). Skipping it would mean making `give` on a field illegal, which would make ownership transfer in real code far more painful.

---

## Mutability (MUT-01, MUT-02)

### Why `Cell<T>` is the only escape hatch (MUT-02)

There are real cases where a class is logically immutable but has internal caching (lazy initialization, memoization, mutex-protected state). Rust's answer is `UnsafeCell<T>`, the one type the compiler treats specially as a hole in the rules. Laterita adopts the same model: a single primitive marks the spot, every other interior-mutability mechanism is built on top of it. Concentrating the unsafe assumption in one place is what makes the rest of the language safely checkable.

---

## Lifetimes (LIFE-01 through LIFE-05)

### Why mark-borrow on returns (LIFE-02)

A bare return type means owned. Borrowed returns are explicitly declared with `bound`. We considered the inverse — owned-marked, borrowed-default — and rejected it because owned dominates at API boundaries. Constructors, factories, computed values, query results, anything that mints fresh state all return owned values. Marking the common case adds visual noise to most signatures.

The asymmetry mirrors the producer/consumer framing of the rest of the language. A function return is a producer position, and most production yields fresh ownership. The `bound` marker carves out the case where production is actually a view into an input.

We also considered relying entirely on body-driven inference — keep the signature silent, let the compiler look through to the implementation to decide owned vs. borrowed. That collapses under separate compilation and, more importantly, hides the contract from the caller: they would have to read the body or guess and let the compiler error. The signature is the API; what the caller can do with the return value belongs in the signature.

The hard case prior versions of this design left ambiguous was the receiver-tied borrow (a method returning a slice of `this`). We tried elision rules ("if the body returns a field, tie it to `this`") and a `from this` annotation; both were unsatisfying. Elision hid the contract; `from this` was visually heavy. The chosen form — a single `bound` on the return type — collapses to one short token and makes the relationship explicit at the API boundary.

`give` is also permitted as an optional declarative prefix on a return type, mirroring how `take` is permitted on LHS bindings (MOVE-02). Both are documentary: bare is the canonical form, and the explicit keyword adds nothing operational. The win is for tooling and readers — an IDE hover, generated documentation, or a deliberately verbose signature can show `give` to make the producer-side transfer obvious without changing semantics. The pattern is consistent: `take` on the consuming side, `give` on the producing side, both optional where the surrounding context already determines the operation.

### Why `bound` instead of `from` or apostrophe-letter

Rust's `'a` notation is famously off-putting to newcomers — it looks like a syntax error and forces the reader to learn an entirely new sigil class. We rejected that early.

The interesting alternative was `from`, which reads as "the result borrows from this argument." `bound` won for two reasons. First, it describes the relationship the compiler is enforcing — a lifetime constraint — rather than a data-flow source. The annotation isn't really about where the value came from; it's about what its lifetime is tied to. Second, `bound` collapses the receiver case into a single token. A method whose return is bound to `this` writes `bound T method(...)` — no second word, no awkward `from this` compound.

The known cost is overlap with Java's "upper bound / lower bound" terminology in generics (`<T extends Number>`). We accept the cost: generics and lifetimes are different beasts, the syntactic positions don't overlap (generics-bounds appear in `<...>`, lifetime-bound appears in parameter and return positions), and a reader is unlikely to confuse them after the first encounter.

### Why intersection on multiple bounds (LIFE-03)

When a returned borrow is bound to several sources, its lifetime is the shortest of them. The intuition is the same as Rust's lifetime intersection: the borrow can only be valid while *all* of its sources are valid. The compiler enforces this; the user doesn't reason about it explicitly unless they want a tighter bound, in which case they remove a `bound` marker.

### Why unmarked sources are an error (LIFE-04)

Earlier drafts treated the receiver as a default contributor — an instance method returning a borrow was implicitly tied to `this`. That made signatures silent in a way that hid information from the caller. The current rule is stricter: a borrow tied to an input the caller can't see in the signature is a compile error, with the diagnostic naming the input the body actually borrows from. The fix is always local to the signature, and the caller-visible contract is always complete.

---

## Cleanup (DROP-01 through DROP-07)

### Why universal `onDrop()`, not opt-in (DROP-01)

This is the big realization: try-with-resources in real Java is the right *mechanism* but the wrong *default*. The clutter you correctly identified — needing `try ()` for every stateful binding — comes from cleanup being opt-in. Laterita makes it the default. Every binding gets deterministic cleanup; no syntactic marker required.

### Why the name is `onDrop()` and not `close()`

Reusing `close()` was the first instinct — Java already has `AutoCloseable`, and it would have meant "no new concept, just universal." We rejected it because Java code that ports to Laterita already has `close()` methods on streams, sockets, JDBC connections, and a long tail of resource classes. Mixing a language-orchestrated `close()` with the user-defined ones creates ambiguity at every call site: is this a normal method call, a manual scope-exit invocation, or a leftover from try-with-resources thinking? A different name keeps the language hook visually separate from any user `close()` method that survives migration.

`onDrop()` reads as event-handler convention — *what runs when this is dropped* — and aligns with Rust's `Drop` trait without inheriting Rust's terminology wholesale (Rust's method is bare `drop`; the `on` prefix matches Java/Kotlin/Android event-handler naming the Java-derived audience already knows). The name says "the system invokes this; you specify the body."

### Why not `finalize()` or `_dispose` (both rejected)

`finalize()` was tempting and wrong. Three reasons:
1. `finalize()` is being removed from Java. Building the language's core mechanism on a method the parent language is removing is fragile.
2. Almost no class overrides `finalize()` today, so calling it at scope exit would mostly call no-ops. Useful only after every standard library class is updated.
3. The semantic mismatch is bad signaling. `Object.finalize()` is the GC reclamation hook. Repurposing the name to mean "scope exit" would confuse every reader who knows Java.

An earlier draft used `_dispose()` with a leading-underscore convention to signal "do not call." That worked, but the visibility modifier `internal` (DROP-06) does the job more formally — type-system enforcement instead of naming convention — and lets us drop the visually awkward underscore. With `internal` carrying the "uncallable" property, `onDrop()` can read like a normal method name.

### Why `internal` for compiler-only methods (DROP-06)

Forbidden-by-convention versus forbidden-by-type-system is a real distinction. The earlier `_dispose` design relied on a reserved-name rule plus a code-review heuristic ("if you see this identifier outside an override, something's wrong"). The current design uses a visibility modifier the compiler enforces.

Naming the modifier `internal` accepts a known cost: C# already uses `internal` to mean "assembly-scoped public," which is broader than what Laterita means by it. We accepted the clash because no other candidate was as natural at first reading. `lifecycle` was the cleanest narrow alternative but limits the keyword's future use; `intrinsic` connotes a compiler-provided body, not just a compiler-only call site; `hidden` reads informally. `internal` paired with a clear specification — *only the compiler invokes; user code may override but never call, including via `super`* — communicates the intent in one word, and the C# reader recovers the meaning after the first encounter.

The keyword is reserved for future compiler-orchestrated hooks. It is deliberately *not* a general-purpose access level; ordinary visibility scoping continues to use `public`/`protected`/`private`/package-default. Adding `internal` to the visibility list would invite misuse — wrapping arbitrary methods to hide them from callers — which is not what the modifier is for.

### Why reverse declaration order (DROP-02)

This is RAII order. If you opened `A` then opened `B` that depends on `A`, you should drop `B` first then `A`. Reverse-declaration order matches the natural dependency order in almost every real case.

### Why drop flags (DROP-04)

Without per-field tracking, partial moves either have to be forbidden (severely limiting the language) or have to leak undefined behavior on cleanup (catastrophic). Drop flags are the proven solution. Rust uses them, the optimization story is well understood (most flags are statically constant and get optimized away), and the runtime cost in code that doesn't actually unwind is approximately zero.

### Why `super.onDrop()` is auto-chained (DROP-05)

For most Java methods, the user is responsible for calling `super.foo()` explicitly when an override needs the parent's behavior. This is the right default for ordinary methods because the user, not the language, decides whether super's behavior should run.

`onDrop()` is the exception. It's a compiler-orchestrated lifecycle hook — the user never invokes it directly (DROP-06), and forgetting `super.onDrop()` would silently leak the parent class's resources. There is no design decision to delegate to the user: cleanup of the inherited state is *always* needed when the subclass is being dropped. A "remember to chain" rule would be a pure footgun with no upside.

Auto-chaining puts `onDrop()` on the same footing as the synthesized copy constructor (OBJ-01), which auto-inserts `super(source)`. Both are language-internal recursive backbones; both should not require the user to remember to participate. The user's role in an override is to specify *what* to clean up, not whether to chain.

### Why explicit `onDrop()` calls are forbidden (DROP-06)

Once the compiler emits all drop calls — at scope exits (DROP-01), on partial-move paths (DROP-04), on exception unwind (EXC-02), at the end of overrides (DROP-05) — there is no remaining use case for user-invoked drop. Allowing it would create a category of bugs (double-drop, mismatched lifetimes, drop-then-use) for no expressive gain.

Forbidding it has two payoffs:

1. **Double-drop can't be expressed.** Lifetime is exclusively scope-bound; the compiler emits exactly one drop per binding. A whole class of double-free-shaped bugs simply doesn't exist in Laterita source.
2. **Type-system enforcement.** With `internal` as a visibility modifier, the compiler rejects every illegal call site. There is no "did the author mean to call this here?" question.

The cost is no early-cleanup mechanism: a binding lives until its scope ends, period. We considered an opt-in early-cleanup keyword (a `drop x;` form, sugar for "consume `x` and run its `onDrop`") and chose not to specify one. Real cases for early cleanup are rare; structuring scopes — extracting an inner block or a helper function — covers the cases that matter; and adding any escape hatch reintroduces the double-drop surface we just closed. If a future need is convincing, the keyword can be added later without breaking existing code.

### Why `onDrop()` aborts on throw (DROP-07)

Without DROP-07, an exception escaping `onDrop()` would create real safe-code memory leaks:

- Sibling bindings in the same scope: DROP-02 drops in reverse order, so an exception from the inner binding's `onDrop` skips the outer one's `onDrop`.
- Auto-chained super (DROP-05): a throw from the override body skips the compiler-appended `super.onDrop()`, leaking the parent's resources.
- Enclosing scope unwind (EXC-02): a throwing drop during exception unwind interrupts the unwind itself.

Three options were considered: compile-time enforcement (forbid `throws` clauses on `onDrop`), runtime abort, or catch-and-continue (Java's try-with-resources `addSuppressed` model). Compile-time enforcement is too restrictive — it bans transitively any operation that *could* throw, which sweeps in legitimate flush/sync calls. Catch-and-continue brings back the try-with-resources complexity the language was meant to simplify away, and it keeps "throwing drops" as a normal control-flow shape that user code has to reason about.

Abort-on-throw matches Rust's choice for the same reason: a drop hook is the wrong place to surface fallible operations. The override's role is best-effort cleanup. If a flush can fail meaningfully, the user calls a separate `flush()` method explicitly while the binding is still alive — somewhere a caller can handle the result. Anything that reaches `onDrop` and throws is, by definition, a cleanup contract violation; treating it as fatal makes the contract observable instead of silently corrupting subsequent execution.

The runtime guard at each compiler-emitted call site is cheap (a try-catch wrapper on a code path that almost never executes). The diagnostic on abort identifies the throwing class and the originating exception, so cleanup-contract violations are debuggable.

---

## Copying (OBJ-01, OBJ-02)

### Why a copy constructor and a `clone()` method

Ownership rules force the question: when a function needs an owned value but the caller has a borrow, *something* has to produce a duplicate. Without a defined story, every type author would invent their own — `User.copy()`, `Cart.duplicate()`, `Order.snapshot()` — with subtly different contracts. The clean answer is two layered mechanisms:

- **Copy constructor (OBJ-01).** The actual duplication mechanism. Every class has a `protected ClassName(ClassName source)`, auto-generated to recurse through fields. Constructors are already the only context where immutable fields can be initialized (BIND-04), so duplication composes with the rest of the language without special pleading.
- **`clone()` method (OBJ-02).** A public wrapper that calls the copy constructor. This is the API generic and polymorphic code uses. `element.clone()` virtually dispatches to the actual class's `clone()`, which calls that class's copy constructor.

### Why the synthesized copy constructor calls `field.clone()`, not `new FieldType(source.field)`

The recursive step in OBJ-01 is `source.field.clone()`, not `new FieldType(source.field)`. Both would produce the same value when reachable, but the latter is not generally reachable: copy constructors are `protected`, and a class can only invoke another class's protected constructor from within a subclass relationship. A `User` class with a `Shared<Address>` field cannot call `new Shared<Address>(source.address)` — `Shared`'s copy constructor is not accessible from `User`.

`clone()` sidesteps the visibility problem entirely. It's `public` (OBJ-02), uniformly callable from any context, and dispatches virtually so subtype duplication works correctly when a field is held at a supertype. The call chain is `clone() → copy constructor → field.clone() → field copy constructor → ...`, with the public/protected boundary alternating cleanly: every cross-class step goes through `clone()`, every within-class step uses the copy constructor for direct field access.

This also makes opt-out clean: a class that can't be copied overrides `clone()` with a `broken` body, and the brokenness propagates transparently through any enclosing class's auto-generated copy constructor (which calls the field's `clone()`). No separate "this copy constructor is broken" channel is needed.

We arrived at this two-layer design after walking through several alternatives.

**Universal `Object.clone()`.** Java's model. The clone method lives on Object; classes opt in via `Cloneable`. The API is famously broken — bit-copy semantics, post-construction mutation, `CloneNotSupportedException`. Reimplementing it in Laterita reproduced most of the same complications, since `super.clone()` chaining doesn't fit BIND-04. Rejected.

**Opt-in `Cloneable<T>` interface.** Rust's approach. Generic functions constrain `T extends Cloneable<T>` and types implement the interface. Type-safe but noisy: the bound has to appear on every generic that touches potentially-cloneable values, and makes the dominant case (yes, this is copyable) explicit when it should be implicit. Rejected.

**Copy constructor only, no `clone()`.** We tried this. Generic code would have to write `new T(element)`, which has two problems: the syntax is unfamiliar (Java forbids it because of erasure; even with monomorphization it's an unusual construct), and it constructs the *static* type — slicing any subtype data when `T` is held at a supertype. Rejected for ergonomics and for the polymorphism loss.

The chosen design avoids all three pitfalls. The copy constructor does the real work and stays `protected` so external code can't bypass class-level invariants. `clone()` provides the public, virtually-dispatched API generic code wants. The synthesized `clone()` body is one line — `return new Self(this);` — so there is essentially no implementation surface to get wrong.

### Why no special-casing for `Shared` or `Atomic`

Earlier drafts of OBJ-01 named `Shared` and `Atomic` explicitly: "primitives bitwise, `Shared`/`Atomic` via their refcount-bumping copy constructors, other objects recursively." The list was redundant. `Shared` and `Atomic` are just classes whose `clone()` happens to bump a refcount (using `unsafe` internals per UNS-01). The recursive rule "copy each field via its type's `clone()`" picks them up uniformly. Future ownership wrappers — `Cow<T>`, weak handles, anything else — slot in the same way without amending OBJ-01.

If a field type's `clone()` is `broken` (as `Heap<T>.clone()` is, per STD-06), the enclosing class's auto-generated copy constructor inherits the brokenness through the call chain. The compile error appears at the actual call site, with a path through the field. Same mechanism that handles direct `broken`; no separate "synthesis fails" rule needed.

---

## Unreachability (UNR-01)

### Why `broken` for opt-out

A class that can't be copied (a file handle, a single-use resource, anything wrapping `Heap<T>`) needs a way to say so. Three options were on the table:

1. **Throw at runtime.** Override the copy constructor to throw `UnsupportedOperationException`. Java's traditional approach. Fails late, surfaces at the wrong place, and bypasses any compile-time guarantee about which types are copyable.
2. **Opt-in interface (`Cloneable<T>`).** Type-safe but adds bound-noise to every generic signature, and makes the dominant case ("yes I'm copyable") explicit when it should be implicit.
3. **Compile-time opt-out via `broken`.** A statement that declares the path unreachable; the compiler rejects calls that can reach it. Failure is at the actual problem site (the instantiation that triggers it).

The third option lets the dominant case (copyable) stay implicit while making the rare case (non-copyable) surface as a compile error rather than a runtime throw. Generic signatures stay clean — no `extends Cloneable<T>` bounds — and per-monomorphization checking (COMP-02) localizes the diagnostic.

### Precedent

`broken` is essentially C++'s `= delete` generalized to a statement position. C++ uses `= delete` for exactly this purpose — declaring a copy constructor (or any function) intentionally unavailable, with calls rejected at compile time. The C++ pattern has worked well for over a decade for the special-member-deletion use case (Rule of Zero/Three/Five).

Adjacent ideas exist in other languages: Rust's `!` (never type) and `unreachable!()` macro for divergence; refinement types in Liquid Haskell, F\*, and Idris for conditional unreachability with static checking. The unconditional form specified here is closest to `= delete` in spirit.

### Why not just throw

A throw is a runtime contract; `broken` is a compile-time contract. The difference matters because generic code over a type parameter can't tell at definition time whether a particular instantiation will be valid — but with `broken` and per-monomorphization checking, the *user* of the generic with a non-copyable type sees the error at their call site, not in production. This is the same trade Rust makes with trait bounds, achieved here without the bound boilerplate.

### Why a statement, not a method modifier

C++ uses `= delete` as a definition syntax (`Foo() = delete;`). We considered something equivalent at the method-signature level. A statement-form keyword turned out to compose better: it works for partial bodies (a function that's deleted only on certain paths, the conditional form), it places the diagnostic message inline, and it generalizes to any place control flow ends — not just the deleted-method case. The unconditional form is the only one in the spec for now; the conditional form is open (see open questions).

### Why `String` is no longer final (STR-01)

The `final String` decision in Java 1.0 was driven by 2000-era constraints: string interning for `==`, immutability guarantees in a GC'd world, hash caching, JIT optimization assumptions. Modern JVMs handle all of these without `final`. Modern AOT compilers handle them with monomorphization.

What `final` *costs* is type safety in domain modeling. Modern Java APIs are full of `String` arguments where the compiler can't help — `createUser(String email, String name, String address)` — and the community has worked around this with Lombok's `@Value`, microtype libraries, and records wrapping single strings. Letting `String` be extended fixes this directly:

```laterita
class Email extends String { ... }
class Name extends String { ... }
public User createUser(Email email, Name name, Address address) { ... }
```

The compiler now catches argument-order bugs that today are silent runtime errors.

### Why owned vs. borrowed strings tracked per-binding (STR-02 through STR-04)

Real Java pretends `String` is one thing. It isn't — sometimes it's an independent allocation (`toUpperCase` result), sometimes it's a view (`substring` result, where reallocating the source is illegal while the view exists). Rust splits these into `String` and `&str`, two visibly different types.

We chose to keep them as one type at the source level, with the compiler tracking per-binding whether the string is owned or borrowed. This preserves Java's "everything is just a reference" feel — the user writes `String name` either way. The complexity moves into the compiler. The cost is internal complexity; the gain is that Java's surface syntax is preserved.

### Why subclasses are owned (STR-05)

A subclass like `Email` carries an invariant ("contains an @ sign"). If `Email` could be a borrow into a mutable buffer, the buffer's owner could break the invariant from underneath. Forcing user-defined subclasses to be owned is the simplest rule that prevents this. The standard library's `String` itself can have borrowed instances because `String` has no invariant beyond "valid UTF-8" that holding a slice could break.

---

## Closures (CLO-01 through CLO-04)

### Why three modes, inferred (CLO-01, CLO-02)

This is Rust's `Fn` / `FnMut` / `FnOnce` distinction, which Rust forces you to think about because closures need precise typing for trait dispatch. Laterita takes the same three categories — read, mutate, consume — but lets the compiler classify the closure from its body. Users write a lambda; the compiler does the work.

### Why three corresponding interfaces (CLO-03)

Method signatures need a way to say "this needs a closure I can call from many threads" vs. "this needs a closure I can call once." Three interfaces, picked by the receiver based on what guarantee the API needs. Most user code doesn't see them — you write a lambda and the compiler picks the right one. You see them when (a) writing library code that takes closures, or (b) the compiler tells you your closure is the wrong kind for this API.

The concrete interface names from the conversation (`Closure`, `RepeatableClosure`, `SharedClosure`) were illustrative. The spec leaves them open because we never settled on names.

### Why closures carry capture lifetimes (CLO-04)

A closure that borrows `name` cannot outlive `name`. This is the same lifetime-bounded-by-input principle from LIFE-02, applied to closures. Without it, you could create a closure, the captured variable would die, and calling the closure would deref freed memory. The standard ownership rules force this, but it's worth calling out as a separate requirement because closures are where it bites people.

---

## Exceptions (EXC-01 through EXC-04)

### Why preserve Java's exception syntax (EXC-01)

Earlier in the design I sneaked in a Swift-style `try`-at-call-site model with sealed error types and exhaustive pattern matching in catch. You correctly pushed back: those are nice ideas, but they aren't *forced* by ownership. They're orthogonal language improvements.

The actually-ownership-forced changes to exceptions are minimal: cleanup during unwind (EXC-02), drop flag participation (EXC-03), and a runtime-implementation question about stack traces (EXC-04). Everything else is regular Java exceptions, including checked exceptions, the type hierarchy, and the syntax. Whether to *also* fix Java's exception ergonomics is left as an open question rather than baked into the spec.

### Why cleanup runs on unwind (EXC-02)

This is the same problem C++ destructors solve and Rust's drop-on-unwind solves. Without it, exceptions through ownership transfers would leak. The user writes ordinary code; the compiler emits the cleanup along the unwind path.

### Why drop flags participate (EXC-03)

Same reasoning as DROP-04 generalized: the unwinder must consult per-field move state, otherwise partial moves followed by exceptions either leak (no cleanup) or double-free (cleanup on already-moved fields). The flags are already there from DROP-04; the unwind path just consults them.

### Why lazy stack-trace resolution (EXC-04)

In real Java today, `fillInStackTrace` is one of the more expensive things a program does, because the JVM walks the stack and resolves symbols at throw time. In an AOT-compiled language without a JVM, we have the option of decoupling capture from resolution. Capture is cheap (frame-pointer walk, ~100ns); resolution is the expensive part (symbol table lookup), and most exceptions are caught and discarded without anyone reading the trace.

Lazy resolution gives near-zero cost in the common case (throw, catch, recover) and full information in the rare case (throw, log, debug). Rust's `Backtrace` does this; Laterita adopts the same model.

---

## Unsafe (UNS-01 through UNS-04)

### Why method-level only, not classes or blocks (UNS-01)

The big simplification you proposed: if `unsafe` only marks private methods, the audit boundary is the method signature. A reviewer reads each `private unsafe` method, verifies its preconditions, and trusts that the public API is safe by composition. Inlining is fine because the safety reasoning is per-method.

This is *tighter* than Rust. Rust allows `unsafe { }` blocks deep inside public functions, and the audit boundary can be hard to find. Forcing extraction into a named private method makes every unsafe operation in a codebase trivially enumerable: `grep "private unsafe"` finds them all.

The minor cost is that some inline unsafe operations have to be extracted into helper methods. The compiler inlines them back, so the runtime cost is zero. The slight visual ugliness is, arguably, *good* — you can't bury unsafe operations inline in a 200-line public method.

### Why a fixed list of operations (UNS-02)

Rust's `unsafe` unlocks a known finite list of operations (deref raw pointer, call unsafe fn, etc.). Everything else still type-checks normally. This is what makes `unsafe` audits tractable: you're not asking "is this whole function correct?", you're asking "is this `*ptr` deref valid?"

Laterita does the same. The list of unsafe operations is small, fixed, and documented. Anything else still gets normal compiler checking.

### Why fields of unsafe types force the surrounding rules (UNS-03)

A class that holds a `Heap<T>` field has invariants the compiler can't check (the pointer must be non-null, well-aligned, point to live memory of the right type). Maintaining those invariants requires unsafe context at every method that touches the field. Forcing this propagation prevents the easy mistake of "I'll just hold a Heap<T> and use it from safe methods" — which would be unsound.

### Why standard checks still apply inside `unsafe` (UNS-04)

`unsafe` is a small, targeted unlock — not a blanket "anything goes" mode. Inside an unsafe method, the type system still types, the borrow checker still borrow-checks, lifetimes still infer. You only unlock the specific operations in UNS-02. This is what keeps unsafe code reviewable: even unsafe code is mostly checked by the compiler, and the unchecked parts are localized to known constructs.

---

## Standard Library (STD-01 through STD-07)

### Why `Shared` and `Atomic` are split

Single-threaded reference counting doesn't need atomic operations. Cross-thread sharing does. Splitting them lets single-threaded code skip the synchronization cost entirely, which is significant in tight loops. Rust does the same with `Rc<T>` vs. `Arc<T>`. The cost is a slight conceptual overhead — two types instead of one — but the performance and correctness gains are worth it.

### Why `.share()` is explicit (STD-01)

Three reasons:

1. **Visibility.** A refcount bump is non-trivial work, possibly atomic, possibly contended. Hiding it behind implicit copy semantics (Rust's `Arc::clone`) means the cost is invisible in profiles. Making it explicit puts the cost where the reader can see it.
2. **Composition with the binding rules.** `Shared<T> b = a` is a borrow (no work). `Shared<T> b = give a` is a move (no work). `Shared<T> b = a.share()` is a refcount bump. Each does something different and each is sometimes the right choice. Rust forces `Arc::clone` even when a borrow or a move would do.
3. **No language feature needed.** `Shared<T>` becomes an ordinary class with an ordinary method. The compiler doesn't need to know anything about it.

### Why cycles leak (STD-01)

`Shared<T>` is reference-counted: when the last handle's `onDrop()` runs, the count reaches zero and the value is dropped. A cycle of strong handles never reaches zero — every handle is held up by another handle in the cycle, and none can decrement past one. The cycle leaks.

This is the same limitation Rust's `Rc<T>` and `Arc<T>` carry, with the same answer: `Weak<T>` (STD-03) for the back-edge in any structure that may form a cycle. We considered adding a cycle collector (a partial tracing GC) and rejected it on Rust's grounds — the runtime cost and complexity exceed the value, given that `Weak<T>` solves the common cases and acyclic ownership is the dominant pattern. Laterita does not aim to be better than Rust at memory management; it aims to give Java developers the same safety properties Rust gives systems programmers, in syntax they already know.

The implication for COMP-01 is that "memory management determined statically" is not literally true for refcounted types — refcount reclamation is dynamic, and cycles are an unrecoverable leak. The spec acknowledges this explicitly rather than masking it.

### Why race-safe upgrade (STD-04)

You spotted this during the verification phase. A naïve `Weak::upgrade` that reads the strong count and then increments has a TOCTOU race: the strong count could drop to zero between the read and the bump, and the upgraded handle would point at destroyed memory. Compare-and-swap fixes this — the increment only happens if the count hasn't changed since we read it. Rust's `Arc::Weak::upgrade` does this for the same reason, with carefully chosen memory ordering.

### Why `Cell<T>` and `Heap<T>` are unsafe primitives (STD-05, STD-06)

These are the irreducible escape hatches. `Cell<T>` is the documented hole in MUT-01. `Heap<T>` is the only way to allocate without compiler-tracked ownership. Everything else in the standard library — `Shared`, `Atomic`, `Mutex`, lazy initializers, growable collections — is built on top of them in `private unsafe` methods. The unsafe surface is small, concentrated, and auditable.

### Why `Send` is mentioned but underspecified (STD-07)

Cross-thread move safety needs to be tracked. We agreed it does, but never settled on the syntax for declaring `Send`-ness. Rust uses an auto-trait; Laterita could use an interface, an annotation, or compile-time inference. The spec records the requirement; the open questions document records the missing decision.

---

## Compilation Model (COMP-01 through COMP-05)

### Why AOT and no GC (COMP-01)

The whole point of the exercise. Java's GC papers over ownership. Removing it forces every ownership question to be answered statically, which is what gives Laterita its safety properties. AOT compilation is the natural target — the language has no runtime that benefits from a JIT, no dynamic class loading that benefits from interpretation, and no reflection (COMP-05) that benefits from full runtime type info.

### Why monomorphization (COMP-02)

Without GC, generic dispatch through type erasure (Java's current model) has nowhere to put the type information at runtime. Monomorphization — emitting one specialized version of a generic per concrete type — is the proven solution from C++ templates and Rust. The cost is binary size; the gain is that generic code runs at the same speed as hand-specialized code.

### Why compiler-inserted cleanup is invisible (COMP-03)

The user shouldn't see drop calls in their source. This is what makes `onDrop()` feel like a language feature rather than a discipline. The compiler emits the calls, the user writes ordinary code.

### Why drop flags are an optimization target (COMP-04)

Most drop flags are statically determined — the compiler can prove a field is always moved by a certain point, or never moved. In those cases, the flag becomes a constant and gets optimized away. The runtime overhead in real code is near zero. This isn't critical to specify, but it matters for implementers worried that drop flags will slow things down. They won't, in practice.

### Why no reflection (COMP-05)

Two reasons, one structural and one about exposure.

The structural reason: monomorphization (COMP-02) erases generic identity at runtime. `List<String>` and `List<Integer>` are distinct compiled types with no shared metadata describing them as "the same generic." Field offsets are baked in. A reflection API that pretended otherwise would either have to defeat monomorphization (re-introducing the runtime type information we deliberately erased) or lie about what it's looking at. Both are bad outcomes; neither is worth the compatibility win.

The exposure reason: unrestricted reflection has been a recurring source of vulnerabilities in the Java ecosystem (deserialization gadget chains, sandbox escapes, library tampering). Removing it forecloses an entire class of attacks. The cost is paid by libraries that previously did at runtime what they can now do at compile time. That cost is real but bounded — the techniques are proven (Dagger, Micronaut, Quarkus, kotlinx.serialization, Quarkus/AspectJ compile-time weaving). The benefit accrues to every Laterita program for free.

What is genuinely lost: loading arbitrary user-supplied bytecode at runtime (intentional — that's the security hazard), JRebel-style hot reload (mitigated by fast incremental rebuilds), and `Proxy.newProxyInstance` over interfaces unknown at compile time. The first is a feature, not a regression. The second is a developer-experience cost worth investing in fast compilation to offset. The third is rare in real codebases outside mocking and dynamic RPC stubs, both of which are codegen-able when the interface is known.

The reflection question was OQ-03 in the open-questions document; it is now settled in favor of "none."

---

## What Laterita Is Not

It's worth being explicit about a few things this design deliberately doesn't do.

**Not a JVM language.** Laterita compiles to native code. It doesn't run on the JVM. The Java compatibility is at the source level — the syntax looks like Java, the standard library looks like Java's, but under the hood there's no GC, no class loader, and no reflection (COMP-05). Existing Java bytecode does not run in Laterita.

**Not a Rust replacement.** Rust is more permissive in some ways (raw `unsafe { }` blocks, multiple smart-pointer styles, no inheritance) and more disciplined in others (no exceptions, lifetimes always visible, no implicit conversions). Laterita makes different tradeoffs because its target audience is Java developers, not systems programmers coming from C++.

**Not a fork of Java's standard library.** Most of Java's `java.util` would need to be reimplemented for Laterita's ownership rules. `ArrayList`, `HashMap`, `TreeMap`, `String`, `StringBuilder` — all have different semantics under ownership. The names are preserved; the implementations are new.

**Not finished.** This document and the spec describe the design we converged on. The open questions document records what we explicitly didn't settle. Several of those questions are load-bearing — particularly around exceptions and Spring-style frameworks. Real implementations will need to make those choices.
