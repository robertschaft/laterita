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

### Why `mut` sits in the visibility slot (BIND-05)

`mut` is conceptually a visibility-like marker, not a behavioral one. By BIND-06 a `mut` method can only be called on a `mut` receiver — so the marker doesn't describe *what the body does* in a way the caller has to reason about, it describes *which receivers can see the method at all*. From a caller's point of view, an immutable binding sees a strictly smaller API surface than a `mut` binding does; that is exactly the shape of a visibility predicate.

Placing `mut` immediately after `public`/`protected`/`private`/`internal` reflects this. Reading left-to-right, the modifier list answers two questions in order: "to whom is this visible?" (the visibility keyword) and "on what receivers?" (`mut` or absence). Everything that comes after — `static`, `final`, `override`, `unsafe`, Java's behavioral modifiers — describes orthogonal facts about the body or the inheritance role and follows in the conventional Java positions.

We considered the alternative position the spec drafts originally used — `mut` immediately before the return type, after every other modifier — and rejected it. Putting `mut` next to the return type reads as if `mut` qualifies the return value, which it does not; the receiver-mutation semantics belong to the method, not the result. Anchoring `mut` to the visibility slot also keeps the return-type prefix `bound` uncontested in the slot directly preceding the return type, where it genuinely does qualify the returned value.

### Why methods declare consumption of `this` with `give` (BIND-07)

The receiver-mode markers form a triple: bare (read), `mut` (mutate), `give` (consume). Spec drafts initially considered `take` here to mirror the parameter form (`take T name`) — methods would then have read with `void f()`, mutate with `mut void f()`, and consume with `take void f()`. We chose `give` instead.

The call site is the dominant reader. For parameters there are two surfaces: the function signature uses `take` to declare the receiving end, and the caller may write `give x` to mark the giving end. Methods have only one surface — the call `obj.consume()` is itself the transfer, with no separate caller-side opt-in. Reading the signature, the caller wants to know what *they* lose, not what the method gains. `public give void close()` reads as "calling this gives `this` up." `take` would read in the wrong direction.

The mnemonic carries from the existing use of `give`. `give x` at a call site means the caller is releasing ownership of `x`. `give` on a method signature means the caller is releasing ownership of the receiver. Same word, same direction; a reader who has met `give x` understands `give R foo()` immediately.

The small inconsistency with parameters — `take T name` for the receiving side, `give R foo()` for the consuming side — is accepted on its own terms. Parameters are a two-sided contract written from the function-author's perspective; methods are a one-sided contract read from the caller's perspective. Each side gets the word that fits its dominant reader.

### Why owned returns lost the optional `give` prefix (LIFE-02)

Earlier drafts allowed `give` as an optional declarative prefix on a return type, equivalent to the bare form, on the theory that explicit producer-side marking helped tooling and readers. With `give` now also a method-level receiver-consume modifier (BIND-07) sitting in the slot immediately after the visibility modifiers, the two readings collide visually: `give Stream<R> map(...)` could parse as either receiver-consume (BIND-07) or owned-return-prefix (LIFE-02). Removing the optional return-type form eliminates the ambiguity. Owned remains the default; `bound` is the only prefix that qualifies a return type. Code that previously wrote `give T foo()` for emphasis simply drops the keyword.

### Ordering: `give mut` parallels `take mut T`

When both modifiers appear on a method, the order is `give mut`, matching the parameter form `take mut T` (MOVE-03). The ownership marker comes first, the mutability marker second. The semantic parallel is exact: `take T name` is "owned, slot not reassignable"; `take mut T name` is "owned, slot reassignable"; `give R foo()` is "consumes `this`, `this` slot not reassignable"; `give mut R foo()` is "consumes `this`, `this` slot reassignable." Whether method bodies have practical use for a reassignable `this` is a separate question; the spec admits the syntax for consistency with parameters.

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

With nullable types in the language, `Optional<T>` and `T?` are isomorphic and `T?` is shorter, more idiomatic, and avoids `Optional<Optional<T>>` confusion when generics nest. STD-03's `WeakReference<T>::get()` returns `Rc<T>?` rather than `Optional<Rc<T>>` for this reason.

---

## Move and Borrow (MOVE-01 through MOVE-10)

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

### Why `take` participates in overload resolution but `mut` does not (MOVE-09)

Java is an overloading language; we accept that and extend overload resolution along the ownership axis. The asymmetry between `take` and `mut` is deliberate.

`take` and borrow are different *operations*. A consuming `put(take K, take V)` and a borrowing `put(K, take V)` are two reasonable APIs on the same Map: the first lets the caller donate the key, the second lets the caller keep it. Same name, same effect on the receiver, different ownership contract — exactly the situation overloading is for.

`mut` and immutable borrow are not different operations; `mut` is a *strength* on borrow. Two same-named methods that differed only in `mut` on a parameter would be saying "I write to this argument" vs. "I don't" — and that distinction wants different names, not the same name with different access strengths. There is also no caller-side opt-in syntax for `mut` parallel to `give` for `take`: a caller has no way to disambiguate `f(x)` between `f(T)` and `f(mut T)` even if both existed. The combination — no useful semantic distinction, no caller-side disambiguator — makes `mut` a poor fit for the overload axis. We exclude it.

`final` on Java parameters is excluded from the signature for a different but related reason: it is callee-internal entirely, the caller doesn't care, and there is nothing observable to overload on. `take` is observable at the call site, so it earns its place in the signature; `mut` is observable at the call site too but provides no useful overload distinction, so it does not.

Borrow wins as the ownership tie-breaker because moving is observable and shouldn't happen by accident: a caller writing `f(x)` expects `x` to remain usable afterward, and a `take` overload silently overriding that expectation would break it invisibly at the call site. The opt-in is `give`, matching MOVE-01's consumer-side default.

The axis order — Java specificity first, ownership only as tie-breaker — is the deliberate trade. Putting the ownership axis first would have shielded bare call sites from silent moves at every overload set, but at the cost of reordering Java's overload-resolution intuition, where the most-specific applicable method wins, full stop. Keeping Java's order makes ported Java code resolve the way a Java reader expects, and confines the new axis to genuine ties (same-type pairs like `put(K)` vs `put(take K)`). Code-transformation tooling benefits from the same property: nothing about Java's familiar resolution shifts under porting unless the ported program actually introduces the new axis.

The cost of this choice is asymmetric. Adding a same-type borrow overload to an existing `take` shifts bare call sites from consume to borrow on the tie-breaker — usually harmless (the argument is dropped a little later), occasionally observable (large buffers, lock guards, files). Adding a more-specific `take` overload to an existing borrow shifts bare call sites at the subtype from borrow to consume on Java specificity — the same shape of break Java already has when adding a more-specific overload, just along a new axis. Both cases argue for the same discipline: write `give` at any call site whose ownership semantics are load-bearing, even when only one overload exists today. The opt-in marker is where caller intent gets recorded.

#### Recommendation: `mut` and `take` are caller-side work

When a method's body needs `mut` access to a parameter or needs to consume it, the signature should declare `mut` or `take` and the caller does the work of providing it — by holding `mut`, by surrendering ownership with `give`, or by cloning before the call. A method whose signature is `f(K)` should not internally clone or take a temporary `mut` lock to do something the signature didn't ask for; if the body needs mut or ownership, the signature should say so.

This is non-normative — methods sometimes legitimately clone internally (defensive copies, caching layers), and the compiler should not police every internal `.clone()`. The default mental model is what we are after: *the cost the caller can read from the signature is the cost the caller bears*. Rust's `HashMap::insert(k: K, v: V)` consumes both arguments and lets callers `.clone()` if they want to keep them; that is the idiom Laterita inherits, expressed in Java's overload-aware vocabulary rather than Rust's overload-free one.

### Why override variance differs for `take` and `mut` (MOVE-10)

Once `take` is part of the signature and `mut` is not, the override rule for each falls out almost automatically.

`take` is invariant for overrides for two reinforcing reasons. The structural one: `take` is part of the overload identity, so an override of `f(take T)` must carry `take`; otherwise it is overriding a different method (or not overriding any inherited method, depending on what else exists in the supertype).

The call-site reading argues for invariance too, and on its own terms. A memory-aware reader looking at `take` in a signature expects ownership to actually transfer — that is the contract `take` exists to carry, and it is the contract `give` at the call site is meant to record. If an override could silently downgrade `take` to borrow, the contract would not hold for any specific dispatch target: the caller's `give x` to an interface declaring `take` would mean "x might be consumed, depending on which implementation wins dispatch." That is exactly the invisibility the modifier exists to eliminate. `take` always means take, on the interface and on every override.

`mut` is contravariant: an override may drop `mut` but not add it. The intuition is the same as Java's `throws` rule. An interface that promises "I might mutate your argument" can be implemented by a class that doesn't actually mutate — the body simply asks for less than the contract granted, and any caller satisfying the interface's `mut` requirement automatically satisfies the implementation's bare requirement. The reverse is unsound: a class that demands `mut` cannot stand in for an interface that promised only a borrow, because callers passing through the interface type would supply only an immutable borrow and the implementation would have nothing to mutate.

The Java parallel — `throws` clauses contract on overrides, parameter types stay invariant — guides the shape of this rule. Modifier strength relaxes; identity does not. `mut` is a strength; `take` is identity.

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

The recursive step in OBJ-01 is `source.field.clone()`, not `new FieldType(source.field)`. Both would produce the same value when reachable, but the latter is not generally reachable: copy constructors are `protected`, and a class can only invoke another class's protected constructor from within a subclass relationship. A `User` class with an `Rc<Address>` field cannot call `new Rc<Address>(source.address)` — `Rc`'s copy constructor is not accessible from `User`.

`clone()` sidesteps the visibility problem entirely. It's `public` (OBJ-02), uniformly callable from any context, and dispatches virtually so subtype duplication works correctly when a field is held at a supertype. The call chain is `clone() → copy constructor → field.clone() → field copy constructor → ...`, with the public/protected boundary alternating cleanly: every cross-class step goes through `clone()`, every within-class step uses the copy constructor for direct field access.

This also makes opt-out clean: a class that can't be copied overrides `clone()` with a `broken` body, and the brokenness propagates transparently through any enclosing class's auto-generated copy constructor (which calls the field's `clone()`). No separate "this copy constructor is broken" channel is needed.

We arrived at this two-layer design after walking through several alternatives.

**Universal `Object.clone()`.** Java's model. The clone method lives on Object; classes opt in via `Cloneable`. The API is famously broken — bit-copy semantics, post-construction mutation, `CloneNotSupportedException`. Reimplementing it in Laterita reproduced most of the same complications, since `super.clone()` chaining doesn't fit BIND-04. Rejected.

**Opt-in `Cloneable<T>` interface.** Rust's approach. Generic functions constrain `T extends Cloneable<T>` and types implement the interface. Type-safe but noisy: the bound has to appear on every generic that touches potentially-cloneable values, and makes the dominant case (yes, this is copyable) explicit when it should be implicit. Rejected.

**Copy constructor only, no `clone()`.** We tried this. Generic code would have to write `new T(element)`, which has two problems: the syntax is unfamiliar (Java forbids it because of erasure; even with monomorphization it's an unusual construct), and it constructs the *static* type — slicing any subtype data when `T` is held at a supertype. Rejected for ergonomics and for the polymorphism loss.

The chosen design avoids all three pitfalls. The copy constructor does the real work and stays `protected` so external code can't bypass class-level invariants. `clone()` provides the public, virtually-dispatched API generic code wants. The synthesized `clone()` body is one line — `return new Self(this);` — so there is essentially no implementation surface to get wrong.

### Why no special-casing for `Rc` or `Arc`

Earlier drafts of OBJ-01 named `Rc` and `Arc` explicitly: "primitives bitwise, `Rc`/`Arc` via their refcount-bumping copy constructors, other objects recursively." The list was redundant. `Rc` and `Arc` are just classes whose `clone()` happens to bump a refcount (using `unsafe` internals per UNS-01). The recursive rule "copy each field via its type's `clone()`" picks them up uniformly. Future ownership wrappers — `Cow<T>`, weak handles, anything else — slot in the same way without amending OBJ-01.

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

C++ uses `= delete` as a definition syntax (`Foo() = delete;`). We considered something equivalent at the method-signature level. A statement-form keyword turned out to compose better: it works for partial bodies (a function that's deleted only on certain paths, expressible as `if (cond) broken ...`), it places the diagnostic message inline, and it generalizes to any place control flow ends — not just the deleted-method case.

---

## Strings (STR-01 through STR-05)

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

The signature-level markers introduced for lifetimes (`bound` per LIFE-02) and parameters (`take` per MOVE-03) make the public contract explicit: a method's owned-vs-borrowed return is visible to callers, and a `take` parameter is visible at the call site. What the compiler tracks silently is *intra-method* flow — within a function body the per-binding owned/borrowed state is internal bookkeeping, not part of any public surface.

The dominant ergonomic concern with the one-type choice is "I have a borrow here but the next position needs ownership." In Rust's two-type model the user picks the right conversion (`to_string`, `to_owned`, `String::from`, `clone`). In Laterita that whole pick disappears: `clone()` is universal (OBJ-02), every type carries it unless `broken`, and it always returns an owned `give` value. The diagnostic for any owned/borrowed mismatch is therefore uniform — *"this position needs an owned String; binding is borrowed — try `.clone()`"* — and the fix is one method call. With `clone()` as the universal escape valve, the type system stays out of the way of the dominant case, which is the real argument against the two-type model.

### Why subclasses are owned (STR-05)

A subclass like `Email` carries an invariant ("contains an @ sign"). If `Email` could be a borrow into a mutable buffer, the buffer's owner could break the invariant from underneath. Forcing user-defined subclasses to be owned is the simplest rule that prevents this. The standard library's `String` itself can have borrowed instances because `String` has no invariant beyond "valid UTF-8" that holding a slice could break.

---

## Closures (CLO-01 through CLO-04)

### Why three modes, inferred (CLO-01, CLO-02)

This is Rust's `Fn` / `FnMut` / `FnOnce` distinction, which Rust forces you to think about because closures need precise typing for trait dispatch. Laterita takes the same three categories — read, mutate, consume — but lets the compiler classify the closure from its body. Users write a lambda; the compiler does the work.

### Why closures have structural function types (CLO-03)

Earlier drafts of this section planned a small set of nominal closure interfaces, parallel to Java's `Function`, `BiFunction`, `Consumer`, etc. The unresolved question (OQ-05) was only their names. Once parameter modes (`take`, `mut`, `bound`) entered the type system, the count exploded: a binary closure has roughly 3 input modes per parameter, several return-bound configurations, and 3 receiver modes from CLO-01 — order of 100 nominal interfaces per arity, before counting primitive specializations. No naming convention survives that.

Structural function types resolve the explosion at the source. The type *is* the signature: `(take A, mut B) -> R` is a distinct type from `(bound A, B) -> R` not because two interfaces were declared, but because the type expressions differ. The compiler compares structurally, the same way `int[]` and `String[]` are different without a separate interface for each. No stdlib zoo, no naming committee, no question of which canonical interface a lambda targets.

The cost is one new kind of type expression in the grammar. Laterita's type system is already growing modifier-bearing positions (`take`, `mut`, `bound`), so adding a type form that aggregates those positions is a smaller step than it would be in plain Java. Lambda literal syntax (`(a, b) -> body`) is unchanged from Java; only the *target* changes from a SAM interface to a function type.

The trade-off is that source-level `import` of a closure type isn't possible — there is no `Function` to import. We accept this. Reusable function-shaped contracts in real code almost always have richer obligations than a SAM expresses (a name, documentation, related methods), and those still want a regular interface. What the structural form fixes is the ergonomic wart of "I just need a callback parameter" requiring a published interface to land in.

OQ-05 is dissolved by this decision: there are no closure-interface names to fix because there are no closure interfaces.

### Why parameter mode controls closure mode (CLO-03)

The receiver-mode story for closures follows directly from BIND-06 and BIND-07, with no new rule needed. A lambda is an object whose SAM has a receiver mode determined by its captures (CLO-01). The slot holding the lambda is a parameter (or field, etc.) with one of the standard modifier forms. The transitivity rules already in the spec handle the rest:

- A bare slot can only invoke bare-receiver methods on its binding → only read closures fit.
- A `mut` slot can invoke bare- or mut-receiver methods → read or mutate fit.
- A `take` slot owns its binding and can invoke any-receiver method, including a `give` SAM → all three modes fit.

This is the same mode-flow that applies to any other type. The closure interacts with the type system the same way any other object does; the capture-mode classification of CLO-01 determines the SAM's receiver mode, and everything downstream is pre-existing rules. No special "closure mode" axis was needed.

### Why closures carry capture lifetimes (CLO-04)

A closure that borrows `name` cannot outlive `name`. This is the same lifetime-bounded-by-input principle from LIFE-02, applied to closures. Without it, you could create a closure, the captured variable would die, and calling the closure would deref freed memory. The standard ownership rules force this, but it's worth calling out as a separate requirement because closures are where it bites people.

---

## Exceptions (EXC-01 through EXC-04)

### Why preserve Java's exception syntax (EXC-01)

Earlier in the design I sneaked in a Swift-style `try`-at-call-site model with sealed error types and exhaustive pattern matching in catch. The pushback was correct: those are nice ideas, but they aren't *forced* by ownership — they're orthogonal language improvements with their own costs. Sealed error types want a pattern-matching story Java doesn't have; mandatory `try` at call sites is a substantial syntactic change for marginal gain; exhaustive catch makes wide-net handlers (logging frameworks, top-level fallbacks) more awkward, not less. Each is defensible in isolation; none clears the bar of "introducing an unfamiliar shape pays for itself."

The ownership-forced changes to exceptions are minimal: cleanup during unwind (EXC-02), drop flag participation (EXC-03), and a runtime-implementation question about stack traces (EXC-04). The syntax — `try`/`catch`/`finally`, the `Throwable` hierarchy, the `throw` keyword — survives unchanged. The one Java-ergonomics fix that does make it into the spec is dropping the checked/unchecked distinction (EXC-05), where the consensus against checked exceptions is unusually strong and the feature actively fights other choices in the design (closure interop, lambda-friendly stdlib).

### Why no checked exceptions (EXC-05)

Java's checked-exception model — methods declare every checked exception they may throw, callers must catch or re-declare — was meant to force callers to think about recoverable failures. Three decades in, the consensus is that it didn't work and is more burden than benefit. Laterita drops the checking entirely, keeping `throws` only as documentation.

The strongest evidence is consensus across both language designers and the Java ecosystem itself.

- **No mainstream language designed after Java has adopted the model.** Anders Hejlsberg refused it in C# — his 2003 interview is the canonical critique: versioning brittleness (adding a thrown type is a binary-incompatible API change), the `throws Exception` escape valve, scaling failures across deep call stacks. Kotlin, Scala, and Swift each declined. The case is unusually one-sided for a language design question.

- **The Java ecosystem routes around the feature.** Spring's data-access layer wraps `SQLException` into the unchecked `DataAccessException` hierarchy on principle. Hibernate, Jackson, and modern Jakarta EE APIs are unchecked-by-default. Lombok's `@SneakyThrows` exists almost entirely to bypass the mechanism, and is widely used. When the most popular ecosystem in the language is built on top of escape hatches from a language feature, that feature is not paying its rent.

- **Lambdas broke the remaining case.** The functional interfaces introduced in Java 8 — `Function`, `Consumer`, `Supplier` — don't declare exceptions, so any checked throw inside a stream pipeline forces wrapping into `RuntimeException` or a custom `CheckedFunction` shim. Laterita wants closures (CLO-01 through CLO-04) to be ordinary; carrying forward a feature that fights closure interop is incoherent.

- **The signature-contagion problem is real.** It was already the rationale for THR-08 making `InterruptedException` unchecked. The argument generalizes: every checked exception that propagates through a deep call stack pollutes every signature on the path, the path inevitably reaches a method whose author didn't want to think about that exception, and the author writes `throws Exception` to escape — losing the precision the model was trying to deliver.

The misuse pattern of "exceptions for control flow" (parsing-by-throw, `NumberFormatException` as a question) is real but separate. It is a stdlib-API problem (use `T?` or a parsed-result type for expected absence), not a checking problem; dropping the checked discipline does not encourage it and dropping it does not solve it.

The cost of the change is small. `throws` clauses remain legal as documentation, so existing Java method signatures translate without syntactic edits. Tools that surface declared exception types continue to work. The language merely stops enforcing the declaration. What is gained: clean lambda interop, no signature contagion, no `@SneakyThrows`-shaped workaround culture, and consistency with Laterita's other modern-Java-friendly choices (sticky interrupt flag, ownership-bound thread lifetime, no reflection).

We considered going further — replacing exceptions with a `Result<T, E>` type, or adopting Swift's `try`-at-call-site model — and rejected both for the EXC-01 reason. They are improvements with merit, but they require importing pattern-matching infrastructure Java doesn't have, and they impose a much larger surface change than dropping a single piece of enforcement. Drop the checking, keep the syntax. The minimal change captures most of the win.

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

## Standard Library (STD-01 through STD-09)

### Why `Rc` and `Arc` are split

Single-threaded reference counting doesn't need atomic operations. Cross-thread sharing does. Splitting them lets single-threaded code skip the synchronization cost entirely, which is significant in tight loops. Rust does the same with `Rc<T>` vs. `Arc<T>`. The cost is a slight conceptual overhead — two types instead of one — but the performance and correctness gains are worth it.

### Why `.share()` is explicit (STD-01)

Three reasons:

1. **Visibility.** A refcount bump is non-trivial work, possibly atomic, possibly contended. Hiding it behind implicit copy semantics (Rust's `Arc::clone`) means the cost is invisible in profiles. Making it explicit puts the cost where the reader can see it.
2. **Composition with the binding rules.** `Rc<T> b = a` is a borrow (no work). `Rc<T> b = give a` is a move (no work). `Rc<T> b = a.share()` is a refcount bump. Each does something different and each is sometimes the right choice. Rust forces `Arc::clone` even when a borrow or a move would do.
3. **No language feature needed.** `Rc<T>` becomes an ordinary class with an ordinary method. The compiler doesn't need to know anything about it.

### Why cycles leak (STD-01)

`Rc<T>` is reference-counted: when the last handle's `onDrop()` runs, the count reaches zero and the value is dropped. A cycle of strong handles never reaches zero — every handle is held up by another handle in the cycle, and none can decrement past one. The cycle leaks.

This is the same limitation Rust's `Rc<T>` and `Arc<T>` carry, with the same answer: `WeakReference<T>` (STD-03) for the back-edge in any structure that may form a cycle. We considered adding a cycle collector (a partial tracing GC) and rejected it on Rust's grounds — the runtime cost and complexity exceed the value, given that `WeakReference<T>` solves the common cases and acyclic ownership is the dominant pattern. Laterita does not aim to be better than Rust at memory management; it aims to give Java developers the same safety properties Rust gives systems programmers, in syntax they already know.

The implication for COMP-01 is that "memory management determined statically" is not literally true for refcounted types — refcount reclamation is dynamic, and cycles are an unrecoverable leak. The spec acknowledges this explicitly rather than masking it.

Doubly-linked structures and graph-shaped data (the concern surfaced as OQ-12) port using exactly these primitives: `Rc<T>` / `Arc<T>` on forward edges, `WeakReference<T>` on back edges. No additional standard-library type for graphs is introduced. The verbosity over a GC-tracked back-pointer is the deliberate cost of deterministic, refcount-based reclamation. Migration of doubly-linked lists, parent-pointer trees, and adjacency-list graphs is mechanical: each back-pointer becomes a `WeakReference<T>::get()` at use sites.

### Why race-safe upgrade (STD-04)

You spotted this during the verification phase. A naïve `WeakReference::get` that reads the strong count and then increments has a TOCTOU race: the strong count could drop to zero between the read and the bump, and the upgraded handle would point at destroyed memory. Compare-and-swap fixes this — the increment only happens if the count hasn't changed since we read it. Rust's `Arc::Weak::upgrade` does this for the same reason, with carefully chosen memory ordering.

### Why `Cell<T>` and `Heap<T>` are unsafe primitives (STD-05, STD-06)

These are the irreducible escape hatches. `Cell<T>` is the documented hole in MUT-01. `Heap<T>` is the only way to allocate without compiler-tracked ownership. Everything else in the standard library — `Rc`, `Arc`, `Mutex`, lazy initializers, growable collections — is built on top of them in `private unsafe` methods. The unsafe surface is small, concentrated, and auditable.

### Why `local`, not `Send` (STD-07)

Cross-thread move and borrow safety needs to be tracked. Rust uses two positive auto-traits (`Send` and `Sync`); Laterita inverts the marker and uses one negative property: `local`. The few stdlib primitives that are not safe to cross thread boundaries (`Rc<T>`, `Cell<T>`, `Heap<T>`) are declared `local`; everything else is non-local by default and may cross threads.

The reason for inverting is Java-target ergonomics. With a positive marker, every user class would have to declare `implements Send` (or be silently inferred via auto-trait machinery) to be usable in concurrent code. With the inverted marker, the *default* for ordinary user classes is "sendable," which is what Java programmers expect. The annotation surface is concentrated in the small set of stdlib primitives plus the rare thread-affine class.

Send and Sync are collapsed into one property because the distinction (Send-but-not-Sync, e.g., Rust's `Cell<T>`) is rare and unusable without the kind of fine-grained borrow reasoning Java programmers don't expect. The single `local` marker covers both move and borrow restrictions.

Hand-synchronized stdlib types (`Arc<T>`, `Mutex<T>`, `Thread`) override the inferred `local` property by declaring `unsafe nonlocal`. The unsafe declaration is the same admission of proof obligation as every other `unsafe` in the language: the author is asserting a property the compiler cannot verify, and UNS-04 still applies.

### Why borrow-checked iteration reuses Java's API (STD-08)

The cursor-that-mutates-its-container is one of the canonical patterns where ownership type systems reach for internal `unsafe`. Generative lifetimes, region typing, and similar academic devices can defeat the issue but at a cost (proliferating type parameters, separate inference machinery) out of proportion for the use case. Rust shipped `Vec::retain`, `Vec::drain`, `extract_if`, and `LinkedList::cursor_mut` — all backed by `unsafe`. Laterita follows the same shape: a small stdlib API implemented with `private unsafe`, with the audit boundary the four or five method bodies that compose it.

The design choice that took the most thought was whether to introduce a new `Cursor<T>` type or reuse Java's existing `Iterator<T>` and `ListIterator<T>`. The case for a new type was that the borrow-checked semantics are a real change from Java; a new name would warn the reader. The case for reuse won: every method on `ListIterator` already has the right meaning, the loop shapes are identical, and inventing new vocabulary would force every Java reader to learn which iterator class to reach for in which situation. The borrow rules replace `modCount` and `ConcurrentModificationException` underneath, but the API surface above is the one Java developers already use.

The single signature deviation — `Iterator.remove()` and `ListIterator.remove()` returning `give T` rather than `void` — is forced by the ownership model. Java's void return reflects an assumption Laterita can't carry: that the caller "still has" the element from the prior `next()` call as a reference into the collection. In Laterita, `next()` returns a `bound T` borrow tied to the iterator's position, and any mutating call on the iterator invalidates that borrow at the type level. Returning the removed element by `give` is what restores the user's access to the value after the borrow is gone, and it incidentally folds Rust's separate `drain`/`extract_if` API into a one-liner over `remove()`. Statement-form `it.remove();` (ignoring the return) still compiles — the result drops via `onDrop`, matching Java's observable behavior.

`ConcurrentModificationException` doesn't carry over. Its job — detecting "you mutated the collection while iterating" — is exactly what MOVE-04 enforces statically. The runtime category exists in Java because the language can't express the constraint at compile time. Laterita can, so the runtime exception becomes a compile error, and the `modCount` field can leave the standard library entirely.

We considered keeping a separate `Cursor<T>` type for the cursor case anyway, on the theory that "iterator" connotes read-only iteration in many readers' heads. Decided against: `ListIterator` already exists in Java with mutation methods (`remove`, `set`, `add`), so the semantic precedent is there even if many Java developers underuse it. Two iterator types in Java's vocabulary, two iterator types in Laterita's — same names, sharper guarantees.

### Why `Mutex<T>` returns a guard, not a `void` `lock()` (STD-09)

Java's `Lock` interface separates `lock()` from `unlock()`, which means the unlock can be skipped on an exception path. The defensive idiom is `try { lock.lock(); ... } finally { lock.unlock(); }` — a try/finally ceremony for what should be a syntactic invariant. Laterita has no need for the ceremony: `lock()` returns a borrowed `MutexGuard<T>` whose `onDrop` releases the lock, scope exits run `onDrop` automatically (DROP-01), and exception unwind triggers `onDrop` along the way (EXC-02). Forgetting to unlock isn't expressible.

This is the same pattern as Rust's `MutexGuard`, C++'s `std::lock_guard`/`std::unique_lock`, and the try-with-resources shape Java itself documents for `Lock` users. The change Laterita makes is to elevate the discipline from "remember to use it" to unconditional: every `Mutex<T>` user gets the guard, whether they thought to want it or not.

---

## Threads (THR-01 through THR-10)

### Why reuse `java.lang.Thread` (THR-01, THR-02)

The goal is that Java programmers can read and write Laterita without learning new concurrency vocabulary. `new Thread(() -> body).start()` is the canonical Java spawn; reusing it directly means the surface looks identical. The two changes (`onDrop()` semantics, sticky interrupt flag) are behind the API, not on it.

The deprecated methods (`stop`, `suspend`, `resume`, `destroy`) are dropped because they are unsafe under any memory model — Java itself deprecated them — and the unsafety is worse without a GC to paper over the resulting state corruption.

### Why ownership-bound thread lifetime (THR-01, THR-06)

Without a GC, a thread that outlives the data it borrows is a use-after-free. With a GC, Java handles this transparently — the thread keeps the borrowed objects alive. Laterita can't rely on that, so the lifetime relationship has to be explicit.

The cleanest expression of "this thread cannot outlive its owner" is to bind the thread to a variable and have `onDrop()` interrupt-and-join. Variable scope already exists; reusing it for thread lifetime requires no new construct (no `scope { }` block, no structured-task helpers in the language). The borrow checker already enforces "borrows don't outlive their source" — applying that to spawn captures gives cross-thread borrow safety for free.

### Why no detach (THR-01, by omission)

Detached threads are zombie processes by another name. Every real-world use case (long-running servers, background flushers, async loggers) is better expressed as "owned by a top-level binding or by an object the user keeps alive deliberately." Modern systems push genuine fire-and-forget work to queues, schedulers, or container infrastructure — not to in-process detached threads. Erlang's hierarchical supervisor model is the precedent: every process is owned, no zombies.

The cost is that libraries can no longer quietly spawn background threads — they must expose a client object whose lifetime the user manages. This is a feature: it surfaces the resource.

### Why sticky interrupt flag (THR-03)

Java's `Thread.interrupted()` clears the flag on read, and `InterruptedException` clears it on throw. This was modeled on signal-handler semantics in 1995 and made sense when threads were expensive enough to pool and re-run. It has produced thirty years of cancellation bugs: code catches `InterruptedException`, "handles" it, and silently escapes cancellation because the flag is now clear. The standard advice — "always re-set the flag in the catch block" — is the user manually patching around the language default, the tell that the design is wrong.

Modern Java (virtual threads, structured concurrency) has no remaining use case for clear-on-read. Threads are cheap, not pooled-and-re-run; cancellation is a final state, not a consumable signal. Sticky flag is what every modern user actually wants.

### Why interrupt is exposed but the flag operations are restricted (THR-07, THR-09)

`Thread.interrupt()` is fine to expose because, with a sticky flag, it can't be silently lost. The flag is observable but not clearable; an external interrupt request cannot be "consumed" by code that catches the wrong exception. The bug class that motivated hiding `interrupt()` in earlier drafts is foreclosed by the flag semantics, not by API restrictions.

`Thread.interrupted()` is kept (Java compatibility) but redefined as a synonym of `isInterrupted()` — the clear-on-read behavior is the bug, not a feature worth preserving.

### Why `InterruptedException` is unchecked (THR-08)

A special case of EXC-05: all exceptions are unchecked. The cancellation-specific argument — sticky-flag semantics make recoverable handling moot, and the signature contagion was severe in Java — was originally local to `InterruptedException` and is what motivated the eventual generalization to every exception type.

### Why `onDrop()` cannot block (THR-05)

`onDrop()` runs on the unwind path. If a blocking call inside `onDrop()` itself throws `InterruptedException`, cleanup is abandoned mid-flight: locks held, files unflushed, memory leaked. The simplest rule that prevents this is "no blocking calls in `onDrop()`." It is checked statically at `onDrop()` definition.

The rule survives a survey of stdlib types whose cleanup *appears* to need blocking. `Mutex<T>.onDrop()` is a flag update — not a wait. A queue-like primitive (e.g. a Laterita `BlockingQueue<T>`) wakes blocked endpoints with an error in its `onDrop()` — a notification, not a wait. Buffered IO needs `flush()`, but `flush()` belongs in `close()`, which Laterita keeps distinct from `onDrop()` (the same Closeable model Java has): a user who skips `close()` and falls back on `onDrop()` gets an unflushed buffer, just as in Java today. `Thread.onDrop()` does block — but it runs in the *parent's* stack as the cancellation orchestrator (THR-06), not in a body subject to interruption, so THR-05 explicitly exempts it.

So the rule is universal for user-facing `onDrop` bodies, with one privileged exemption (`Thread.onDrop`) that is named in the spec rather than gestured at. No `unsafe onDrop` escape hatch is needed; the integrity guarantee — cleanup completes atomically with respect to interruption — holds without erosion.

### Why no `cancel()`, no `tryJoin()`, no `parallelFirst()` (THR-09, by omission)

The model deliberately keeps the public surface to what Java already exposes plus `onDrop()`. Higher-level orchestration primitives (timeout-aware joining, fork-join helpers, structured task scopes) belong in libraries, not in the language spec. The minimal surface — `start()`, `interrupt()`, `join()`, `isInterrupted()`, plus `onDrop()` and `give x;` — is sufficient to express every cancellation pattern. Library authors compose those into higher-level primitives as needed.

### Why `synchronized` is removed

Java's `synchronized` keyword is dropped — both the method modifier and the `synchronized(obj) { ... }` block — and so are `Object.wait`/`notify`/`notifyAll`. Mutual exclusion is provided exclusively by `Mutex<T>` (and related stdlib primitives). Four reasons.

**The intrinsic monitor isn't free.** Java's `synchronized` works because every `Object` carries a hidden header word the JVM materializes into a monitor on first contention. In a JVM with a GC and an object header already present for other reasons, the marginal cost is small. In an AOT-compiled language with no GC (COMP-01), giving every allocation an intrinsic-lock slot is a per-object cost paid by code that never locks anything. Concentrating mutual exclusion in a stdlib type means only the objects that need a lock pay for one.

**It doesn't compose with ownership.** `synchronized` locks *beside* data: holding a monitor doesn't restrict what fields the compiler lets you touch, and unsynchronized access to the same fields elsewhere is a normal compile success. `Mutex<T>` is shaped the opposite way — the lock *owns* the data, and the only path to the protected state is through the guard. In an ownership-typed language this is strictly the better primitive: the compiler proves that every access to the protected state happens under the lock, which `synchronized` cannot.

**Reentrancy collides with borrow exclusivity.** Java monitors are reentrant; the same thread can re-enter its own `synchronized` block freely. `Mutex<T>` is not, because re-acquiring its guard would mean handing out a second mutable borrow of the protected `T` while the first is still live, which MOVE-04 forbids. Preserving `synchronized` would mean either keeping reentrancy (and carving out a hole in MOVE-04) or silently making `synchronized` non-reentrant (and breaking the compatibility argument that justified keeping the keyword in the first place). Neither is acceptable; dropping the keyword is.

**`wait`/`notify` aren't separable from intrinsic monitors.** They are defined on `Object` and only meaningful while holding that object's monitor. Without intrinsic monitors there is nothing for them to attach to. They are also blocking interruption points (THR-04), which means they would have to participate in the cancellation model, and they would have to not appear in `onDrop` bodies (THR-05). All of that is better expressed by a stdlib condition-variable type sitting next to `Mutex<T>` when the need actually arises.

The migration cost is small: a `synchronized` method becomes a method on a class whose mutable state lives behind a `Mutex<T>` field; a `synchronized(obj)` block becomes a `mutex.lock()` scope. The translation is mechanical and the result is more honest about what the lock protects.

### Why mutex poisoning, no bypass (THR-10)

A thread that unwinds while holding a mutex leaves the protected data in an unknown state — possibly mid-write, possibly with broken invariants. Silently releasing the lock and letting the next acquirer proceed (Java's `synchronized` default, `parking_lot::Mutex`'s choice) means the bug in the unwound critical section produces silent wrong behavior in every subsequent acquirer — exactly the bug class hardest to track down. Poisoning makes the integrity hazard observable.

The remaining design choice was whether to provide a bypass for callers who want the guard regardless of poison state. Rust's `std::sync::Mutex` does (`unwrap_or_else(|p| p.into_inner())`); Laterita does not. The cases that motivated bypasses in Rust mostly turned out not to need poisoning at all — counters, best-effort caches, emergency logging are "I don't care if it's poisoned" cases, where the cleaner answer is no poisoning, not poisoning-with-escape. The cases where the bypass would correctly enable repair (recompute a cached aggregate, rebuild a derived index) typically collapse under reordering: defensive code that mutates locals first and the struct last leaves invariants intact even when intermediate steps panic. The cases where reordering doesn't help — a panicking user callback inside a generic-container's critical section — usually shouldn't be repaired anyway, because the structure itself may be broken.

Removing the bypass also closes a cargo-cult risk. Once `lockPoisoned()` exists, the path of least resistance for "this throws sometimes" is to call the bypass and ignore the issue, which negates the safety signal poisoning was introduced to provide. The pattern aligns with Laterita's broader stance: take Rust's safety guarantees, give them Java's surface, remove escape hatches that don't carry their weight.

If a future need proves real, a more targeted API — a destructive `Mutex<T>.take()` that consumes the mutex, or `Mutex<T>.replace(give T)` that swaps the protected value on a poisoned mutex — is a smaller and less abusable addition than `lockPoisoned()`. The current spec leaves both unaddressed; either can be added later without breaking existing code.

The result is a fourth point in the design space — poisoning yes, bypass no — stricter than Rust's `std::sync::Mutex`, more signaling than `parking_lot::Mutex` or Java's intrinsic locks. The unique combination is consistent with the rest of the language.

| | Poisoning | Bypass |
|---|---|---|
| Java `synchronized`, `parking_lot::Mutex` | no | n/a |
| Rust `std::sync::Mutex` | yes | yes (`into_inner`) |
| Laterita `Mutex<T>` | yes | no |

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
