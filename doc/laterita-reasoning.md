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

### Why `close()` is null-aware

NULL-09 specifies that scope-exit `close()` on a `T?` skips `null` and dispatches on the contained value otherwise. This composes with DROP-04's drop-flag machinery — the compiler already had to track per-binding "still owned?" state; "is this `T?` non-null?" is the same shape of conditional cleanup. No new runtime mechanism is introduced.

### Why no separate Optional<T>

With nullable types in the language, `Optional<T>` and `T?` are isomorphic and `T?` is shorter, more idiomatic, and avoids `Optional<Optional<T>>` confusion when generics nest. STD-03's `Weak<T>::upgrade()` returns `Shared<T>?` rather than `Optional<Shared<T>>` for this reason.

---

## Move and Borrow (MOVE-01 through MOVE-07)

### Why default assignment is a borrow (MOVE-01)

Looking at real Java code, the overwhelming common case is "I want to read this, I don't want to take it away from where it lives." Defaulting to a borrow matches that intuition. The user writes ordinary Java-looking code; the compiler infers a borrow; both bindings remain usable. Making move the default would have been the Rust approach, but it would have meant `^` (or `move` keyword) on essentially every assignment — friction with no payoff.

### Why `^` at the use site, not in signatures (MOVE-02, MOVE-03)

Two arguments converged on this:

The first is locality. A `move` keyword in front of a whole statement (`move users.add(name)`) detaches visually from the variable being consumed. `^name` sits on the variable. You can grep for `^` and find every ownership transfer in your codebase.

The second is signature simplicity. Rust puts ownership in function signatures. We initially copied that and then realized: the *caller* knows whether they want to keep the value or hand it off. The same function — `void store(String s, List<String> into)` — should accept either pattern. With `^` at the call site, the function doesn't need two variants:

```laterita
store(name, list);    // borrow, name still usable
store(^name, list);   // move, name consumed
```

This is more flexible than Rust and feels more Java-like, where callers in Java already make many decisions through what they pass.

### Why borrow exclusivity (MOVE-04)

This is the rule that actually buys safety. It rules out data races at compile time, iterator invalidation at compile time, and all the family of bugs that come from "two pieces of code each thought they had exclusive access to this." It is the load-bearing wall of the entire ownership system.

### Why disjoint field borrows (MOVE-05)

This was a real finding from the verification work. A red-black tree node needs to access `left` and `right` independently while the tree is mutably borrowed. The naïve borrow check rejects this even though it's trivially sound — the two fields don't alias. Without disjoint field borrows, large parts of the standard library become unwriteable in safe code (TreeMap, doubly-linked structures, swap-based algorithms). Rust solved this; Laterita inherits the solution.

### Why disjoint slice borrows with `splitAt` for hard cases (MOVE-06)

Same principle as MOVE-05, applied to arrays. The compiler can prove disjointness for trivial cases (`data.slice(0, 50)` and `data.slice(50, 100)`); for arbitrary index arithmetic, a standard library `splitAt` provides safe sub-slices using `unsafe` internally. This is the foundation for parallel divide-and-conquer, in-place sort, and any partition-based algorithm.

### Why partial-move tracking (MOVE-07)

Once you have moves out of fields, you need to know which fields are still alive at every point in the function. This is bookkeeping the compiler does silently, and it pays off both in normal control flow (use-after-move detection on partially-moved values) and during exception unwind (DROP-04, EXC-03). Skipping it would mean making `^` on a field illegal, which would make ownership transfer in real code far more painful.

---

## Mutability (MUT-01, MUT-02)

### Why `Cell<T>` is the only escape hatch (MUT-02)

There are real cases where a class is logically immutable but has internal caching (lazy initialization, memoization, mutex-protected state). Rust's answer is `UnsafeCell<T>`, the one type the compiler treats specially as a hole in the rules. Laterita adopts the same model: a single primitive marks the spot, every other interior-mutability mechanism is built on top of it. Concentrating the unsafe assumption in one place is what makes the rest of the language safely checkable.

---

## Lifetimes (LIFE-01 through LIFE-05)

### Why we kept lifetimes mostly invisible

Rust requires explicit lifetime annotations because its inference rules are conservative and its syntax is older. A modern design can do better. The vast majority of method signatures fall into one of three patterns: no borrows in or out (no lifetime question), one borrow in and out (single-input elision), instance method returning a borrow (receiver elision). Together these cover most real signatures.

In a 10,000-line codebase a developer might write `from` a few dozen times. Every other lifetime is inferred. This is a deliberate ergonomic stance: lifetime-as-thing-the-user-thinks-about is largely a Rust-specific choice that comes from valuing explicitness over inference. Java's culture values inference where it's safe, and lifetimes can mostly be inferred safely.

### Why `from` instead of apostrophe-letter (LIFE-05)

Rust's `'a` notation is famously off-putting to newcomers. It looks like a syntax error. `from` reads naturally — "the result borrows *from* this argument" — and it doesn't introduce a new sigil. The information content is the same; the visual friction is much lower.

### Why conservative ambiguity, not error (LIFE-04)

When a method has multiple borrowed inputs and the compiler can't tell which contributes to the output, the conservative rule (output bounded by all of them) is usually fine. Errors only appear when the conservative bound is too tight for the caller — at which point the suggested fix is to add `from`. The compiler points the user at the solution rather than refusing to compile.

---

## Cleanup (DROP-01 through DROP-05)

### Why universal `close()`, not opt-in (DROP-01)

This is the big realization: try-with-resources in real Java is the right *mechanism* but the wrong *default*. The clutter you correctly identified — needing `try ()` for every stateful binding — comes from cleanup being opt-in. Laterita makes it the default. Every binding gets deterministic cleanup; no syntactic marker required.

This is also why `close()` is the right name. Java already has `AutoCloseable`. We're not introducing a new concept; we're making AutoCloseable the universal contract. A user who already understands try-with-resources understands Laterita's cleanup model — they just don't have to write the `try`.

### Why not `finalize()` (rejected)

Repurposing `finalize()` was tempting and turned out to be wrong. Three reasons:

1. `finalize()` is being removed from Java. Building the language's core mechanism on a method the parent language is removing is fragile.
2. Almost no class overrides `finalize()` today, so calling it at scope exit would mostly call no-ops. Useful only after every standard library class is updated.
3. The semantic mismatch is bad signaling. `Object.finalize()` is the GC reclamation hook. Repurposing the name to mean "scope exit" would confuse every reader who knows Java.

`close()` has none of these problems and aligns with the existing `AutoCloseable` story.

### Why reverse declaration order (DROP-02)

This is RAII order. If you opened `A` then opened `B` that depends on `A`, you should close `B` first then `A`. Reverse-declaration order matches the natural dependency order in almost every real case.

### Why drop flags (DROP-04)

Without per-field tracking, partial moves either have to be forbidden (severely limiting the language) or have to leak undefined behavior on cleanup (catastrophic). Drop flags are the proven solution. Rust uses them, the optimization story is well understood (most flags are statically constant and get optimized away), and the runtime cost in code that doesn't actually unwind is approximately zero.

### Why universal close() is unrestricted (DROP-05)

The conversation didn't fully settle the question of whether user code can call `close()` explicitly, or whether there should be a separate "consume" primitive for early cleanup. The spec deliberately leaves both questions open — see OQ-13.

### Why `close()` overrides follow Java semantics (DROP-06)

When a subclass overrides `close()`, only the override runs unless it explicitly calls `super.close()`. This matches Java's general override model — every other Java method behaves this way — and avoids surprising the reader with a special compiler-injected chain only for `close()`.

The cost of this choice is that a forgotten `super.close()` is a silent leak the compiler does not catch. We accept the cost for two reasons: (1) it preserves consistency with how every other override in Java works, and (2) it is a tooling problem with a known solution — a lint that warns when an override does not call `super.close()`. The alternative — auto-chaining — is tempting but would make `close()` the one Java method whose override semantics are different from every other method, which is a worse trade-off than a lintable footgun.

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
2. **Composition with the binding rules.** `Shared<T> b = a` is a borrow (no work). `Shared<T> b = ^a` is a move (no work). `Shared<T> b = a.share()` is a refcount bump. Each does something different and each is sometimes the right choice. Rust forces `Arc::clone` even when a borrow or a move would do.
3. **No language feature needed.** `Shared<T>` becomes an ordinary class with an ordinary method. The compiler doesn't need to know anything about it.

### Why cycles leak (STD-01)

`Shared<T>` is reference-counted: when the last handle's `close()` runs, the count reaches zero and the value is dropped. A cycle of strong handles never reaches zero — every handle is held up by another handle in the cycle, and none can decrement past one. The cycle leaks.

This is the same limitation Rust's `Rc<T>` and `Arc<T>` carry, with the same answer: `Weak<T>` (STD-03) for the back-edge in any structure that may form a cycle. We considered adding a cycle collector (a partial tracing GC) and rejected it on Rust's grounds — the runtime cost and complexity exceed the value, given that `Weak<T>` solves the common cases and acyclic ownership is the dominant pattern. Laterita does not aim to be better than Rust at memory management; it aims to give Java developers the same safety properties Rust gives systems programmers, in syntax they already know.

The implication for COMP-01 is that "memory management determined statically" is not literally true for refcounted types — refcount reclamation is dynamic, and cycles are an unrecoverable leak. The spec acknowledges this explicitly rather than masking it.

### Why race-safe upgrade (STD-04)

You spotted this during the verification phase. A naïve `Weak::upgrade` that reads the strong count and then increments has a TOCTOU race: the strong count could drop to zero between the read and the bump, and the upgraded handle would point at destroyed memory. Compare-and-swap fixes this — the increment only happens if the count hasn't changed since we read it. Rust's `Arc::Weak::upgrade` does this for the same reason, with carefully chosen memory ordering.

### Why `Cell<T>` and `Heap<T>` are unsafe primitives (STD-05, STD-06)

These are the irreducible escape hatches. `Cell<T>` is the documented hole in MUT-01. `Heap<T>` is the only way to allocate without compiler-tracked ownership. Everything else in the standard library — `Shared`, `Atomic`, `Mutex`, lazy initializers, growable collections — is built on top of them in `private unsafe` methods. The unsafe surface is small, concentrated, and auditable.

### Why `Send` is mentioned but underspecified (STD-07)

Cross-thread move safety needs to be tracked. We agreed it does, but never settled on the syntax for declaring `Send`-ness. Rust uses an auto-trait; Laterita could use an interface, an annotation, or compile-time inference. The spec records the requirement; the open questions document records the missing decision.

---

## Compilation Model (COMP-01 through COMP-04)

### Why AOT and no GC (COMP-01)

The whole point of the exercise. Java's GC papers over ownership. Removing it forces every ownership question to be answered statically, which is what gives Laterita its safety properties. AOT compilation is the natural target — the language has no runtime that benefits from a JIT, no dynamic class loading that benefits from interpretation, no reflection (or much reduced reflection) that benefits from full runtime type info.

### Why monomorphization (COMP-02)

Without GC, generic dispatch through type erasure (Java's current model) has nowhere to put the type information at runtime. Monomorphization — emitting one specialized version of a generic per concrete type — is the proven solution from C++ templates and Rust. The cost is binary size; the gain is that generic code runs at the same speed as hand-specialized code.

### Why compiler-inserted cleanup is invisible (COMP-03)

The user shouldn't see drop calls in their source. This is what makes `close()` feel like a language feature rather than a discipline. The compiler emits the calls, the user writes ordinary code.

### Why drop flags are an optimization target (COMP-04)

Most drop flags are statically determined — the compiler can prove a field is always moved by a certain point, or never moved. In those cases, the flag becomes a constant and gets optimized away. The runtime overhead in real code is near zero. This isn't critical to specify, but it matters for implementers worried that drop flags will slow things down. They won't, in practice.

---

## What Laterita Is Not

It's worth being explicit about a few things this design deliberately doesn't do.

**Not a JVM language.** Laterita compiles to native code. It doesn't run on the JVM. The Java compatibility is at the source level — the syntax looks like Java, the standard library looks like Java's, but under the hood there's no GC, no class loader, no reflection (or much less of it). Existing Java bytecode does not run in Laterita.

**Not a Rust replacement.** Rust is more permissive in some ways (raw `unsafe { }` blocks, multiple smart-pointer styles, no inheritance) and more disciplined in others (no exceptions, lifetimes always visible, no implicit conversions). Laterita makes different tradeoffs because its target audience is Java developers, not systems programmers coming from C++.

**Not a fork of Java's standard library.** Most of Java's `java.util` would need to be reimplemented for Laterita's ownership rules. `ArrayList`, `HashMap`, `TreeMap`, `String`, `StringBuilder` — all have different semantics under ownership. The names are preserved; the implementations are new.

**Not finished.** This document and the spec describe the design we converged on. The open questions document records what we explicitly didn't settle. Several of those questions are load-bearing — particularly around exceptions, reflection, and Spring-style frameworks. Real implementations will need to make those choices.
