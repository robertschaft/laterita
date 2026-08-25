# Laterita Terminology and Abbreviations

This document defines the terms the Laterita specification uses that the Java Language Specification does not.
It assumes a reader fluent in Java and does not restate what the JLS already defines.
Terms Java already has, and that Laterita uses with Java's meaning, are listed as one-line refreshers under "Java refreshers".

Use this document as a reference when reading the spec.
Terms appear in alphabetical order, ignoring a leading `@` or `.`.

---

## Terms

### `Arc<T>`
A reference-counted handle for shared ownership across threads, with atomic count operations.
The cross-thread counterpart of `Rc<T>`.
A new handle is produced by `share()`, and the value is freed when the last handle is dropped.
See `STD-02`.

### bare
Carrying none of the annotations under discussion.

### bitwise copy
A field-for-field copy of a value's representation, running no user-written code.
The synthesized copy constructor copies primitive fields this way and clones object fields instead (`OBJ-01`).

### borrow / borrowed variable
A variable that refers to a value owned elsewhere.
A borrowed variable cannot be moved, and no `onDrop()` runs when it goes out of scope.
A borrow is either shared or mutable.
See `OWN-03`.

### `@borrow` (annotation on fields, record components, type arguments, and parameters)
Declares that a field, a record component, or a generic type argument holds a borrow rather than an owned value (`TARG-01`).
An instance of a class with any `@borrow` field can only be produced as a `@bound` value, with a lifetime intersecting each source (`LIFE-03`).
On a parameter it is meaningful only with `@take`, where it retains the borrow and caps `this` at the parameter's source (`OWN-21`).
It names no source, so it is the structural marker, as against `@bound`, which names the source that bounds a value's lifetime.
See `OWN-09`, `OWN-21`, `LIFE-03`, `TARG-01`.

### `@borrowCapped` (annotation on classes)
Declares that an instance keeps its borrows live until it goes out of scope, so `onDrop()` may read them.
See `LIFE-04`, `DROP-11`.

### `@bound` (annotation on returns, parameters, and variables)
Declares a lifetime relationship between two values.
On a parameter it declares that the method's return is bound to that parameter (`OWN-17`).
On a return type it declares that the return is bound to `this` (`OWN-18`).
It names a source, so it is the relational marker, as against `@borrow`, which declares a borrow whose source is fixed elsewhere.

### `broken()` (static methods on `laterita.lang.Broken`)
The factories of the standard `UncompilableException`, declared `static Broken broken()` and `static Broken broken(String reason)` and normally statically imported.
`throw broken("...")` declares the enclosing path unreachable.
See `UNR-02`.

### buffer splitting
Dividing a contiguous region into two non-overlapping views.
Within one thread, `T[].splitAt` returns borrowed halves and `forEachChunk` passes borrowed chunks to a callback.
Across threads, `T[].splitOff` returns two owning halves and `Arrays.stream` exposes the elements for read-only parallel processing.
See `ARR-01`, `ARR-02`, `ARR-04`.

### call mode
The receiver mode of a functional interface's single abstract method, and therefore a property of the type.
**Shared-call** is a `@readonly` method, **mut-call** a bare one, and **once-call** a `@consuming` one.
It is the `Fn` / `FnMut` / `FnOnce` distinction of Rust, carried on the method rather than on the type name.
Distinct from the mode of the variable that holds the value.
See `CLO-03`.

### `Cell<T>`
The interior-mutability primitive, permitting the contents to be modified through a `@fixed` variable.
Every operation on it requires an `@unsafe` method (`UNS-02`).
See `STD-05`, `MUT-16`.

### `clone()`
The public copy method every class has.
The compiler synthesizes it as `return new Self(this);` unless the class declares its own, and a class forbids copying by declaring one that reaches an `UncompilableException` (`UNR-01`).
See `OBJ-02`.

### closure
A lambda together with the variables it captures.
The compiler classifies a closure by how the body uses its captures: **read** borrows them shared, **mutate** borrows one of them mutably, and **consume** moves one of them in.
The classification fixes the closure's call mode.
See `CLO-01`, `CLO-02`.

### `Condition`
A condition variable bound to a `ReentrantLock` and created by `newCondition()`.
The names and shapes match `java.util.concurrent.locks.Condition`, and the requirement that the caller holds the bound lock is checked at run time.
See `STD-12`.

### const expression
An expression the compiler evaluates at compile time: a literal, a reference to another const-initialized static field, or a call to a constructor or method the compiler admits in that position.
Every static field initializer is one.
See `STAT-02`.

### `@consuming` (annotation on methods)
Declares that a method consumes its receiver: the body owns `this`, and the variable that held the receiver may not be used after the call.
A modifier-position annotation, as `@readonly` is, and the two compose.
See `OWN-15`.

### `@Delegate` (annotation on fields)
Generates, for every `public` instance method of the field's declared type, a forwarding method on the owner that calls the same method on the field.
`Object` methods and `static` methods are not forwarded, and an explicitly declared method shadows the forwarder of the same signature.
See `GEN-01`.

### destruction
Taking an owned object apart field by field with `give(obj.field)`.
Once its first field has been moved out, the object may only be taken further apart, and the moved fields live on in the scope that received them.
A class that implements `onDrop()` is moved whole instead.
See the `DES` topic, `OWN-06`, `DROP-08`.

### desugaring
Rewriting a `.lat` form into its Java-compatible equivalent before analysis (`LAT-00`).

### disjoint
Provably non-overlapping, so two borrows of one value may coexist (`OWN-04`, `OWN-05`).

### divergence point
A path that constructs an `UncompilableException`, which the compiler must prove dead.
See `UNR-01`.

### drop / `onDrop()`
To release a value when its owning variable goes out of scope.
The compiler invokes `onDrop()` at every exit from the scope, including exceptional ones.
A `final` class implements it to release a resource, and any other class holds its resources by composition.
See `DROP-01`, `DROP-09`.

### drop flag
The per-field record of whether a destructed value still owns each of its fields, kept by the compiler so that it emits an `onDrop()` call only for the fields still owned at that point.
See `DROP-04`.

### effectively fixed
A mutable local variable with no mutating use.
It borrows its source as a shared borrow, so several such variables over one value may coexist.
See `MUT-60`.

### exclusivity
The property that at most one mutable borrow of a value exists at a time, and that no other borrow coexists with it.
It is what makes a data race and an invalidated iterator compile-time errors.
See `OWN-03`.

### `@fixed` (annotation)
Declares a variable or a type immutable.
It is the only mutability annotation, and wherever it appears the object may not be modified through that variable.
See `MUT-01`, `MUT-10`.

### flow-sensitive
Analysed per path through a method rather than once per declaration.
Ownership, borrow state, and null narrowing are tracked this way, so a variable's state may differ between two branches.
See `OWN-02`, `LIFE-01`, `NULL-06`.

### frozen view
`@fixed C`, the interface containing only the `@readonly` methods of `C`.
A value of type `C` may be assigned to a variable of type `@fixed C`, and not the reverse.
See `MUT-30`.

### functional interface
Java's functional interface, extended by an anonymous structural form written inline as `(P1, P2, ...) -> R`.
The anonymous form is legal as a parameter type, a return type, a generic bound, or a generic type argument, and it is admitted in `.lat` sources only.
It accepts an optional `@readonly` or `@consuming` prefix declaring its call mode, and its single abstract method is named `apply`.
Laterita treats the nominal and the anonymous form uniformly.
See `FN-01`, `FN-04`, `LAT-05`.

### `give` (static method on `laterita.lang.Intrinsics`)
The move expression, declared `static <T> T give(@take T t)` and normally statically imported.
`give(x)` consumes `x` and returns its owned value, so `var b = give(a);` moves the value into `b` and `give(x);` as a statement drops it at the semicolon.
Consumption of a receiver is not spelled this way, but with `@consuming` on the method.
See `OWN-07`, `OWN-15`.

### `Heap<T>`
The raw allocation primitive, providing allocation, dereference, and release.
Every operation on it requires an `@unsafe` method (`UNS-02`), and application code reaches it through a wrapper such as `Rc<T>` or `Arc<T>`.
See `STD-06`.

### interior mutability
Modifying an object's contents through an immutable variable, which `MUT-14` otherwise forbids.
In safe code it is reachable only through `Cell<T>`.
See `MUT-16`.

### `@internal` (annotation on methods)
Declares that a method may be invoked only from compiler-emitted code.
`onDrop()` is the only such method this specification introduces.
See `DROP-06`.

### `.lat` / `.java` (source file extensions)
The two extensions `latc` accepts.
`.lat` admits the full surface, including `T?`, `?.`, `?:`, `!!`, and the inline functional-interface form.
`.java` is the Java-compatible subset, and the `LAT` topic gives each `.lat` form its `.java`-surface desugaring.
Both extensions share one type system, one annotation surface, and one set of intrinsics.
See `COMP-06`.

### `latc`
The reference Laterita compiler.
It accepts `.lat` and `.java` sources in one compilation, dispatching by extension.
See `COMP-07`.

### lifetime
The span over which a variable is valid.
A borrowed variable's lifetime is bounded by the variable it borrows from, and it is a compile-time error to use it after the value it refers to has been dropped.
See `LIFE-01`.

### `@local` (annotation on types)
Declares a class thread-affine: its instances cannot cross a thread boundary or be captured by a closure that may run on another thread.
`@local(false)` declares the opposite, that the class encapsulates its transitively `@local` fields.
A class with any `@local` field carries one form or the other explicitly.
`Rc<T>` and `Cell<T>` are `@local`, and `Arc<T>`, `Mutex<T>`, and `Thread` are `@local(false)`.
See `STD-07`.

### `LockGuard`
A value witnessing that the calling thread holds a `ReentrantLock`.
It owns one acquisition and releases it from `onDrop()`, so the scope that owns the guard is the critical section.
See `STD-11`.

### method dispatch
Selecting the implementation to run, from the value's runtime class or from its static type (`OBJ-02`, `COMP-02`).

### monomorphization
Compiling one specialized implementation per instantiation of a generic type or method.
See `COMP-02`.

### mutable borrow
A borrow through which the value may be read and written.
Only one may be live at a time, and no other borrow may coexist with it.
It requires the source variable to be mutable, or the borrow to occur in a mutating method of the same object.
See `OWN-03`, `OWN-13`.

### mutating use
A use of a local variable that requires mutation: a call to a mutating method, an assignment through the variable, passing it to a mutable parameter, or returning it through a mutable return type.
A local variable with one borrows its source mutably, and one with none is effectively fixed.
See `MUT-60`.

### `Mutex<T>`
A mutual-exclusion primitive owning the value it protects.
Access is scoped to a closure passed to `with` or `tryWith`, which acquires the lock, invokes the closure on the protected value, releases the lock, and returns the closure's result.
The mutex is poisoned if the closure throws.
See `STD-09`.

### newtype
An idiom rather than a language construct.
A `record` whose sole component carries `@Delegate` generates the wrapped type's forwarding surface (`GEN-01`), has the layout and calling convention of the component alone (`NABI-01`), and has its forwarders inlined (`COMP-08`).
The result is a distinct nominal type with no implicit widening to the component's type and no run-time overhead.
`record Email(@Delegate @take String raw)` is a newtype by idiom.

### nullable type
A type admitting `null` in addition to the values of `T`, written `@Nullable T` on the Java-compatible surface and `T?` in `.lat`.
A bare `T` in Laterita excludes `null`, unlike a Java reference type.
See `NULL-02`, `LAT-01`.

### `@Operator` (annotation on methods)
Marks an instance method as the target an arithmetic operator desugars to in `.lat` sources.
The annotation names the operator (`PLUS`, `MINUS`, `TIMES`, `DIVIDE`, `NEGATE`) and leaves the method name free, so `BigDecimal.add`, `Instant.plus`, and `Duration.negated` qualify under their existing names.
The operator set is closed, and the comparison operators desugar through `Comparable.compareTo` instead, needing no annotation.
See `LAT-07`.

### OQ (prefix in `OQ-NN`)
"Open question", a numbered entry in `doc/laterita-open-questions.md` recording an unresolved language-design decision.
It is not part of the normative specification.

### override variance
The rules governing how an overriding method's annotations may differ from the inherited method's.
One principle covers them: an override may demand less of its callers and guarantee more to them, never the reverse.
See `HIER-05`.

### `@own` (annotation on type parameters)
Declares that a type parameter rejects a borrowed type argument, so `class C<@own T>` accepts `C<Foo>` and not `C<@borrow Foo>`.
The dual of `@borrow`, and the counterpart of a `'static` bound in Rust.
`Arc` and `Mutex` declare their type parameter this way.
See `TARG-06`.

### ownership
The right and the obligation to drop a value.
An owned variable may move the value to another variable, pass it to a `@take` parameter, or drop it at scope exit, and only one variable owns a value at a time.
See `OWN-01`.

### `Pair<L, R>`
The general-purpose mutable class in `laterita.lang` carrying two values, whose components are `public final` fields.
One declaration covers the owned, the borrowed, and the mixed case, driven by what is substituted for `L` and `R`.
See `ARR-04`.

### parameter mode
How a parameter receives its argument: bare borrows it mutably, `@fixed` borrows it shared, `@take` receives ownership, and `@take @fixed` receives ownership frozen.
See `OWN-13`, `MUT-41`.

### poisoned
The state of a `Mutex<T>` whose `with` or `tryWith` closure propagated an exception out of the critical section.
Every later acquisition throws `PoisonedException`, and the only recovery is to replace the mutex.
See `THR-10`.

### producer expression / naming initializer
The two kinds of initializer OWN-02 distinguishes, which decide whether a local variable owns its value or borrows another variable's.
See `OWN-02`.

### `Rc<T>`
A reference-counted handle for shared ownership within one thread.
Each holder holds its own handle, `share()` produces a new one, and the value is freed when the count reaches zero.
See `STD-01`.

### `@readonly` (annotation on methods and inner classes)
Declares that a method does not modify its receiver, so it may be called on any receiver.
A bare method may be called only on a mutable receiver, and only `@readonly` methods appear in the frozen view of a class.
On a non-static inner class it makes the enclosing borrow shared.
See `MUT-13`, `MUT-50`.

### receiver mode
How a method accesses `this`: read-only under `@readonly`, mutating when bare, and consuming under `@consuming`.
The receiver variable's own mode must support it, so a `@fixed` variable cannot call a mutating method.
See `MUT-13`, `MUT-15`, `OWN-15`.

### `ReentrantLock`
A reentrant mutual-exclusion primitive owning no data.
It is modelled on `java.util.concurrent.locks.ReentrantLock`, except that `lock()` returns a `LockGuard` whose `onDrop()` releases the lock, so an unreleased lock is not expressible.
It covers the cases `Mutex<T>` does not, where the guarded state is spread over several fields or where the coordination guards no data at all.
See `STD-10`.

### safe / unsafe code
Safe code obeys every ownership and lifetime rule and is checked in full.
Unsafe code is a `private @unsafe` method, which may additionally perform the operations `UNS-02` lists.
The annotation unlocks those operations only, and every other check still applies.
See `UNS-01`, `UNS-04`.

### shared borrow
A borrow through which the value may only be read.
Any number may coexist, and the source variable need not be mutable.
See `OWN-02`, `OWN-03`.

### slice
A borrowed view into a contiguous region of a `String` or an array, returned by `substring`, `trim`, and `slice` and marked `@bound`.
It is bounded by the lifetime of the value it views.
See `STR-03`, `OWN-05`.

### smart pointer
A type that manages the lifetime of the value it holds, such as `Rc<T>` and `Arc<T>`.
Every smart pointer implements `onDrop()`.

### static lifetime / static borrow
A lifetime spanning the whole execution of the program, and a borrow that carries one.
Such a borrow is not tied to any variable's scope and may be taken in any context, and a string literal is the case this specification relies on.
See `STR-06`, `LIFE-01`.

### string literal
A quoted expression such as `"hello"`, whose type is `@bound String` with a static lifetime.
It occupies read-only program storage rather than the heap, so a variable initialized from one is borrowed and `clone()` is what yields an owned `String`.
See `STR-06`.

### substitution
Replacing a type parameter with a type argument, which carries the argument's variable modifiers into the body (`TARG-01`).

### synthesized
Generated by the compiler rather than written in source.
The copy constructor and `clone()` are synthesized, as are the interface and the implementing class behind each anonymous functional interface.
See `OBJ-01`, `OBJ-02`, `FN-03`.

### `@take` (annotation on parameters)
Declares that a parameter receives ownership of its argument.
At the call site a variable passed to such a parameter is consumed implicitly, and `give(variable)` states the same transfer explicitly.
Consumption of a receiver is the separate `@consuming` annotation.
See `OWN-13`, `OWN-15`.

### thread-affine
Bound to one thread and therefore unsafe to move to another.
Laterita expresses the property with `@local`.
See `STD-07`.

### transitive immutability
Immutability propagating through every access path.
An immutable variable may not be used to call a mutating method or assign a field on any object reachable through it, whatever those fields declare.
See `MUT-14`, `MUT-15`.

### type narrowing
Refining a variable's type along a path, most often from `T?` to `T` after a null check.
See `NULL-06`.

### `UncompilableException`
The exception type whose construction declares a path unreachable, an abstract `RuntimeException` in `laterita.lang`.
It is a compile-time error to construct one on a path the compiler cannot prove dead, so a Laterita program never throws one.
A subclass names a narrower reason in its own type, and `Broken` is the standard one.
See `UNR-01`, `UNR-02`.

### use-after-move
The error of using a variable after its value has been moved elsewhere, which the compiler rejects statically.
See `OWN-07`.

### value class (reserved)
Reserved for a notion stricter than an immutable class, in the spirit of an identity-free inline type.
A `@fixed` class is an immutable class and not a value class, and the term is not used for one.
See `MUT-10`.

### variable modifiers
`@bound`, `@borrow`, `@fixed`, `@take`, and `@own`, each admitted in the positions its rule names.
`@bound` on a parameter or a return (`OWN-17`, `OWN-18`).
`@borrow` on a field, a record component, a generic type argument, and a parameter carrying `@take` (`OWN-09`, `TARG-01`, `OWN-21`).
`@fixed` on a local variable, a field, a parameter, a return, a type argument, a type-parameter declaration, and a class or interface declaration (`MUT-01`, `TARG-03`).
`@take` on a parameter (`OWN-13`), and nowhere else (`OWN-10`, `TARG-02`).
`@own` on a type-parameter declaration (`TARG-06`).

### `WeakReference<T>`
A non-owning reference to a value held by `Rc<T>` or `Arc<T>`.
It does not contribute to the count, and `get()` returns a strong handle or `null`, rather than the value itself, which `java.lang.ref.WeakReference.get()` returns.
See `STD-03`.

---

## Java refreshers

These terms carry their Java meaning.
They are listed because the specification leans on them.

- **effectively final**: a local variable never assigned after its initializer.
Borrow analysis treats it as `final`, and only such a variable may be captured by a closure (`MUT-61`, `CLO-01`).
- **erased parameter types**: a signature's parameter types with generic type arguments removed.
A hand-written member shadows a generated one when their names and erased parameter types match (`GEN` topic).
- **SAM**: the single abstract method of a functional interface.
An anonymous functional interface's is named `apply` (`FN-01`).
- **target typing**: inferring a lambda's type from the context it appears in (`CLO-04`).
- **`var`**: a local variable whose type is inferred from its initializer and which stays assignable.
In Laterita the inferred type carries `@fixed` when the initializer's type does (`MUT-40`).
- **covariant / contravariant / invariant**: an override may relax a position (contravariant), tighten it (covariant), or must match it exactly (invariant).
`HIER-05` states the direction for each annotation.
- **copy constructor**: a constructor taking one parameter of its own class.
Laterita synthesizes one when the class declares none (`OBJ-01`).
- **unwind**: propagating an exception up the call stack.
Laterita runs `onDrop()` alongside `finally` at each frame (`EXC-02`, `EXC-03`).

---

## Notation

| Notation | Meaning |
|----------|---------|
| `T`, `U`, etc. | A type variable, standing for any type |
| `T?` | The nullable type `@Nullable T`, in the `.lat` spelling of `LAT-01` |
| `(T1, T2, ..., Tn) -> R` | An anonymous functional interface taking `T1, ..., Tn` and returning `R`, mut-call unless prefixed `@readonly` or `@consuming`. Legal as a parameter type, return type, generic bound, or generic type argument (`FN-04`), in `.lat` sources only (`LAT-05`) |
| `variable:` or `method:` or `parameter:` | The declaration position the following code snippet occupies |

## Spec code prefixes

Each rule in the specification carries a mnemonic code, grouped by topic:

| Prefix | Topic |
|--------|-------|
| `OWN` | Ownership: owned and borrowed variables, moves and borrows, `@take` / `@borrow` / `@bound`, `@consuming` |
| `LIFE` | Lifetime intersection across borrow sources |
| `MUT` | Mutability: `@fixed` and `@readonly`, transitivity, interior mutability |
| `HIER` | Class hierarchy: inherited immutability, class kind, frozen views, override variance |
| `TARG` | Annotations admitted in generic type arguments, and type-parameter mutability |
| `STAT` | Static fields |
| `NULL` | Nullable types |
| `DROP` | Scope-exit cleanup and `onDrop()` |
| `OBJ` | Copying and `clone()` |
| `DES` | Destruction: taking an owned object apart field by field |
| `UNR` | Unreachable paths and `UncompilableException` |
| `STR` | String ownership and slicing |
| `ARR` | Array methods, indexing, and the `laterita.lang.Arrays` surface |
| `FN` | Functional interfaces, including the anonymous form |
| `CLO` | Closures and lambda capture |
| `EXC` | Exceptions and unwind |
| `UNS` | Unsafe code and the operations it unlocks |
| `STD` | Required standard-library types |
| `THR` | Threads, interrupts, and lock poisoning |
| `COMP` | Compilation model |
| `RESV` | Reserved names and the annotation and intrinsic surface |
| `LAT` | `.lat` surface forms |
| `NABI` | Native ABI guarantees |
| `GEN` | Code-generation annotations |

---

## Java analogies

| Laterita | Java | Difference |
|---|---|---|
| `Rc<T>` | A reference under a manual reference count | Java collects garbage, while Laterita requires an explicit `share()` and frees at count zero |
| `Arc<T>` | A reference under an atomic reference count | As `Rc<T>`, and safe to hand to another thread |
| Ownership and `give(...)` | No counterpart | Java has no ownership, so every reference is a borrow |
| Borrow | An ordinary reference | The same access, under lifetime rules Java does not have |
| `@fixed` | No counterpart | Java cannot say that an object may not be modified through a variable, and leaves assignment to the separate `final` |
| `@fixed class` | Valhalla's `value class` | Both are the marked kind, and an unmarked class is an ordinary mutable class (`MUT-10`) |
| `@local` | Thread confinement by convention | Java has no language-level thread affinity for a type |
| `Cell<T>` | `AtomicReference<T>` | For interior mutability within one thread, with no atomicity and no collector |
| `Mutex<T>` | A `synchronized` block over a guarded field | The closure-scoped API releases the lock for you and ties it to the value it protects |
| `ReentrantLock` and `LockGuard` | `java.util.concurrent.locks.ReentrantLock` | `LockGuard.onDrop()` replaces the manual unlock, and the lock pairs with `Condition` for wait and signal |
| `Condition` | `java.util.concurrent.locks.Condition` | The same API, with the bound lock checked at run time |
| `onDrop()` | `close()` or a finalizer | Invoked for every object at scope exit, closer to a C++ destructor than to a finalizer |
| Drop flag | No counterpart | Java tracks no per-field move state |
