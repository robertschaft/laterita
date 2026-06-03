# Laterita — Terminology and Abbreviations

This document explains terms, abbreviations, and keywords used throughout the Laterita specification that may be unfamiliar to a junior Java developer. It assumes familiarity with core Java syntax and OOP but not with Rust concepts or advanced compiler terminology.

Use this document as a reference when reading the spec. Terms appear in alphabetical order.

---

## Terms

### Arc<T>
An atomic reference-counted smart pointer for shared ownership across thread boundaries. Similar to `Rc<T>` but with atomic (thread-safe) refcount operations. See `Rc<T>` for the single-threaded version. Like Java's garbage-collected heap, `Arc<T>` lets multiple holders reference the same value, but you must explicitly call `.share()` to create an alias, and the value is freed when the last reference drops.

### variable
A local variable, field, or parameter that holds a value. In Laterita, every variable declares whether it owns the value (can move it, can drop it) or borrows it (reads only, or reads with mutation rights). Variables are the primary unit of lifetime tracking.

### borrow / borrowed variable
A variable that refers to a value owned elsewhere, rather than owning it itself. A borrowed variable cannot be moved; when it leaves scope, the compiler does not invoke `onDrop()`. There are two kinds: shared (immutable) and mutable. See `OWN-03` for the rules.

### @borrow (annotation on fields and record components)
Declares that a field (or record component) is a borrow slot rather than an owned slot.
An instance of a class containing any `@borrow` field can only be produced as a `@bound` value, with lifetime intersecting each field's source (LIFE-03).
Distinct from `@bound`, which marks a borrowed *value* at parameters, returns, and generic type arguments.
See `OWN-09`, `LIFE-03`.

### @bound (annotation on returns, parameters, and type arguments)
Declares a lifetime relationship between two values.
On a parameter, declares that the function's return is bound to that parameter (`OWN-17`).
On a return type, declares that the return is bound to `this` (`OWN-18`).
In a generic type argument, declares that the substituted type is borrowed and forces the enclosing instance to be `@bound` (`TARG-01`).
Distinct from `@borrow`, which declares a field as a borrow slot.

### variable modifiers
`@bound`, `@mut`, `@take`, and `@borrow`. Legal positions:
- `@bound`. Parameter, return, generic type argument. Always allowed in type arguments (`TARG-01`).
- `@borrow`. Field, record component (`OWN-09`).
- `@mut`. Local, field, parameter, return. In a type argument only when the enclosing generic is `@mut` (`TARG-03`).
- `@take`. Parameter only. Rejected on fields, locals, and generic type arguments (`OWN-10`, `TARG-02`).


### buffer splitting
Dividing a contiguous region into two non-overlapping views. Single-thread: `T[].splitAt` → `@bound Pair<@bound @mut T[], @bound @mut T[]>` (borrowed halves); `forEachChunk` → borrowed slices via callback. Cross-thread: `T[].splitOff` → `Pair<T[], T[]>` (owning halves); `Arrays.stream(@bound T[])` → `Stream<T>` for read-only parallel processing via `Spliterator`. See `ARR-01`, `ARR-02`, `ARR-04`.

### call mode
A property of a functional-interface *type*: the receiver mode of its single abstract method. **shared-call** (bare SAM — invocable through a shared borrow), **mut-call** (`@mutating` SAM — invocable through a `@mut` variable), or **once-call** (`@consuming` SAM — invocable once, consuming the value). The `Fn` / `FnMut` / `FnOnce` distinction, carried on the SAM. Distinct from the *variable mode* of the variable that holds the value. See `CLO-03`.

### Cell<T>
An interior-mutability primitive permitting mutation of contents through a non-`@mut` variable. The only way to implement mutable state inside a type that is otherwise immutable. Requires `@unsafe` context per `UNS-02`. Similar to Rust's `UnsafeCell<T>`.

### clone() method
An auto-generated method creating a copy of an object. By default, the compiler synthesizes `clone()` as `return new Self(this);`. User code may override it (including with a body that reaches `broken()` to forbid copying). See `OBJ-02`.

### closure
An anonymous function (lambda) that may capture variables from an enclosing scope. Laterita classifies closures by capture mode: **read** (borrows captured variables, can run many times), **mutate** (borrows mutably, runs sequentially), or **consume** (moves captured variables, runs exactly once). See `CLO-01`.

### consume / give (also "moved")
To transfer ownership of a value from one variable to another, or to invoke a method that consumes its receiver. Once consumed, the original variable is no longer usable. Marked at the call site with `give(x)`, a static method on `laterita.lang.Intrinsics` normally statically imported as `give`. In Rust, this is a "move"; Laterita uses the verb `give` in Java's vocabulary.

### @consuming (annotation)
Declares that a method consumes its receiver — the body owns `this`, and after the call returns the variable that held the receiver is consumed and subsequent uses are rejected. A modifier-position annotation on the method, parallel to `@mutating` (MUT-08); the two compose. See `OWN-15`.

### contravariantly
An overriding method may **require less** of its parameters than the base method. For example, if the base declares `@mut T`, the override may drop `@mut` and declare a bare (immutable) borrow—the override is less strict, so any caller satisfying the base contract satisfies the override. See `HIER-05`.

### copy constructor
A constructor that takes a single parameter of the same type as the class being constructed (e.g., `new User(User source)`). Used to duplicate an object. Laterita auto-generates one per `OBJ-01` if not provided.

### divergence point / diverges
A code path that reaches `broken()` (a static method declared in `laterita.lang.Intrinsics`) that must not be reachable. If the compiler can prove the path is reachable, it reports an error. Code following `broken()` is dead code. The method has return type `Nothing` (the bottom type). See `UNR-01`.

### drop / onDrop()
To clean up a value when its owning variable leaves scope. The compiler automatically calls the value's `onDrop()` method at every scope exit (normal return, exception, break, continue, etc.). A `final` class implements `onDrop()` to release resources (files, locks, memory); non-`final` classes hold resources by composition instead. See `DROP-01`, `DROP-09`.

### drop flag
Compiler bookkeeping tracking whether each field of a partially-moved value is still owned. Used to emit correct `onDrop()` calls when only some fields remain. See `DROP-04`.

### exclusive / exclusivity (also "mutual exclusion")
Only one mutable borrow may exist at a time. No other borrows (mutable or immutable) may coexist with a mutable borrow. This prevents data races and iterator invalidation at compile time. See `OWN-03`.

### field (in a struct/class)
A named member variable of a class. Laterita distinguishes between immutable fields (default) and mutable fields (annotated `@mut`). Fields are initialized exactly once in constructors and follow ownership rules like variables. See `OWN-09`.

### functional interface (also "function type")
An interface with a single abstract method (SAM: Single Abstract Method), or an anonymous structural form written inline as `(P1, P2, ...) -> R`. The anonymous form is legal as a parameter type, return type, generic bound, or generic type argument (FN-04); fields and declared local types use a nominal functional interface instead. `.lat`-only per LAT-05; `.java` sources use a nominal functional interface at the same position. The anonymous form admits an optional `@mutating` or `@consuming` prefix that declares the SAM's receiver mode — its call mode (CLO-03). Laterita treats nominal and anonymous forms uniformly. Used for callbacks, functional operations, and closure types. See `FN-01`.

### give (static method on `laterita.lang.Intrinsics`)
The move-expression carrier. An ordinary stdlib helper, `static <T> T give(@take T t) { return t; }` in `laterita.lang.Intrinsics`, normally statically imported. `give(x)` consumes `x` via `@take` and returns its owned value; `var b = give(a)` rebinds; `give(x);` as a statement leaves the result unbound and drops it at the semicolon (OWN-07). Method-level receiver consumption is *not* spelled `give`; it is the `@consuming` annotation on the method (OWN-15).

### Heap<T>
A raw heap-allocation primitive. Provides direct allocation and deallocation. All operations require `@unsafe` context per `UNS-02`. Rarely used by application code; typically wrapped by smart pointers like `Rc<T>` or `Arc<T>`.

### @internal (annotation)
An annotation marking that a method may only be called by compiler-emitted code, never by user code. Used exclusively for `onDrop()`. See `DROP-06`.

### interior mutability
The ability to mutate an object's contents through a non-`@mut` (immutable) variable. Breaks the default rule that mutation requires `@mut` at every level. Implemented only through `Cell<T>` in safe code. See `MUT-11`.

### invariantly
An overriding method's parameter must **match exactly** the base method's parameter. No relaxation allowed. See `HIER-05`. (Contrast with contravariance.)

### .lat / .java (source file extensions)
The two file extensions accepted by `latc`. `.lat` admits the full surface, including `T?`, `?.`, `?:`, `!!`, and inline FI types `(P1, …, Pn) -> R`. `.java` is the Java-compatible subset; the `.lat` forms and their `.java`-surface desugarings are specified in §21 (`LAT-00`–`LAT-05`). Both extensions share the same type system, annotations, and intrinsics.

### latc (laterita compiler)
The reference laterita compiler. Accepts `.lat` and `.java` in a single compilation unit, dispatches by extension per `COMP-06`, and emits artifacts per `COMP-01`–`COMP-04`. See `COMP-07`.

### lifetime
The span of time during which a variable is valid. A borrowed variable's lifetime is bounded by the variable it borrows from; it cannot outlive the referent. A compiler error to use a variable after the value it refers to is dropped. See `LIFE-01`.

### @local (annotation on types)
Declared on a class to express its relationship to threads. `@local` (or `@local(true)`) pins instances to a single thread — they cannot be moved across threads or captured by closures that might run on other threads. `@local(false)` asserts the inverse: the class encapsulates any transitively `@local` fields and is safe to use across threads. A class with `@local` fields must carry one form or the other explicitly. Examples: `Rc<T>`, `Cell<T>` are `@local`; `Arc<T>`, `Mutex<T>`, `Thread` are `@local(false)`. See `STD-07`.

### monomorphization
The compile-time process of specializing generic code. Each instantiation of a generic type or method (e.g., `List<String>` and `List<int>`) generates a separate implementation. See `COMP-02`.

### @mut (annotation)
The unified marker for variable mutability. Appears on: variables (`@mut var x = ...`), fields (`@mut int count`), parameters (`@mut T param`), and class declarations (`@mut class C`, `MUT-05`). Conveys "this can change." A `@mut` class has a mutable surface; a class without the marker is a value class. A method that mutates its receiver is marked with the companion annotation `@mutating`, not `@mut`. See `MUT-01`, `MUT-08`, `MUT-05`.

### @mutating (annotation)
Declares that a method may mutate its receiver — reassign or mutate-through the receiver's `@mut` fields, and call other `@mutating` methods on `this`. A declaration annotation on the method, kept a distinct token from `@mut` so receiver mutation is not spelled like variable mutability. By `MUT-10` a `@mutating` method is callable only on a `@mut` receiver. See `MUT-08`.

### mutable borrow / mut borrow
A borrow that grants both read and write access to the borrowed value. Only one mutable borrow may be active at a time; no immutable borrows may coexist with it. A mutable borrow requires the source variable to be `@mut` or the borrow to occur within a `@mutating` method of the same object. See `OWN-13`, `OWN-03`.

### Mutex<T>
A mutual-exclusion primitive wrapping an owned value. Access is scoped to a closure: `with(@mut @mutating (@mut T) -> R)` and `tryWith(...)` acquire the lock, run the closure on the protected value, release the lock, and return the closure's result. The action slot is mut-call so the closure may capture state by mutable borrow. The mutex is poisoned (`THR-10`) if the closure throws. See `STD-09`.

### newtype
An idiom, not a named language construct. A `record` annotated `@Delegate` on its sole component is a newtype: the compiler generates its full forwarding surface (`GEN-01`), the single-field layout guarantee (`NABI-01`) ensures it has the same size and ABI as the wrapped value, and inlining (`COMP-08`) collapses the forwarders to direct calls. The result is a distinct nominal type (not a subtype of the component's type, no implicit widening) at zero runtime overhead. `record Email(@Delegate @take String raw)` and `record UserId(@Delegate @take Long raw)` are newtypes by idiom.

### nullable type (also `T?` / `@Nullable T`)
A type that admits both a value and the special value `null`. Written `T?` in `.lat` sources and `@Nullable T` in `.java` sources (`@Nullable` declared in `laterita.lang.annotation`). Different from Java's implicit nullability; a bare `T` in Laterita is non-nullable. See `NULL-02`, `LAT-01`.

### @Delegate (annotation on fields)
Placed on a field or record component, it causes the compiler to generate, for every `public` instance method of the field's declared type, a forwarding method on the owner that calls the same method on the field. `Object` and `static` methods are not forwarded. Forwarder return types are the source method's own (they *decay*), and ownership annotations (`@consuming`, `@mutating`) are propagated. An explicitly declared method shadows the forwarder of the same signature, which is how part of a forwarded surface is overridden. `@Delegate` on a `@Nullable` field, a signature clash between two `@Delegate` fields, and cyclic delegation are compile errors. See `GEN-01`.

### @Operator (annotation on methods)
Marks an instance method as the desugaring target for an arithmetic operator in `.lat` sources. The annotation names the operator — `@Operator(PLUS)`, `MINUS`, `TIMES`, `DIVIDE`, `NEGATE` — and the method name is unconstrained, so `BigDecimal.add`, `Instant.plus`/`minus`, and `Duration.negated` qualify under their existing names. Arity must match the operator (one parameter for the binary kinds, zero for `NEGATE`). `a + b` then means the annotated `PLUS` method call on `a`. The operator set is bounded (no `%`, `[]`, or compound assignment) with no user-defined or trait-based overloading; the comparison operators `< <= > >=` desugar separately through `Comparable.compareTo`, needing no annotation. See `LAT-07`.

### onDrop()
A method the compiler invokes to clean up a value. Only a `final` class may implement it with a body (`DROP-09`); a class without an implementation contributes no body. The compiler runs the implementation (if any) as step 1 of the value's drop sequence (`DROP-05`: own body, then own fields in reverse, then each superclass), and triggers the drop sequence on every variable that leaves scope, in reverse declaration order (`DROP-01`, `DROP-02`).

### OQ (prefix in OQ-N)
"Open Question." A numbered entry in the open-questions document listing unresolved language-design decisions. Example: OQ-20 (pattern matching and destructuring under ownership). Not part of the normative spec.

### Pair<L, R>
General-purpose record in `laterita.lang` carrying two values. The same declaration covers owned, borrowed, and mixed cases — driven by what is substituted for `L` and `R` per TARG-01. Instantiated as `Pair<T[], T[]>` by `T[].splitOff` (owned halves; accessors participate in partial-move tracking, OWN-06) and as `@bound Pair<@bound @mut T[], @bound @mut T[]>` by `T[].splitAt` (borrowed mutable halves). See `ARR-04`.

### ownership
Having the right and obligation to drop (clean up) a value when done. An owned variable can move the value to another variable, pass it to a `@take` parameter, or drop it at scope exit. Only one variable can own a value at a time. See `OWN-01`.

### override variance
The rules governing whether an overriding method's signature may differ from the base method's. One principle: an override may **demand less** of its callers (parameters, receiver) and **guarantee more** to them (return), never the reverse. `@take` on a parameter is invariant; `@mut` on a parameter, `@bound` on a parameter or return, `@mutating`, `@consuming`, and class `@mut` may all be dropped, never added. The FI-slot call-mode axis inverts surface direction — override may *strengthen* the slot (bare → `@mutating` → `@consuming`) — because the annotation governs closure acceptance, not parameter variable. See `HIER-05` for the unified table.

### parameter mode / ownership mode
How a parameter receives its argument: bare (borrows the argument), `@mut` (borrows mutably), `@take` (receives ownership), or `@take @mut` (receives ownership and the slot is reassignable). See `OWN-13`.

### partial move
Moving a value out of a field while other fields of the same object remain owned. The compiler tracks which fields are moved and which remain, emitting `onDrop()` only on the unmoved fields. Available only on classes that implement no `onDrop()`. A class with an `onDrop()` body is moved whole (`DROP-08`). See `OWN-06`.

### poisoned (Mutex)
A `Mutex<T>` marked as unusable because the closure passed to its `with` / `tryWith` call propagated an exception out of the critical section. Subsequent attempts to acquire the lock throw `PoisonedException`. The mutex can only be recovered by replacing it entirely. See `THR-10`.

### Rc<T>
A reference-counted smart pointer for single-threaded shared ownership. Like Java's garbage collector but manual: each holder holds a handle, the refcount is explicitly bumped with `.share()`, and the value is freed when the refcount reaches zero. Single-threaded only; use `Arc<T>` for cross-thread sharing. See `STD-01`.

### ReentrantLock
A reentrant mutual-exclusion primitive in `laterita.lang` that owns no data — the lock alone. Modelled on `java.util.concurrent.locks.ReentrantLock` but with safer surface: `lock()` returns a `LockGuard` whose `onDrop` releases the lock, so "forgot to unlock" is impossible. Use when the data being guarded does not fit `Mutex<T>` (state spread across several fields of `this`, or genuinely data-less coordination). Pair with `Condition` for `wait`/`signal` patterns. See `STD-10`.

### LockGuard
A value witnessing that the calling thread holds a `ReentrantLock`. Returned by `ReentrantLock.lock` / `lockInterruptibly` / `tryLock`. Owns one acquisition; releases it via `onDrop` when its scope ends. The owning scope is therefore the critical section. See `STD-11`.

### Condition
A condition variable bound to a `ReentrantLock`, created by `lock.newCondition()`. `await` atomically releases the bound lock and blocks; on signal, re-acquires. `signal` / `signalAll` wake waiters. Names and shapes match `java.util.concurrent.locks.Condition`. The "caller must hold the bound lock" precondition is a runtime check — laterita does not statically associate a `Condition` with a specific `LockGuard` lifetime. See `STD-12`.

### receiver mode (of a method)
How a method accesses its receiver (`this`): bare (read-only), mutating (declared by `@mutating` on the method, MUT-08), or consuming (declared by `@consuming` on the method, OWN-15). The receiver's variable mode must support the receiver mode (e.g., a bare variable cannot call a `@mutating` method).

### safe / unsafe (code)
**Safe code** obeys all ownership and lifetime rules, checked by the compiler. **Unsafe code** is a method annotated `@unsafe` that performs operations otherwise forbidden (raw memory access, cross-thread moves of `@local` types, etc.). The compiler still type-checks `@unsafe` methods; the annotation only unlocks specific operations per `UNS-02`. See `UNS-01`.

### SAM (Single Abstract Method)
The one abstract method of a functional interface. In a lambda or method reference targeting a functional interface, the body must implement the SAM. Parameter and return modes of the SAM are declared as part of the interface. An anonymous functional interface's SAM is named `apply`, and a value `f` is invoked as `f.apply(...)`. See `FN-01`.

### shared borrow / immutable borrow
A borrow that grants read-only access to a borrowed value. Any number of shared borrows may coexist. A shared borrow does not require the source variable to be `mut`. See `OWN-02`, `OWN-03`.

### slice (of a String or array)
A borrowed view into a contiguous region of a String or array. Methods like `substring`, `trim` (on String) and `slice` (on arrays) return a borrowed view (marked `@bound`), not a new copy. The borrow is bounded by the original's lifetime. See `STR-03`, `OWN-05`.

### static borrow
A borrow with a static lifetime — one that is guaranteed to live for the entire program execution. String literals in Laterita are static borrows: they reside in read-only program memory and can be safely borrowed by any variable without lifetime restrictions. See `STR-06`.

### static lifetime
A lifetime that spans the entire program execution. Values with static lifetime (such as string literals) can be borrowed without restriction in any context. The static lifetime is the broadest possible scope, permitting a borrow to flow freely without being tied to a particular variable's scope. See `STR-06`, `LIFE-01`.

### string literal
A quoted string expression in source code (e.g., `"hello"`), which has type `@bound String` with a static lifetime. The literal is not a heap allocation; it resides in the program's read-only memory segment. A variable initialized from a literal is borrowed; to obtain an owned heap-allocated `String`, call `.clone()`. See `STR-06`.

### smart pointer
A wrapper type that manages a value's lifetime. Examples: `Rc<T>` (reference-counted, single-threaded), `Arc<T>` (atomic reference-counted, multi-threaded). Smart pointers carry `onDrop()` to enforce cleanup.

### static analysis
Compile-time reasoning about program behavior without running the code. Laterita's compiler performs static analysis of ownership, borrows, lifetime, mutability, and reachability to catch errors before runtime.

### static field
A field declared `static` — class- or module-level storage with one instance per program. Immutable per `STAT-01` and initialized from a const expression; `@mut static` is rejected. The declared type must be non-`@local` (`STAT-03`). Shared mutable program-wide state is expressed by storing a `Mutex<T>` (`STD-09`), `Arc<T>` (`STD-02`), or an atomic primitive in the immutable slot.

### target typing
Inferring a lambda's type from the context where it appears. If a lambda is assigned to a variable or parameter with a known functional-interface type, the type is used as a hint to type-check the lambda body. See `CLO-04`.

### @take (annotation)
Declares that a parameter receives ownership of its argument (consumed upon call). At the call site, a bare variable passed to a `@take` parameter is implicitly consumed (equivalent to `give(variable)`); or explicitly written as `give(variable)`. See `OWN-13`. (Receiver consumption is the separate `@consuming` annotation on the method — OWN-15.)

### thread-affine (also "thread-local")
A type or resource bound to a specific thread and cannot safely be moved to another thread. In Laterita, expressed via the `@local` annotation. Examples: `Rc<T>`, `Thread.local` storage. See `STD-07`.

### transitivity (of mutability)
Immutability propagates through a variable. A bare (immutable) variable cannot call `@mutating` methods on the held object and cannot mutate its fields. To mutate, every level of access must be `@mut`. See `MUT-10`, `MUT-09`.

### type-inferred variable
A variable whose type is inferred from the RHS expression rather than written explicitly. Forms: `var name = expr` (immutable, inferred), `@mut var name = expr` (mutable, inferred). In laterita mode `var` is immutable by default; the `@mut` annotation opts in to mutability. See `MUT-02`.

### type narrowing / smart cast
Refining a variable's type along a conditional path. Most common with nullable types: after `if (x != null) { ... }`, the variable `x` is narrowed from `T?` to `T` within the block, and methods on `T` are callable without further checks. See `NULL-06`.

### unwind (exception)
The process of propagating an exception up the call stack, running cleanup (`onDrop()` and `finally` blocks) at each frame before the exception reaches the next handler. See `EXC-02`, `EXC-03`.

### use-after-move
An error where a variable is used after its value has been moved elsewhere. The compiler rejects such code statically. See `OWN-07`.

### value class
A class not declared `@mut`. A value class declares no `@mut` fields and exposes no callable `@mutating` method, so its instances cannot be mutated through any variable. It may inherit `@mut` members from a `@mut` ancestor — they are present but not callable on it (`MUT-10`) — and may still hold `Cell<T>` interior-mutable state. The value class is the default; `@mut` is the opt-in. `String`, `Number`, and every `record` are value classes. See `MUT-05`, `HIER-01`.

### WeakReference<T>
A non-owning reference to a value managed by `Rc<T>` or `Arc<T>`. The weak reference is not counted toward the refcount and does not prevent the value from being freed. Calling `get()` returns an `Rc<T>?` or `Arc<T>?` (a strong reference if the value still lives). See `STD-03`.

---

## Notation and Abbreviations

### Code Notation in the Spec

| Notation | Meaning |
|----------|---------|
| `T`, `U`, etc. | Type variable; represents any type |
| `T?` | Nullable version of type `T` (`.lat` form; `.java` writes `@Nullable T` per LAT-01) |
| `(T1, T2, ..., Tn) -> R` | Anonymous functional interface taking `T1, ..., Tn` and returning `R`; shared-call by default. Prefix with `@mutating` or `@consuming` for mut-call / once-call. Legal as parameter, return, generic bound, or generic type argument per FN-04. (`.lat`-only per LAT-05) |
| `variable:` or `method:` or `parameter:` | Marks the following code snippet's scope (e.g., method signature, local variable) |

### Spec Code Prefixes

Each requirement in the spec carries a mnemonic code for cross-reference. Codes are grouped by area:

| Prefix | Area |
|--------|------|
| `OWN` | Ownership: owned vs. borrowed variables, move and borrow rules, `@take` / `@borrow` / `@bound`, `@consuming` |
| `LIFE` | Lifetime intersection across multiple borrow sources |
| `MUT` | Mutability rules — `@mut` marker, `@mutating` methods, transitivity, interior mutability |
| `HIER` | Class hierarchy: `@mut` / value-class inheritance, value subclass freeze, no-widening, override variance |
| `TARG` | Annotations admitted inside generic type arguments |
| `STAT` | Static field rules |
| `NULL` | Nullable types, null safety |
| `DROP` | Scope-exit cleanup, `onDrop()` |
| `OBJ` | Copying, clone semantics |
| `UNR` | Unreachable paths (`broken()`) |
| `STR` | String ownership and slicing |
| `ARR` | Array methods and the `laterita.lang.Arrays` static surface |
| `FN` | Functional interfaces and anonymous function types |
| `CLO` | Closures and lambda capture |
| `EXC` | Exception handling and unwind semantics |
| `UNS` | Unsafe code and privileged operations |
| `STD` | Standard library types (`Rc<T>`, `Arc<T>`, `Mutex<T>`, `@local` marker, etc.) |
| `THR` | Threading, interrupts, `Thread.onDrop()`, lock poisoning |
| `COMP` | Compilation model (monomorphization, reflection, etc.) |
| `LAT` | `.lat` surface forms (syntactic sugar over the Java-compatible surface) |
| `NABI` | Native ABI guarantees |
| `GEN` | Code generation annotations (Lombok-compatible surface) |

---

## Java Analogies

For junior Java developers, here are key Rust/Laterita concepts mapped to Java:

| Rust / Laterita | Java Analog | Difference |
|-----------------|------------|-----------|
| `Rc<T>` (single-threaded) | Variable (with manual refcount) | Java uses GC; Laterita requires explicit `.share()` and tracks refcount |
| `Arc<T>` (multi-threaded) | Variable (with atomic refcount) | Like `Rc<T>`, but thread-safe; less common in Java due to GC |
| Ownership + `give(...)` | Explicit transfer | Java has no ownership concept; all variables are borrows |
| Borrow (`&`) | Variable | Similar to Java; lifetime rules are stricter |
| `@mut` (mutable borrow) | Non-final reference to mutable object | Like Java, but enforced at compile time |
| `@mut class` vs. value class | Valhalla `value class` (inverted) | Valhalla opts *in* to value classes; Laterita opts *in* to mutable classes — the value class is the default |
| `@local` annotation | Thread-local or thread-affine concept | Java doesn't have language-level thread-affinity for types |
| `Cell<T>` | `AtomicReference<T>` (simplified) | Like atomics, but for single-threaded interior mutability; no GC hazard |
| `Mutex<T>` | `synchronized` block on a protected field | Closure-scoped API ensures lock release and ties the lock to the protected value |
| `ReentrantLock` + `LockGuard` | `java.util.concurrent.locks.ReentrantLock` | `LockGuard.onDrop` removes the manual unlock; reentrant; pair with `Condition` for wait/signal |
| `Condition` | `java.util.concurrent.locks.Condition` or `Object.wait`/`notify` | Same API as `j.u.c.l.Condition`; runtime-checks the bound lock is held |
| `onDrop()` | `close()` or finalizer | Guaranteed-called cleanup per object; closer to C++ destructors than Java finalizers |
| `drop` flag | N/A | Java doesn't track per-field move state |

---

## Further Reading

Readers new to ownership and borrowing should start with §1–§3 of `laterita-spec.md` (Ownership, Lifetimes, Mutability) and then read the corresponding sections of `laterita-reasoning.md` to understand the design trade-offs.

For specific term definitions, cross-reference the spec code (e.g., `OWN-02`, `MUT-01`) listed in the spec document itself.
