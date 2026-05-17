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

## Surface Syntax — annotations and static methods (§18)

Every ownership, lifetime, mutability, cleanup, and visibility concept Laterita introduces uses existing Java syntax: annotations on declarations, static method calls in expression and statement positions. The language adds no new keywords.

The migration win is concrete. A `.java`-mode laterita source (COMP-06) is still a `.java` file: `javac` parses it, and IDEs that know nothing about laterita still highlight, navigate, refactor, and complete. The laterita compiler is the strict checker on top, attaching semantics to specific annotations and to unqualified calls of specific stdlib static methods. As long as the source stays within the Java-compatible subset enumerated in COMP-06, nothing else about it has to change to remain parseable by the Java ecosystem.

The cost is visual heft: `void f(@bound @mut Buf b)` reads more loudly than `void f(mut bound Buf b)` would have. Annotations are the only modifier slot Java reserves for third parties, so for a language whose primary value proposition is migrating Java code, that compatibility dominates the typographic preference.

Expression-position concepts can't be annotations — `@give x` would not parse — so they live as static methods on `laterita.lang.Intrinsics`. With static import, call sites read `give(x)` and `broken()` unqualified; to `javac` they are ordinary static method calls.

Type inference reuses Java's `var`, with the default-immutable rule (MUT-01) extending to it: `var x = expr` is immutable; `@mut var x = expr` is mutable. No separate keyword for type-inferred mutable bindings.

### Two source surfaces: `.lat` and `.java` (COMP-06, COMP-07)

Five forms in the spec — `T?`, `?.`, `?:`, `!!`, and inline FI types `(P1, …, Pn) -> R` — can't ride on annotations or static calls; their natural slots are type expressions and operators that Java's grammar doesn't extend. Each has a strong ergonomic case (Kotlin's null operators per NULL-02; inline FI types as the only escape from the interface-name explosion per FN-01), but each breaks the "still a `.java` file" promise.

The spec splits the source surface in two rather than dropping either group. `.java` is the Java-compatible subset — exactly the surface the spec described before this section existed — and remains the migration on-ramp and IDE-compatible form. `.lat` is purely additive: any annotated `.java` source is also a valid `.lat` source via the COMP-06 table's reverse substitutions, and the compiler dispatches by extension. The compiler is named `latc`, parallel to `javac` and `rustc`, signalling a separate front-end while leaving compiled artifacts unchanged. The cost is one binary syntactic decision per file; the benefit is that the spec stops quietly assuming both surfaces at once.

---

## Bindings (BIND-01 through BIND-07)

### Why `@mut` is the *single* mutability marker (BIND-02)

`@mut` denotes mutability uniformly across bindings, fields, methods, and parameters. Each position expresses the same underlying idea — "this can change" — so one marker means a reader can grep for `@mut` and find every mutation point in the system. The vocabulary matches Rust's, which is the lower-friction choice for the audience already familiar with the ownership story Laterita brings to Java.

### Why fields default to immutable (BIND-03)

Rust's transitivity insight: immutability is only meaningful if it propagates. If a `var` binding could still mutate the object's fields, "immutable" would be a hopeful suggestion rather than a guarantee. Making fields immutable by default forces an explicit choice for mutation, exactly where Effective Java has been recommending we make that choice for years (favor immutability, favor records over JavaBeans).

### Why methods declare mutation in the signature (BIND-05)

A `@mut`-annotated method answers a question Java developers have always had to answer informally: "does this method modify the receiver?" Today you read the body or hope the documentation is accurate. With `@mut` in the signature, the compiler knows and the caller knows. It also matches Rust's `&self`/`&mut self`, expressed in Java's syntactic vocabulary. By BIND-06 a `@mut` method is only callable on a `@mut` receiver, so the marker is a visibility-like predicate on the caller's API surface, not a description of the body the caller has to reason about.

### Why methods declare consumption of `this` with `@take` on an explicit `this` (BIND-07)

Java's grammar already permits an explicit `this` as the first parameter slot. Laterita reuses that slot: `@take Self this` declares receiver consumption, parallel to `@take T name` on an ordinary parameter (MOVE-03). The mental model — "the `this` slot is a parameter like any other, with the same annotations governing it" — collapses two questions into one. `@mut` and `@bound` on the receiver compose the same way they do on parameters; no per-position rule book.

### Why constructors are a special initialization case (BIND-04)

This is the same accommodation Rust makes for struct initialization and Java already makes for `final` fields. Immutable fields have to be assigned somewhere; the constructor is the only place that makes sense. Generalizing today's `final` to be the default for every field is straightforward.

### Why mutability is transitive (BIND-06)

If a `var` binding could call `@mut` methods, immutability would mean nothing — it would just be a comment. The transitivity rule is what makes "this object is read-only" a real guarantee. It also means handing someone a `var` reference to a complex object graph is genuinely safe — they cannot change anything, anywhere, through it. This is one of the largest correctness wins in the language, and it falls out of getting one rule right.

---

## Mutability (MUT-01, MUT-02)

### Why `Cell<T>` is the only escape hatch (MUT-02)

There are real cases where a class is logically immutable but has internal caching (lazy initialization, memoization, mutex-protected state). Rust's answer is `UnsafeCell<T>`, the one type the compiler treats specially as a hole in the rules. Laterita adopts the same model: a single primitive marks the spot, every other interior-mutability mechanism is built on top of it. Concentrating the unsafe assumption in one place is what makes the rest of the language safely checkable.

---

## Move and Borrow (MOVE-01 through MOVE-10)

### Why default assignment is a borrow (MOVE-01)

Looking at real Java code, the overwhelming common case is "I want to read this, I don't want to take it away from where it lives." Defaulting to a borrow matches that intuition. The user writes ordinary Java-looking code; the compiler infers a borrow; both bindings remain usable. Making move the default would have been the Rust approach, but it would have meant `give(x)` (or some move marker) on essentially every assignment — friction with no payoff.

### Why `give(...)` and `@take` instead of one shared marker (MOVE-02, MOVE-03)

Giving up ownership and taking ownership are different actions, even though they are two ends of the same transfer. Earlier drafts used a single sigil (`^`) in both positions on the theory that "ownership crosses this boundary" was a unifying meaning. That meaning is real but stretched — the sigil reads as "out of this binding" at a use site and as "into this slot" at a parameter, which are inverse roles.

Two English verbs make the asymmetry first-class. `@take T name` on a parameter declares "this slot receives ownership"; `give(binding)` at a use site declares "this source releases ownership." The pairing is symmetric in the sense any sender/receiver pair is symmetric: same operation, two named ends, neither overloaded with the other's job.

We considered keeping ownership entirely at the call site (silent signatures) and rejected it. The function knows whether it needs ownership; that need is part of its public contract. A signature `void store(@take String s, ...)` says "s is consumed" without forcing the caller to read the body. The signature is the API.

The two markers also support different inference at different positions. At a call site, the parameter declaration is canonical, so `give(...)` on the argument is optional — `store(name, list)` is unambiguous when `store`'s signature has `@take String s`. At a binding assignment, both sides are local and we want the marker on the side that *acts*: the source binding releasing ownership. So `var b = give(a)` is the explicit form; `@take String b = give(a)` is allowed for documentation but adds nothing operational; `@take String b = a` (bare RHS) is rejected because the bare RHS is a borrow per MOVE-01 and `@take` cannot infer a move from a borrowing source.

This asymmetry — `give(...)` optional at call sites, mandatory at assignments — is deliberate. The call site has a published contract to lean on; the assignment doesn't. Quiet by default at the boundary that has more information; explicit at the one that doesn't.

Producer/consumer asymmetry runs through the rest of the language as well: bindings default to borrow on the consumer side (MOVE-01), expressions default to owned on the producer side (return values, constructors, computed expressions). `give(...)` and `@take` only appear at genuine ownership transfers, and the surrounding code reads like Java.

### Why borrow exclusivity (MOVE-04)

This is the rule that actually buys safety. It rules out data races at compile time, iterator invalidation at compile time, and all the family of bugs that come from "two pieces of code each thought they had exclusive access to this." It is the load-bearing wall of the entire ownership system.

### Why disjoint field borrows (MOVE-05)

This was a real finding from the verification work. A red-black tree node needs to access `left` and `right` independently while the tree is mutably borrowed. The naïve borrow check rejects this even though it's trivially sound — the two fields don't alias. Without disjoint field borrows, large parts of the standard library become unwriteable in safe code (TreeMap, doubly-linked structures, swap-based algorithms). Rust solved this; Laterita inherits the solution.

### Why disjoint slice borrows with `splitAt` for hard cases (MOVE-06)

Same principle as MOVE-05, applied to arrays. The compiler can prove disjointness for trivial cases (`data.slice(0, 50)` and `data.slice(50, 100)`); for arbitrary index arithmetic, the splitting and chunked-iteration methods on `T[]` and `laterita.lang.Arrays` (ARR-01, ARR-02) supply the disjointness witness. Each reduces to two ordinary slice expressions whose disjointness MOVE-06 already covers — no `@unsafe` context is required. This is the foundation for parallel divide-and-conquer, in-place sort, and any partition-based algorithm.

### Why partial-move tracking (MOVE-07)

Once you have moves out of fields, you need to know which fields are still alive at every point in the function. This is bookkeeping the compiler does silently, and it pays off both in normal control flow (use-after-move detection on partially-moved values) and during exception unwind (DROP-04, EXC-03). Skipping it would mean making `give(...)` on a field illegal, which would make ownership transfer in real code far more painful.

### Why `@take` participates in overload resolution but `@mut` does not (MOVE-09)

Java is an overloading language; Laterita extends overload resolution along the ownership axis. The asymmetry between `@take` and `@mut` is deliberate.

`@take` and borrow are different *operations*. A consuming `put(@take K, @take V)` and a borrowing `put(K, @take V)` are two reasonable APIs on the same Map: the first lets the caller donate the key, the second lets the caller keep it.

`@mut` is not a different operation — it is a *strength* on borrow. Two same-named methods differing only in `@mut` would be saying "I write to this argument" vs. "I don't," which wants different names, not the same name with different strengths. There is also no caller-side disambiguator parallel to `give(...)` for `@take`: a caller has no way to pick between `f(T)` and `f(@mut T)`. No useful distinction, no caller-side opt-in — `@mut` doesn't belong on the overload axis.

Borrow wins as the ownership tie-breaker because moving is observable and shouldn't happen by accident: a caller writing `f(x)` expects `x` usable afterward. The explicit opt-in is `give(x)`, matching MOVE-01's consumer-side default.

The axis order — Java specificity first, ownership only as tie-breaker — keeps ported Java code resolving the way a Java reader expects, and confines the new axis to genuine ties (same-type pairs like `put(K)` vs `put(@take K)`). Putting ownership first would have shielded bare call sites from silent moves at every overload set, at the cost of reordering Java's familiar most-specific-wins rule.

The cost of this choice is asymmetric. Adding a same-type borrow overload to an existing `@take` shifts bare call sites from consume to borrow on the tie-breaker — usually harmless (the argument is dropped a little later), occasionally observable (large buffers, files, threads). Adding a more-specific `@take` overload to an existing borrow shifts bare call sites at the subtype from borrow to consume on Java specificity — the same shape of break Java already has when adding a more-specific overload, just along a new axis. Both cases argue for the same discipline: write `give(...)` at any call site whose ownership semantics are load-bearing, even when only one overload exists today. The opt-in marker is where caller intent gets recorded.

#### Recommendation: `@mut` and `@take` are caller-side work

When a method's body needs `@mut` access to a parameter or needs to consume it, the signature should declare `@mut` or `@take` and the caller does the work of providing it — by holding `@mut`, by surrendering ownership with `give(...)`, or by cloning before the call. A method whose signature is `f(K)` should not internally clone or take a temporary `@mut` lock to do something the signature didn't ask for; if the body needs mut or ownership, the signature should say so.

This is non-normative — methods sometimes legitimately clone internally (defensive copies, caching layers), and the compiler should not police every internal `.clone()`. The default mental model is what we are after: *the cost the caller can read from the signature is the cost the caller bears*. Rust's `HashMap::insert(k: K, v: V)` consumes both arguments and lets callers `.clone()` if they want to keep them; that is the idiom Laterita inherits, expressed in Java's overload-aware vocabulary rather than Rust's overload-free one.

### Why override variance differs for `@take` and `@mut` (MOVE-10)

Once `@take` is part of the signature and `@mut` is not, the override rules fall out automatically.

`@take` is invariant. Structurally it is part of overload identity (MOVE-09), so an override of `f(@take T)` must carry `@take` or else be overriding a different method. The call-site reading reinforces this: an override silently downgrading `@take` to borrow would mean `give(x)` through an interface meant "x might be consumed, depending on which implementation wins dispatch" — exactly the invisibility the annotation exists to eliminate.

`@mut` is contravariant: an override may drop `@mut`, not add it. An interface that promises "I might mutate your argument" may be implemented by a class that doesn't; any caller satisfying the inherited `@mut` requirement automatically satisfies the override's bare one. The reverse is unsound: a `@mut`-demanding override cannot stand behind an inherited bare signature, because callers through the interface would supply only an immutable borrow.

The Java parallel — `throws` relaxes on overrides; parameter types stay invariant — names the pattern: modifier strength relaxes, identity does not. `@mut` is a strength; `@take` is identity.

---

## Lifetimes (LIFE-01 through LIFE-05)

### Why mark-borrow on returns (LIFE-02)

A bare return type means owned; borrowed returns are explicitly declared with `@bound`. The inverse — owned-marked, borrowed-default — would mark the common case: constructors, factories, computed values, query results all return owned values, so marking owned adds visual noise to most signatures. `@bound` carves out the case where production is actually a view into an input.

Body-driven inference (keep the signature silent, let the compiler look through to the implementation) was rejected: it collapses under separate compilation, and it hides the contract from the caller, who would have to read the body to know what the return is good for. The signature is the API.

### Why `@bound` instead of `'a` or `from`

Rust's `'a` notation looks like a syntax error to newcomers and introduces an entirely new sigil class — rejected. The strongest alternative was `from`, but `@bound` describes the relationship the compiler enforces (a lifetime constraint) rather than the data-flow source, and it collapses the receiver case into one token (`@bound T method(...)` — no second word, no awkward `from this` compound).

The known cost is overlap with Java's "upper bound / lower bound" generics terminology; the syntactic positions don't overlap and a reader is unlikely to confuse them after the first encounter.

### Why intersection on multiple bounds (LIFE-03)

When a returned borrow is bound to several sources, its lifetime is the shortest of them. The intuition is the same as Rust's lifetime intersection: the borrow can only be valid while *all* of its sources are valid. The compiler enforces this; the user doesn't reason about it explicitly unless they want a tighter bound, in which case they remove a `@bound` marker.

### Why unmarked sources are an error (LIFE-04)

Earlier drafts treated the receiver as a default contributor — an instance method returning a borrow was implicitly tied to `this`. That made signatures silent in a way that hid information from the caller. The current rule is stricter: a borrow tied to an input the caller can't see in the signature is a compile error, with the diagnostic naming the input the body actually borrows from. The fix is always local to the signature, and the caller-visible contract is always complete.

---

## Cleanup (DROP-01 through DROP-10)

### Why universal `onDrop()`, not opt-in (DROP-01)

Try-with-resources in real Java is the right *mechanism* but the wrong *default* — needing `try ()` around every stateful binding is friction that an ownership-typed language doesn't have to pay. Laterita makes cleanup the default. Every binding gets deterministic cleanup; no syntactic marker required.

### Why the name is `onDrop()` and not `close()`

Java code that ports to Laterita already has `close()` methods on streams, sockets, JDBC connections, and a long tail of resource classes. A language-orchestrated `close()` mixed with the user-defined ones would create ambiguity at every call site: is this a normal method call, a manual scope-exit invocation, or a leftover from try-with-resources thinking? A different name keeps the language hook visually separate from any user `close()` that survives migration.

`onDrop()` reads as event-handler convention — *what runs when this is dropped* — aligning with Rust's `Drop` trait without inheriting Rust's bare-`drop` naming. The `on` prefix matches Java/Kotlin/Android event-handler convention the audience already knows.

### Why not `finalize()`

`finalize()` is being removed from Java, so the parent language is taking it out from under us; almost nothing overrides it today, so reusing it at scope exit would mostly call no-ops; and its semantic association with GC reclamation would confuse every reader who knows Java. Three independently sufficient strikes.

### Why `@internal` for compiler-only methods (DROP-06)

Forbidden-by-convention versus forbidden-by-type-system is a real distinction. An earlier draft used `_dispose()` with a leading-underscore convention; the current design replaces convention with `@internal`, an annotation the compiler enforces, and lets `onDrop()` read like a normal method name.

Reusing the word `internal` accepts a known clash: C# already uses `internal` to mean "assembly-scoped public," which is broader than Laterita's meaning. No other candidate was as natural at first reading — `lifecycle` is narrow, `intrinsic` suggests a compiler-provided body, `hidden` reads informally — and the C# reader recovers the meaning after the first encounter.

`@internal` is reserved for future compiler-orchestrated hooks. It is deliberately *not* a general-purpose access level; ordinary visibility continues to use `public`/`protected`/`private`/package-default. Promoting it would invite misuse — wrapping arbitrary methods to hide them from callers — which is not what the annotation is for.

### Why reverse declaration order (DROP-02)

This is RAII order. If you opened `A` then opened `B` that depends on `A`, you should drop `B` first then `A`. Reverse-declaration order matches the natural dependency order in almost every real case.

### Why drop flags (DROP-04)

Without per-field tracking, partial moves either have to be forbidden (severely limiting the language) or have to leak undefined behavior on cleanup (catastrophic). Drop flags are the proven solution. Rust uses them, the optimization story is well understood (most flags are statically constant and get optimized away), and the runtime cost in code that doesn't actually unwind is approximately zero.

### Why teardown is compiler-orchestrated (DROP-05)

For ordinary methods the user writes `super.foo()` when an override needs the parent's behavior. `onDrop()` is the exception: the user never invokes it (DROP-06), so the super-chain cannot be user-written; and the *order* — own body, then own fields in reverse, then the superclass subobject, recursively — is the only order that keeps every field live while the body that might read it runs. A "remember to chain" rule would be a pure footgun. This puts `onDrop()` on the same footing as the synthesized copy constructor (OBJ-01), which auto-inserts `super(source)`: language-internal recursive backbones the user should not have to participate in.

Under DROP-09 only the leaf `final` class can declare a body, so today every step-1 invocation above the leaf is empty. The full chain is still specified so the ordering is unambiguous and composes unchanged if the `final` restriction is ever loosened.

### Why explicit `onDrop()` calls are forbidden (DROP-06)

Once the compiler emits all drop calls — at scope exits (DROP-01), on partial-move paths (DROP-04), on exception unwind (EXC-02), and through the drop sequence (DROP-05) — there is no remaining use case for user-invoked drop. Allowing it would create a category of bugs (double-drop, mismatched lifetimes, drop-then-use) for no expressive gain.

Forbidding it has two payoffs:

1. **Double-drop can't be expressed.** Lifetime is exclusively scope-bound; the compiler emits exactly one drop per binding. A whole class of double-free-shaped bugs simply doesn't exist in Laterita source.
2. **Type-system enforcement.** With `@internal` as a visibility modifier, the compiler rejects every illegal call site. There is no "did the author mean to call this here?" question.

The cost is no early-cleanup mechanism: a binding lives until its scope ends, period. We considered an opt-in early-cleanup keyword (a `drop x;` form, sugar for "consume `x` and run its `onDrop`") and chose not to specify one. Real cases for early cleanup are rare; structuring scopes — extracting an inner block or a helper function — covers the cases that matter; and adding any escape hatch reintroduces the double-drop surface we just closed. If a future need is convincing, the keyword can be added later without breaking existing code.

### Why `onDrop()` exceptions terminate the body but continue the drop sequence (DROP-07)

The earliest draft made any exception from `onDrop()` abort the program. The motivation was preventing leaks — three concrete concerns drove the abort choice:

- Sibling bindings in the same scope: DROP-02 drops in reverse order, so a throw from the inner binding's `onDrop` would skip the outer's.
- Drop sequence teardown (DROP-05): a throw from the body would skip the rest of the sequence — the class's remaining fields and every superclass subobject — leaking them.
- Enclosing scope unwind (EXC-02): a throwing drop during exception unwind would interrupt the unwind itself.

The current rule resolves all three differently. An exception terminates only the `onDrop()` *body* it escapes; field teardown of the same value, superclass teardown, sibling `onDrop()` invocations, and any in-progress exception unwind all continue. The drop-time exception then propagates at the binding's scope exit, joining the normal exception flow — the same shape Java's `finally` block gives a throw, with multi-throw accumulation following try-with-resources' `addSuppressed` convention.

Two things make the looser rule sound. DROP-10 guarantees `this` does not escape the body, so no external reference to the value can survive into the rest of the drop sequence — the value remains unreachable regardless of whether the body threw. And the compiler-emitted drop sequence is the one paying attention to multi-throw accumulation, not user code: the first thrown exception is the propagating one; later throws attach via `addSuppressed` automatically. User code does not have to reason about suppressed exceptions to write a correct `onDrop()`.

The trade against abort-on-throw is that drop-time exceptions are now a control-flow shape callers can observe — the same shape Java callers already see from `close()` inside try-with-resources. The benefit is that `onDrop()` bodies performing legitimately fallible operations (flush, fsync) can let exceptions reach a handler instead of being silently swallowed or fatally aborted, and the failure paths are testable in ordinary unit tests rather than crashing the test process. Rust chose abort because it had no exception machinery to integrate with; Laterita does, so it can use it.

A residual rule of thumb survives from the abort draft: `onDrop()` should still be best-effort cleanup. Fallible operations whose failure carries semantic meaning belong in an explicit `close()` (THR-05's split) where the caller has a clear handler; letting them escape `onDrop()` is the fallback when no such call site exists, not the primary contract.

### Why `onDrop()` cannot observe moved-out fields (DROP-08)

Partial moves (MOVE-07) and a universal `onDrop()` (DROP-01) collide: after `give(x.left)`, the field is gone, but `x`'s scope exit still has to run `x`'s cleanup. There is exactly one `onDrop()` body per class, and it cannot be specialized per drop site, so if that body reads `left` it would be reading a vacated slot on the partial-move path. Three ways out:

1. **Forbid the partial move when `onDrop()` reads the field.** What DROP-08 picks.
2. **Skip `onDrop()` entirely on a partial-move path, dropping only the survivors.** Rejected: partially moving one field would silently disable the whole object's destructor — a quiet correctness hole exactly where resource handling matters most.
3. **Run `onDrop()` anyway and make a moved-out field read inside it a separate error.** Same observable rule as (1) — the body still can't touch a possibly-moved field — but the diagnostic surfaces at the `onDrop()` definition rather than at the move. We prefer the move site: that's where the programmer made the choice, and the body stays a normal method whose field reads need no special annotation.

Rust resolves the same tension by forbidding *any* move out of a field of a type that implements `Drop` (`E0509`). DROP-08 is the finer-grained version: Rust's prohibition attaches at the *type* level — a `Drop` type has all its fields locked, even those the drop never touches — whereas DROP-08 attaches at the *field* level, locking only fields the `onDrop()` body actually reads. A class without an `onDrop()` declaration (every record, every plain data carrier) reads nothing and stays fully splittable; a class that reads only some of its fields pins only those. The common case keeps Rust-style partial moves with none of Rust's `mem::take`/`Option`/`ManuallyDrop` ceremony. A class that *does* read a field in `onDrop()` pins that field, which is the right trade: if cleanup needs the value, the value has to still be there.

This also explains why `StringBuilder.build()` (BIND-07) — `return give(this.contents);` — is legal: `StringBuilder` declares no `onDrop()`, so `contents` is unpinned. Give `StringBuilder` an `onDrop()` that reads `contents` and the `give(this.contents)` becomes an error, which is the correct signal that the design now needs a different shape (e.g. an explicit `close()` per THR-05's split, or holding the buffer behind a handle that the `build()` path can extract without the husk needing it).

### Why `onDrop()` is confined to `final` classes (DROP-09, resolving OQ-18)

OQ-18 asked what discipline keeps an `onDrop()` body safe once inheritance is in play. The hazard is specific: a base class's `onDrop()` runs *after* the subclass's (reverse-construction order, DROP-05), by which point the subclass's fields are gone — so if the base body virtual-dispatches into a method the subclass overrode to touch those fields, it reads freed storage. C++ hit exactly this and grew a language rule for it: inside `~Base`, `this` "is" a `Base`, so virtual calls resolve to `Base`'s versions, never a derived override (Effective C++ Item 9; SEI CERT OOP50-CPP). That rule is alien to Java, where dispatch is always virtual — importing a static-dispatch mode into the one place a Java reader least expects one is a poor trade.

So instead of changing dispatch, we removed the precondition. Surveying where a destructor or `Drop` is actually *needed* in mature code — C++ `lock_guard`/`unique_lock`/`unique_ptr`/`shared_ptr`/`fstream`, `std::jthread`, Qt's lockers, Boost.ScopeExit; Rust `MutexGuard`/`RwLock*Guard`/`Ref`/`RefMut`, `Box`/`Vec`/`String`/`Rc`/`Arc`, `File`/`TcpStream`, `tempfile::TempDir`, `rusqlite::Connection`, `memmap2::Mmap`, channel endpoints, `tracing` span guards, `scopeguard` — turns up exactly one pattern: a *leaf* type that owns a lock, a refcount, a buffer, an OS/FFI handle, a channel end, or a scope-bound ambient state, and releases it. Not one of them is an extensible base class whose `onDrop()` calls a hook a subclass customizes; that shape essentially does not occur, and C++'s own guidance is to avoid it — "call a nonvirtual private function instead; each class releases its own resources." The cases that *look* like base-class cleanup hooks — buffered-IO flush, `fsync`, the error-returning `TempDir::close()` — are the *fallible* operations both languages deliberately keep out of the destructor, which is THR-05's `close()`/`onDrop()` split already.

DROP-09 encodes that empirical shape: an `onDrop()` body may live only on a `final` class. A `final` class has no subclass, so no override of anything it calls on `this` can exist below it; combined with DROP-06 (no user-invoked `onDrop()`), the down-dispatch hazard is structurally impossible — no static-dispatch mode needed, because there is nothing to dispatch into. The Java idiom of overriding a cleanup hook in an open base class is replaced by composition: an extensible class holds its resources through `final` handle fields (`Rc<T>`, `Arc<T>`, `Thread`, …) whose own `onDrop()`s do the release during field teardown (DROP-05, step 2). Rust's ecosystem is structured the same way — `Drop` lives on leaf newtypes, not on trait-object hierarchies — so the discipline is proven.

With at most one user `onDrop()` body per instance, several candidate rules from OQ-18's earlier options collapse: a static-dispatch mode inside `onDrop()`, a "may call only `private`/`final` methods on `this`" rule, and a separate "no `onDrop()` on interfaces" rule are all unnecessary once the leaf is `final`. DROP-05's full chain stays specified, but every step-1 above the leaf is empty in practice.

The orthogonal cleanup rules — DROP-02 reverse-order, DROP-04/NULL-09 drop flags, DROP-06 `@internal`, DROP-07 throw-continuation, DROP-08 moved-out fields, DROP-10 no-escape-of-this, THR-05 no-blocking — all stand unchanged: DROP-09 closes the inheritance axis, none of the others. The one ripple beyond the cleanup section: `Thread` implements `onDrop()`, so it is `final` (THR-06), and the Java pattern of subclassing `Thread` gives way to passing a `Runnable`/lambda to the constructor.

### Why `this` does not escape `onDrop()` (DROP-10)

The once-per-instance guarantee on `onDrop()` (DROP-09) breaks if the body can smuggle `this` out — to a global, a returned value, a captured collection — because then the value would continue to be reachable after the drop sequence has torn down its fields and freed its storage. Either the smuggled reference points at dropped memory (use-after-free) or a later drop runs over the same instance (double drop). Rust prevents both at once: `Drop::drop` takes `&mut self` (no move out of the receiver possible) and E0509 blocks moves out of fields of a `Drop` type. Laterita's analog is DROP-10.

Most of the work is already done by ordinary borrow-checking: if `this` in `onDrop()` is treated as a borrow bounded by the call, then storing it in a longer-lived location is a lifetime error, just as it would be in any other method. DROP-10 spells out the receiver case explicitly so the question doesn't depend on whether `onDrop()`'s receiver is owned or borrowed in the formal model — `give(this)` is rejected outright, and so is any path that smuggles the receiver into something that outlives the call.

The rule also enables DROP-07's "throw doesn't abort the drop sequence" semantics. Without DROP-10, a thrown exception from the body could leave a smuggled reference to a partially-cleaned-up instance, with no safe way for the runtime to continue. With DROP-10, the field teardown that follows a throw can never be observed by an external reader of `this`, so completing the sequence is sound whether the body returned normally or threw.

---

## Unreachability (UNR-01)

### Why `broken()` for opt-out

A class that can't be copied (a file handle, a single-use resource, anything wrapping `Heap<T>`) needs a way to say so. Three options were on the table:

1. **Throw at runtime.** Override the copy constructor to throw `UnsupportedOperationException`. Java's traditional approach. Fails late, surfaces at the wrong place, and bypasses any compile-time guarantee about which types are copyable.
2. **Opt-in interface (`Cloneable<T>`).** Type-safe but adds bound-noise to every generic signature, and makes the dominant case ("yes I'm copyable") explicit when it should be implicit.
3. **Compile-time opt-out via `broken()`.** A statement that declares the path unreachable; the compiler rejects calls that can reach it. Failure is at the actual problem site (the instantiation that triggers it).

The third option lets the dominant case (copyable) stay implicit while making the rare case (non-copyable) surface as a compile error rather than a runtime throw. Generic signatures stay clean — no `extends Cloneable<T>` bounds — and per-monomorphization checking (COMP-02) localizes the diagnostic.

### Precedent

`broken()` is essentially C++'s `= delete` generalized to a statement position. C++ uses `= delete` for exactly this purpose — declaring a copy constructor (or any function) intentionally unavailable, with calls rejected at compile time. The C++ pattern has worked well for over a decade for the special-member-deletion use case (Rule of Zero/Three/Five).

Adjacent ideas exist in other languages: Rust's `!` (never type) and `unreachable!()` macro for divergence; refinement types in Liquid Haskell, F\*, and Idris for conditional unreachability with static checking. The unconditional form specified here is closest to `= delete` in spirit.

### Why not just throw

A throw is a runtime contract; `broken()` is a compile-time contract. The difference matters because generic code over a type parameter can't tell at definition time whether a particular instantiation will be valid — but with `broken()` and per-monomorphization checking, the *user* of the generic with a non-copyable type sees the error at their call site, not in production. This is the same trade Rust makes with trait bounds, achieved here without the bound boilerplate.

### Why a statement, not a method modifier

C++ uses `= delete` as a definition syntax (`Foo() = delete;`). We considered something equivalent at the method-signature level. A statement-form `broken()` call turned out to compose better: it works for partial bodies (a function that's deleted only on certain paths, expressible as `if (cond) broken(...)`), it places the diagnostic message inline, and it generalizes to any place control flow ends — not just the deleted-method case.

---

## Copying (OBJ-01, OBJ-02)

### Why a copy constructor and a `clone()` method

Ownership rules force the question: when a function needs an owned value but the caller has a borrow, *something* has to produce a duplicate. Without a defined story, every type author would invent their own — `User.copy()`, `Cart.duplicate()`, `Order.snapshot()` — with subtly different contracts. The clean answer is two layered mechanisms:

- **Copy constructor (OBJ-01).** The actual duplication mechanism. Every class has a `protected ClassName(ClassName source)`, auto-generated to recurse through fields. Constructors are already the only context where immutable fields can be initialized (BIND-04), so duplication composes with the rest of the language without special pleading.
- **`clone()` method (OBJ-02).** A public wrapper that calls the copy constructor. This is the API generic and polymorphic code uses. `element.clone()` virtually dispatches to the actual class's `clone()`, which calls that class's copy constructor.

### Why the synthesized copy constructor calls `field.clone()`, not `new FieldType(source.field)`

The recursive step in OBJ-01 is `source.field.clone()`, not `new FieldType(source.field)`. Both would produce the same value when reachable, but the latter is not generally reachable: copy constructors are `protected`, and a class can only invoke another class's protected constructor from within a subclass relationship. A `User` class with an `Rc<Address>` field cannot call `new Rc<Address>(source.address)` — `Rc`'s copy constructor is not accessible from `User`.

`clone()` sidesteps the visibility problem entirely. It's `public` (OBJ-02), uniformly callable from any context, and dispatches virtually so subtype duplication works correctly when a field is held at a supertype. The call chain is `clone() → copy constructor → field.clone() → field copy constructor → ...`, with the public/protected boundary alternating cleanly: every cross-class step goes through `clone()`, every within-class step uses the copy constructor for direct field access.

This also makes opt-out clean: a class that can't be copied overrides `clone()` with a `broken()` body, and the brokenness propagates transparently through any enclosing class's auto-generated copy constructor (which calls the field's `clone()`). No separate "this copy constructor is broken" channel is needed.

We arrived at this two-layer design after walking through several alternatives.

**Universal `Object.clone()`.** Java's model. The clone method lives on Object; classes opt in via `Cloneable`. The API is famously broken — bit-copy semantics, post-construction mutation, `CloneNotSupportedException`. Reimplementing it in Laterita reproduced most of the same complications, since `super.clone()` chaining doesn't fit BIND-04. Rejected.

**Opt-in `Cloneable<T>` interface.** Rust's approach. Generic functions constrain `T extends Cloneable<T>` and types implement the interface. Type-safe but noisy: the bound has to appear on every generic that touches potentially-cloneable values, and makes the dominant case (yes, this is copyable) explicit when it should be implicit. Rejected.

**Copy constructor only, no `clone()`.** We tried this. Generic code would have to write `new T(element)`, which has two problems: the syntax is unfamiliar (Java forbids it because of erasure; even with monomorphization it's an unusual construct), and it constructs the *static* type — slicing any subtype data when `T` is held at a supertype. Rejected for ergonomics and for the polymorphism loss.

The chosen design avoids all three pitfalls. The copy constructor does the real work and stays `protected` so external code can't bypass class-level invariants. `clone()` provides the public, virtually-dispatched API generic code wants. The synthesized `clone()` body is one line — `return new Self(this);` — so there is essentially no implementation surface to get wrong.

### Why no special-casing for `Rc` or `Arc`

Earlier drafts of OBJ-01 named `Rc` and `Arc` explicitly: "primitives bitwise, `Rc`/`Arc` via their refcount-bumping copy constructors, other objects recursively." The list was redundant. `Rc` and `Arc` are just classes whose `clone()` happens to bump a refcount (using `@unsafe` internals per UNS-01). The recursive rule "copy each field via its type's `clone()`" picks them up uniformly. Future ownership wrappers — `Cow<T>`, weak handles, anything else — slot in the same way without amending OBJ-01.

If a field type's `clone()` is `broken()` (as `Heap<T>.clone()` is, per STD-06), the enclosing class's auto-generated copy constructor inherits the brokenness through the call chain. The compile error appears at the actual call site, with a path through the field. Same mechanism that handles direct `broken()`; no separate "synthesis fails" rule needed.

---

## Optionality (NULL-01 through NULL-10)

### Why non-nullable by default

NPE remains the dominant runtime failure in Java codebases despite decades of static-analysis tooling. Laterita has the option Java didn't: make non-nullable the default and require a syntactic marker to opt in to absence. Kotlin (2011) and Swift (2014) made the same call, with well-validated safety gains.

The rule turns NPE from "any reference might fault at any time" into "only `T?` references might be null, and you can't dereference one without proving it isn't." The compiler does the proof.

### Why Kotlin's model rather than Optional<T> or Rust's Option<T>

`Optional<T>` is verbose, doesn't compose with collections (`List<Optional<String>>`), and doesn't help fields whose underlying type is still nullable. Rust's `Option<T>` is sound but presupposes a pattern-matching story Java doesn't have. Kotlin's type-level nullability — `T` and `T?` as distinct types, with flow-sensitive smart casts — preserves Java's surface (`String name`, not `Option<String> name`), needs no pattern matching to be useful, and the `?.`/`?:`/`!!` operators visually localize null-handling decisions. NULL-06 smart-cast narrowing makes "null-check then use" read like ordinary Java.

### Why `onDrop()` is null-aware

NULL-09 specifies that scope-exit `onDrop()` on a `T?` skips `null` and dispatches on the contained value otherwise. This composes with DROP-04's drop-flag machinery — the compiler already had to track per-binding "still owned?" state; "is this `T?` non-null?" is the same shape of conditional cleanup. No new runtime mechanism is introduced.

### Why no separate Optional<T>

With nullable types in the language, `Optional<T>` and `T?` are isomorphic and `T?` is shorter, more idiomatic, and avoids `Optional<Optional<T>>` confusion when generics nest. STD-03's `WeakReference<T>::get()` returns `Rc<T>?` rather than `Optional<Rc<T>>` for this reason.

---

## Exceptions (EXC-01 through EXC-05)

### Why preserve Java's exception syntax (EXC-01)

A Swift-style `try`-at-call-site model with sealed error types and exhaustive pattern matching was considered and dropped. None of it is *forced* by ownership — they're orthogonal improvements with their own costs. Sealed error types need a pattern-matching story Java doesn't have; mandatory call-site `try` is a substantial syntactic change for marginal gain; exhaustive catch makes wide-net handlers more awkward, not less.

The ownership-forced changes to exceptions are minimal: cleanup during unwind (EXC-02), drop-flag participation (EXC-03), and a runtime-implementation question about stack traces (EXC-04). `try`/`catch`/`finally`, the `Throwable` hierarchy, and the `throw` keyword survive unchanged. The one Java-ergonomics fix that does make it into the spec is dropping the checked/unchecked distinction (EXC-05).

### Why no checked exceptions (EXC-05)

Java's checked-exception model has been a three-decade experiment that the field has rejected, and Laterita stops enforcing it. The evidence is unusually one-sided:

- **No mainstream language designed after Java adopted the model.** Hejlsberg's 2003 C# critique — versioning brittleness, the `throws Exception` escape valve, scaling failures across deep stacks — has held up; Kotlin, Scala, and Swift each declined.
- **The Java ecosystem routes around the feature.** Spring wraps `SQLException` into unchecked `DataAccessException` on principle. Hibernate, Jackson, and modern Jakarta EE are unchecked-by-default. Lombok's `@SneakyThrows` exists almost entirely to bypass the mechanism. When the dominant ecosystem in the language is built on escape hatches, the feature isn't paying its rent.
- **Lambdas broke the remaining case.** Java 8's `Function`/`Consumer`/`Supplier` don't declare exceptions, forcing every checked throw in a stream pipeline into a `RuntimeException` wrapper or a `CheckedFunction` shim. Laterita wants closures (CLO-01–06) to be ordinary; carrying a feature that fights closure interop is incoherent.
- **Signature contagion is real.** The argument that made THR-08 unchecked generalizes: every checked exception pollutes signatures along its propagation path until some author writes `throws Exception` to escape, losing the precision the model meant to deliver.

`throws` clauses remain legal as documentation, so existing Java signatures translate without edits. Replacing exceptions with `Result<T, E>` or Swift's call-site `try` would require pattern-matching infrastructure Java doesn't have and a much larger surface change for marginal additional benefit. Drop the checking, keep the syntax.

### Why cleanup runs on unwind (EXC-02)

This is the same problem C++ destructors solve and Rust's drop-on-unwind solves. Without it, exceptions through ownership transfers would leak. The user writes ordinary code; the compiler emits the cleanup along the unwind path.

### Why drop flags participate (EXC-03)

Same reasoning as DROP-04 generalized: the unwinder must consult per-field move state, otherwise partial moves followed by exceptions either leak (no cleanup) or double-free (cleanup on already-moved fields). The flags are already there from DROP-04; the unwind path just consults them.

### Why lazy stack-trace resolution (EXC-04)

In real Java today, `fillInStackTrace` is one of the more expensive things a program does, because the JVM walks the stack and resolves symbols at throw time. In an AOT-compiled language without a JVM, we have the option of decoupling capture from resolution. Capture is cheap (frame-pointer walk, ~100ns); resolution is the expensive part (symbol table lookup), and most exceptions are caught and discarded without anyone reading the trace.

Lazy resolution gives near-zero cost in the common case (throw, catch, recover) and full information in the rare case (throw, log, debug). Rust's `Backtrace` does this; Laterita adopts the same model.

---

## Functional Interfaces (FN-01 through FN-03)

### Why "functional interface" rather than "function type"

Earlier drafts called this construct a "function type," following Scala / Kotlin / TypeScript usage. The Java audience reads "functional interface" more directly: it is the term for "interface with one abstract method" they have used since Java 8, and the construct here is exactly that with the *interface declaration* elided. Calling it a functional interface — anonymous and structural — costs nothing and lands faster than introducing a parallel vocabulary.

The "anonymous" qualifier matters when distinguishing from Java's nominal functional interfaces. Both forms coexist: a `Function<T, R>` declaration is still a functional interface; `(T) -> R` is an anonymous functional interface that has the same shape without the published name. FN-02 keeps them as distinct types; the user picks which one to use based on whether the contract deserves a name.

### Why structural rather than nominal (FN-01)

Earlier drafts planned a small set of nominal closure interfaces, parallel to Java's `Function`, `BiFunction`, `Consumer`, etc. The unresolved question (OQ-05) was only their names. Once parameter modes (`@take`, `@mut`, `@bound`) entered the type system, the count exploded: a binary shape has roughly 3 input modes per parameter, several return-bound configurations, and 3 receiver modes from CLO-01 — order of 100 nominal interfaces per arity, before counting primitive specializations. No naming convention survives that.

The anonymous structural form resolves the explosion at the source. The type *is* the signature: `(take A, mut B) -> R` is a distinct type from `(bound A, B) -> R` not because two interfaces were declared, but because the type expressions differ. The compiler compares structurally, the same way `int[]` and `String[]` are different without a separate interface for each. No stdlib zoo, no naming committee, no question of which canonical interface a value targets.

The cost is one new kind of type expression in the grammar. Laterita's type system is already growing modifier-bearing positions (`@take`, `@mut`, `@bound`), so adding a type form that aggregates those positions is a smaller step than it would be in plain Java.

The trade-off is that source-level `import` of an anonymous functional interface isn't possible — there is no name to import. We accept this. Reusable function-shaped contracts in real code almost always have richer obligations than a SAM expresses (a name, documentation, related methods), and those still want a nominal interface. What the anonymous form fixes is the ergonomic wart of "I just need a callback parameter" requiring a published interface to land in.

OQ-05 is dissolved by this decision: there are no closure-interface names to fix because there are no closure interfaces.

### Why functional interfaces are kept separate from closures (FN vs CLO)

A functional interface is a type-system concept; a closure is a value-construction concept. The type `(int) -> int` exists independent of how its values are produced — a method reference, a lambda, or any other future construction yields the same type. Putting the type-system rules in FN and the value-construction rules in CLO mirrors the conceptual boundary.

The slot-mode rule (CLO-03) and the override/overload variance for FI parameters (CLO-05) live on the closure side because they govern how an FI *value* is held and invoked through a binding — the same axis as capture mode. FN keeps the type syntax, identity, and synthesis; CLO covers everything that depends on the value living inside a binding.

### Why anonymous synthesis (FN-03)

Java's existing lambda implementation strategy is dynamic — `LambdaMetafactory` synthesizes the class at runtime. Laterita removes reflection (COMP-05) and targets AOT compilation, so synthesis is moved fully to the compiler. The class still exists at runtime, just produced statically and not addressable from source code. The user's mental model is "the lambda is the value"; the synthesized class is implementation detail.

---

## Closures (CLO-01 through CLO-06)

### Why three modes, inferred (CLO-01, CLO-02)

Rust's `Fn` / `FnMut` / `FnOnce` distinction, which Rust forces the user to think about because closures need precise typing for trait dispatch. Laterita keeps the three categories — read, mutate, consume — but lets the compiler infer them from the body. Users write a lambda; the compiler does the work.

### Why slot mode controls invocation (CLO-03)

This is not a new rule — it falls directly out of BIND-06 and BIND-07. A function value is just an object with a SAM. The slot holding it is an ordinary binding with one of the standard modifier forms. The receiver-mode transitivity rules already enforce: bare bindings call only bare-receiver methods, `@mut` bindings can call mut, `@take` bindings can call take-receiver methods. We document the consequence in CLO-03 because function values are where readers will look first, but no new mechanism is introduced.

### Why lambdas inhabit functional interfaces (CLO-04)

A lambda literal is one way to construct a value of a functional-interface type. CLO-04 is the bridge from the closure-side rules (capture modes from CLO-01, capture-mode inference from CLO-02) to the type-side rules (FN-01 syntax, CLO-03 slot mode). The capture mode determines the receiver mode of the SAM, the receiver mode determines which slots accept the closure, and the slot's mode is what the user reads from the API signature. Each step is one of the existing pieces; CLO-04 just lines them up.

We considered making lambdas the *only* construction (and so collapsing FN and CLO into one section). Method references are the immediate counter-example: `String::length` produces a functional-interface value with no captures and no lambda body. Future constructions (curried partial applications, function composition results) would also be functional-interface values without being lambdas. Keeping the type-side and value-side rules separate keeps the type-system surface stable as more value-constructions appear.

### Why slot-mode override variance is inverted (CLO-05)

MOVE-10 makes `@mut` contravariant for ordinary parameters: an override may *drop* `@mut`. CLO-05 inverts this for the slot mode of a functional-interface parameter: an override may *add* `@mut`, never drop it. The two rules look opposite but are the same principle expressed in different domains — *an override must continue to accept every value the inherited declaration accepted.*

For an ordinary parameter `mut Buf b`, the modifier names a capability the function reserves over the caller's value. Dropping `@mut` reduces what the function will do with `b`; callers with mutable borrow sources continue to work because mutable degrades to immutable on demand. Contravariance is sound.

For a functional-interface slot `mut (T) -> R fn`, the modifier still names the slot's invocation capability — but that capability *defines what closures may be assigned into the slot* (CLO-04). A `@mut` slot accepts read **and** mutate closures; a bare slot accepts only read. Dropping `@mut` shrinks the admissible-closure set, breaking callers who satisfied the inherited contract with a mutate closure. The safe direction is to add `@mut`, broadening the set.

### Why closures carry capture lifetimes (CLO-06)

A closure that borrows `name` cannot outlive `name`. This is the same lifetime-bounded-by-input principle from LIFE-02, applied to closures. Without it, you could create a closure, the captured variable would die, and calling the closure would deref freed memory. The standard ownership rules force this, but it's worth calling out as a separate requirement because closures are where it bites people.

---

## Strings (STR-01 through STR-08)

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

The signature-level markers introduced for lifetimes (`@bound` per LIFE-02) and parameters (`@take` per MOVE-03) make the public contract explicit: a method's owned-vs-borrowed return is visible to callers, and a `@take` parameter is visible at the call site. What the compiler tracks silently is *intra-method* flow — within a function body the per-binding owned/borrowed state is internal bookkeeping, not part of any public surface.

The dominant ergonomic concern with the one-type choice is "I have a borrow here but the next position needs ownership." In Rust's two-type model the user picks the right conversion (`to_string`, `to_owned`, `String::from`, `clone`). In Laterita that whole pick disappears: `clone()` is universal (OBJ-02), every type carries it unless its body reaches `broken()`, and it always returns an owned value. The diagnostic for any owned/borrowed mismatch is therefore uniform — *"this position needs an owned String; binding is borrowed — try `.clone()`"* — and the fix is one method call. With `clone()` as the universal escape valve, the type system stays out of the way of the dominant case, which is the real argument against the two-type model.

### Why subclasses are owned (STR-05)

A subclass like `Email` carries an invariant ("contains an @ sign"). If `Email` could be a borrow into a mutable buffer, the buffer's owner could break the invariant from underneath. Forcing user-defined subclasses to be owned is the simplest rule that prevents this. The standard library's `String` itself can have borrowed instances because `String` has no invariant beyond "valid UTF-8" that holding a slice could break.

### Why string literals are borrowed, not owned (STR-06)

A literal lives in the program's read-only static segment, not on the heap. Treating it as owned would either lie about ownership (no allocation took place) or force every literal expression to allocate a heap copy — both unacceptable. Borrowing is the honest description: the literal owns itself, every binding onto it is a view. The static lifetime is universal, so a literal flows freely into any borrow context, and the few sites that need owned storage call `.clone()` (STR-02).

This makes the spec's earlier example `String greeting = "hello"` a borrowed binding, which propagates predictably: passing `greeting` to `void inspect(String s)` is fine; passing it to `void store(take String s)` is rejected with the standard "try `.clone()`" diagnostic. There is no special rule for literals beyond "their lifetime is static" — they participate in MOVE-01 and STR-02 like any other borrow.

### Why `String` exposes no mut methods (STR-07)

`mut String` with in-place operations (overwrite, truncate, clear) was considered and rejected. Bulk construction is `StringBuilder`'s job. Secret-zeroing isn't actually solved by `String.clear()` because copies have typically already flowed elsewhere — a dedicated `Secret` type that forbids copy and zeroes on drop is the right answer, outside `String`. The remaining motivation, narrow-domain in-place edits, doesn't justify a mut-method surface that the rest of the design pushes against.

A binding may still be *declared* `@mut String` — `@mut` is general (BIND-02), and rejecting it on `String` would be a special case. The declaration is inert for in-place purposes (no `@mut`-receiver method exists on `String`), but reassignment of a `@mut String` field still works, which is what `StringBuilder`'s `@mut String contents` field relies on.

### Why default receiver mode is borrow (STR-08)

The same Java-feel argument that motivates non-final classes (STR-01) and per-binding tracking (STR-02) applies at the receiver position: `s.toUpperCase().trim()` should not consume `s`, and `int n = s.length()` should not move it. Borrow-by-default also matches how literals enter the type system (STR-06), so the receiver-side default lines up with the value-side default. The surprising case — methods that consume `this` (rare; terminal conversions) — carries an explicit marker, so it's visible at the call site rather than buried in documentation. Mut receivers don't appear at all per STR-07.

### Why `String` needs no splitting machinery

A `bound String` is read-only — STR-07 leaves `String` with no `@mut` methods — so multiple non-overlapping views of the same source are just multiple shared borrows under MOVE-04. No disjointness obligation, no `splitAt`, no `@unsafe`: `String.split`, `Pattern.split`, `String.lines`, `URI` component getters, and `StringTokenizer.nextToken` all implement as repeated `substring` calls (STR-03) into a result array.

Rust's `str::split_at_mut` exists because `&mut str` is a thing the language tracks; Laterita's one-type `String` admits no mutable view, so that primitive has no analog to need. The genuinely different case — two simultaneous `mut T[]` slices for parallel in-place algorithms — is settled by ARR-01 and ARR-02.

---

## Arrays (ARR-01 through ARR-03)

### Why a dedicated section for arrays (resolving OQ-19)

The same parallel that gives `String` its own section (§12) motivates one for `T[]`: the type is built into the language, the operations on it are load-bearing for real code, and the rules deserve to be stated together rather than scattered across MOVE-06 and stdlib commentary. The mut-array splitting question OQ-19 left open is settled here.

Surveying the Rust ecosystem confirmed the primitive is load-bearing. `rayon`'s entire parallel-iterator abstraction is built on `split_at_mut`-style producers; `core::slice::sort` uses it for quicksort partitioning; `image` and the FFT crates use `chunks_mut` pervasively for per-row and per-tile iteration; `bytes` and `tokio-util` reimplement the concept as `split_to`/`split_off` for protocol buffer carving. Several major crates — `ndarray::multi_slice_mut!`, `nalgebra::MatrixViewMut`, `BytesMut`, `hashbrown` — ship their own variants because the standard slice API doesn't fit their layout. A language without a splitting primitive forces every serious library to fall back on `from_raw_parts_mut` plus borrow laundering, exactly the `@unsafe` propagation Laterita is trying to avoid.

### Why two surfaces (ARR-01 and ARR-02)

Laterita accepts both `.lat` and `.java` sources per COMP-06, but the surfaces diverge on two features that affect array APIs: anonymous functional interfaces (FN-01) exist only in `.lat`, and Java has no syntax for adding methods to `T[]`. The `.lat` surface uses both — methods on `T[]` taking `(@bound @mut T[]) -> void` parameters — for the natural in-language reading. The `.java` surface uses static methods on `laterita.lang.Arrays` with named functional interfaces (ARR-03) for migration compatibility: a Java developer can write the same algorithm in `.java`, pass it through the laterita compiler, and get the same borrow-checked behavior the `.lat` version would.

The duplication is regrettable but bounded — four operations on each side, with `ArraySplit<T>` shared between them — and motivated entirely by Java compatibility. Migration tooling per OQ-15 can mechanically translate between the two forms.

### Why the record-return shape (`ArraySplit<T>`)

OQ-19 considered three shapes for the two-way split: continuation-passing (a lambda receiving both halves), record return, and a multi-return language feature. The continuation-passing form is the smallest language addition but forces every call site to introduce a lambda for what is otherwise an ordinary binding step:

```laterita
arr.splitAt(mid, (left, right) -> {
    spawnWorker(give(left));
    processLocally(give(right));
    return null;
});
```

vs.

```laterita
var s = arr.splitAt(mid);
spawnWorker(give(s.left));
processLocally(give(s.right));
```

The record form reads as ordinary Java. The multi-return language feature would add the most surface for the narrowest benefit and was rejected. The record form does require that `@bound` propagates through a record's fields out to its consumers — a small generalization of LIFE-03 already implicit in the existing lifetime rules.

For per-chunk iteration the trade-off goes the other way: the chunk's lifetime is most naturally expressed as "valid for the duration of this callback," which is exactly what a lambda binding provides. `forEachChunk` / `forEachChunkExact` / `reduceChunks` use the continuation-passing form for that reason; the per-call chunk borrow expires at the end of each invocation, giving disjointness across successive yields without any explicit tracking.

### Why `MutableConsumer` is a sibling of `Consumer`, not a subtype

`MutableConsumer<T>::accept(@bound @mut T)` narrows `Consumer<T>::accept(T)`'s parameter requirement: only callers holding `@mut T` access can invoke it. Declaring `MutableConsumer<T> extends Consumer<T>` would let code holding a `Consumer<T>` reference call `accept(bare T)` on what is actually a `MutableConsumer<T>` whose body assumes `@mut` access — a contravariance violation, the same shape MOVE-10 forbids for `@mut`-narrowing overrides on classes.

The reverse direction — `Consumer<T> extends MutableConsumer<T>` — would be sound, because a `Consumer` accepts strictly more than a `MutableConsumer` requires. But `Consumer` is in `java.util.function` and not modifiable. So `MutableConsumer` and `MutableReducer` stand as siblings of the JDK functional interfaces, not subtypes. A lambda literal at a call site selects the appropriate target by the receiving slot's declared type; no shared supertype is needed because the call-site type is always known from the surrounding signature.

### Why no binding modifiers in type arguments

A `List<@mut Foo>`-style annotation — placing `@mut` on a generic type argument rather than on a binding — would let a `@bound List<@mut Foo>` view grant `@mut Foo` element access by propagating the type-argument annotation into the substituted method signatures. The expressiveness is real (worker pools, grids, frames where the container shape is fixed but contents change), and Laterita's binding-mode model could in principle support it where Rust's reference-and-lifetime model cannot. We considered and rejected it.

The hazard is aliasing through shared borrows. Two callers each holding `@bound List<@mut Foo>` would each call `list.get(0)` and both receive a `@mut Foo` to the same slot. Avoiding this requires per-element exclusivity tracking — either statically, via a new piece of borrow-checker analysis that knows shared container views grant mutually-exclusive element borrows, or at runtime, via `RefCell`-style panic-on-double-borrow. Rust avoided the question by tying mutability to references and lifetimes and offering `Cell<T>` (`!Sync`, single-threaded) as the explicit interior-mutability escape hatch. Laterita follows the same discipline.

The rule that falls out: binding modifiers (`@mut`, `@take`, `@bound`) appear only at binding positions — parameters, returns, locals, fields, and the parameter/return slots of anonymous functional interfaces — never on a generic type argument. `MutableConsumer<T>` carries `@bound @mut` on the SAM's parameter declaration, fixed by the interface; instantiating `MutableConsumer<Foo[]>` substitutes only the type `Foo[]` for `T`, not the annotation. This keeps the substitution rule mechanically simple and rules out the parallel-`get(0)` hazard at the language level. Cases that genuinely need shared-container-with-mut-elements continue to use `Cell<T>` (STD-05) explicitly, paying the `@unsafe` cost visibly at the storage site.

### Why `T[]` is the canonical contiguous-mut backing

When a stdlib container needs `@mut` element access through any binding to the container, the cheapest path is a `T[]` field. Java arrays expose slot writes through any reference, so `@bound @mut T[]` permits in-place slot mutation without `Cell<T>` and without `@unsafe` propagation. `ArrayList`, `HashMap`'s bucket arrays, and the rest of the array-backed stdlib fit this pattern naturally. Reaching for `Cell<T>` is required only for non-array layouts — linked-list nodes, tree nodes — where the mut element lives behind an object field rather than an array slot.

### Why the cross-thread story is deferred

The ARR-01 / ARR-02 surface covers any in-place divide-and-conquer, any per-chunk iteration, any in-place fold on a single mut array — provided both halves of every split are processed in the same thread, possibly via a callback. It does not cover the case where the two halves are sent to *different* threads with independent ownership and independent drop. A `@bound @mut T[]` slice is a non-owning borrow tied to some upstream owner; moving it across a thread boundary requires escaping that bound, but the bound is exactly what guarantees the slice cannot outlive its source.

Plain `Arc<T[]>` (STD-02) does not solve this: both `Arc<T[]>` handles share the *whole* allocation, not disjoint ranges, so mut access through them would require per-element `Mutex<T>` and defeat the disjointness story. The right primitive is a refcounted segmented owning slice — each handle carrying `(allocation_ptr, offset, length)` plus a share of the refcount, with the type-system invariant guaranteeing no two live handles overlap. This is the shape of Rust's `BytesMut::split_off` and underpins the zero-copy network buffer crates. It is deferred to OQ-21 because it adds a dedicated stdlib type and a non-trivial disjointness invariant, neither of which the single-thread case needs.

### Why method-level only, not classes or blocks (UNS-01)

`@unsafe` only marks private methods, so the audit boundary is the method signature: a reviewer reads each `private @unsafe` method, verifies its preconditions, and trusts that the public API is safe by composition.

This is *tighter* than Rust. Rust allows `unsafe { }` blocks deep inside public functions, where the audit boundary can be hard to find. Forcing extraction into a named private method makes every unsafe operation in a codebase trivially enumerable (`grep "private @unsafe"`). The compiler inlines the helper back, so the runtime cost is zero; the slight visual heft is arguably a feature — unsafe operations can't be buried inline in a 200-line public method.

### Why a fixed list of operations (UNS-02)

Rust's `@unsafe` unlocks a known finite list of operations (deref raw pointer, call unsafe fn, etc.). Everything else still type-checks normally. This is what makes `@unsafe` audits tractable: you're not asking "is this whole function correct?", you're asking "is this `*ptr` deref valid?"

Laterita does the same. The list of unsafe operations is small, fixed, and documented. Anything else still gets normal compiler checking.

### Why fields of unsafe types force the surrounding rules (UNS-03)

A class that holds a `Heap<T>` field has invariants the compiler can't check (the pointer must be non-null, well-aligned, point to live memory of the right type). Maintaining those invariants requires unsafe context at every method that touches the field. Forcing this propagation prevents the easy mistake of "I'll just hold a Heap<T> and use it from safe methods" — which would be unsound.

### Why standard checks still apply inside `@unsafe` (UNS-04)

`@unsafe` is a small, targeted unlock — not a blanket "anything goes" mode. Inside an unsafe method, the type system still types, the borrow checker still borrow-checks, lifetimes still infer. You only unlock the specific operations in UNS-02. This is what keeps unsafe code reviewable: even unsafe code is mostly checked by the compiler, and the unchecked parts are localized to known constructs.

---

## Standard Library (STD-01 through STD-09)

### Why `Rc` and `Arc` are split

Single-threaded reference counting doesn't need atomic operations; cross-thread sharing does. Splitting them lets single-threaded code skip the synchronization cost in tight loops. Rust does the same.

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

### Why `@local`, not `Send` (STD-07)

Cross-thread move and borrow safety needs to be tracked. Rust uses two positive auto-traits (`Send` and `Sync`); Laterita inverts the marker and uses one negative property: `@local`. The few stdlib primitives that are not safe to cross thread boundaries (`Rc<T>`, `Cell<T>`, `Heap<T>`) are declared `@local`; everything else is non-local by default and may cross threads.

The reason for inverting is Java-target ergonomics. With a positive marker, every user class would have to declare `implements Send` (or be silently inferred via auto-trait machinery) to be usable in concurrent code. With the inverted marker, the *default* for ordinary user classes is "sendable," which is what Java programmers expect. The annotation surface is concentrated in the small set of stdlib primitives plus the rare thread-affine class.

Send and Sync are collapsed into one property because the distinction (Send-but-not-Sync, e.g., Rust's `Cell<T>`) is rare and unusable without the kind of fine-grained borrow reasoning Java programmers don't expect. The single `@local` marker covers both move and borrow restrictions.

Hand-synchronized stdlib types (`Arc<T>`, `Mutex<T>`, `Thread`) override the inferred `@local` property by annotating themselves `@unsafe @nonlocal`. The `@unsafe` annotation is the same admission of proof obligation as every other `@unsafe` in the language: the author is asserting a property the compiler cannot verify, and UNS-04 still applies.

### Why borrow-checked iteration reuses Java's API (STD-08)

The cursor-that-mutates-its-container is one of the canonical patterns where ownership type systems reach for internal `@unsafe`. Generative lifetimes, region typing, and similar academic devices can defeat the issue but at a cost (proliferating type parameters, separate inference machinery) out of proportion for the use case. Rust shipped `Vec::retain`, `Vec::drain`, `extract_if`, and `LinkedList::cursor_mut` — all backed by `@unsafe`. Laterita follows the same shape: a small stdlib API implemented with `private unsafe`, with the audit boundary the four or five method bodies that compose it.

The design choice that took the most thought was whether to introduce a new `Cursor<T>` type or reuse Java's existing `Iterator<T>` and `ListIterator<T>`. The case for a new type was that the borrow-checked semantics are a real change from Java; a new name would warn the reader. The case for reuse won: every method on `ListIterator` already has the right meaning, the loop shapes are identical, and inventing new vocabulary would force every Java reader to learn which iterator class to reach for in which situation. The borrow rules replace `modCount` and `ConcurrentModificationException` underneath, but the API surface above is the one Java developers already use.

The single signature deviation — `Iterator.remove()` and `ListIterator.remove()` returning an owned `T` rather than `void` — is forced by the ownership model. Java's void return reflects an assumption Laterita can't carry: that the caller "still has" the element from the prior `next()` call as a reference into the collection. In Laterita, `next()` returns a `@bound T` borrow tied to the iterator's position, and any mutating call on the iterator invalidates that borrow at the type level. Returning the removed element by ownership transfer is what restores the user's access to the value after the borrow is gone, and it incidentally folds Rust's separate `drain`/`extract_if` API into a one-liner over `remove()`. Statement-form `it.remove();` (ignoring the return) still compiles — the result drops via `onDrop`, matching Java's observable behavior.

`ConcurrentModificationException` doesn't carry over. Its job — detecting "you mutated the collection while iterating" — is exactly what MOVE-04 enforces statically. The runtime category exists in Java because the language can't express the constraint at compile time. Laterita can, so the runtime exception becomes a compile error, and the `modCount` field can leave the standard library entirely.

We considered keeping a separate `Cursor<T>` type for the cursor case anyway, on the theory that "iterator" connotes read-only iteration in many readers' heads. Decided against: `ListIterator` already exists in Java with mutation methods (`remove`, `set`, `add`), so the semantic precedent is there even if many Java developers underuse it. Two iterator types in Java's vocabulary, two iterator types in Laterita's — same names, sharper guarantees.

### Why `Mutex<T>` exposes its protected value through a closure (STD-09)

Java's `Lock` interface separates `lock()` from `unlock()`, leaving room to skip the unlock on an exception path. The defensive `try { lock.lock(); ... } finally { lock.unlock(); }` idiom is a syntactic ceremony for what should be a structural invariant. Three shapes for removing it were considered.

**A returned guard** (Rust `MutexGuard`, C++ `std::lock_guard`) whose `onDrop()` releases the lock. Acquisition returns `bound mut MutexGuard<T>`; scope exit and exception unwind both run `onDrop` (DROP-01, EXC-02), so forgetting to unlock is inexpressible. The trouble is THR-10 poisoning: the guard's `onDrop()` must mark the mutex poisoned only when the lock is being released *because an exception is propagating through the scope*, not on a normal exit. Because `onDrop()` is one method body called on both paths, distinguishing them requires a runtime in-flight-exception indicator — Rust pays for this with `std::thread::panicking()`, a thread-local bit consulted from inside `MutexGuard::drop`. The mechanism works, but it is language-level special machinery (a runtime fact the spec must surface through some hook) in service of one feature on one type.

**`AutoCloseable` plus a compiler must-use-as-resource rule.** Same one-method-two-paths problem inside `close()` (still needs the runtime bit, just relocated), and it puts a `try (...)` wrapper around every lock site — the same ceremony the first shape exists to remove. Net negative.

**A closure-scoped method on the mutex — the chosen shape.** `<R> R with((bound mut T) -> R action)` and `<R> Optional<R> tryWith(...)` acquire the lock, run the closure on the protected value, release the lock, and return the closure's result. Poison detection is an ordinary `try`/`catch` around the closure invocation in stdlib code: the closure either returns or throws, and control flow itself is the signal. No runtime in-flight-exception indicator is required. The protected `T` is reachable only inside the closure, so there is no handle to smuggle, leak, or hold across uncertain control flow.

The trade against the guard shape is ergonomic. The locked region is a closure body, not a `{ }` block: two-mutex critical sections nest (`m1.with(t1 -> m2.with(t2 -> { ... }))`), and outer-function `return` / outer-loop `break` from inside the closure are unavailable. For the short critical sections that dominate real code these costs are invisible, and `with` returning `R` lets values flow out cleanly. In exchange, THR-10 reduces from "the unwind path sets a flag" — a property the language has to surface through some runtime mechanism — to "if the closure throws, `with` poisons before rethrowing," ordinary stdlib code using features every Laterita user already has (generic methods, anonymous functional interfaces per FN-01, `try`/`catch`). The `@unsafe @nonlocal` surface of `Mutex<T>` shrinks accordingly: the lock primitive and `Cell<T>` access still need `@unsafe`, as in any safe-mutex implementation, but the poison-detection layer above no longer does.

The closed-off patterns — passing a guard between methods, holding the lock across complex non-local control flow — were already weakened in Laterita by `@bound` lifetimes. Closing them off completely in exchange for removing language-level poisoning machinery is a net simplification.

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

The rule survives a survey of stdlib types whose cleanup *appears* to need blocking. `Mutex<T>.onDrop()` drops the protected value and releases the raw OS lock primitive — neither blocks. A queue-like primitive (e.g. a Laterita `BlockingQueue<T>`) wakes blocked endpoints with an error in its `onDrop()` — a notification, not a wait. Buffered IO needs `flush()`, but `flush()` belongs in `close()`, which Laterita keeps distinct from `onDrop()` (the same Closeable model Java has): a user who skips `close()` and falls back on `onDrop()` gets an unflushed buffer, just as in Java today. `Thread.onDrop()` does block — but it runs in the *parent's* stack as the cancellation orchestrator (THR-06), not in a body subject to interruption, so THR-05 explicitly exempts it.

So the rule is universal for user-facing `onDrop` bodies, with one privileged exemption (`Thread.onDrop`) that is named in the spec rather than gestured at. No `unsafe onDrop` escape hatch is needed; the integrity guarantee — cleanup completes atomically with respect to interruption — holds without erosion.

### Why no `cancel()`, no `tryJoin()`, no `parallelFirst()` (THR-09, by omission)

The model deliberately keeps the public surface to what Java already exposes plus `onDrop()`. Higher-level orchestration primitives (timeout-aware joining, fork-join helpers, structured task scopes) belong in libraries, not in the language spec. The minimal surface — `start()`, `interrupt()`, `join()`, `isInterrupted()`, plus `onDrop()` and `give(x);` — is sufficient to express every cancellation pattern. Library authors compose those into higher-level primitives as needed.

### Why `synchronized` is removed

Java's `synchronized` keyword is dropped — both the method modifier and the `synchronized(obj) { ... }` block — and so are `Object.wait`/`notify`/`notifyAll`. Mutual exclusion is provided exclusively by `Mutex<T>` (and related stdlib primitives). Four reasons.

**The intrinsic monitor isn't free.** Java's `synchronized` works because every `Object` carries a hidden header word the JVM materializes into a monitor on first contention. In a JVM with a GC and an object header already present for other reasons, the marginal cost is small. In an AOT-compiled language with no GC (COMP-01), giving every allocation an intrinsic-lock slot is a per-object cost paid by code that never locks anything. Concentrating mutual exclusion in a stdlib type means only the objects that need a lock pay for one.

**It doesn't compose with ownership.** `synchronized` locks *beside* data: holding a monitor doesn't restrict what fields the compiler lets you touch, and unsynchronized access to the same fields elsewhere is a normal compile success. `Mutex<T>` is shaped the opposite way — the lock *owns* the data, and the only path to the protected state is through the `with`/`tryWith` closure (STD-09). In an ownership-typed language this is strictly the better primitive: the compiler proves that every access to the protected state happens under the lock, which `synchronized` cannot.

**Reentrancy collides with borrow exclusivity.** Java monitors are reentrant; the same thread can re-enter its own `synchronized` block freely. `Mutex<T>` is not, because re-entering `with` from inside an outstanding `with` closure would mean handing out a second mutable borrow of the protected `T` while the first is still live, which MOVE-04 forbids. Preserving `synchronized` would mean either keeping reentrancy (and carving out a hole in MOVE-04) or silently making `synchronized` non-reentrant (and breaking the compatibility argument that justified keeping the keyword in the first place). Neither is acceptable; dropping the keyword is.

**`wait`/`notify` aren't separable from intrinsic monitors.** They are defined on `Object` and only meaningful while holding that object's monitor. Without intrinsic monitors there is nothing for them to attach to. They are also blocking interruption points (THR-04), which means they would have to participate in the cancellation model, and they would have to not appear in `onDrop` bodies (THR-05). All of that is better expressed by a stdlib condition-variable type sitting next to `Mutex<T>` when the need actually arises.

The migration cost is small: a `synchronized` method becomes a method on a class whose mutable state lives behind a `Mutex<T>` field; a `synchronized(obj)` block becomes a `mutex.with(t -> { ... })` call. The translation is mechanical and the result is more honest about what the lock protects.

### Why mutex poisoning, no bypass (THR-10)

A thread that unwinds while holding a mutex leaves the protected data in an unknown state — possibly mid-write, possibly with broken invariants. Silently releasing the lock and letting the next acquirer proceed (Java's `synchronized` default, `parking_lot::Mutex`'s choice) means the bug in the unwound critical section produces silent wrong behavior in every subsequent acquirer — exactly the bug class hardest to track down. Poisoning makes the integrity hazard observable.

The remaining design choice was whether to provide a bypass for callers who want access regardless of poison state. Rust's `std::sync::Mutex` does (`unwrap_or_else(|p| p.into_inner())`); Laterita does not. The cases that motivated bypasses in Rust mostly turned out not to need poisoning at all — counters, best-effort caches, emergency logging are "I don't care if it's poisoned" cases, where the cleaner answer is no poisoning, not poisoning-with-escape. The cases where the bypass would correctly enable repair (recompute a cached aggregate, rebuild a derived index) typically collapse under reordering: defensive code that mutates locals first and the struct last leaves invariants intact even when intermediate steps panic. The cases where reordering doesn't help — a panicking user callback inside a generic-container's critical section — usually shouldn't be repaired anyway, because the structure itself may be broken.

Removing the bypass also closes a cargo-cult risk. Once `lockPoisoned()` exists, the path of least resistance for "this throws sometimes" is to call the bypass and ignore the issue, which negates the safety signal poisoning was introduced to provide. The pattern aligns with Laterita's broader stance: take Rust's safety guarantees, give them Java's surface, remove escape hatches that don't carry their weight.

If a future need proves real, a more targeted API — a destructive `Mutex<T>.take()` that consumes the mutex, or `Mutex<T>.replace(@take T)` that swaps the protected value on a poisoned mutex — is a smaller and less abusable addition than `lockPoisoned()`. The current spec leaves both unaddressed; either can be added later without breaking existing code.

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
