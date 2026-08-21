# Laterita — Terminology and Abbreviations

This document explains terms, abbreviations, and keywords used throughout the Laterita specification that may be unfamiliar to a junior Java developer.
It assumes familiarity with core Java syntax and OOP but not with Rust concepts or advanced compiler terminology.

Use this document as a reference when reading the spec.
Terms appear in alphabetical order.

---

## Terms

### Arc<T>
An atomic reference-counted smart pointer for shared ownership across thread boundaries.
Similar to `Rc<T>` but with atomic (thread-safe) refcount operations.
See `Rc<T>` for the single-threaded version.
Like Java's garbage-collected heap, `Arc<T>` lets multiple holders reference the same value, but you must explicitly call `.share()` to create an alias, and the value is freed when the last reference drops.

### variable
A local variable, field, or parameter that holds a value.
In Laterita, every variable declares whether it owns the value (can move it, can drop it) or borrows it (reads only, or reads with mutation rights).
Variables are the primary unit of lifetime tracking.

### borrow / borrowed variable
A variable that refers to a value owned elsewhere, rather than owning it itself.
A borrowed variable cannot be moved.
When it leaves scope, the compiler does not invoke `onDrop()`.
There are two kinds: shared (immutable) and mutable.
See `OWN-03` for the rules.

### @borrow (annotation on fields, record components, type arguments, and parameters)
Declares that a slot is a borrow slot rather than an owned slot: a field, a record component, or a generic type argument (`TARG-01`).
An instance of a class containing any `@borrow` slot can only be produced as a `@bound` value, with lifetime intersecting each source (LIFE-03).
On a parameter it is meaningful only with `@take`: a `@take @borrow` parameter retains the borrow and caps `this` at the parameter's source by signature (`OWN-21`), while a bare `@borrow` parameter equals a plain borrow.
It names no source, the structural role, distinct from `@bound`, which marks a borrowed *value* whose source is named.
See `OWN-09`, `OWN-21`, `LIFE-03`, `TARG-01`.

### @bound (annotation on returns and parameters)
Declares a lifetime relationship between two values.
On a parameter, declares that the function's return is bound to that parameter (`OWN-17`).
On a return type, declares that the return is bound to `this` (`OWN-18`).
It names a source, the relational role, distinct from `@borrow`, which declares a borrow slot whose source is fixed elsewhere.

### @own (annotation on type parameters)
Declares that a type parameter rejects a borrowed type argument: `class C<@own T>` admits `C<Foo>` but not `C<@borrow Foo>`.
The dual of `@borrow`, and the analog of a `'static` bound in Rust.
Applied to the owning containers `Arc` (`STD-02`) and `Mutex` (`STD-09`).
See `TARG-06`.

### @borrowCapped (annotation on classes)
Marks a class whose instance keeps its borrows live until the instance goes out of scope, so `onDrop()` may access them.
See `LIFE-04`, `DROP-11`.

### variable modifiers
`@bound`, `@fixed`, `@take`, `@borrow`, and `@own`.
Legal positions:
- `@bound`.
Parameter, return (`OWN-17`, `OWN-18`).
- `@borrow`.
Field, record component, generic type argument, and parameter with `@take` (`OWN-09`, `TARG-01`, `OWN-21`).
- `@fixed`.
Local, field, parameter, return, type argument, type-parameter declaration, and class or interface declaration.
The only mutability marker, withdrawing referent mutability wherever it appears (`MUT-01`, `TARG-03`).
- `@take`.
Parameter only.
Rejected on fields, locals, and generic type arguments (`OWN-10`, `TARG-02`).
- `@own`.
Type parameter declaration (`TARG-06`).

### bitwise copy
A field-for-field copy of a value's raw bytes, with no user code run.
The synthesized copy constructor copies primitive fields this way and clones object fields instead (`OBJ-01`).

### boxing / unboxing
Converting a primitive to its wrapper reference type (`int` to `Integer`) and back.
Laterita does not auto-box at the type level, so a null-bearing integer is written `@Nullable Integer` rather than a nullable primitive (`NULL-02`).

### buffer splitting
Dividing a contiguous region into two non-overlapping views.
Single-thread: `T[].splitAt` → `@bound Pair<@borrow T[], @borrow T[]>` (borrowed halves, lending mutably or read-only as the receiver does per `MUT-17`, spelled as the two static methods `splitAt` and `splitMutableAt` in the `.java` mirror), `forEachChunk` → borrowed slices via callback.
Cross-thread: `T[].splitOff` → `Pair<T[], T[]>` (owning halves), `Arrays.stream(@bound T[])` → `Stream<T>` for read-only parallel processing via `Spliterator`.
See `ARR-01`, `ARR-02`, `ARR-04`.

### call mode
A property of a functional-interface *type*: the receiver mode of its single abstract method.
**shared-call** (bare SAM, invocable through a shared borrow), **mut-call** (`@mutating` SAM, invocable through a mutable variable), or **once-call** (`@consuming` SAM, invocable once, consuming the value).
The `Fn` / `FnMut` / `FnOnce` distinction, carried on the SAM.
Distinct from the *variable mode* of the variable that holds the value.
See `CLO-03`.

### Cell<T>
An interior-mutability primitive permitting mutation of contents through a `@fixed` variable.
The only way to implement mutable state inside a type that is otherwise immutable.
Requires `@unsafe` context per `UNS-02`.
Similar to Rust's `UnsafeCell<T>`.

### clone() method
An auto-generated method creating a copy of an object.
By default, the compiler synthesizes `clone()` as `return new Self(this);`.
User code may override it (including with a body that reaches `broken()` to forbid copying).
See `OBJ-02`.

### closure
An anonymous function (lambda) that may capture variables from an enclosing scope.
Laterita classifies closures by capture mode: **read** (borrows captured variables, can run many times), **mutate** (borrows mutably, runs sequentially), or **consume** (moves captured variables, runs exactly once).
See `CLO-01`.

### const expression
An expression the compiler can evaluate at compile time: a literal, a reference to another const-initialized static, or a call to a const-eligible constructor or function.
Every static field initializer must be one (`STAT-02`).

### const-eligible operations
The operations admitted inside a const expression, fixed by the compiler and standard library.
They cover at minimum primitive arithmetic, string literals, and the const-eligible constructors of the synchronizing stdlib types (`STAT-02`).

### consume / give (also "moved")
To transfer ownership of a value from one variable to another, or to invoke a method that consumes its receiver.
Once consumed, the original variable is no longer usable.
Marked at the call site with `give(x)`, a static method on `laterita.lang.Intrinsics` normally statically imported as `give`.
In Rust, this is a "move", Laterita uses the verb `give` in Java's vocabulary.

### @consuming (annotation)
Declares that a method consumes its receiver: the body owns `this`, and after the call returns the variable that held the receiver is consumed and subsequent uses are rejected.
A modifier-position annotation on the method, parallel to `@mutating` (MUT-13), the two compose.
See `OWN-15`.

### @fixed (annotation)
The single mutability marker: it withdraws *referent mutability*, the right to mutate a value through a binding by calling a `@mutating` method on it or writing through it.
Mutability is granted by default at every position whose declared type is a mutable class, and nothing grants it, so `@fixed` is the only word on the axis (`MUT-01`).
It is orthogonal to reassigning the binding itself, the *slot*, which is on by default and locked by `final` (`MUT-20`).
It is redundant where the position is already immutable and load-bearing everywhere else (`MUT-31`).
On a type-parameter declaration `<@fixed T>` writes `@fixed` at every usage of `T` and leaves the bound unchanged (`TARG-03`).
On a class declaration `@fixed class C` declares an immutable class (`MUT-10`).
On a type, `@fixed C` names the *frozen view* of `C` (`MUT-30`).
A method that mutates its receiver is marked with the companion annotation `@mutating`, not `@fixed`.
See `MUT-01`, `MUT-30`, `MUT-31`.

### contravariantly
An overriding method may **require less** of its parameters than the base method.
For example, if the base declares a bare `T` parameter, the override may add `@fixed` and declare a shared borrow: the override is less strict, so any caller satisfying the base contract satisfies the override.
See `HIER-05`.

### copy constructor
A constructor that takes a single parameter of the same type as the class being constructed (e.g., `new User(User source)`).
Used to duplicate an object.
Laterita auto-generates one per `OBJ-01` if not provided.

### destruction
Taking an owned object apart field by field, by `give`-ing a directly named accessible field (`give(obj.field)`).
Works on a POJO in any source, and on a `record` only in `.lat`, where its components are public (`LAT-08`).
A record's accessor returns a borrow and cannot be moved.
An object is destructed as soon as its first field is moved out: from then on no method may be called on it, its fields may not be assigned, and it cannot be returned or stored, only taken further apart.
Its lifetime ends at that point (DES-02) and its formerly owned fields live on independently in the scope that received them (OWN-06).
The scope drops each such field at its end like any owned variable, unless the field is moved on first, tracking per field whether it survives (DROP-04).
Available only on classes that implement no `onDrop()`.
A class with an `onDrop()` body is moved whole (`DROP-08`).
See the `DES` topic.

### divergence point / diverges
A code path that reaches `broken()` (a static method declared in `laterita.lang.Intrinsics`) that must not be reachable.
If the compiler can prove the path is reachable, it reports an error.
Code following `broken()` is dead code.
The method has return type `Nothing` (the bottom type).
See `UNR-01`.

### drop / onDrop()
To clean up a value when its owning variable leaves scope.
The compiler automatically calls the value's `onDrop()` method at every scope exit (normal return, exception, break, continue, etc.).
A `final` class implements `onDrop()` to release resources (files, locks, memory), non-`final` classes hold resources by composition instead.
See `DROP-01`, `DROP-09`.

### drop flag
Compiler bookkeeping tracking whether each field of a destructed value is still owned.
Used to emit correct `onDrop()` calls when only some fields remain.
See `DROP-04`.

### demanding use
A use of a local that requires mutation: calling a `@mutating` method on it, writing through it, passing it to a mutable slot, or returning it through a mutable return type.
A local with one borrows its source mutably, and a local with none is *effectively fixed* (`MUT-60`).
A demanding use of an immutable local is rejected (`MUT-15`).
See `MUT-60`.

### effectively final
A non-`final` local that is never reassigned.
Its slot is fixed, so borrow analysis treats it as locked (`MUT-61`).
A closure may capture only an effectively final local, exactly Java's lambda rule (`CLO-01`).

### effectively fixed
A mutable local none of whose uses demands mutation: no `@mutating` call on it, no write through it, no passing it to a mutable slot, and no returning it through a mutable return type.
Such a local borrows its source shared, so several of them over one value coexist (`OWN-03`).
It is the borrow-mode counterpart of *effectively final*, classified from the uses the same way and for the same reason (`MUT-60`).

### fixed (static method on `laterita.lang.Intrinsics`)
`fixed(x)` returns a `@fixed @bound` borrow of `x`, the expression-position form of the `@fixed` downgrade.
The original stays usable and several frozen views coexist.
See `MUT-42`.

### frozen view
`@fixed C`, the interface carrying only the members of `C` that need no mutability.
Every mutable `C` implements it, so a `C` value fills a `@fixed C` slot and not the reverse.
The views are ordered like the types they view, so `@fixed Object` is the top type (`MUT-30`).
It is not a type a class declaration names, so implementing it makes no class immutable (`HIER-01`).
An immutable class declared under a mutable ancestor is the declaration-site form of the same thing (`HIER-03`, `HIER-04`).

### erased parameter types
A signature's parameter types with generic type arguments removed, as under Java's erasure.
A hand-written member shadows a generated one when their names and erased parameter types match (`GEN` topic).

### exclusive / exclusivity (also "mutual exclusion")
Only one mutable borrow may exist at a time.
No other borrows (mutable or immutable) may coexist with a mutable borrow.
This prevents data races and iterator invalidation at compile time.
See `OWN-03`.

### field (in a struct/class)
A named member variable of a class.
A field has the same two orthogonal mutability axes as a local (`MUT-21`, `MUT-22`): its slot is reassignable unless `final` (and only where the class and receiver are mutable), and it is mutated through unless annotated `@fixed`.
Every field of an immutable class is `final` and `@fixed` (`MUT-22`).
Every field is initialized exactly once in a constructor (`OWN-11`) and follows ownership rules like a variable.
See `OWN-09`, `MUT-21`, `MUT-22`.

### flow-sensitive
Analysed per program path rather than once per declaration.
Ownership, borrow state, and null narrowing are tracked flow-sensitively, so a variable's status may differ between two branches (`OWN-02`, `LIFE-01`, `NULL-06`).

### functional interface (also "function type")
An interface with a single abstract method (SAM: Single Abstract Method), or an anonymous structural form written inline as `(P1, P2, ...) -> R`.
The anonymous form is legal as a parameter type, return type, generic bound, or generic type argument (FN-04), fields and declared local types use a nominal functional interface instead.
`.lat`-only per LAT-05, `.java` sources use a nominal functional interface at the same position.
The anonymous form admits an optional `@mutating` or `@consuming` prefix that declares the SAM's receiver mode, its call mode (CLO-03).
Laterita treats nominal and anonymous forms uniformly.
Used for callbacks, functional operations, and closure types.
See `FN-01`.

### give (static method on `laterita.lang.Intrinsics`)
The move-expression carrier.
An ordinary stdlib helper, `static <T> T give(@take T t) { return t; }` in `laterita.lang.Intrinsics`, normally statically imported.
`give(x)` consumes `x` via `@take` and returns its owned value.
`var b = give(a)` rebinds.
`give(x);` as a statement leaves the result unbound and drops it at the semicolon (OWN-07).
Method-level receiver consumption is *not* spelled `give`.
It is the `@consuming` annotation on the method (OWN-15).

### Heap<T>
A raw heap-allocation primitive.
Provides direct allocation and deallocation.
All operations require `@unsafe` context per `UNS-02`.
Rarely used by application code, typically wrapped by smart pointers like `Rc<T>` or `Arc<T>`.

### @internal (annotation)
An annotation marking that a method may only be called by compiler-emitted code, never by user code.
Used exclusively for `onDrop()`.
See `DROP-06`.

### immutable class
A class or interface declared `@fixed`, or inheriting immutability from an immutable supertype (`HIER-01`).
Its fields are `final` and `@fixed`, and it declares no `@mutating` method, so its instances cannot be mutated through any binding (`MUT-10`, `MUT-22`).
It may inherit mutable members from a mutable ancestor, present but not callable on it (`MUT-15`), and may still hold `Cell<T>` interior-mutable state.
A class with no immutable supertype and no `@fixed` is mutable, the default (`HIER-02`).
`String`, `Number`, and every `record` and `enum` are immutable.
The stricter notion of a *value class* is reserved (see below).
See `MUT-10`, `HIER-01`, `HIER-02`.

### interior mutability
The ability to mutate an object's contents through a `@fixed` (immutable) variable.
Breaks the rule that an immutable binding reaches nothing mutable (`MUT-14`).
Implemented only through `Cell<T>` in safe code.
See `MUT-16`.

### invariantly
An overriding method's parameter must **match exactly** the base method's parameter.
No relaxation allowed.
See `HIER-05`. (Contrast with contravariance.)

### .lat / .java (source file extensions)
The two file extensions accepted by `latc`.
`.lat` admits the full surface, including `T?`, `?.`, `?:`, `!!`, and inline FI types `(P1, …, Pn) -> R`.
`.java` is the Java-compatible subset, and the `.lat` forms and their `.java`-surface desugarings are specified in the `LAT` topic.
Both extensions share the same type system, annotations, and intrinsics.

### latc (laterita compiler)
The reference laterita compiler.
Accepts `.lat` and `.java` in a single compilation unit, dispatches by extension per `COMP-06`, and emits artifacts per `COMP-01`–`COMP-04`.
See `COMP-07`.

### lifetime
The span of time during which a variable is valid.
A borrowed variable's lifetime is bounded by the variable it borrows from.
It cannot outlive the referent.
A compiler error to use a variable after the value it refers to is dropped.
See `LIFE-01`.

### @local (annotation on types)
Declared on a class to express its relationship to threads.
`@local` (or `@local(true)`) pins instances to a single thread: they cannot be moved across threads or captured by closures that might run on other threads.
`@local(false)` asserts the inverse: the class encapsulates any transitively `@local` fields and is safe to use across threads.
A class with `@local` fields must carry one form or the other explicitly.
Examples: `Rc<T>`, `Cell<T>` are `@local`.
`Arc<T>`, `Mutex<T>`, `Thread` are `@local(false)`.
See `STD-07`.

### monomorphization
The compile-time process of specializing generic code.
Each instantiation of a generic type or method (e.g., `List<String>` and `List<int>`) generates a separate implementation.
See `COMP-02`.

### @mutating (annotation)
Declares that a method may mutate its receiver: reassign the receiver's non-`final` fields, mutate through its fields, and call other `@mutating` methods on `this`.
A declaration annotation on the method, kept a distinct token from `@fixed` so receiver mutation is not spelled like binding mutability.
It may be declared only on a mutable class or interface, and by `MUT-15` it is callable only on a mutable receiver.
The receiver is the one position whose default is no mutation, since every method has one and an immutable class must still have callable methods.
See `MUT-13`.

### mutable borrow / mut borrow
A borrow that grants read and write access through the borrowed value.
Only one mutable borrow may be active at a time, and no other borrow may coexist with it.
A mutable borrow requires the source variable to be mutable, or the borrow to occur within a `@mutating` method of the same object.
A borrow of a value of an immutable class is always shared (`MUT-14`).
See `OWN-03`, `OWN-13`.

### mutable class
A class or interface that is not declared `@fixed` and inherits immutability from no supertype, the default kind (`HIER-02`).
It may declare `@mutating` methods and fields that are reassigned or mutated through (`MUT-10`).
Every mutable class implements its own *frozen view* `@fixed C` (`MUT-30`).
See `MUT-10`, `HIER-02`.

### mutable slot / immutable slot
A slot is *immutable* when it carries `@fixed` or its declared type is an immutable class, and *mutable* otherwise (`MUT-31`).
The kind decides what may fill the slot: a mutable slot rejects an immutable value, and an immutable slot accepts either, downgrading a mutable one to its frozen view.
`@fixed` on a slot whose declared type is already immutable is redundant, so `String s` and `@fixed String s` declare the same slot.
See `MUT-31`, `MUT-01`.

### Mutex<T>
A mutual-exclusion primitive wrapping an owned value.
Access is scoped to a closure: `with(@mutating (T) -> R)` and `tryWith(...)` acquire the lock, run the closure on the protected value, release the lock, and return the closure's result.
The action slot is mut-call so the closure may capture state by mutable borrow.
The mutex is poisoned (`THR-10`) if the closure throws.
See `STD-09`.

### newtype
An idiom, not a named language construct.
A `record` annotated `@Delegate` on its sole component is a newtype: the compiler generates its full forwarding surface (`GEN-01`), the single-field layout guarantee (`NABI-01`) ensures it has the same size and ABI as the wrapped value, and inlining (`COMP-08`) collapses the forwarders to direct calls.
The result is a distinct nominal type (not a subtype of the component's type, no implicit widening) at zero runtime overhead.
`record Email(@Delegate @take String raw)` and `record UserId(@Delegate @take Long raw)` are newtypes by idiom.

### nullable type (also `T?` / `@Nullable T`)
A type that admits both a value and the special value `null`.
Written `T?` in `.lat` sources and `@Nullable T` in `.java` sources (`@Nullable` declared in `laterita.lang.annotation`).
Different from Java's implicit nullability.
A bare `T` in Laterita is non-nullable.
See `NULL-02`, `LAT-01`.

### @Delegate (annotation on fields)
Placed on a field or record component, it causes the compiler to generate, for every `public` instance method of the field's declared type, a forwarding method on the owner that calls the same method on the field.
`Object` and `static` methods are not forwarded.
Forwarder return types are the source method's own (they *decay*), and ownership annotations (`@consuming`, `@mutating`) are propagated.
An explicitly declared method shadows the forwarder of the same signature, which is how part of a forwarded surface is overridden.
`@Delegate` on a `@Nullable` field, a signature clash between two `@Delegate` fields, and cyclic delegation are compile errors.
See `GEN-01`.

### @Operator (annotation on methods)
Marks an instance method as the desugaring target for an arithmetic operator in `.lat` sources.
The annotation names the operator (`@Operator(PLUS)`, `MINUS`, `TIMES`, `DIVIDE`, `NEGATE`) and the method name is unconstrained, so `BigDecimal.add`, `Instant.plus`/`minus`, and `Duration.negated` qualify under their existing names.
Arity must match the operator (one parameter for the binary kinds, zero for `NEGATE`).
`a + b` then means the annotated `PLUS` method call on `a`.
The operator set is bounded (no `%`, `[]`, or compound assignment) with no user-defined or trait-based overloading, the comparison operators `< <= > >=` desugar separately through `Comparable.compareTo`, needing no annotation.
See `LAT-07`.

### onDrop()
A method the compiler invokes to clean up a value.
Only a `final` class may implement it with a body (`DROP-09`), a class without an implementation contributes no body.
The compiler runs the implementation (if any) as step 1 of the value's drop sequence (`DROP-05`: own body, then own fields in reverse, then each superclass), and triggers the drop sequence on every variable that leaves scope, in reverse declaration order (`DROP-01`, `DROP-02`).

### OQ (prefix in OQ-N)
"Open Question." A numbered entry in the open-questions document listing unresolved language-design decisions.
Example: OQ-20 (pattern matching and destructuring under ownership).
Not part of the normative spec.

### Pair<L, R>
General-purpose mutable class in `laterita.lang` carrying two values.
The same declaration covers owned, borrowed, and mixed cases, driven by what is substituted for `L` and `R` per TARG-01.
Instantiated as `Pair<T[], T[]>` by `T[].splitOff` (owned halves, destructed by direct field access `give(p.left)` / `give(p.right)`, OWN-06) and as `@bound Pair<@borrow T[], @borrow T[]>` by `T[].splitAt` (borrowed halves whose mutability follows the receiver, MUT-17).
See `ARR-04`.

### ownership
Having the right and obligation to drop (clean up) a value when done.
An owned variable can move the value to another variable, pass it to a `@take` parameter, or drop it at scope exit.
Only one variable can own a value at a time.
See `OWN-01`.

### override variance
The rules governing whether an overriding method's signature may differ from the base method's.
One principle: an override may **demand less** of its callers (parameters, receiver) and **guarantee more** to them (return), never the reverse.
`@take` on a parameter is invariant.
`@bound` on a parameter or return, `@mutating`, `@consuming`, and `@fixed` on a return may all be dropped, never added, and `@fixed` on a parameter or on a class may be added, never dropped.
The FI-slot call-mode axis inverts surface direction (override may *strengthen* the slot (bare → `@mutating` → `@consuming`)) because the annotation governs closure acceptance, not parameter variable.
See `HIER-05` for the unified table.

### parameter mode / ownership mode
How a parameter receives its argument: bare (borrows the argument mutably), `@fixed` (borrows it shared), `@take` (receives ownership and may mutate through it), or `@take @fixed` (receives ownership frozen).
Every parameter slot is `final`: the name cannot be reassigned in the body (`MUT-20`).
See `OWN-13`, `MUT-41`.

### poisoned (Mutex)
A `Mutex<T>` marked as unusable because the closure passed to its `with` / `tryWith` call propagated an exception out of the critical section.
Subsequent attempts to acquire the lock throw `PoisonedException`.
The mutex can only be recovered by replacing it entirely.
See `THR-10`.

### Rc<T>
A reference-counted smart pointer for single-threaded shared ownership.
Like Java's garbage collector but manual: each holder holds a handle, the refcount is explicitly bumped with `.share()`, and the value is freed when the refcount reaches zero.
Single-threaded only, use `Arc<T>` for cross-thread sharing.
See `STD-01`.

### ReentrantLock
A reentrant mutual-exclusion primitive in `laterita.lang` that owns no data, the lock alone.
Modelled on `java.util.concurrent.locks.ReentrantLock` but with safer surface: `lock()` returns a `LockGuard` whose `onDrop` releases the lock, so "forgot to unlock" is impossible.
Use when the data being guarded does not fit `Mutex<T>` (state spread across several fields of `this`, or genuinely data-less coordination).
Pair with `Condition` for `wait`/`signal` patterns.
See `STD-10`.

### LockGuard
A value witnessing that the calling thread holds a `ReentrantLock`.
Returned by `ReentrantLock.lock` / `lockInterruptibly` / `tryLock`.
Owns one acquisition.
Releases it via `onDrop` when its scope ends.
The owning scope is therefore the critical section.
See `STD-11`.

### Condition
A condition variable bound to a `ReentrantLock`, created by `lock.newCondition()`.
`await` atomically releases the bound lock and blocks.
On signal, re-acquires.
`signal` / `signalAll` wake waiters.
Names and shapes match `java.util.concurrent.locks.Condition`.
The "caller must hold the bound lock" precondition is a runtime check: laterita does not statically associate a `Condition` with a specific `LockGuard` lifetime.
See `STD-12`.

### receiver mode (of a method)
How a method accesses its receiver (`this`): bare (read-only), mutating (declared by `@mutating` on the method, MUT-13), or consuming (declared by `@consuming` on the method, OWN-15).
The receiver's variable mode must support the receiver mode (e.g., a bare variable cannot call a `@mutating` method).

### safe / unsafe (code)
**Safe code** obeys all ownership and lifetime rules, checked by the compiler.
**Unsafe code** is a method annotated `@unsafe` that performs operations otherwise forbidden (raw memory access, cross-thread moves of `@local` types, etc.).
The compiler still type-checks `@unsafe` methods, the annotation only unlocks specific operations per `UNS-02`.
See `UNS-01`.

### SAM (Single Abstract Method)
The one abstract method of a functional interface.
In a lambda or method reference targeting a functional interface, the body must implement the SAM.
Parameter and return modes of the SAM are declared as part of the interface.
An anonymous functional interface's SAM is named `apply`, and a value `f` is invoked as `f.apply(...)`.
See `FN-01`.

### shared borrow / immutable borrow
A borrow that grants read-only access to a borrowed value.
Any number of shared borrows may coexist.
A shared borrow does not require the source variable to be `mut`.
See `OWN-02`, `OWN-03`.

### slice (of a String or array)
A borrowed view into a contiguous region of a String or array.
Methods like `substring`, `trim` (on String) and `slice` (on arrays) return a borrowed view (marked `@bound`), not a new copy.
The borrow is bounded by the original's lifetime.
See `STR-03`, `OWN-05`.

### static borrow
A borrow with a static lifetime: one that is guaranteed to live for the entire program execution.
String literals in Laterita are static borrows: they reside in read-only program memory and can be safely borrowed by any variable without lifetime restrictions.
See `STR-06`.

### static lifetime
A lifetime that spans the entire program execution.
Values with static lifetime (such as string literals) can be borrowed without restriction in any context.
The static lifetime is the broadest possible scope, permitting a borrow to flow freely without being tied to a particular variable's scope.
See `STR-06`, `LIFE-01`.

### string literal
A quoted string expression in source code (e.g., `"hello"`), which has type `@bound String` with a static lifetime.
The literal is not a heap allocation, it resides in the program's read-only memory segment.
A variable initialized from a literal is borrowed, to obtain an owned heap-allocated `String`, call `.clone()`.
See `STR-06`.

### smart pointer
A wrapper type that manages a value's lifetime.
Examples: `Rc<T>` (reference-counted, single-threaded), `Arc<T>` (atomic reference-counted, multi-threaded).
Smart pointers carry `onDrop()` to enforce cleanup.

### static analysis
Compile-time reasoning about program behavior without running the code.
Laterita's compiler performs static analysis of ownership, borrows, lifetime, mutability, and reachability to catch errors before runtime.

### static field
A field declared `static`, class- or module-level storage with one instance per program.
Immutable per `STAT-01` and initialized from a const expression.
Every static field is `final` and `@fixed` whatever its declaration writes, the one position where the language withdraws mutability rather than leaving the choice to the author.
The declared type must be non-`@local` (`STAT-03`).
Shared mutable program-wide state is expressed by storing a `Mutex<T>` (`STD-09`), `Arc<T>` (`STD-02`), or an atomic primitive in the immutable slot.

### synthesized / synthesis
Generated by the compiler rather than written in source.
Copy constructors and `clone()` are synthesized (`OBJ-01`, `OBJ-02`), as are the interface and implementing class behind each anonymous functional interface (`FN-03`).

### target typing
Inferring a lambda's type from the context where it appears.
If a lambda is assigned to a variable or parameter with a known functional-interface type, the type is used as a hint to type-check the lambda body.
See `CLO-04`.

### @take (annotation)
Declares that a parameter receives ownership of its argument (consumed upon call).
At the call site, a bare variable passed to a `@take` parameter is implicitly consumed (equivalent to `give(variable)`), or explicitly written as `give(variable)`.
See `OWN-13`. (Receiver consumption is the separate `@consuming` annotation on the method, OWN-15.)

### thread-affine (also "thread-local")
A type or resource bound to a specific thread and cannot safely be moved to another thread.
In Laterita, expressed via the `@local` annotation.
Examples: `Rc<T>`, `Thread.local` storage.
See `STD-07`.

### transitivity (of mutability)
Immutability propagates through a variable.
A `@fixed` or shared variable cannot call `@mutating` methods on the held object and cannot mutate its fields, even if its own slot is reassignable.
To mutate through, no level of the access path may be `@fixed` or shared.
See `MUT-15`, `MUT-14`.

### type-inferred variable
A variable whose type is inferred from the RHS expression rather than written explicitly.
Forms: `var name = expr` (reassignable), `final var name = expr` (slot locked), `@fixed var name = expr` (mutation through the referent withdrawn).
A laterita `var` is reassignable by default, exactly as in Java, and takes its declared type, `@fixed` included, from the RHS of the first assignment (`MUT-40`).
See `MUT-40`.

### type narrowing / smart cast
Refining a variable's type along a conditional path.
Most common with nullable types: after `if (x != null) { ... }`, the variable `x` is narrowed from `T?` to `T` within the block, and methods on `T` are callable without further checks.
See `NULL-06`.

### unwind (exception)
The process of propagating an exception up the call stack, running cleanup (`onDrop()` and `finally` blocks) at each frame before the exception reaches the next handler.
See `EXC-02`, `EXC-03`.

### use-after-move
An error where a variable is used after its value has been moved elsewhere.
The compiler rejects such code statically.
See `OWN-07`.

### value class (reserved)
Reserved for a future notion stricter than an immutable class, in the spirit of an identity-free inline value type.
Laterita's `@fixed` classes are *immutable classes* (`MUT-10`), not value classes.
Do not use "value class" for a `@fixed` class or instance.

### virtual dispatch / static dispatch
Selecting a method implementation from the value's runtime class (virtual) or from its static type at compile time (static).
`clone()` dispatches virtually to the value's runtime class (`OBJ-02`).

### WeakReference<T>
A non-owning reference to a value managed by `Rc<T>` or `Arc<T>`.
The weak reference is not counted toward the refcount and does not prevent the value from being freed.
Calling `get()` returns an `Rc<T>?` or `Arc<T>?` (a strong reference if the value still lives).
See `STD-03`.

---

## Notation and Abbreviations

### Code Notation in the Spec

| Notation | Meaning |
|----------|---------|
| `T`, `U`, etc. | Type variable, representing any type |
| `T?` | Nullable version of type `T` (`.lat` form, where `.java` writes `@Nullable T` per LAT-01) |
| `(T1, T2, ..., Tn) -> R` | Anonymous functional interface taking `T1, ..., Tn` and returning `R`, shared-call by default. Prefix with `@mutating` or `@consuming` for mut-call / once-call. Legal as parameter, return, generic bound, or generic type argument per FN-04. (`.lat`-only per LAT-05) |
| `variable:` or `method:` or `parameter:` | Marks the following code snippet's scope (e.g., method signature, local variable) |

### Spec Code Prefixes

Each requirement in the spec carries a mnemonic code for cross-reference.
Codes are grouped by area:

| Prefix | Area |
|--------|------|
| `OWN` | Ownership: owned vs. borrowed variables, move and borrow rules, `@take` / `@borrow` / `@bound`, `@consuming` |
| `LIFE` | Lifetime intersection across multiple borrow sources |
| `MUT` | Mutability rules: the `@fixed` marker, `@mutating` methods, transitivity, interior mutability |
| `HIER` | Class hierarchy: inherited immutability, default kind, immutable subclass freeze, no-widening, override variance |
| `TARG` | Annotations admitted inside generic type arguments, and type-parameter mutability |
| `STAT` | Static field rules |
| `NULL` | Nullable types, null safety |
| `DROP` | Scope-exit cleanup, `onDrop()` |
| `OBJ` | Copying, clone semantics |
| `DES` | Destruction: taking an owned object apart field by field (`give(obj.field)`) |
| `UNR` | Unreachable paths (`broken()`) |
| `STR` | String ownership and slicing |
| `ARR` | Array methods, indexing, and the `laterita.lang.Arrays` static surface |
| `FN` | Functional interfaces and anonymous function types |
| `CLO` | Closures and lambda capture |
| `EXC` | Exception handling and unwind semantics |
| `UNS` | Unsafe code and privileged operations |
| `STD` | Standard library types (`Rc<T>`, `Arc<T>`, `Mutex<T>`, `@local` marker, etc.) |
| `THR` | Threading, interrupts, `Thread.onDrop()`, lock poisoning |
| `COMP` | Compilation model (monomorphization, reflection, etc.) |
| `RESV` | Reserved names and the annotation / intrinsic surface |
| `LAT` | `.lat` surface forms (syntactic sugar over the Java-compatible surface) |
| `NABI` | Native ABI guarantees |
| `GEN` | Code generation annotations (Lombok-compatible surface) |

---

## Java Analogies

For junior Java developers, here are key Rust/Laterita concepts mapped to Java:

| Rust / Laterita | Java Analog | Difference |
|-----------------|------------|-----------|
| `Rc<T>` (single-threaded) | Variable (with manual refcount) | Java uses GC, while Laterita requires explicit `.share()` and tracks refcount |
| `Arc<T>` (multi-threaded) | Variable (with atomic refcount) | Like `Rc<T>`, but thread-safe, and less common in Java due to GC |
| Ownership + `give(...)` | Explicit transfer | Java has no ownership concept, so all variables are borrows |
| Borrow (`&`) | Variable | Similar to Java, but lifetime rules are stricter |
| `@fixed` (no mutate-through) | Reference used only to read the object | Java has no way to say it, while laterita withdraws mutation with `@fixed`, with reassignment the separate `final` axis |
| `@fixed class` vs. mutable class | Valhalla `value class` | Both opt *in* to the restricted kind, and a class with no immutable supertype and no `@fixed` is an ordinary mutable class (HIER-02) |
| `@local` annotation | Thread-local or thread-affine concept | Java doesn't have language-level thread-affinity for types |
| `Cell<T>` | `AtomicReference<T>` (simplified) | Like atomics, but for single-threaded interior mutability, with no GC hazard |
| `Mutex<T>` | `synchronized` block on a protected field | Closure-scoped API ensures lock release and ties the lock to the protected value |
| `ReentrantLock` + `LockGuard` | `java.util.concurrent.locks.ReentrantLock` | `LockGuard.onDrop` removes the manual unlock, the lock is reentrant, and it pairs with `Condition` for wait/signal |
| `Condition` | `java.util.concurrent.locks.Condition` or `Object.wait`/`notify` | Same API as `j.u.c.l.Condition`, and runtime-checks the bound lock is held |
| `onDrop()` | `close()` or finalizer | Guaranteed-called cleanup per object, closer to C++ destructors than Java finalizers |
| `drop` flag | N/A | Java doesn't track per-field move state |
