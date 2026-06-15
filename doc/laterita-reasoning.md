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

## Surface Syntax — annotations and static methods (RESV)

Every ownership, lifetime, mutability, cleanup, and visibility concept Laterita introduces uses existing Java syntax: annotations on declarations, static method calls in expression and statement positions. The language adds no new keywords.

The migration win is concrete. A `.java`-mode laterita source (COMP-06) is still a `.java` file: `javac` parses it, and IDEs that know nothing about laterita still highlight, navigate, refactor, and complete. The laterita compiler is the strict checker on top, attaching semantics to specific annotations and to unqualified calls of specific stdlib static methods. As long as the source stays within the Java-compatible surface (COMP-06), nothing else about it has to change to remain parseable by the Java ecosystem.

The cost is visual heft: `Buf f(@bound @mut Buf b)` reads more loudly than `Buf f(mut bound Buf b)` would have. Annotations are the only modifier slot Java reserves for third parties, so for a language whose primary value proposition is migrating Java code, that compatibility dominates the typographic preference.

Expression-position concepts can't be annotations — `@give x` would not parse — so they live as static methods on `laterita.lang.Intrinsics`. With static import, call sites read `give(x)` and `broken()` unqualified; to `javac` they are ordinary static method calls.

Type inference reuses Java's `var`: a `var x = expr` is reassignable exactly as a Java `var`, and its referent mutability is inherited from `expr` the way ownership is, with `@fix var x = expr` opting out (MUT-02).
No separate keyword for type-inferred locals.

### Two source surfaces: `.lat` and `.java` (COMP-06, LAT)

Five forms — `T?`, `?.`, `?:`, `!!`, and inline FI types `(P1, …, Pn) -> R` — can't ride on annotations or static calls; their natural slots are type expressions and operators that Java's grammar doesn't extend. Each has a strong ergonomic case (Kotlin's null operators, LAT-01 through LAT-04; inline FI types as the only escape from the interface-name explosion, LAT-05), but each breaks the "still a `.java` file" promise.

The source surface is therefore split in two rather than dropping either group. `.java` is the Java-compatible subset, every topic except `LAT`, and is the migration on-ramp and IDE-compatible form. `.lat` is purely additive: it admits the five forms, and any annotated `.java` source is also a valid `.lat` source. The compiler is named `latc`, parallel to `javac` and `rustc`, signalling a separate front-end while leaving compiled artifacts unchanged. The cost is one binary syntactic decision per file.

### Why the `.lat` surface is pure syntactic sugar (LAT-00)

The five `.lat` forms are confined to the `LAT` topic, which carries a hard constraint: every form in it is sugar with an exact desugaring to the Java-compatible surface, and none introduces semantics of its own (LAT-00).
This keeps the two surfaces from drifting into two languages.
A `.lat` source and its desugared `.java` form are the same program.
Migration tooling rewrites one into the other mechanically in either direction.

The constraint also resolves where a future feature belongs.
A proposed addition that is genuinely just notation, a shorter spelling for something the annotation-and-intrinsic surface (RESV) already expresses, goes in the `LAT` topic.
A proposed addition that carries new type, ownership, or runtime behavior cannot be sugar.
It belongs on the Java-compatible surface, expressed through annotations or intrinsics so that the `.java` surface carries it too.
Without this rule, `.lat` would accumulate semantics that `.java` could not express, and the migration promise that the surfaces are interchangeable would quietly fail.

### Why `.lat` drops the diamond on constructor calls (LAT-06)

Java requires the diamond `<>` on a parameterized constructor call because the diamond-less form `new Pair(...)` is the *raw-type* constructor, preserved from before Java 5. The diamond is the syntactic marker that distinguishes "infer the type arguments" from "construct the raw type."

Laterita has no raw types: AOT compilation (COMP-01), monomorphization (COMP-02), and the absence of reflection (COMP-05) leave the escape hatch with no purpose. The diamond therefore carries no information in `.lat` — its absence cannot mean "raw type" because raw types are not a surface form. Requiring it would pay Java's backward-compatibility tax for a constraint Laterita does not share. LAT-06 makes it optional; the `.java` mirror inserts it back, because `.java` must remain `javac`-parseable (COMP-06) and the diamond-less form there is the raw-type constructor.

---

## Ownership (OWN-01 through OWN-21)

### Why methods declare consumption of `this` with `@consuming` (OWN-15)

Receiver mutation and receiver consumption are the two non-bare receiver modes. Both are declared the same way: a modifier-position annotation on the method, reading like `public` or `final`. The two compose (`@mutating @consuming` for a method that does both), and either alone narrows the visible API surface to receivers whose variable mode supports it.

### Why static fields are immutable (STAT-01, STAT-03)

A static slot is reachable from every thread for the program's lifetime; no variable-level borrow check can hand out exclusive access to it. Allowing `@mut static` would either bake in undefined behavior — Rust's `static mut` posture, which the language now actively discourages — or demand a runtime synchronization mechanism the safe surface does not have. Forbidding it outright costs nothing: the cases that genuinely need shared mutable program-wide state already have a clean expression as a `Mutex<T>`, `Arc<T>`, or atomic primitive held in an immutable static, where the synchronization sits inside the wrapper and the static slot itself never changes.

Const-only initialization keeps the AOT story honest. There is no classloader (COMP-01), so no per-class init lock to serialize arbitrary initializers, no observable initialization order to specify across compilation units, and no static-init-order fiasco to inherit from C++. Initializers that genuinely require runtime work go through a once-init wrapper held in the static slot, which serializes its first-access work behind its own internal synchronization. The non-`@local` restriction (STAT-03) plugs the only remaining cross-thread leak: a thread-affine handle in a process-global slot would, by definition, reach every thread.

---

## Mutability (MUT-01 through MUT-11)

### Why `@mut` is the *unified* referent-mutability marker (MUT-01)

`@mut` denotes referent mutability uniformly across locals, fields, parameters, and returns, and the companion `@mutating` (MUT-08) marks a method that mutates its receiver.
Each expresses the same underlying idea: this path can change the value it reaches.
The vocabulary matches Rust's, which is the lower-friction choice for the audience already familiar with the ownership story Laterita brings to Java.

### Why `@fix` is the explicit non-mutability marker (MUT-01b)

`@mut` marks the rarer, more dangerous choice, so most positions stay bare and mean "not mutable" by default: the value-class default of MUT-05, the immutable field of MUT-07a, the borrow parameter of MUT-04.
A single word is still needed for the one place the default runs the other way, where a context would otherwise grant mutability and the author wants it gone.
Three such contexts exist.
A local inherits mutability from its initializer (MUT-02), a class extending a `@mut` class defaults to `@mut` (HIER-02), and a type-parameter usage is assumed `@mut` under the worst-case rule (TARG-03b).
`@fix` is the opt-out in all three, the dual of `@mut` in the way `final` is the opt-out of the reassignable slot.

Spelling the opt-out as one marker rather than three context-specific keywords keeps the surface small and the reading uniform.
Wherever `@mut` may be written, `@fix` may be written to mean its negation.
The goal is to let ordinary code drop `@mut` entirely while still naming immutability where a default would otherwise lose it.
Accepting `@fix` as a redundant no-op where the default is already immutable, rather than rejecting it, matches `final`'s treatment and lets generated or defensive code state the property without the compiler second-guessing whether it was needed.

### Why slot and referent mutability are separate axes (MUT-02, MUT-03)

A binding has two capabilities that Java keeps apart: rebinding the name to another value, the slot, and mutating the value reached through the name, the referent.
Laterita keeps them apart, with one marker each.
`final` locks the slot, exactly its Java meaning, so a Java local that is reassigned keeps working with no annotation and a Java `final` keeps its force.
`@mut` grants referent mutability, the property that actually needs guarding: the right to call a `@mutating` method or write through the binding, and `@fix` (MUT-01b) is its explicit negation where a default would otherwise grant mutation.
Folding both onto a single `@mut` would force an annotation onto every reassigned-but-not-mutated local, which Java writes bare, and would leave the common "mutable object, locked slot" pattern, `private final List<Item> items`, reading as `@mut final` undoing half of what `@mut` granted.
Keeping them separate lines each axis up with the marker that already means it: `final` for the slot, the referent axis for mutation, every combination spellable and none redundant.
The safety property rides on borrows, not on owned locals.
A shared borrow still cannot mutate anything it reaches (MUT-09), so handing out a bare borrow is a genuine read-only guarantee, while an owned local mutating the value it alone holds aliases nothing and is always sound (MUT-02).
Reassigning a slot repoints a name and mutates no object, so it needs no guard, and a never-reassigned slot is treated as effectively final for borrow analysis (MUT-02).

### Why a local's referent mutability is inherited from its initializer (MUT-02)

Ownership of a local already follows its initializer (OWN-02): a producer yields an owner, a name yields a borrow, and no marker is written.
Referent mutability follows the same source.
An owned local of a `@mut` class can mutate the value it alone holds, so making it `@mut` by default aliases nothing and removes the marker from the dominant owned-mutable-local case.
This diverges from Rust, which requires `let mut` on owned locals, and the divergence is deliberate: the language's stated aim is to write less `@mut`, and an owned local mutating its own value has no soundness reason to be annotated.
The read-only guarantee that matters rides on borrows, not on owned locals.
A shared borrow still cannot mutate anything it reaches (MUT-09), so passing a bare borrow is still a real read-only contract, and a mutable borrow into a local still demands the explicit `@mut` the borrow case has always required (OWN-04).
`@fix` recovers an immutable owned local for the cases that want one, locking the referent the way `final` locks the slot, so the two axes read in parallel rather than one inferring its capability and the other defaulting off.

### Why fields default to immutable (MUT-07a, MUT-07b)

Rust's transitivity insight: immutability is only meaningful if it propagates.
If a bare variable could still mutate the object's fields, "immutable" would be a hopeful suggestion rather than a guarantee.
So mutation *through* a field, the referent axis, is opt-in and needs `@mut` on the field, exactly the explicit choice Effective Java has recommended for years (favor immutability, favor records over JavaBeans).
Reassigning a field is the orthogonal slot axis (MUT-07b), reachable only inside a `@mut` class through a `@mut` receiver, so a value class, the default kind, still exposes no field mutation of either sort after construction.

### Why a parameter slot is always final (MUT-04)

A parameter name is a slot the caller filled.
Rebinding it inside the body discards the link to what the caller passed, a move Java permits but style guides routinely forbid.
Laterita locks every parameter slot rather than offer a per-parameter `final` opt-in, because the body has no reason the caller can see to repoint the name: it reads the argument, mutates through it when `@mut`, or moves it onward when `@take`.
Moving a `@take` parameter onward with `give` consumes the value and does not rebind the slot, so the lock takes nothing the ownership model wanted.
The referent axis is untouched: `@mut` on a parameter still lends a mutable borrow, and `@take @mut` still owns and mutates through.

### Why methods declare mutation in the signature (MUT-08)

A receiver-mutating method answers a question Java developers have always had to answer informally: "does this method modify the receiver?"
Today you read the body or hope the documentation is accurate.
With `@mutating` on the method, the compiler knows and the caller knows.
By MUT-10 a `@mutating` method is only callable on a `@mut` receiver, so the marker is a visibility-like predicate on the caller's API surface, not a description of the body the caller has to reason about.

### Why mutability is transitive (MUT-10)

If a shared (non-`@mut`) borrow could call `@mutating` methods, immutability would mean nothing.
The transitivity rule is what makes "this object is read-only" a real guarantee.
It also means handing someone a bare (shared) borrow into a complex object graph is genuinely safe: they cannot change anything, anywhere, through it.
An owned local is the other case, and it is sound to let it mutate the value it alone holds (MUT-02), because no other binding aliases that value.
This is one of the largest correctness wins in the language, and it falls out of getting one rule right.

### Why `Cell<T>` is the only escape hatch (MUT-11)

There are real cases where a class is logically immutable but has internal caching (lazy initialization, memoization, mutex-protected state). Rust's answer is `UnsafeCell<T>`, the one type the compiler treats specially as a hole in the rules. Laterita adopts the same model: a single primitive marks the spot, every other interior-mutability mechanism is built on top of it. Concentrating the unsafe assumption in one place is what makes the rest of the language safely checkable.

### Why classes are marked `@mut` (MUT-05 through HIER-04)

Variable-level `@mut` (MUT-01) answers "can *this variable* change the object?" It does not answer "can the object change *at all*?" A class with a `@mut` field gives no signal at its declaration that it carries mutable state — a reader has to scan its members. Marking the class supplies that signal: `@mut class` declares a mutable surface; an unmarked class is a *value class* whose instances carry no callable `@mutating` method.

This inverts the Valhalla value-class proposal, where the mutable identity class is the unmarked default and `value class` is the opt-in. Laterita's experience runs the other way: most application types (domain values, DTOs, records, configuration) are immutable, and the mutable ones (builders, collections, counters, streams) are the minority. Making the value class the default and `@mut` the opt-in puts the annotation on the rarer, more dangerous choice, and lets a reader classify a type from its declaration line alone. HIER-02 refines how the default is computed once a supertype is in play, but the root case, a class extending only `Object`, keeps the value-class default this argument calls for.

The inheritance rule (HIER-01) keeps the property legible: a `@mut` class extends only `@mut` classes, so every hierarchy is a run of `@mut` classes from `Object` down to a frontier, then value classes below it — the transition happens once and never reverses. A value class extending a `@mut` class is the useful corner: it inherits the mutable API but cannot call it (MUT-10), so `class ImmutableConfig extends Config` derives a frozen variant of a mutable class with no re-declaration — something Java expresses only with a runtime-throwing wrapper.

HIER-04 is what keeps MUT-10's check static. If a value-class instance could be widened into a `@mut` variable of a `@mut` superclass, a `@mutating` method called through that variable would mutate a value the program treats as frozen. Forbidding that one widening — `@mut` access originates only at construction of a `@mut` class, never by widening or cast — guarantees every `@mut` variable refers to a genuinely `@mut` instance, so callability is decided entirely from the static type and the variable mode, with no runtime tag.

`Cell<T>` stays the interior-mutability escape hatch (MUT-11): a value class may hold a `Cell` field and mutate through it. The reference-counted handles depend on this — `Rc<T>` and `Arc<T>` mutate a refcount through `Cell` and so need no `@mut` surface of their own.

Interfaces carry the same `@mut` marker for the same reason a class does: a published interface signals its mutability surface at the declaration. The restriction "only `@mut` interfaces may declare `@mutating` methods" lets MUT-10's static check use one uniform predicate over the receiver's static type (class or interface). A value class implementing a `@mut` interface inherits its `@mutating` methods as a frozen view — the same corner HIER-01 opens for class inheritance.

---

## Class Hierarchy and Override (HIER-01 through HIER-05)

### Why default mutability follows the supertype and interfaces (HIER-02)

The default kind is chosen to need the fewest markers in real hierarchies.
A subclass almost always wants the kind of what it extends.
A subclass of a `@mut` class is overwhelmingly another mutable type, and a subclass of a value class cannot be anything else (HIER-01).
So the supertype's kind is the default, and only the deliberate frozen view, a value subclass of a `@mut` parent (HIER-03), writes `@fix`.

The root case, a class extending only `Object`, cannot take its default from `Object`, which is `@mut` (MUT-05).
Defaulting to `Object` would make every bare class mutable and lose the value-class-by-default property that lets a reader classify a type from its declaration line (reasoning "Why classes are marked `@mut`").
The implemented interfaces are the available signal instead.
A class implementing a `@mut` interface is committing to the mutable surface that interface published, so it defaults to `@mut`.
A class implementing only value-class interfaces, or none, has declared no mutable surface and defaults to a value class.
Either default is overridable, so the rule only removes the marker from the common case rather than forbidding the rare one.

### Why default assignment is a borrow (OWN-02)

Looking at real Java code, the overwhelming common case is "I want to read this, I don't want to take it away from where it lives." Defaulting to a borrow matches that intuition. The user writes ordinary Java-looking code; the compiler infers a borrow; both variables remain usable. Making move the default would have been the Rust approach, but it would have meant `give(x)` (or some move marker) on essentially every assignment — friction with no payoff.

### Why `@take` on parameters and `give` as the move-expression (OWN-13, OWN-07)

`@take` on a parameter is the one published-contract marker for ownership transfer through a method boundary.
`void store(@take String s)` is unambiguous from the caller's view, and the signature is the API.
Callers needn't read the body to learn which arguments are consumed.

`give` is not a separate mechanism.
It is the stdlib helper

```java
public static <T> T give(@take T t) { return t; }   // laterita.lang.Intrinsics
```

normally statically imported.
The expression form of ownership transfer needs *some* surface syntax, and Java syntax admits only method calls — there is no place to write a "consume this expression" operator.
Wrapping `@take` in a generic identity method produces exactly the move expression: `give(a)` consumes `a` via `@take` and returns its owned value.
`var b = give(a)` rebinds the value into a new local.
`give(x);` as a statement leaves the result unbound, dropping it per OWN-07.

Alternatives rejected:

- **A single sigil for both giving and taking** (`^x` at use site, `^T` at parameter). Giving and taking are inverse roles; one symbol straddling them obscures the asymmetry of every transfer.
- **`@take` on local variable declarations** as a documentary marker. A local's ownership is fixed by its RHS (OWN-02), so `@take` adds nothing operational and would dominate the surface with marks the compiler already knows.
- **Implicit ownership transfer with no parameter marker**. The function's need for ownership is part of its contract; callers shouldn't have to read the body to learn which arguments are consumed.

### Why borrow exclusivity (OWN-03)

This is the rule that actually buys safety. It rules out data races at compile time, iterator invalidation at compile time, and all the family of bugs that come from "two pieces of code each thought they had exclusive access to this." It is the load-bearing wall of the entire ownership system.

### Why disjoint field borrows (OWN-04)

A red-black tree node needs to access `left` and `right` independently while the tree is mutably borrowed. The naïve borrow check rejects this even though it's trivially sound — the two fields don't alias. Without disjoint field borrows, large parts of the standard library become unwriteable in safe code (TreeMap, doubly-linked structures, swap-based algorithms). Rust solved this; Laterita inherits the solution.

### Why disjoint slice borrows with `splitAt` for hard cases (OWN-05)

Same principle as OWN-04, applied to arrays. The compiler can prove disjointness for trivial cases (`data.slice(0, 50)` and `data.slice(50, 100)`); for arbitrary index arithmetic, the splitting and chunked-iteration methods on `T[]` and `laterita.lang.Arrays` (ARR-01) supply the disjointness witness. Each reduces to two ordinary slice expressions whose disjointness OWN-05 already covers — no `@unsafe` context is required. This is the foundation for parallel divide-and-conquer, in-place sort, and any partition-based algorithm.

### Why destruction is restricted to direct field access (DES)

Destruction earns its keep in one job: taking a plain object with accessible fields (a POJO) apart into independently owned fields.
That is the case that recurs.
A builder hands out its parts, a `Pair` splits into two owning halves (ARR-04), a state object distributes its resources before it dies.
Everything in destruction's shape follows from confining the feature to that job and rejecting the temptation to let a destructed object keep behaving like a whole one.

Destruction is gated like a consuming move, not like a mutation.
It ends the object's lifetime (DES-02), so any borrow of it still live at that point would outlive its source, which LIFE-01 rejects and OWN-03 already prevents while a mutable borrow holds the owner frozen.
The gate is exclusivity, not mutability.
Consuming an object is not writing through it, so destruction requires ownership but never `@mut`, and a non-`@mut` owned value is taken apart exactly as a `@mut` one is.

Per-field move state is the enabling bookkeeping.
The compiler must know which fields are still alive at every point, both for use-after-move detection and for emitting the right drops on the destruction path and on exception unwind (DROP-04, EXC-03).
For that state to stay decidable, each move names one specific field through a direct field-access path.
When control flow moves different fields on different branches, the per-field drop flags that reconcile conditional ownership at a join (DROP-04) carry the differing move states past it, so a form like `cond ? give(x.a) : give(x.b)` needs no special restriction and is accepted.
The move also has to reach an owned field directly, which is why it reads a field rather than a method result.
A method returns either an owned value it produced or a borrow, never a handle to one of the receiver's tracked fields, and letting a call silently destruct its receiver would hide the move behind a method, which is the invisibility `give` exists to prevent.
This is what keeps a `.java` record off the direct destruction path: its accessor is `@bound`, returning a borrow of the component, not the owned component itself, so there is nothing for `give` to take.
A POJO with accessible fields is the destructable shape.
The `.lat` `give`-of-a-component spelling (LAT-08) does not add a second shape: it desugars to a generated `@consuming` method that moves the record's components into a companion POJO, which is then taken apart by ordinary destruction.
Routing through the companion lets records reuse the one destructable shape and keeps the record a `record` in the `.java` mirror, so LAT-00 holds without exception.
Mirroring a destructed record instead to a `.java` class with public component fields would have made destructability a property of the source surface, namely whether `.lat` code happens to take the record apart.
That is the one thing LAT-00 forbids: a declaration's identity must not depend on its file extension.

Once a field is gone the object is a husk, and the restrictions keep that fact honest.
A method receives the whole receiver, part of which no longer exists, so no method may run on a destructed object.
Only further fields may be moved out, and the husk drops its survivors at block end.
This is the rule Rust enforces, for the same reason: there is no sound `&self` or `self` to hand a method once a field has moved.
Forbidding field assignment, and forbidding return or storage of the husk, close the remaining ways a half-object could escape and be mistaken for a live one.
From its first move to the end of its block, the object exists only to be taken apart.

A `@borrow` field is carried out, not discarded.
It leaves the destructed object as a `@bound` value still tied to its original source (OWN-06, LIFE-03), exactly as moving a reference field out of a Rust struct yields a reference with the same lifetime.
A borrow owns nothing to transfer, so destruction moves the borrow itself rather than converting it to ownership, and the borrowed data is untouched.
Discarding the borrow at destruction instead would be gratuitously stricter than Rust and would throw away a still-valid borrow the caller may want.

This is also where the feature stops colliding with closures.
Field-granular *move* capture, where a closure reaches into a live object and moves one field into its environment as Rust 2021 does (RFC 2229), is not expressible, because a closure captures whole variables rather than declared field paths and a move must name a field path.
The idiom instead is to destruct the object into owned locals first and let the closure capture those locals, which is precisely the pre-2021 Rust pattern.
Field-granular *borrow* capture is unaffected: a Read or Mutate closure (CLO-01) borrowing one field is an ordinary borrow, not a destruction, and stays available whenever the closure does not outlive the source.

### Why ownership annotations are not part of overload identity (OWN-13)

Two same-name methods that differ only in laterita-introduced annotations (`@take`, `@mut`, `@bound` on a parameter; `@mutating`, `@consuming` on the method) are a duplicate declaration. Three reasons reinforce one answer.

*The Java surface forbids it.* COMP-06 requires the Java-compatible surface to parse as annotated `.java` under `javac`, and `javac` ignores annotations when computing the overload signature. A rule that admitted "differs only in `@take`" overloads would be unimplementable in the surface it lives in: `javac` would reject the pair as duplicate methods before any laterita pass saw them.

*There is no caller-side disambiguator.* For `@mut` and the receiver-mode annotations, a caller has no syntactic handle to pick between two same-named candidates — the choice would have to fall on a tie-breaker, and a tie-breaker that silently flips ownership behavior is exactly the invisibility the explicit annotations exist to eliminate. `give(...)` exists for `@take` on parameters, but lifting it into overload resolution makes resolution depend on a call-site marker whose primary job (OWN-07) is to mark a move — overloading the marker overloads the rule.

*Same-name multi-shape is not the load-bearing case.* The shapes that genuinely need to coexist — borrow vs. consume on the same operation, shared-receiver vs. consuming-receiver — are better served by distinct names, because the names then read at the call site. `splitAt` (borrow) and `splitOff` (consume) on arrays (ARR-01) is the canonical pattern; `BytesMut::split_off` in Rust is the same idiom. Rust's `HashMap::insert(k: K, v: V)` consumes both arguments and lets callers `.clone()` if they want to keep them; that is the idiom Laterita inherits, just without Rust's "type signature is the only signature" constraint, since a Java surface that admitted ownership overloads would still need distinct names where the consuming and borrowing variants are both wanted in the same class.

#### Recommendation: `@mut` and `@take` are caller-side work

When a method's body needs `@mut` access to a parameter or needs to consume it, the signature should declare `@mut` or `@take` and the caller does the work of providing it — by holding `@mut`, by surrendering ownership with `give(...)`, or by cloning before the call. A method whose signature is `f(K)` should not internally clone or take a temporary `@mut` lock to do something the signature didn't ask for; if the body needs mut or ownership, the signature should say so.

This is non-normative — methods sometimes legitimately clone internally (defensive copies, caching layers), and the compiler should not police every internal `.clone()`. The default mental model is the goal: *the cost the caller can read from the signature is the cost the caller bears*.

### Why override variance follows one principle across every annotation (HIER-05)

One principle drives every row of HIER-05's table: an override may **demand less** of its callers and **guarantee more** to them, never the reverse — the same shape as Java's `throws` relaxation applied uniformly across every Laterita annotation.

On the demand side (parameters, receiver), an override may drop `@mut` on a parameter (override needs only shared access; callers holding `@mut` still qualify), drop `@bound` on a parameter jointly with the return (override no longer needs a lifetime source from the caller), drop `@mutating` (override no longer needs a `@mut` receiver), or weaken `@consuming` to `@mutating` or bare (override no longer needs ownership of the receiver). Adding any of these tightens the call-site demand — callers with the weaker form the base contract promised to accept would be rejected. `@take` on a parameter is the one exception: it is identity, not strength, and either dropping or adding it breaks callers using the inherited type. Class `@mut` (HIER-01) is the same shape at the class level — a value subclass of a `@mut` parent drops the receiver demand for inherited `@mutating` methods.

On the guarantee side, `@bound` on the return is covariant in strength: an override may return owned where the base promised a value bound to `this` (the per-OWN-18 meaning of `@bound` on a return). Owned is a stronger guarantee — a value the caller may freely move or hold past the receiver's lifetime. Callers using the inherited type continue treating the result as receiver-bound and remain sound. The reverse — returning a receiver-bound value where the base promised owned — would silently constrain a value the caller intended to move or store. The FI-slot call-mode row inverts the surface direction for the same underlying principle: strengthening the slot (bare → `@mutating` → `@consuming`) widens the set of closures it accepts, so every closure the base accepted remains accepted — the annotation governs closure acceptance, not parameter variable.

---

## Lifetimes (LIFE-01 through LIFE-04)

### Why mark-borrow on returns (OWN-16, OWN-17, OWN-18)

A bare return type means owned; borrowed returns are explicitly declared with `@bound`. The inverse — owned-marked, borrowed-default — would mark the common case: constructors, factories, computed values, query results all return owned values, so marking owned adds visual noise to most signatures. `@bound` carves out the case where production is actually a view into an input.

Body-driven inference (keep the signature silent, let the compiler look through to the implementation) was rejected: it collapses under separate compilation, and it hides the contract from the caller, who would have to read the body to know what the return is good for. The signature is the API.

### Why `@bound` instead of `'a` or `from`

Rust's `'a` notation looks like a syntax error to newcomers and introduces an entirely new sigil class — rejected. The strongest alternative was `from`, but `@bound` describes the relationship the compiler enforces (a lifetime constraint) rather than the data-flow source, and it collapses the receiver case into one token (`@bound T method(...)` — no second word, no awkward `from this` compound).

The known cost is overlap with Java's "upper bound / lower bound" generics terminology; the syntactic positions don't overlap and a reader is unlikely to confuse them after the first encounter.

### Why a parameter can store a borrow into `this` (OWN-21)

A generic container already stores borrows: `List<@borrow Foo>.add` monomorphizes to `@take @borrow` and caps the list at each element's source (TARG-04, TARG-05).
A non-generic method storing a borrow into a `@borrow` field would otherwise have no spelling, and a setter like GEN-02's `setBorrowed` would install a borrow with no lifetime account.
Admitting `@take @borrow` in parameter position gives that the direct spelling, and the cap falls out of LIFE-02 and LIFE-03 with no new machinery: the caller-side check is exactly the one TARG-05 already requires, as in Rust, where the signature alone relates the borrow to the receiver.
The cap binds by signature rather than by inspecting the body: narrowing `this` is always sound even if the body never stores the borrow, the caller never depends on the callee's implementation, and storing without the cap stays impossible.
Named lifetimes stay rejected, since the relationship rides the existing `@take` and `@borrow` markers, not a lifetime variable.

### Why intersection on multiple bounds (LIFE-02)

When a returned borrow is bound to several sources, its lifetime is the shortest of them. The intuition is the same as Rust's lifetime intersection: the borrow can only be valid while *all* of its sources are valid. The compiler enforces this; the user doesn't reason about it explicitly unless they want a tighter bound, in which case they remove a `@bound` marker.

### Why unmarked sources are an error (OWN-19)

Treating the receiver as a default contributor — an instance method returning a borrow implicitly tied to `this` — would make signatures silent in a way that hides information from the caller. The rule is therefore stricter: a borrow tied to an input the caller can't see in the signature is a compile error, with the diagnostic naming the input the body actually borrows from. The fix is always local to the signature, and the caller-visible contract is always complete.

### Why `@borrowCapped` rather than always capping at the borrows (LIFE-04)

A `@bound` instance already cannot outlive its borrow sources while it is used (LIFE-03), so cleanup is the only special case.
A class that never reads its borrows at drop leaves its `@borrow` fields untouched, because DROP-05 skips them, so the source is free to die first.
The moment `onDrop()` reads a borrow, the drop becomes a use of that borrow at scope exit, later than any statement the checker can otherwise see.

Two simpler rules are rejected.
Always permitting the read, silently extending every `@borrow` field's required lifetime to scope exit, hides from the reader whether holding a borrow tightens its source's lifetime and pessimizes the common leaf that only releases an owned handle.
Forbidding the read outright makes lock guards, span timers, and scope-bound writers inexpressible, since each must touch the borrowed thing once, at the end.
`@borrowCapped` carries the stronger obligation on the class, where a reader sees it, and only the classes that read borrows at drop pay for it.
DROP-02 then meets it within a scope for free, dropping a borrower before the source it was declared after.

`@borrowCapped` is a lifetime contract on the instance, a separate axis from `final`, which governs only where an `onDrop()` body may live (DROP-09).
Keeping them apart lets an extensible base carry the contract while the `final` subclass that inherits it reads the borrows.

The contract is inherited downward, the only direction sound under upcasting: a `@borrowCapped` instance viewed through a supertype variable must still owe the obligation, so every subtype must owe at least as much.
The obligation therefore rides on the value the way `@bound` does, fixed at construction and carried across assignment and upcast, rather than read off the static class at the drop site.
A subclass may add `@borrowCapped` to a non-capped base, since the value is marked at its own construction.

---

## Cleanup (DROP-01 through DROP-11)

### Why universal `onDrop()`, not opt-in (DROP-01)

Try-with-resources in real Java is the right *mechanism* but the wrong *default* — needing `try ()` around every stateful variable is friction that an ownership-typed language doesn't have to pay. Laterita makes cleanup the default. Every variable gets deterministic cleanup; no syntactic marker required.

### Why the name is `onDrop()` and not `close()`

Java code that ports to Laterita already has `close()` methods on streams, sockets, JDBC connections, and a long tail of resource classes. A language-orchestrated `close()` mixed with the user-defined ones would create ambiguity at every call site: is this a normal method call, a manual scope-exit invocation, or a leftover from try-with-resources thinking? A different name keeps the language hook visually separate from any user `close()` that survives migration.

`onDrop()` reads as event-handler convention — *what runs when this is dropped* — aligning with Rust's `Drop` trait without inheriting Rust's bare-`drop` naming. The `on` prefix matches Java/Kotlin/Android event-handler convention the audience already knows.

### Why not `finalize()`

`finalize()` is being removed from Java, so the parent language is taking it out from under us; almost nothing overrides it today, so reusing it at scope exit would mostly call no-ops; and its semantic association with GC reclamation would confuse every reader who knows Java. Three independently sufficient strikes.

### Why `@internal` for compiler-only methods (DROP-06)

Forbidden-by-convention versus forbidden-by-type-system is a real distinction. A leading-underscore convention (`_dispose()`) is forbidden only by habit; `@internal` replaces convention with an annotation the compiler enforces, and lets `onDrop()` read like a normal method name.

Reusing the word `internal` accepts a known clash: C# already uses `internal` to mean "assembly-scoped public," which is broader than Laterita's meaning. No other candidate was as natural at first reading — `lifecycle` is narrow, `intrinsic` suggests a compiler-provided body, `hidden` reads informally — and the C# reader recovers the meaning after the first encounter.

`@internal` is reserved for future compiler-orchestrated hooks. It is deliberately *not* a general-purpose access level; ordinary visibility continues to use `public`/`protected`/`private`/package-default. Promoting it would invite misuse — wrapping arbitrary methods to hide them from callers — which is not what the annotation is for.

### Why reverse declaration order (DROP-02)

This is RAII order. If you opened `A` then opened `B` that depends on `A`, you should drop `B` first then `A`. Reverse-declaration order matches the natural dependency order in almost every real case.

### Why drop flags (DROP-04)

Without per-field tracking, destruction either has to be forbidden (severely limiting the language) or has to leak undefined behavior on cleanup (catastrophic). Drop flags are the proven solution. Rust uses them, the optimization story is well understood (most flags are statically constant and get optimized away), and the runtime cost in code that doesn't actually unwind is approximately zero.

### Why teardown is compiler-orchestrated (DROP-05)

For ordinary methods the user writes `super.foo()` when an override needs the parent's behavior. `onDrop()` is the exception: the user never invokes it (DROP-06), so the super-chain cannot be user-written; and the *order* — own body, then own fields in reverse, then the superclass subobject, recursively — is the only order that keeps every field live while the body that might read it runs. A "remember to chain" rule would be a pure footgun. This puts `onDrop()` on the same footing as the synthesized copy constructor (OBJ-01), which auto-inserts `super(source)`: language-internal recursive backbones the user should not have to participate in.

Under DROP-09 only the leaf `final` class can declare a body, so today every step-1 invocation above the leaf is empty. The full chain is still specified so the ordering is unambiguous and composes unchanged if the `final` restriction is ever loosened.

### Why explicit `onDrop()` calls are forbidden (DROP-06)

Once the compiler emits all drop calls — at scope exits (DROP-01), on destruction paths (DROP-04), on exception unwind (EXC-02), and through the drop sequence (DROP-05) — there is no remaining use case for user-invoked drop. Allowing it would create a category of bugs (double-drop, mismatched lifetimes, drop-then-use) for no expressive gain.

Forbidding it has two payoffs:

1. **Double-drop can't be expressed.** Lifetime is exclusively scope-bound; the compiler emits exactly one drop per variable. A whole class of double-free-shaped bugs simply doesn't exist in Laterita source.
2. **Type-system enforcement.** With `@internal` as a visibility modifier, the compiler rejects every illegal call site. There is no "did the author mean to call this here?" question.

The cost is no early-cleanup mechanism: a variable lives until its scope ends, period. An opt-in early-cleanup keyword (a `drop x;` form, sugar for "consume `x` and run its `onDrop`") is deliberately not specified. Real cases for early cleanup are rare; structuring scopes — extracting an inner block or a helper function — covers the cases that matter; and any such escape hatch reintroduces the double-drop surface DROP-06 closes. If a future need proves convincing, the keyword can be added later without breaking existing code.

### Why `onDrop()` exceptions terminate the body but continue the drop sequence (DROP-07)

One option is to make any exception from `onDrop()` abort the program. Aborting prevents leaks, and three concrete concerns motivate it:

- Sibling variables in the same scope: DROP-02 drops in reverse order, so a throw from the inner variable's `onDrop` would skip the outer's.
- Drop sequence teardown (DROP-05): a throw from the body would skip the rest of the sequence — the class's remaining fields and every superclass subobject — leaking them.
- Enclosing scope unwind (EXC-02): a throwing drop during exception unwind would interrupt the unwind itself.

DROP-07 resolves all three differently. An exception terminates only the `onDrop()` *body* it escapes; field teardown of the same value, superclass teardown, sibling `onDrop()` invocations, and any in-progress exception unwind all continue. The drop-time exception then propagates at the variable's scope exit, joining the normal exception flow — the same shape Java's `finally` block gives a throw, with multi-throw accumulation following try-with-resources' `addSuppressed` convention.

Two things make the looser rule sound. DROP-10 guarantees `this` does not escape the body, so no external reference to the value can survive into the rest of the drop sequence — the value remains unreachable regardless of whether the body threw. And the compiler-emitted drop sequence is the one paying attention to multi-throw accumulation, not user code: the first thrown exception is the propagating one; later throws attach via `addSuppressed` automatically. User code does not have to reason about suppressed exceptions to write a correct `onDrop()`.

The trade against abort-on-throw is that drop-time exceptions are a control-flow shape callers can observe — the same shape Java callers already see from `close()` inside try-with-resources. The benefit is that `onDrop()` bodies performing legitimately fallible operations (flush, fsync) can let exceptions reach a handler instead of being silently swallowed or fatally aborted, and the failure paths are testable in ordinary unit tests rather than crashing the test process. Rust chose abort because it had no exception machinery to integrate with; Laterita does, so it can use it.

A rule of thumb still holds: `onDrop()` should be best-effort cleanup. Fallible operations whose failure carries semantic meaning belong in an explicit `close()` (THR-05's split) where the caller has a clear handler; letting them escape `onDrop()` is the fallback when no such call site exists, not the primary contract.

### Why a class with `onDrop()` cannot be destructed (DROP-08)

Destruction (OWN-06) and a universal `onDrop()` (DROP-01) collide: after `give(x.left)`, the field is gone, but `x`'s scope exit still has to run `x`'s cleanup.
There is exactly one `onDrop()` body per class, and it cannot be specialized per drop site, so a body that read a moved-out field would be reading a vacated slot on the destruction path.
The rule that resolves this is the one Rust reaches by the same route (`E0509`): a value whose class implements `onDrop()` is moved atomically, so no field may be moved out of it.
Among the candidates this is the only one that keeps the destructor's view of its own fields trivially sound.

The discarded candidates fall two ways.
*Disable cleanup on the destruction path* (skip `onDrop()` entirely once any field is moved, dropping only the survivors) is rejected outright: destructing one field would silently switch off the whole object's destructor, a quiet correctness hole exactly where resource handling matters most.
*Pin only the fields the body reads* (the finer-grained, per-field lock) is rejected as more machinery for less safety.
Its pinned set is a function of the `onDrop()` body's internals and of every method that body transitively calls on `this`, so the set of fields that may be `give`-n out is invisible at the move site and brittle under refactoring: adding one field read inside cleanup silently breaks a `give` in unrelated code.
The analysis cost buys little, because a class that carries an `onDrop()` is a resource-owning leaf (DROP-09) such as a lock guard, a handle, or a refcount, and gutting one of its fields while keeping the husk alive is rarely what the programmer means.
The type-level rule states the contract a reader can see from the class alone: *has `onDrop()` means moved whole*.
The rare case that genuinely wants both cleanup and a surrenderable part pays a single, explicit idiom: extract the part before the husk is built, or hold it behind a handle the cleanup path does not touch.

Diagnosing at the move site (rather than at the `onDrop()` definition) keeps the error where the programmer made the choice, and lets the `onDrop()` body stay an ordinary method whose owned-field reads need no special annotation (borrow-field reads are gated separately by DROP-11).

This also explains why `StringBuilder.build()` (OWN-15), which is `return this.contents;`, is legal: `StringBuilder` declares no `onDrop()`, so it is freely splittable and the move of `this.contents` is fine.
Give `StringBuilder` an `onDrop()` and that move becomes an error, which is the correct signal that the design now needs a different shape (for example, an explicit `close()` per THR-05's split, or holding the buffer behind a handle the `build()` path can extract without the husk needing it).

### Why `onDrop()` is confined to `final` classes (DROP-09)

The question is what discipline keeps an `onDrop()` body safe once inheritance is in play. The hazard is specific: a base class's `onDrop()` runs *after* the subclass's (reverse-construction order, DROP-05), by which point the subclass's fields are gone — so if the base body virtual-dispatches into a method the subclass overrode to touch those fields, it reads freed storage. C++ hit exactly this and grew a language rule for it: inside `~Base`, `this` "is" a `Base`, so virtual calls resolve to `Base`'s versions, never a derived override (Effective C++ Item 9; SEI CERT OOP50-CPP). That rule is alien to Java, where dispatch is always virtual — importing a static-dispatch mode into the one place a Java reader least expects one is a poor trade.

So instead of changing dispatch, we removed the precondition. Surveying where a destructor or `Drop` is actually *needed* in mature code — C++ `lock_guard`/`unique_lock`/`unique_ptr`/`shared_ptr`/`fstream`, `std::jthread`, Qt's lockers, Boost.ScopeExit; Rust `MutexGuard`/`RwLock*Guard`/`Ref`/`RefMut`, `Box`/`Vec`/`String`/`Rc`/`Arc`, `File`/`TcpStream`, `tempfile::TempDir`, `rusqlite::Connection`, `memmap2::Mmap`, channel endpoints, `tracing` span guards, `scopeguard` — turns up exactly one pattern: a *leaf* type that owns a lock, a refcount, a buffer, an OS/FFI handle, a channel end, or a scope-bound ambient state, and releases it. Not one of them is an extensible base class whose `onDrop()` calls a hook a subclass customizes; that shape essentially does not occur, and C++'s own guidance is to avoid it — "call a nonvirtual private function instead; each class releases its own resources." The cases that *look* like base-class cleanup hooks — buffered-IO flush, `fsync`, the error-returning `TempDir::close()` — are the *fallible* operations both languages deliberately keep out of the destructor, which is THR-05's `close()`/`onDrop()` split already.

DROP-09 encodes that empirical shape: an `onDrop()` body may live only on a `final` class. A `final` class has no subclass, so no override of anything it calls on `this` can exist below it; combined with DROP-06 (no user-invoked `onDrop()`), the down-dispatch hazard is structurally impossible — no static-dispatch mode needed, because there is nothing to dispatch into. The Java idiom of overriding a cleanup hook in an open base class is replaced by composition: an extensible class holds its resources through `final` handle fields (`Rc<T>`, `Arc<T>`, `Thread`, …) whose own `onDrop()`s do the release during field teardown (DROP-05, step 2). Rust's ecosystem is structured the same way — `Drop` lives on leaf newtypes, not on trait-object hierarchies — so the discipline is proven.

With at most one user `onDrop()` body per instance, several candidate rules collapse: a static-dispatch mode inside `onDrop()`, a "may call only `private`/`final` methods on `this`" rule, and a separate "no `onDrop()` on interfaces" rule are all unnecessary once the leaf is `final`. DROP-05's full chain stays specified, but every step-1 above the leaf is empty in practice.

The orthogonal cleanup rules (DROP-02 reverse-order, DROP-04/NULL-09 drop flags, DROP-06 `@internal`, DROP-07 throw-continuation, DROP-08 no destruction of `onDrop()` classes, DROP-10 no-escape-of-this, THR-05 no-blocking) all stand unchanged: DROP-09 closes the inheritance axis, none of the others. The one ripple beyond the cleanup section: `Thread` implements `onDrop()`, so it is `final` (THR-06), and the Java pattern of subclassing `Thread` gives way to passing a `Runnable`/lambda to the constructor.

### Why `this` does not escape `onDrop()` (DROP-10)

The once-per-instance guarantee on `onDrop()` (DROP-09) breaks if the body can smuggle `this` out — to a global, a returned value, a captured collection — because then the value would continue to be reachable after the drop sequence has torn down its fields and freed its storage. Either the smuggled variable points at dropped memory (use-after-free) or a later drop runs over the same instance (double drop). Rust prevents both at once: `Drop::drop` takes `&mut self` (no move out of the receiver possible) and E0509 blocks moves out of fields of a `Drop` type. Laterita's analog is DROP-10.

Most of the work is already done by ordinary borrow-checking: if `this` in `onDrop()` is treated as a borrow bounded by the call, then storing it in a longer-lived location is a lifetime error, just as it would be in any other method. DROP-10 spells out the receiver case explicitly so the question doesn't depend on whether `onDrop()`'s receiver is owned or borrowed in the formal model — `give(this)` is rejected outright, and so is any path that smuggles the receiver into something that outlives the call.

The rule also enables DROP-07's "throw doesn't abort the drop sequence" semantics. Without DROP-10, a thrown exception from the body could leave a smuggled reference to a partially-cleaned-up instance, with no safe way for the runtime to continue. With DROP-10, the field teardown that follows a throw can never be observed by an external reader of `this`, so completing the sequence is sound whether the body returned normally or threw.

### Why `onDrop()`'s receiver is mutable (MUT-10)

Cleanup routinely writes: flush a buffer, reset a flag, decrement a count.
Rust's `Drop::drop` takes `&mut self` for exactly this reason, and an `onDrop()` body needs the same capability.

`onDrop()` runs in the teardown phase, the dual of the constructor's initialization phase.
In both phases `this` is uniquely owned, so granting a mutable receiver introduces no aliased mutation: no borrow of the instance is live at drop, which DROP-10 secures by forbidding the receiver from escaping the body.
The receiver is therefore `@mut` regardless of class kind, with no `@mutating` annotation written by the author.

Making destructor mutation opt-in, by requiring `@mutating` on `onDrop()` and a `@mut` class to carry it, is rejected.
It would diverge from Rust, where every drop is mutation-capable, and it would impose ceremony on the most ordinary cleanup bodies.

The value-class boundary is preserved rather than pierced.
The freeze takes effect when the constructor returns and stays in effect through teardown, so a value class's fields remain immutable in `onDrop()` and the body is read-only.
The mutable receiver is thus inert on a value class and supplies the cleanup capability only where mutation is already part of the type's surface.
This mirrors Rust, where a type with a non-trivial destructor cannot be `Copy`: a type that must mutate during cleanup is a `@mut` class, not a value class.

Field-level rules still apply.
The mutable receiver only unlocks what any `@mut` receiver gets (MUT-07b, MUT-10): reassignment of non-`final` fields and mutation through `@mut` fields, so a `final` field is no more writable in `onDrop()` than in a `@mutating` method.
`onDrop()` is an ordinary method body in every respect except that its receiver mode is fixed by the teardown phase rather than declared.

### Why borrow reads in `onDrop()` are gated, and generics count as borrows (DROP-11)

DROP-11 is the field-level face of LIFE-04.
It is the check the compiler performs at the `onDrop()` body, where LIFE-04 is the obligation that check places on every instance's scope exit.
Gating the read at the body rather than at the call site keeps the diagnostic where the programmer wrote the borrow read, and lets the call-site lifetime check stay ordinary.

A field whose type is a bare type parameter is treated as a borrow unless the parameter is `@own`, because an unconstrained `T` can be monomorphized to a `@borrow` argument (TARG-01) and the `onDrop()` body is checked once, against the most permissive substitution.
Reading such a field could therefore read a borrow whose source has already died, the exact hazard `@borrowCapped` exists to prevent.
`@own` (TARG-06) removes that possibility at the type parameter, so an `@own T` field is owned under every substitution and reads at drop like any other owned field.
The conservative direction is the safe one: a spurious "needs `@borrowCapped`" is a visible annotation the author adds, whereas a spurious "owned" would be a use-after-free.

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

C++ uses `= delete` as a definition syntax (`Foo() = delete;`). An equivalent at the method-signature level is the obvious alternative. A statement-form `broken()` call composes better: it works for partial bodies (a function that's deleted only on certain paths, expressible as `if (cond) broken(...)`), it places the diagnostic message inline, and it generalizes to any place control flow ends — not just the deleted-method case.

---

## Copying (OBJ-01, OBJ-02)

### Why a copy constructor and a `clone()` method

Ownership rules force the question: when a function needs an owned value but the caller has a borrow, *something* has to produce a duplicate. Without a defined story, every type author would invent their own — `User.copy()`, `Cart.duplicate()`, `Order.snapshot()` — with subtly different contracts. The clean answer is two layered mechanisms:

- **Copy constructor (OBJ-01).**
  The actual duplication mechanism.
  Every class has a `protected ClassName(ClassName source)`, auto-generated to recurse through fields.
  Constructors are already the only context where `final` fields can be initialized (OWN-11), so duplication composes with the rest of the language without special pleading.
- **`clone()` method (OBJ-02).** A public wrapper that calls the copy constructor. This is the API generic and polymorphic code uses. `element.clone()` virtually dispatches to the actual class's `clone()`, which calls that class's copy constructor.

### Why the synthesized copy constructor calls `field.clone()`, not `new FieldType(source.field)`

The recursive step in OBJ-01 is `source.field.clone()`, not `new FieldType(source.field)`. Both would produce the same value when reachable, but the latter is not generally reachable: copy constructors are `protected`, and a class can only invoke another class's protected constructor from within a subclass relationship. A `User` class with an `Rc<Address>` field cannot call `new Rc<Address>(source.address)` — `Rc`'s copy constructor is not accessible from `User`.

`clone()` sidesteps the visibility problem entirely. It's `public` (OBJ-02), uniformly callable from any context, and dispatches virtually so subtype duplication works correctly when a field is held at a supertype. The call chain is `clone() → copy constructor → field.clone() → field copy constructor → ...`, with the public/protected boundary alternating cleanly: every cross-class step goes through `clone()`, every within-class step uses the copy constructor for direct field access.

This also makes opt-out clean: a class that can't be copied overrides `clone()` with a `broken()` body, and the brokenness propagates transparently through any enclosing class's auto-generated copy constructor (which calls the field's `clone()`). No separate "this copy constructor is broken" channel is needed.

The two-layer design is what remains after ruling out three alternatives.

**Universal `Object.clone()`.** Java's model. The clone method lives on Object; classes opt in via `Cloneable`. The API is famously broken — bit-copy semantics, post-construction mutation, `CloneNotSupportedException`. Reimplementing it in Laterita reproduced most of the same complications, since `super.clone()` chaining doesn't fit OWN-11. Rejected.

**Opt-in `Cloneable<T>` interface.** Rust's approach. Generic functions constrain `T extends Cloneable<T>` and types implement the interface. Type-safe but noisy: the bound has to appear on every generic that touches potentially-cloneable values, and makes the dominant case (yes, this is copyable) explicit when it should be implicit. Rejected.

**Copy constructor only, no `clone()`.** Generic code would then have to write `new T(element)`, which has two problems: the syntax is unfamiliar (Java forbids it because of erasure; even with monomorphization it's an unusual construct), and it constructs the *static* type — slicing any subtype data when `T` is held at a supertype. Rejected for ergonomics and for the polymorphism loss.

The chosen design avoids all three pitfalls. The copy constructor does the real work and stays `protected` so external code can't bypass class-level invariants. `clone()` provides the public, virtually-dispatched API generic code wants. The synthesized `clone()` body is one line — `return new Self(this);` — so there is essentially no implementation surface to get wrong.

### Why no special-casing for `Rc` or `Arc`

Naming `Rc` and `Arc` explicitly in OBJ-01 — "primitives bitwise, `Rc`/`Arc` via their refcount-bumping copy constructors, other objects recursively" — would be redundant. `Rc` and `Arc` are just classes whose `clone()` happens to bump a refcount (using `@unsafe` internals per UNS-01). The recursive rule "copy each field via its type's `clone()`" picks them up uniformly. Future ownership wrappers — `Cow<T>`, weak handles, anything else — slot in the same way without amending OBJ-01.

If a field type's `clone()` is `broken()` (as `Heap<T>.clone()` is, per STD-06), the enclosing class's auto-generated copy constructor inherits the brokenness through the call chain. The compile error appears at the actual call site, with a path through the field. Same mechanism that handles direct `broken()`; no separate "synthesis fails" rule needed.

---

## Optionality (NULL-01 through NULL-10, LAT-01 through LAT-04)

### Why non-nullable by default

NPE remains the dominant runtime failure in Java codebases despite decades of static-analysis tooling. Laterita has the option Java didn't: make non-nullable the default and require a syntactic marker to opt in to absence. Kotlin (2011) and Swift (2014) made the same call, with well-validated safety gains.

The rule turns NPE from "any variable might fault at any time" into "only `T?` variables might be null, and you can't dereference one without proving it isn't." The compiler does the proof.

### Why Kotlin's model rather than Optional<T> or Rust's Option<T>

`Optional<T>` is verbose, doesn't compose with collections (`List<Optional<String>>`), and doesn't help fields whose underlying type is still nullable. Rust's `Option<T>` is sound but presupposes a pattern-matching story Java doesn't have. Kotlin's type-level nullability — `T` and `T?` as distinct types, with flow-sensitive smart casts — preserves Java's surface (`String name`, not `Option<String> name`), needs no pattern matching to be useful, and the `?.`/`?:`/`!!` operators visually localize null-handling decisions. NULL-06 smart-cast narrowing makes "null-check then use" read like ordinary Java.

### Why `onDrop()` is null-aware

NULL-09 specifies that scope-exit `onDrop()` on a `T?` skips `null` and dispatches on the contained value otherwise. This composes with DROP-04's drop-flag machinery — the compiler already had to track per-variable "still owned?" state; "is this `T?` non-null?" is the same shape of conditional cleanup. No new runtime mechanism is introduced.

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

This is the same problem C++ destructors solve and Rust's drop-on-unwind solves. Without it, exceptions through ownership transfers would leak. The user writes ordinary code; the compiler emits the cleanup along the unwind path. EXC-03 generalizes DROP-04 to the unwind path — same drop flags, consulted on the same condition.

### Why lazy stack-trace resolution (EXC-04)

In real Java today, `fillInStackTrace` is one of the more expensive things a program does, because the JVM walks the stack and resolves symbols at throw time. In an AOT-compiled language without a JVM, we have the option of decoupling capture from resolution. Capture is cheap (frame-pointer walk, ~100ns); resolution is the expensive part (symbol table lookup), and most exceptions are caught and discarded without anyone reading the trace.

Lazy resolution gives near-zero cost in the common case (throw, catch, recover) and full information in the rare case (throw, log, debug). Rust's `Backtrace` does this; Laterita adopts the same model.

---

## Functional Interfaces (FN-01)

### Why "functional interface" rather than "function type"

Calling this construct a "function type," following Scala / Kotlin / TypeScript usage, is the obvious alternative. But the Java audience reads "functional interface" more directly: it is the term for "interface with one abstract method" they have used since Java 8, and the construct here is exactly that with the *interface declaration* elided. Calling it a functional interface — anonymous and structural — costs nothing and lands faster than introducing a parallel vocabulary.

The "anonymous" qualifier matters when distinguishing from Java's nominal functional interfaces. Both forms coexist: a `Function<T, R>` declaration is still a functional interface; `(T) -> R` is an anonymous functional interface that has the same shape without the published name. FN-02 keeps them as distinct types; the user picks which one to use based on whether the contract deserves a name.

### Why structural rather than nominal (FN-01)

A small set of nominal closure interfaces, parallel to Java's `Function`, `BiFunction`, `Consumer`, etc., is the obvious starting point — leaving only their names to settle. But once parameter modes (`@take`, `@mut`, `@bound`) enter the type system, the count explodes: a binary shape has roughly 3 input modes per parameter, several return-bound configurations, and 3 receiver modes from CLO-01 — order of 100 nominal interfaces per arity, before counting primitive specializations. No naming convention survives that.

The anonymous structural form resolves the explosion at the source. The type *is* the signature: `(take A, mut B) -> R` is a distinct type from `(bound A, B) -> R` not because two interfaces were declared, but because the type expressions differ. The compiler compares structurally, the same way `int[]` and `String[]` are different without a separate interface for each. No stdlib zoo, no naming committee, no question of which canonical interface a value targets.

The cost is one new kind of type expression in the grammar. Laterita's type system is already growing modifier-bearing positions (`@take`, `@mut`, `@bound`), so adding a type form that aggregates those positions is a smaller step than it would be in plain Java.

The trade-off is that source-level `import` of an anonymous functional interface isn't possible — there is no name to import. We accept this. Reusable function-shaped contracts in real code almost always have richer obligations than a SAM expresses (a name, documentation, related methods), and those still want a nominal interface. What the anonymous form fixes is the ergonomic wart of "I just need a callback parameter" requiring a published interface to land in.

This dissolves the question: there are no closure-interface names to fix because there are no closure interfaces.

### Why anonymous functional interfaces are restricted to positions read at a call site (FN-04)

A type with no name is ergonomic where it is read in the same place it is written. The allowed positions — parameter, return, generic bound, generic type argument — all sit inside a signature or call expression the reader is already looking at: a `(P) -> R` parameter against the signature the caller has open; a return against the producing function (OWN-17 / OWN-18 partial application); a generic bound like `<F extends @mutating (T) -> R>` against the method declaration that requires it; a generic type argument like `Stream<(T) -> R>` against the construction or signature instantiating it. Forbidding the generic-bound case would force every `map` / `filter` / `reduce`-shaped method to declare a nominal `Mapper` / `Predicate` / `Reducer` interface purely for the bound, defeating the purpose of the structural form in the API surface where it matters most; TARG-01 already carries lifetime information through generic type arguments, and the FI case is the same mechanism applied to a function-shaped type.

Field and declared-local positions are excluded for the inverse reason. A field of anonymous-FI type puts an unnameable type on a class's published surface, with no name to anchor documentation or IDE navigation. A declared-local annotation duplicates work `var` inference already does — the RHS produced the FI value, and the local needs only to hold it. The "function-shaped contract that outlives a single call" cases carry richer obligations — a name, documentation, related methods — that a nominal FI exists to hold (FN-03). The restriction governs the written type expression, not value flow: a `var` local may hold an anonymous-FI value by inference where no type is spelled.

### Why the anonymous form spells call mode with `@mutating` / `@consuming` prefix (FN-01)

The SAM of an anonymous FI must carry the same three receiver modes that nominal SAMs do (CLO-03: shared-call / mut-call / once-call), or the anonymous form is restricted to shared-call and every mut-call or once-call callback forces the caller back to a nominal interface — reintroducing the interface-name pressure FN-01 exists to remove. The question is only *how* to spell the mode on a nameless type.

A prefix on the type expression — `@mutating (P) -> R`, `@consuming (P) -> R` — wins over the alternatives. The annotations are not new vocabulary: `@mutating` (MUT-08) and `@consuming` (OWN-15) already declare exactly these receiver modes on regular methods. The anonymous form simply attaches them to the SAM that the type expression denotes, and the desugaring (LAT-05) places them on the synthesized SAM declaration unchanged. No new annotation, no new keyword, and the `.java` mirror — a nominal interface with the same annotation on its SAM — reads identically.

The Rust correspondence is direct: bare `(P) -> R` is `Fn`, `@mutating (P) -> R` is `FnMut`, `@consuming (P) -> R` is `FnOnce`, and CLO-04's containment carries the `Fn ⊆ FnMut ⊆ FnOnce` ordering across both surfaces. Java developers reading the form learn one rule — "the prefix is the SAM's receiver mode" — and the rest follows from receiver-mode rules they already know.

Alternatives evaluated and rejected:

- **A trailing marker on the arrow** (`(P) -mut> R`, `(P) -once> R`). Introduces new arrow forms with no precedent in either Java or Rust; obscures the connection to `@mutating` / `@consuming` on regular methods; doubles the grammar surface for the FI arrow.
- **Encoding mode in the variable annotations** (treat `@mut (P) -> R` as mut-call). Conflates the variable's mutability with the SAM's receiver mode — two genuinely separate axes (CLO-03). The conflation makes owned, multi-call FI variables inexpressible, and forces an inversion of HIER-05's contravariance for the FI slot's `@mut` — an inversion that is an artifact of treating one annotation as two.
- **Inverting the default to once-call** so that `(P) -> R` accepts any closure and stricter modes are opt-in. The inversion sounds appealing for the "I forgot to annotate" worst case, but it pessimizes the dominant case (read callbacks invocable many times), forces an annotation on every `forEach`-shaped API in the language, and breaks the example shape `<A, B, R> @bound (B) -> R partial(...)` in CLO-03 — which borrows a captured closure and calls it repeatedly, only sound when the closure is shared-call. Defaulting to shared-call matches Java's prior expectation and Rust's `Fn` semantics.
- **Restricting the anonymous form to shared-call permanently**, leaving mut-call and once-call to nominal interfaces. This leaves ARR-01's chunk callback (mutate-mode by design) and `Mutex.with`'s critical-section closure (typically mutate-mode) requiring nominal interfaces purely because they cannot be shared-call — reintroducing the interface-name pressure FN-01 exists to remove, just for the call-mode axis.

The prefix is mutually exclusive between `@mutating` and `@consuming` on a single anonymous form: a SAM that is both is rare (a one-shot mutator with no further calls) and stays expressible through a nominal interface — there is no inline spelling for `@mutating @consuming`. The two together do not appear in the surveyed Rust ecosystem either: `FnOnce` already covers "called at most once" without a separate "mutating once" variant.

### Why anonymous synthesis lives in FN (FN-03)

Java's existing lambda implementation strategy is dynamic — `LambdaMetafactory` synthesizes the class at runtime. Laterita removes reflection (COMP-05) and targets AOT compilation, so synthesis is moved fully to the compiler. The class still exists at runtime, just produced statically and not addressable from source code. The user's mental model is "the lambda is the value"; the synthesized class is implementation detail. The rule belongs in FN because synthesis is a property of the anonymous FI type, not of the variable that holds it.

### Why functional-interface values are invoked through the SAM (FN-01)

Java has no call-on-variable syntax: a functional-interface value is an object, invoked through its single abstract method (`f.apply(x)`, `r.run()`, `c.accept(x)`). Laterita keeps this. A `fn(args)` form that calls a variable directly would be a sixth non-Java syntactic surface (the `RESV` topic lists five) bought for no semantic gain, since it desugars to the SAM call anyway, and it works against the "looks and feels like Java" goal. The cost of omitting it is one `.apply` per call site, which Java programmers already expect.

The SAM of an anonymous functional interface is named `apply`, giving the nameless type one fixed, predictable method name, matching `java.util.function.Function`. A fixed name is not optional: LAT-05 desugars the anonymous spelling to a nominal interface, and the `.java` mirror must call a method that exists — without a canonical name there is nothing for either surface to invoke.

---

## Closures (CLO-01 through CLO-06)

Laterita keeps Rust's three capture categories (read / mutate / consume = `Fn` / `FnMut` / `FnOnce`) but infers them from the body rather than asking the user to declare them.

### Why a captured local must be effectively final (CLO-01)

Rust's `FnMut` closures may reassign a captured variable, and the borrow checker would have no problem with the laterita equivalent: the capture would be an exclusive borrow of the slot for the closure's lifetime (OWN-03).
The Java-compatible surface rules the form out anyway: `javac` rejects a lambda that uses a local which is not final or effectively final (JLS 15.27.2), so a reassigning capture cannot be written in a `.java` source, the same bar that keeps annotation-only overloads out of the language (OWN-13).
A `.lat`-only spelling that desugars to a compiler-generated `@mut` holder was considered and rejected: the desugaring would rewrite every later use of the local in the enclosing method, not just the closure body, stretching LAT-00's pure-sugar promise for marginal ergonomics.
Java's idiom already expresses the need directly, and laterita checks it: capture a `@mut` holder and mutate through it, the referent axis doing what the slot axis cannot.
Adopting Java's capture rule keeps every closure form expressible on both surfaces and costs only what Java already costs.

### Why call mode and variable mode are separate (CLO-03)

A functional-interface value is an object with one method, so two questions arise for it as for any object: what does invoking the method require of the caller, and how is the object itself held? The first is the *call mode* — and it is nothing more than the SAM's receiver mode, so it reuses `@mutating` (MUT-08) and `@consuming` (OWN-15) with no new vocabulary. The second is the *variable mode* — ordinary ownership and `@mut`, identical to every other variable.

Fusing the two into a single three-valued slot mode on the variable was rejected: it made one everyday shape inexpressible — an object that *owns* a callback and invokes it many times. Ownership and the bound on a repeatedly-invocable closure were forced onto the same `@take` / `@mut` token, so "owned, multi-call" had no spelling. Separating the axes dissolves the problem: the call-mode bound lives on the interface type, the way a Rust `where F: FnMut` bound does, while ownership stays the field's ordinary default. Invocation then falls straight out of MUT-10 / OWN-15 receiver transitivity with the functional-interface value as the receiver, and no new mechanism is introduced.

### Why lambdas inhabit functional interfaces (CLO-04)

A lambda literal is one way to construct a value of a functional-interface type. CLO-04 is the bridge from the closure-side rules (capture modes from CLO-01, capture-mode inference from CLO-02) to the functional-interface type (FN-01, CLO-03). The capture mode fixes the SAM's receiver mode, the receiver mode is the type's call mode, and the call mode determines which interface types the closure is a value of. Each step is one of the existing pieces; CLO-04 just lines them up.

Making lambdas the *only* construction (collapsing FN and CLO into one section) is rejected. Method variables are the immediate counter-example: `String::length` produces a functional-interface value with no captures and no lambda body. Future constructions (curried partial applications, function composition results) would also be functional-interface values without being lambdas. Keeping the type-side rules (FN) and value-side rules (CLO) separate keeps the type-system surface stable as more value-constructions appear.

### Why call-mode override variance is covariant in strength (CLO-05)

HIER-05 makes `@mut` contravariant for ordinary parameters: an override may *drop* `@mut` because the override demands less of the caller. CLO-05 carries the same principle to the call-mode axis of an FI slot, but in the *opposite syntactic direction*: an override may *strengthen* the call mode (bare → `@mutating` → `@consuming`), never weaken it. Both rules are the same principle — *an override must continue to accept every value the inherited declaration accepted* — applied to two different axes.

For an ordinary parameter `@mut Buf b`, `@mut` names a capability the function reserves over the caller's value. Dropping it reduces what the function will do; callers continue to work because a mutable borrow degrades to immutable on demand. Contravariance is sound.

For an FI slot, the call-mode prefix on the type determines which closures fit (CLO-04). A shared-call slot accepts read closures only; a mut-call slot adds mutate; a once-call slot adds consume. *Strengthening* the slot's call mode broadens the admissible-closure set; weakening it would reject closures the inherited contract said it would accept. So overrides may strengthen the call mode, never weaken it.

The two axes split cleanly: the call-mode prefix on the FI type (FN-01) carries acceptance-set semantics, and the variable-mode annotations on the parameter (`@mut`, `@take`, `@bound`) carry HIER-05 semantics — `@mut` contravariant, `@take` invariant. Acceptance-set widening lives where it belongs (on the type expression that determines acceptance), HIER-05's direction lives where it belongs (on the parameter annotation), and each axis follows the rule appropriate to its meaning with no inversion to memorize.

### Why closures carry capture lifetimes (CLO-06)

A closure is a struct (FN-03): its captures are fields, a by-borrow capture is a `@borrow` field, and a by-move capture is an owned field.
Capture lifetimes are therefore not a new principle but LIFE-03 applied to that struct: any `@borrow` capture makes the closure a `@bound` value bound to the intersection of its captured sources, while owned captures contribute nothing.
A closure that borrows `name` cannot outlive `name`, the same reason an instance with a `@borrow` field cannot outlive its source.
The relational form (OWN-17) is needed only when the closure escapes through a return and binds to captured parameters, as in `partial`.
In-scope captures need no annotation, and mixed captures bind only by their borrowed parts, which the field model gives directly and a single OWN-17 framing would miss.

---

### Why operators are bounded sugar (LAT-07)

C++ unbounded overloading and Rust trait-based overloading are both rejected. The former produces unreadable code (`<<` for stream insertion, expression templates). The latter requires the `Add`/`AddAssign`/`Add<&T>` matrix plus coherence rules, which is more machinery than a Java-shaped language wants. Bounded sugar as in Kotlin, Python, Swift, and C# avoids both: a fixed set of operators desugaring to methods via ordinary overload resolution. None of those languages collapsed into C++ chaos from it.

Deriving operator sugar from a fixed set of method names is rejected because Java stdlib arithmetic method names are inconsistent (`BigDecimal.add`, `Instant.plus`, `Duration.negated`) and some should not be usable with an operator at all (`Collection.add`). An `Arithmetic<T>` interface is rejected because not all arithmetic operations make sense for a given type: `Instant + Duration` is valid, `Instant * Duration` is not. Splitting `Arithmetic` into smaller interfaces (e.g. `Add<T>`) would require some stdlib classes to add duplicate methods (`Instant.add`), and would force laterita to support implementing the same interface with different type arguments (`Instant implements Subtract<Instant,Duration>, Subtract<Duration,Instant>`). Per-method `@Operator(op)` carries its own signature and has none of these limits. Comparison uses `Comparable` with no annotation because `compareTo` has exactly one ecosystem meaning and is already widely used.

Resolution is left-operand-directed with unboxing as the primitive fallback and no other implicit conversion, so `value + literal` and `literal + value` either both succeed (both operands unbox to the same primitive type) or both fail (type error on both orderings). A reflected form would buy symmetry at the cost of implicit conversions Laterita avoids everywhere else.

---

## Code Generation Annotations (GEN)

### Why adopt the whole stable Lombok surface natively (GEN-*)

Lombok exists because Java's boilerplate burden is high enough that the ecosystem delegates codegen to a third-party tool, so its annotation names are the closest thing Java has to a standard codegen vocabulary. Laterita supports the whole stable set natively and unchanged, so a Java-plus-Lombok source migrates without edits and keeps the same observable result. Supporting every annotation, even the ones a `record` or value class already covers, is the deliberate choice: a migration that silently drops `@Data` or rejects `@Synchronized` is a migration that fails, and the cost of accepting a redundant generator is near zero.

The model absorbs the apparent conflicts rather than rejecting them, because a Lombok annotation may add the laterita annotation it implies. A generated setter mutates, so a class-level `@Setter` makes the class `@mut` and a Java mutable bean keeps its meaning with no source edit. A field-level `@Setter` needs an already-`@mut` class, since a lone mutable field cannot live in a value class. `@Value` lands on a value class and `@Data` on a `@mut` class, so Laterita's immutability default and Java's mutability default meet in the middle without a keyword. `@NonNull` is already the default and is accepted as a no-op rather than a duplicate spelling. `@Synchronized` reproduces its private-lock semantics through `ReentrantLock` (STD-10) instead of the absent keyword, which is what Lombok itself does on the JVM, only with an explicit lock object. `@SneakyThrows` is a no-op while EXC-05 keeps every exception unchecked, and regains its purpose if OQ-22 restores the checked distinction.

`@Delegate` is the one experimental annotation kept, because it is the keystone of the newtype idiom and of composition-over-inheritance generally. Lombok flags it as permanently experimental for reasons that do not carry over. Its generics handling is erasure-bound and version-fragile, but laterita monomorphizes (COMP-02), so a forwarded generic method has a concrete signature and the attribute restrictions disappear. Lombok also cannot let you implement some methods and delegate the rest, but laterita's shadowing rule gives exactly that for free. The pitfalls that keep `@Delegate` experimental in Java are artifacts of the JVM, so Laterita keeps the core (the forwarded methods appear on the owner and behave like the original) and drops the caveats.

### How Laterita supports the newtype idiom

The newtype pattern decomposes into three independent rules that each have value elsewhere: NABI-01 (single-field layout, needed for FFI regardless), GEN-01 (`@Delegate` codegen, useful for any composition-over-inheritance wrapper), and COMP-08 (inlining, a general optimization). A dedicated `@newtype` annotation would only restate that `@Delegate` is present. A named unifying rule would bundle three orthogonal concepts into one composite. The composition is the design.

---

## Strings (STR-02 through STR-08)

### Why owned vs. borrowed strings tracked per-variable (STR-02 through STR-04)

Real Java pretends `String` is one thing. It isn't — sometimes it's an independent allocation (`toUpperCase` result), sometimes it's a view (`substring` result, where reallocating the source is illegal while the view exists). Rust splits these into `String` and `&str`, two visibly different types.

They are kept as one type at the source level, with the compiler tracking per-variable whether the string is owned or borrowed. This preserves Java's "everything is just a variable" feel — the user writes `String name` either way. The complexity moves into the compiler. The cost is internal complexity; the gain is that Java's surface syntax is preserved.

The signature-level markers introduced for lifetimes (`@bound` per OWN-17 / OWN-18) and parameters (`@take` per OWN-13) make the public contract explicit: a method's owned-vs-borrowed return is visible to callers, and a `@take` parameter is visible at the call site. What the compiler tracks silently is *intra-method* flow — within a function body the per-variable owned/borrowed state is internal bookkeeping, not part of any public surface.

The dominant ergonomic concern with the one-type choice is "I have a borrow here but the next position needs ownership." In Rust's two-type model the user picks the right conversion (`to_string`, `to_owned`, `String::from`, `clone`). In Laterita that whole pick disappears: `clone()` is universal (OBJ-02), every type carries it unless its body reaches `broken()`, and it always returns an owned value. The diagnostic for any owned/borrowed mismatch is therefore uniform — *"this position needs an owned String; variable is borrowed — try `.clone()`"* — and the fix is one method call. With `clone()` as the universal escape valve, the type system stays out of the way of the dominant case, which is the real argument against the two-type model.

### Why string literals are borrowed, not owned (STR-06)

A literal lives in the program's read-only static segment, not on the heap. Treating it as owned would either lie about ownership (no allocation took place) or force every literal expression to allocate a heap copy — both unacceptable. Borrowing is the honest description: the literal owns itself, every variable onto it is a view. The static lifetime is universal, so a literal flows freely into any borrow context, and the few sites that need owned storage call `.clone()` (STR-02).

This makes the spec's earlier example `String greeting = "hello"` a borrowed variable, which propagates predictably: passing `greeting` to `void inspect(String s)` is fine; passing it to `void store(take String s)` is rejected with the standard "try `.clone()`" diagnostic. There is no special rule for literals beyond "their lifetime is static" — they participate in OWN-02 and STR-02 like any other borrow.

### Why `String` is a value class (STR-07)

`mut String` with in-place operations (overwrite, truncate, clear) was considered and rejected. Bulk construction is `StringBuilder`'s job. Secret-zeroing isn't actually solved by `String.clear()` because copies have typically already flowed elsewhere — a dedicated `Secret` type that forbids copy and zeroes on drop is the right answer, outside `String`. The remaining motivation, narrow-domain in-place edits, doesn't justify a mut-method surface that the rest of the design pushes against.

A variable or field may still be *declared* `@mut String`, since `@mut` is general (MUT-01) and rejecting it on one type would be a special case, but it grants nothing.
`String` has no `@mutating` method, and reassignment comes from the non-`final` slot (MUT-02, MUT-07b), not from `@mut`.

### Why default receiver mode is borrow (STR-08)

The same Java-feel argument that motivates per-variable tracking (STR-02) applies at the receiver position: `s.toUpperCase().trim()` should not consume `s`, and `int n = s.length()` should not move it. Borrow-by-default also matches how literals enter the type system (STR-06), so the receiver-side default lines up with the value-side default. The surprising case, a method that consumes `this` (rare, terminal conversions), carries an explicit marker, so it's visible at the call site rather than buried in documentation. Mut receivers don't appear at all per STR-07.

### Why `String` needs no splitting machinery

A `bound String` is read-only — STR-07 leaves `String` with no `@mutating` methods — so multiple non-overlapping views of the same source are just multiple shared borrows under OWN-03. No disjointness obligation, no `splitAt`, no `@unsafe`: `String.split`, `Pattern.split`, `String.lines`, `URI` component getters, and `StringTokenizer.nextToken` all implement as repeated `substring` calls (STR-03) into a result array.

Rust's `str::split_at_mut` exists because `&mut str` is a thing the language tracks; Laterita's one-type `String` admits no mutable view, so that primitive has no analog to need. The genuinely different case — two simultaneous `mut T[]` slices for parallel in-place algorithms — is settled by ARR-01.

---

## Arrays (ARR-01 through ARR-04)

### Why a dedicated section for arrays

Parallel to `String` (STR): the type is built in, the operations are load-bearing, and the rules belong together rather than scattered across OWN-05 and stdlib commentary.

A survey of the Rust ecosystem confirmed the primitive is load-bearing — `rayon`'s parallel iterators are built on `split_at_mut`-style producers, `core::slice::sort` uses it for quicksort partitioning, `image` and the FFT crates use `chunks_mut` pervasively, and `bytes`/`tokio-util` reimplement the concept for protocol buffer carving. Without a stdlib primitive, every serious library falls back on `from_raw_parts_mut` plus borrow laundering — exactly the `@unsafe` propagation Laterita is avoiding.

### Why two surfaces and a record return (ARR-01, ARR-02)

The `.java` and `.lat` surfaces differ on two features the array API depends on: anonymous functional interfaces (FN-01) exist only in `.lat`, and Java has no syntax for adding methods to `T[]`. The `.lat` surface uses both (ARR-01); the `.java` mirror substitutes static methods on `laterita.lang.Arrays` and named FIs (ARR-02, ARR-03). Migration tooling translates between them.

For the two-way split, three shapes were considered — continuation-passing, record return, multi-return language feature. The record form reads as ordinary Java:

```java
var s = arr.splitAt(mid);
var worker = spawnWorker(s.left());      // borrows the left half, bound to s which must survive worker
processLocally(s.right());
```

vs. the continuation-passing form which forces a lambda for an otherwise straight-line bind. The multi-return feature would add the most surface for the narrowest benefit. Record wins; `@bound` propagating through record fields is a small generalization of LIFE-02 already implicit in the lifetime rules.

For per-chunk iteration the trade-off inverts: the chunk's natural lifetime *is* "this callback," so `forEachChunk` / `forEachChunkExact` take a lambda. Successive chunks are disjoint by construction because each chunk's bound expires at its call's return — no explicit tracking needed.

A dedicated `reduceChunks` was considered and rejected.
Every in-place reduce expressible as `reduceChunks(buf, n, init, (acc, c) -> ...)` is also expressible as `@mut R acc = init; forEachChunk(buf, n, c -> ...)`, where the closure captures `acc` as a Mutate-mode variable (CLO-01, mutating through the `@mut` capture) and the `@mut` body slot accepts mutating closures (FN-01).
The only case `reduceChunks` covers uniquely is "immutable accumulator type threaded through mut chunks," which is rare in Java-flavored code and does not appear in the surveyed Rust array idioms.
Dropping it kept the surface at three methods instead of four.
Callers needing a read-only fold over chunks would want a different API shape (`@bound T[]` arr, `@bound T[]` chunks) that the current spec doesn't yet need.

### Why `MutableConsumer` is a sibling of `Consumer`, not a subtype

`MutableConsumer<T>::accept(@bound @mut T)` narrows `Consumer<T>::accept(T)`'s parameter — a contravariance violation if declared as a subtype, the same shape HIER-05 forbids for `@mut`-narrowing overrides. The reverse direction (`Consumer` extends `MutableConsumer`) would be sound but `Consumer` is in `java.util.function` and not modifiable. So the two stand as siblings; lambda literals target whichever the surrounding signature names.

### Why type arguments admit `@borrow` and `@mut` but not `@take` (TARG-01, TARG-02, TARG-03)

A type argument may carry `@borrow` and `@mut`, but not `@take`.

`@take` is a parameter mode — it describes a transfer of ownership *into a slot* at a call site — not a property a value carries. As a type argument it would have no referent: `Pair<@take K, @take V>` cannot say anything, because there is no call and no slot. Ownership of a generic structure's contents is carried by the structure's own variable (owned vs. `@bound`), so `@take` in argument position is rejected (TARG-02).

`@borrow` composes cleanly (TARG-01): a type argument names no source, so a borrow slot is exactly what it is.
An instance that stores a `@borrow`-substituted argument can only be produced as a `@bound` value, with lifetime per LIFE-02/TARG-04, and no struct-level lifetime parameters are needed.

`@mut` in a type argument — `List<@mut Foo>` — is the hard case. The expressiveness is real (worker pools, grids, fixed-shape mutable contents), and the hazard is aliasing: an element accessor `@bound E get(int i)` returns `@mut @bound Foo` when `E` is `@mut Foo`, and two coexisting shared borrows of a `List<@mut Foo>` would each call `get(0)` and receive a `@mut Foo` to the same slot. Banning `@mut` from type arguments outright would push the case onto `Cell<T>`, but that is heavier than the hazard requires.

The hazard exists only when the container is *shared* — duplicable into many coexisting borrows. A `@mut` container is an exclusive borrow (OWN-03): a `@mut` element borrow drawn from it re-borrows the whole container, exactly the receiver-reborrow pattern `splitAt` already uses (ARR-01), and a second concurrent element borrow is then a borrow-check error rather than aliasing. So `@mut` is admitted in a type argument precisely when the enclosing generic type is itself `@mut` (TARG-03): `@mut List<@mut Foo>` is sound and expressible; `List<@mut Foo>` — a shared container with mutable elements — stays rejected. A genuinely shared container whose elements mutate through shared borrows still uses `Cell<T>` (STD-05), with the `@unsafe` cost visible at the storage site.

### Why a type parameter assumes worst-case mutability, and `@fix` opts out (TARG-03b, TARG-08)

A generic body is checked once and monomorphized against every argument (COMP-02), so it must be sound for the most capable argument it admits.
For mutability the most capable argument is a `@mut` instance, so an unconstrained `T` is assumed `@mut` everywhere it is used.
The assumption is conservative in the safe direction.
A body that type-checks against a possibly-mutable `T` stays correct when `T` turns out to be a value class, whereas a body that assumed immutability could be handed a mutable argument and alias it.
Reading the assumption off the bound rather than a separate annotation means a constrained parameter such as `T extends Map` carries `Map`'s kind for free, with no per-usage marks.

`@fix` is the opt-out, mirroring how `@own` (TARG-06) opts a parameter out of admitting borrows.
`@fix T` at the declaration, shorthand for `T extends @fix Object`, freezes every usage at once and is the form a value-only container wants.
`@fix` on a single usage narrows just that occurrence, for a body that stores a `T` mutably in one field but exposes it immutably through one accessor.
Because `@fix` only removes a capability, it constrains the container at no site, the dual of TARG-03's rule that a written `@mut` element demands a `@mut` container.

### Why `@take` needs no degradation for borrows (TARG-05)

A `@borrow` value is a reference: an arrow to a value another variable owns.
`@take` keeps what the slot is given, so `@take @borrow` keeps the arrow, not the value it points at.
The referent stays owned where it was and is untouched, which is why `@take @borrow` is not the contradiction it first appears to be.
Underneath, `@take` is by-value transfer, not a claim on the referent.
Transferring a value costs a copy when the value is freely copyable and a move otherwise, the same copy-versus-move split the language applies everywhere.
A shared borrow is copyable, so `@take` of one copies it and the caller keeps its own.
An exclusive `@mut` borrow is not, so `@take` moves it and the caller loses access.
Degrading `@take` to a bare parameter for borrows fails on storage: a bare borrow parameter is scoped to the call (OWN-14) and cannot be kept, yet a container's `add` must store its element.
So `@take` stays for every element mode, and `@take @borrow` is the ordinary monomorphization rather than a contradiction.

### Why `@own` rather than reusing `@take` or a bound (TARG-06)

A type that must own its contents needs a way to reject a borrowed type argument at the declaration.
`@own` is a dedicated marker, the dual of `@borrow`, so the constraint reads at the type parameter exactly where a borrowed argument would otherwise be supplied.
Reusing `@take` is rejected: `@take` is a call-site transfer mode, and giving it a second meaning on a declaration where no call occurs overloads the token.
A marker-interface bound such as `T extends Owned` is rejected: ownership is not a supertype or method relationship, so a synthetic bound that every owned type would satisfy carries no real interface.
`@own` is the analog of a `'static` bound in Rust, applied to the owning containers `Arc` and `Mutex`.

### Why a bare borrow return binds to its container (TARG-07)

A bare `T` return means owned (OWN-16), but a borrowed type argument turns it into a borrow with no declared source, which OWN-19 and OWN-20 reject.
The return must therefore name a source.
Binding it to the container, rather than to the removed element's own origin, is sound because the container's lifetime already intersects every element source (LIFE-03), and it avoids per-element source tracking.
The cost is precision: a value pulled out cannot be kept past the container, where Rust's element-typed lifetime would allow it.
That is the same collapse tradeoff TARG-04 accepts, and the rare cases that need the longer lifetime call `.clone()` to obtain an owned value.

### Why `T[]` is the canonical contiguous-mut backing

Java array slots write through any variable, so `@bound @mut T[]` permits in-place slot mutation without `Cell<T>` and without `@unsafe` propagation. `ArrayList`, `HashMap` buckets, and the array-backed stdlib fit naturally. `Cell<T>` is needed only for non-array layouts (linked-list nodes, tree nodes) where the element lives behind an object field.

### Why the cross-thread story splits in two (ARR-01, ARR-02)

A single owned array must be divisible so the halves are independently usable by different threads, and a parallel iteration surface is needed for the data-parallel case. Two distinct usage shapes need different primitives, and trying to cover both with one API underweights whichever shape isn't its native fit.

**Long-lived ownership transfer.** A worker takes a half and keeps it for an arbitrary, possibly unbounded duration. Borrows are insufficient: `@bound @mut T[]` cannot outlive the receiver's source, and the source can't be a stack variable the spawning thread waits on. The half must *own* its segment. `splitOff` consumes the receiver and returns two owning `T[]` values whose representations share the underlying allocation through an internal refcount, freed when the last half drops. The result is wrapped in `Pair<T[], T[]>` (ARR-04) so partial-move (OWN-06) lets the caller extract each half via the accessor and `give` it to a thread independently.

**Data-parallel iteration.** Rather than carry a bespoke `parallelForEachChunk` on `T[]`, the parallel path goes through `Arrays.stream(@bound T[])` returning a bare `Stream<T>` — the JDK type, bound to the source by the parameter-source form of OWN-17 (static methods have no receiver, so `@bound` belongs on the parameter, not the return). `.parallel().forEach(...)` already exists on every Java developer's mental model, and the underlying `Spliterator` splits work the same way a hand-rolled chunk fan-out would. Callers who need a specific executor drive the stream with `ForkJoinPool.submit(...)` per standard JDK practice. The cost is one extra abstraction layer (the stream) and a real limitation — the stream produces transformed/aggregated values but does not write back into the source — so the in-place parallel mutation case stays on the `splitOff` path. The win is no new language-level executor concept (no rayon-style `pool.install(...)` shim) and one fewer load-bearing primitive on `T[]`; for read-only parallel reductions (map / filter / reduce / sum / collect), the standard `Consumer<T>` / `Function<T,R>` / `Predicate<T>` interfaces from `java.util.function` accept `@bound T` parameters by default, so no parallel SAM hierarchy is required.

Why not overload `splitAt` to cover both halves of the split case: the two forms differ only in receiver mode (`@mutating` borrow vs `@consuming`), and those annotations are not part of the overload signature (OWN-13) — two same-name methods that differed only in receiver mode would be a duplicate declaration. Using distinct names — `splitAt` for the borrowed return, `splitOff` for the consuming return — is both a Rust precedent (`BytesMut::split_off`) and the only spelling that reads unambiguously at every call site.

The candidate options for cross-thread split alone were all single-segment primitives that didn't fit the data-parallel shape and forced a second API anyway: (a) per-element `Mutex<T>` over `Arc<T[]>`, (b) dedicated `SharedSlice<T>` stdlib type, (c) extend `Arc<T[]>` with range metadata.
Folding the segmented-slice representation into `T[]` itself (closer to (c), but without disturbing `Arc<T>` for non-array `T`) keeps the surface small: callers see no new type for the slice, because `T[]` *is* the owning slice.
The pair shape rides on a single general-purpose `Pair<L, R>` record (ARR-04) whose owned-vs-borrowed instantiation is driven by generic substitution per TARG-01 (`Pair<T[], T[]>` for the cross-thread owning return, `@bound Pair<@borrow @mut T[], @borrow @mut T[]>` for the in-thread borrowed return), so any future API returning a two-tuple can reuse the same record rather than minting a new domain type.

### Why method-level only, not classes or blocks (UNS-01)

`@unsafe` only marks private methods, so the audit boundary is the method signature: a reviewer reads each `private @unsafe` method, verifies its preconditions, and trusts that the public API is safe by composition.

This is *tighter* than Rust.
Rust allows `unsafe { }` blocks deep inside public functions, where the audit boundary can be hard to find.
Forcing extraction into a named private method makes every unsafe operation in a codebase trivially enumerable (`grep "private @unsafe"`).
The compiler inlines the helper back, so the runtime cost is zero.
The slight visual heft is arguably a feature: unsafe operations can't be buried inline in a 200-line public method.

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
2. **Composition with the variable rules.** `Rc<T> b = a` is a borrow (no work). `Rc<T> b = give a` is a move (no work). `Rc<T> b = a.share()` is a refcount bump. Each does something different and each is sometimes the right choice. Rust forces `Arc::clone` even when a borrow or a move would do.
3. **No language feature needed.** `Rc<T>` becomes an ordinary class with an ordinary method. The compiler doesn't need to know anything about it.

### Why cycles leak (STD-01)

`Rc<T>` is reference-counted: when the last handle's `onDrop()` runs, the count reaches zero and the value is dropped. A cycle of strong handles never reaches zero — every handle is held up by another handle in the cycle, and none can decrement past one. The cycle leaks.

This is the same limitation Rust's `Rc<T>` and `Arc<T>` carry, with the same answer: `WeakReference<T>` (STD-03) for the back-edge in any structure that may form a cycle. Adding a cycle collector (a partial tracing GC) is rejected on Rust's grounds — the runtime cost and complexity exceed the value, given that `WeakReference<T>` solves the common cases and acyclic ownership is the dominant pattern. Laterita does not aim to be better than Rust at memory management; it aims to give Java developers the same safety properties Rust gives systems programmers, in syntax they already know.

The implication for COMP-01 is that "memory management determined statically" is not literally true for refcounted types — refcount reclamation is dynamic, and cycles are an unrecoverable leak. The spec acknowledges this explicitly rather than masking it.

Doubly-linked structures and graph-shaped data port using exactly these primitives: `Rc<T>` / `Arc<T>` on forward edges, `WeakReference<T>` on back edges. No additional standard-library type for graphs is introduced. The verbosity over a GC-tracked back-pointer is the deliberate cost of deterministic, refcount-based reclamation. Migration of doubly-linked lists, parent-pointer trees, and adjacency-list graphs is mechanical: each back-pointer becomes a `WeakReference<T>::get()` at use sites.

### Why race-safe upgrade (STD-04)

A naïve `WeakReference::get` that reads the strong count and then increments has a TOCTOU race: the strong count could drop to zero between the read and the bump, and the upgraded handle would point at destroyed memory. Compare-and-swap fixes this — the increment only happens if the count hasn't changed since we read it. Rust's `Arc::Weak::upgrade` does this for the same reason, with carefully chosen memory ordering.

### Why `Cell<T>` and `Heap<T>` are unsafe primitives (STD-05, STD-06)

These are the irreducible escape hatches. `Cell<T>` is the documented hole in MUT-09. `Heap<T>` is the only way to allocate without compiler-tracked ownership. Everything else in the standard library — `Rc`, `Arc`, `Mutex`, lazy initializers, growable collections — is built on top of them in `private unsafe` methods. The unsafe surface is small, concentrated, and auditable.

### Why `@local`, not `Send` (STD-07)

Rust uses two positive auto-traits (`Send` + `Sync`); Laterita inverts to one negative marker (`@local`). The inversion makes the common case — ordinary user classes are safe to cross threads — the unannotated default, which is what Java programmers expect. The `Send`/`Sync` split is collapsed because `Send`-but-not-`Sync` requires fine-grained borrow reasoning Java programmers don't expect.

A class with `@local` fields must explicitly choose `@local` (inherit thread-affinity) or `@local(false)` (encapsulate). The compiler does not infer the choice — making it explicit forces the author to name what they're claiming, and prevents accidentally promoting an `@local`-bearing class into the thread-safe pool by composition. The parameter form keeps both sides of the decision under one name: every class touching `@local` types declares a `@local(...)` annotation, the boolean selects affinity vs encapsulation. `@unsafe` remains independent and METHOD-only (UNS-01): per-operation trust on the unverifiable steps inside the encapsulating implementation.

### Why borrow-checked iteration reuses Java's API (STD-08)

The cursor-that-mutates-its-container is one of the canonical patterns where ownership type systems reach for internal `@unsafe`. Generative lifetimes, region typing, and similar academic devices can defeat the issue but at a cost (proliferating type parameters, separate inference machinery) out of proportion for the use case. Rust shipped `Vec::retain`, `Vec::drain`, `extract_if`, and `LinkedList::cursor_mut` — all backed by `@unsafe`. Laterita follows the same shape: a small stdlib API implemented with `private unsafe`, with the audit boundary the four or five method bodies that compose it.

A central design choice is whether to introduce a new `Cursor<T>` type or reuse Java's existing `Iterator<T>` and `ListIterator<T>`. The case for a new type is that the borrow-checked semantics are a real change from Java; a new name would warn the reader. The case for reuse wins: every method on `ListIterator` already has the right meaning, the loop shapes are identical, and inventing new vocabulary would force every Java reader to learn which iterator class to reach for in which situation. The borrow rules replace `modCount` and `ConcurrentModificationException` underneath, but the API surface above is the one Java developers already use.

The single signature deviation — `Iterator.remove()` and `ListIterator.remove()` returning an owned `T` rather than `void` — is forced by the ownership model. Java's void return reflects an assumption Laterita can't carry: that the caller "still has" the element from the prior `next()` call as a variable into the collection. In Laterita, `next()` returns a `@bound T` borrow tied to the iterator's position, and any mutating call on the iterator invalidates that borrow at the type level. Returning the removed element by ownership transfer is what restores the user's access to the value after the borrow is gone, and it incidentally folds Rust's separate `drain`/`extract_if` API into a one-liner over `remove()`. Statement-form `it.remove();` (ignoring the return) still compiles — the result drops via `onDrop`, matching Java's observable behavior.

`ConcurrentModificationException` doesn't carry over. Its job — detecting "you mutated the collection while iterating" — is exactly what OWN-03 enforces statically. The runtime category exists in Java because the language can't express the constraint at compile time. Laterita can, so the runtime exception becomes a compile error, and the `modCount` field can leave the standard library entirely.

Keeping a separate `Cursor<T>` type for the cursor case anyway — on the theory that "iterator" connotes read-only iteration in many readers' heads — is also rejected: `ListIterator` already exists in Java with mutation methods (`remove`, `set`, `add`), so the semantic precedent is there even if many Java developers underuse it. Two iterator types in Java's vocabulary, two iterator types in Laterita's — same names, sharper guarantees.

### Why `Mutex<T>` exposes its protected value through a closure (STD-09)

Java's `Lock` interface separates `lock()` from `unlock()`, leaving room to skip the unlock on an exception path. The defensive `try { lock.lock(); ... } finally { lock.unlock(); }` idiom is a syntactic ceremony for what should be a structural invariant. Three shapes for removing it were considered.

**A returned guard** (Rust `MutexGuard`, C++ `std::lock_guard`) whose `onDrop()` releases the lock. Acquisition returns `bound mut MutexGuard<T>`; scope exit and exception unwind both run `onDrop` (DROP-01, EXC-02), so forgetting to unlock is inexpressible. The trouble is THR-10 poisoning: the guard's `onDrop()` must mark the mutex poisoned only when the lock is being released *because an exception is propagating through the scope*, not on a normal exit. Because `onDrop()` is one method body called on both paths, distinguishing them requires a runtime in-flight-exception indicator — Rust pays for this with `std::thread::panicking()`, a thread-local bit consulted from inside `MutexGuard::drop`. The mechanism works, but it is language-level special machinery (a runtime fact the spec must surface through some hook) in service of one feature on one type.

**`AutoCloseable` plus a compiler must-use-as-resource rule.** Same one-method-two-paths problem inside `close()` (still needs the runtime bit, just relocated), and it puts a `try (...)` wrapper around every lock site — the same ceremony the first shape exists to remove. Net negative.

**A closure-scoped method on the mutex — the chosen shape.** `<R> R with(@mut @mutating (@mut T) -> R action)` and `<R> Optional<R> tryWith(...)` acquire the lock, run the closure on the protected value, release the lock, and return the closure's result. Poison detection is an ordinary `try`/`catch` around the closure invocation in stdlib code: the closure either returns or throws, and control flow itself is the signal. No runtime in-flight-exception indicator is required. The protected `T` is reachable only inside the closure, so there is no handle to smuggle, leak, or hold across uncertain control flow.

The trade against the guard shape is ergonomic. The locked region is a closure body, not a `{ }` block: two-mutex critical sections nest (`m1.with(t1 -> m2.with(t2 -> { ... }))`), and outer-function `return` / outer-loop `break` from inside the closure are unavailable. For the short critical sections that dominate real code these costs are invisible, and `with` returning `R` lets values flow out cleanly. In exchange, THR-10 reduces from "the unwind path sets a flag" — a property the language has to surface through some runtime mechanism — to "if the closure throws, `with` poisons before rethrowing," ordinary stdlib code using features every Laterita user already has (generic methods, anonymous functional interfaces per FN-01, `try`/`catch`). The `@unsafe` surface of `Mutex<T>` shrinks accordingly: the lock primitive and `Cell<T>` access still need `@unsafe` methods, as in any safe-mutex implementation, but the poison-detection layer above no longer does.

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

Detached threads are zombie processes by another name. Every real-world use case (long-running servers, background flushers, async loggers) is better expressed as "owned by a top-level variable or by an object the user keeps alive deliberately." Modern systems push genuine fire-and-forget work to queues, schedulers, or container infrastructure — not to in-process detached threads. Erlang's hierarchical supervisor model is the precedent: every process is owned, no zombies.

The cost is that libraries can no longer quietly spawn background threads — they must expose a client object whose lifetime the user manages. This is a feature: it surfaces the resource.

### Why sticky interrupt flag (THR-03)

Java's `Thread.interrupted()` clears the flag on read, and `InterruptedException` clears it on throw. This was modeled on signal-handler semantics in 1995 and made sense when threads were expensive enough to pool and re-run. It has produced thirty years of cancellation bugs: code catches `InterruptedException`, "handles" it, and silently escapes cancellation because the flag is now clear. The standard advice — "always re-set the flag in the catch block" — is the user manually patching around the language default, the tell that the design is wrong.

Modern Java (virtual threads, structured concurrency) has no remaining use case for clear-on-read. Threads are cheap, not pooled-and-re-run; cancellation is a final state, not a consumable signal. Sticky flag is what every modern user actually wants.

### Why interrupt is exposed but the flag operations are restricted (THR-07, THR-09)

`Thread.interrupt()` is fine to expose because, with a sticky flag, it can't be silently lost. The flag is observable but not clearable; an external interrupt request cannot be "consumed" by code that catches the wrong exception. The bug class that would otherwise motivate hiding `interrupt()` is foreclosed by the flag semantics, not by API restrictions.

`Thread.interrupted()` is kept (Java compatibility) but redefined as a synonym of `isInterrupted()` — the clear-on-read behavior is the bug, not a feature worth preserving.

### Why `InterruptedException` is unchecked (THR-08)

A special case of EXC-05: all exceptions are unchecked. The cancellation-specific argument — sticky-flag semantics make recoverable handling moot, and signature contagion is severe — applies with particular force to `InterruptedException`, and generalizes to every exception type.

### Why `onDrop()` cannot block (THR-05)

`onDrop()` runs on the unwind path. If a blocking call inside `onDrop()` itself throws `InterruptedException`, cleanup is abandoned mid-flight: locks held, files unflushed, memory leaked. The simplest rule that prevents this is "no blocking calls in `onDrop()`." It is checked statically at `onDrop()` definition.

The rule survives a survey of stdlib types whose cleanup *appears* to need blocking. `Mutex<T>.onDrop()` drops the protected value and releases the raw OS lock primitive — neither blocks. A queue-like primitive (e.g. a Laterita `BlockingQueue<T>`) wakes blocked endpoints with an error in its `onDrop()` — a notification, not a wait. Buffered IO needs `flush()`, but `flush()` belongs in `close()`, which Laterita keeps distinct from `onDrop()` (the same Closeable model Java has): a user who skips `close()` and falls back on `onDrop()` gets an unflushed buffer, just as in Java today. `Thread.onDrop()` does block — but it runs in the *parent's* stack as the cancellation orchestrator (THR-06), not in a body subject to interruption, so THR-05 explicitly exempts it.

So the rule is universal for user-facing `onDrop` bodies, with one privileged exemption (`Thread.onDrop`) that is named in the spec rather than gestured at. No `unsafe onDrop` escape hatch is needed; the integrity guarantee — cleanup completes atomically with respect to interruption — holds without erosion.

### Why no `cancel()`, no `tryJoin()`, no `parallelFirst()` (THR-09, by omission)

The model deliberately keeps the public surface to what Java already exposes plus `onDrop()`. Higher-level orchestration primitives (timeout-aware joining, fork-join helpers, structured task scopes) belong in libraries, not in the language spec. The minimal surface — `start()`, `interrupt()`, `join()`, `isInterrupted()`, plus `onDrop()` and `give(x);` — is sufficient to express every cancellation pattern. Library authors compose those into higher-level primitives as needed.

### Why `synchronized` is removed and replaced by `ReentrantLock` + `Condition` (STD-10–STD-12)

Java's `synchronized` keyword is dropped — both the method modifier and the `synchronized(obj) { ... }` block — and so are `Object.wait` / `notify` / `notifyAll`. They are replaced by three stdlib types: `Mutex<T>` (STD-09) for data-bound locking, and `ReentrantLock` + `Condition` (STD-10, STD-12) for the data-less and multi-condition cases. Four reasons motivate the removal.

**The intrinsic monitor isn't free.** Java's `synchronized` works because every `Object` carries a hidden header word the JVM materializes into a monitor on first contention. In an AOT-compiled language with no GC (COMP-01), giving every allocation an intrinsic-lock slot is a per-object cost paid by code that never locks anything. Concentrating mutual exclusion in a stdlib type means only the objects that actually need a lock pay for one.

**It doesn't compose with ownership.** `synchronized` locks *beside* data: holding a monitor doesn't restrict what fields the compiler lets you touch, and unsynchronized access to the same fields elsewhere is a normal compile success. `Mutex<T>` is shaped the opposite way — the lock *owns* the data, and the only path to the protected state is through the `with`/`tryWith` closure (STD-09). In an ownership-typed language this is strictly the better primitive: the compiler proves that every access to the protected state happens under the lock, which `synchronized` cannot.

**`synchronized` and `wait`/`notify` lower to runtime checks the surface hides.** Java's `synchronized(obj)` resolves the lock object dynamically, and `obj.wait()` requires holding `obj`'s monitor — a precondition checked at runtime via `IllegalMonitorStateException`. Both rely on a per-`Object` monitor field that the source surface never names. Laterita's stance is that the cost of an operation should be visible at the call site, and that safety checks should be static where possible. Preserving the keyword would keep the runtime-only flavor of these constraints; replacing it with explicit stdlib types puts the lock object, the guard's lifetime, and the condition pairing in the source.

**`wait`/`notify` aren't separable from intrinsic monitors.** They are defined on `Object` and only meaningful while holding that object's monitor. Without intrinsic monitors there is nothing for them to attach to. `Condition` (STD-12) is the dedicated stdlib home — bound explicitly to a `ReentrantLock`, with the same `await` / `signal` / `signalAll` surface and the same runtime "must hold the lock" precondition Java enforces, but with the lock object visible in the type.

The migration cost is small. A `synchronized(obj) { body }` block becomes `try (var __ = lock.lock()) { body }` over a `ReentrantLock`, where `LockGuard.onDrop` (STD-11) guarantees release on every exit path (DROP-01); a `synchronized` method becomes a method whose body acquires a per-instance lock the same way. `obj.wait()` becomes `cond.await()` on a `Condition` paired with the same lock. The translation is mechanical, and `LockGuard`'s `onDrop` makes "forgot to unlock" structurally impossible — a real safety gain over Java's `try { lock.lock(); ... } finally { lock.unlock(); }` idiom. Where the protected state fits cleanly inside one value, the further migration to `Mutex<T>` is the recommended end state: it adds the compile-time guarantee that every access goes through the lock, which neither `synchronized` nor `ReentrantLock` can provide.

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

### Why no reflection (COMP-05)

Two reasons, one structural and one about exposure.

The structural reason: monomorphization (COMP-02) erases generic identity at runtime. `List<String>` and `List<Integer>` are distinct compiled types with no shared metadata describing them as "the same generic." Field offsets are baked in. A reflection API that pretended otherwise would either have to defeat monomorphization (re-introducing the runtime type information we deliberately erased) or lie about what it's looking at. Both are bad outcomes; neither is worth the compatibility win.

The exposure reason: unrestricted reflection has been a recurring source of vulnerabilities in the Java ecosystem (deserialization gadget chains, sandbox escapes, library tampering). Removing it forecloses an entire class of attacks. The cost is paid by libraries that previously did at runtime what they can now do at compile time. That cost is real but bounded — the techniques are proven (Dagger, Micronaut, Quarkus, kotlinx.serialization, Quarkus/AspectJ compile-time weaving). The benefit accrues to every Laterita program for free.

What is genuinely lost: loading arbitrary user-supplied bytecode at runtime (intentional — that's the security hazard), JRebel-style hot reload (mitigated by fast incremental rebuilds), and `Proxy.newProxyInstance` over interfaces unknown at compile time. The first is a feature, not a regression. The second is a developer-experience cost worth investing in fast compilation to offset. The third is rare in real codebases outside mocking and dynamic RPC stubs, both of which are codegen-able when the interface is known.

The reflection question is settled in favor of "none."

---

## What Laterita Is Not

It's worth being explicit about a few things this design deliberately doesn't do.

**Not a JVM language.** Laterita compiles to native code. It doesn't run on the JVM. The Java compatibility is at the source level — the syntax looks like Java, the standard library looks like Java's, but under the hood there's no GC, no class loader, and no reflection (COMP-05). Existing Java bytecode does not run in Laterita.

**Not a Rust replacement.** Rust is more permissive in some ways (raw `unsafe { }` blocks, multiple smart-pointer styles, no inheritance) and more disciplined in others (no exceptions, lifetimes always visible, no implicit conversions). Laterita makes different tradeoffs because its target audience is Java developers, not systems programmers coming from C++.

**Not a fork of Java's standard library.** Most of Java's `java.util` would need to be reimplemented for Laterita's ownership rules. `ArrayList`, `HashMap`, `TreeMap`, `String`, `StringBuilder` — all have different semantics under ownership. The names are preserved; the implementations are new.

---

**Not finished.** This document and the spec describe the current design. `laterita-open-questions.md` records the language-design questions the spec deliberately leaves open; `resolved-questions.md` is the registry of closed decisions and rejected alternatives. Several open questions are load-bearing — particularly around exceptions. Real implementations will need to make those choices.
