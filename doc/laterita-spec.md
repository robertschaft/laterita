# Laterita — Language Specification

This document specifies the normative requirements that a Laterita compiler and standard library must satisfy. Each requirement carries a mnemonic code for cross-reference.

Codes are grouped by area: `BIND` (bindings), `MOVE` (move/borrow), `MUT` (mutability), `LIFE` (lifetimes), `DROP` (cleanup), `STR` (strings), `ARR` (arrays), `CLO` (closures), `EXC` (exceptions), `UNS` (unsafe), `STD` (standard library types), `COMP` (compilation model).

---

## 1. Bindings

### BIND-01 — Three binding forms

The language provides exactly three local binding forms:

| Form | Meaning |
|---|---|
| `Type name = expr` | immutable, owned, type explicit |
| `let name = expr` | immutable, owned, type inferred |
| `mut name = expr` | mutable, type inferred |

A mutable binding with explicit type is written `mut Type name = expr`.

```laterita
String greeting = "hello";
let count = items.size();
mut sb = new StringBuilder();
mut int retries = 0;
```

### BIND-02 — `mut` is the unified mutability marker

The keyword `mut` denotes mutability in every position it appears: local bindings, fields, methods, and parameters. Java's `var` keyword (local-variable type inference) is not used in Laterita; type-inferred locals are written `let` (immutable) or `mut` (mutable).

### BIND-03 — Field declarations follow binding rules

Fields follow the same rules as locals. A field without `mut` cannot be reassigned and cannot be mutated through. A field with `mut` permits both reassignment and mutation through the binding.

```laterita
class User {
    String name;             // immutable field
    mut int loginCount;      // mutable field
}
```

### BIND-04 — Constructors initialize immutable fields

Every field of a class must be assigned exactly once in every constructor before any method on `this` is invoked. Immutable fields can only be assigned in constructors. Mutable fields can be assigned in constructors and reassigned in `mut` methods.

### BIND-05 — Methods declare mutation of `this` with `mut`

A method whose return type is prefixed with `mut` may mutate `this` (i.e., reassign or mutate-through `mut` fields, and call other `mut` methods on `this`). A method without `mut` cannot.

```laterita
class Counter {
    mut int n;
    int read()      { return n; }       // immutable receiver
    mut void inc()  { n = n + 1; }      // mutable receiver
}
```

### BIND-06 — Mutability transitivity

Mutation requires `mut` at every level of access. To call a `mut` method, the receiver binding must be `mut`. To mutate a field, the field must be `mut` and the binding holding the containing object must be `mut` (or the mutation must occur in a `mut` method of the same object).

```laterita
let counter = new Counter();
counter.inc();              // ERROR: counter is not mut
mut c2 = new Counter();
c2.inc();                   // OK
```

---

## 2. Optionality

### NULL-01 — Types are non-nullable by default

A bare type `T` excludes the null state. A binding of type `T` always holds a valid value after initialization, and methods on `T` may be invoked without a null check.

```laterita
String name = "Alice";
print(name.length());       // always safe
```

### NULL-02 — `?` suffix denotes a nullable type

The type `T?` admits either a value of `T` or the special value `null`. `T` and `T?` are distinct types: `T` widens to `T?` implicitly; `T?` does not narrow to `T` without a check (NULL-06) or an assertion (NULL-07).

```laterita
String? maybeName = lookup(id);
print(maybeName.length());   // ERROR: requires null check
```

### NULL-03 — `null` literal

The literal `null` has type `Nothing?` and is assignable to any `T?`. `null` is not assignable to a non-nullable type.

### NULL-04 — Safe call `?.`

`expr?.method(args)` evaluates to `null` if `expr` is `null`, otherwise invokes `method` on `expr`. The result type is `R?` where `R` is the method's return type.

```laterita
int? len = maybeName?.length();
```

### NULL-05 — Elvis operator `?:`

`a ?: b` evaluates to `a` if `a` is non-null, otherwise to `b`. The result type is the common type of the non-nullable form of `a` and the type of `b`.

```laterita
String shown = maybeName ?: "anonymous";
```

### NULL-06 — Smart narrowing on null check

After a control-flow narrowing (e.g., `if (x != null) { ... }`, `if (x == null) return;`), the binding's type within the proven-non-null region is `T`, not `T?`. Calls that require `T` are permitted without further annotation.

```laterita
if (maybeName != null) {
    print(maybeName.length());   // OK: narrowed to String
}
```

### NULL-07 — Null assertion `!!`

`expr!!` converts `T?` to `T`. If `expr` is `null`, a `NullPointerException` is thrown. This is the only path from `T?` to `T` at the type level without a flow-sensitive narrowing.

### NULL-08 — Field default is non-nullable

Fields obey NULL-01: a field declared `T` is non-nullable, and BIND-04's "assigned exactly once in every constructor" requirement guarantees no observable `null`. A nullable field is declared `T?`.

```laterita
class User {
    String name;            // non-nullable
    String? nickname;       // nullable
}
```

### NULL-09 — `close()` skips null

When a binding of type `T?` leaves scope, the compiler-inserted `close()` call is conditional: if the value is `null`, no call is made; otherwise `close()` is invoked on the contained value. This composes with DROP-04's drop-flag treatment — the compiler already tracks per-binding "still live?" state.

### NULL-10 — Move and borrow on `T?`

`^expr` where `expr` has type `T?` transfers either the contained `T` (leaving the source as `null`) or transfers `null`. Borrow rules apply identically to `T?` and `T`. A borrow of a `T?` is itself a `T?`-borrow; null narrowing (NULL-06) on a borrowed binding narrows to a `T`-borrow.

---

## 3. Move and Borrow

### MOVE-01 — Default assignment is a borrow

Assigning a binding from another binding produces a shared (immutable) borrow of the source. Both bindings remain usable. The borrow's lifetime is bounded by the source binding's lifetime.

```laterita
String a = makeString();
String b = a;               // shared borrow
print(a);                   // OK
print(b);                   // OK
```

### MOVE-02 — `^` prefix marks a move at the use site

A `^` prefix on a binding name in any expression position consumes the binding's ownership and transfers it to the destination. After a move, the source binding is no longer usable.

```laterita
String a = makeString();
String b = ^a;              // a is consumed
// print(a);                // ERROR: use after move
print(b);                   // OK
```

### MOVE-03 — Function arguments use the same rules

A bare argument is a borrow; a `^`-prefixed argument is a move. Function signatures do not declare borrow-vs-move; the caller decides at the call site.

```laterita
void store(String s, mut List<String> into);

let name = "Alice";
store(name, list);          // borrow of name
store(^name, list);         // move of name
```

### MOVE-04 — Borrow exclusivity

For any binding's lifetime, either:
- any number of immutable (shared) borrows may coexist, or
- exactly one mutable borrow may exist, with no other borrows.

The compiler must reject programs that violate this.

### MOVE-05 — Disjoint field borrows

Two simultaneous borrows of statically distinct fields of the same struct are non-aliasing and must be permitted, including when both are mutable. The compiler must perform this disjointness analysis.

```laterita
class Pair { mut int left; mut int right; }
mut Pair p = new Pair();
mut int l = p.left;
mut int r = p.right;        // OK: disjoint fields
```

### MOVE-06 — Disjoint array slice borrows

Two simultaneous borrows of array slices with provably disjoint index ranges must be permitted. The compiler proves disjointness for constant ranges and for ranges related by simple arithmetic. For arbitrary computed ranges, the standard library must provide a `splitAt` operation that returns proven-disjoint sub-slices.

```laterita
int[] data = new int[100];
mut int[] left  = data.slice(0, 50);
mut int[] right = data.slice(50, 100);   // OK: provably disjoint
```

### MOVE-07 — Partial moves are tracked per field

Moving out of a field of a value leaves that field in the moved-out state while leaving other fields valid. The compiler must track per-field move state through the function and use it both for use-after-move checking and for cleanup emission (see DROP-04).

---

## 4. Mutability Rules (Cross-cutting)

### MUT-01 — Immutability is transitive through borrows

A shared (immutable) borrow grants no mutation rights regardless of any `mut` markers on fields reached through it. Mutation through a borrow requires the borrow itself to be mutable.

### MUT-02 — Interior mutability requires `Cell<T>`

A type that needs to mutate its contents through an immutable receiver must hold those contents inside `Cell<T>`. This is the only mechanism that bypasses MUT-01, and `Cell<T>` is an unsafe primitive (see UNS-02).

---

## 5. Lifetimes

### LIFE-01 — No borrow may outlive its referent

The compiler must reject any program in which a borrow is used after the binding it borrows from has been dropped or moved.

### LIFE-02 — Single-input elision

If a method takes exactly one borrowed parameter and returns a borrowed value, the returned borrow's lifetime is tied to that input. No annotation is required.

```laterita
String firstWord(String s) {
    return s.substring(0, s.indexOf(' '));   // returned borrow tied to s
}
```

### LIFE-03 — Receiver elision

For an instance method that returns a borrowed value, if no explicit annotation is given, the returned borrow is tied to `this`.

```laterita
class Cache {
    Map<String, Entry> entries;
    Entry get(String key) { return entries.get(key); }   // returned borrow tied to this
}
```

### LIFE-04 — Multiple-input ambiguity is conservative

When a method has multiple borrowed inputs that could each contribute to a returned borrow and no annotation distinguishes them, the returned borrow's lifetime is the intersection (i.e., bounded by the shortest-lived input).

### LIFE-05 — `from` keyword for explicit annotation

When the conservative LIFE-04 result is wrong, the programmer annotates the contributing inputs with `from`. The returned borrow is tied only to inputs marked `from`.

```laterita
String prefixOf(from text, String pattern) {
    return text.substring(0, pattern.length());   // tied to text only
}
```

The compiler must produce an error suggesting `from` annotation when ambiguity prevents a successful borrow check.

---

## 6. Scope-Exit Cleanup

### DROP-01 — Universal `close()`

Every binding triggers an invocation of its value's `close()` method when the binding leaves scope. `Object.close()` is a no-op by default; classes override it to perform cleanup. No syntactic opt-in is required at the call site.

```laterita
{
    Shared<File> f = openFile();
    f.read();
}   // f.close() runs here
```

### DROP-02 — Reverse declaration order

Within a scope, bindings are closed in the reverse of their declaration order.

### DROP-03 — Cleanup on all exit paths

`close()` must be invoked on every exit path from a scope: normal completion, return, break, continue, and exceptional unwind.

### DROP-04 — Drop flags for partial moves

When MOVE-07 has resulted in partially-moved values, the compiler must emit code that consults per-field move state and invokes `close()` only on the parts still owned at the exit point. Implementations may optimize away drop flags when static analysis proves they are constant.

### DROP-05 — `close()` is universal and automatic

`close()` is invoked by the compiler at scope exit per DROP-01 through DROP-04. This specification does not address whether user code may invoke `close()` explicitly nor whether early cleanup is supported.

### DROP-06 — `close()` follows Java override semantics

When a class overrides `close()`, only the most-derived override runs at scope exit. The compiler does not automatically chain `super.close()`. Subclass overrides that need superclass cleanup must call `super.close()` explicitly, conventionally as the last statement of the override.

```laterita
class CachedFile extends File {
    Cache cache;

    override void close() {
        cache.flush();
        super.close();   // required: closes the underlying File
    }
}
```

> **Reminder.** A subclass override that omits `super.close()` will not clean up the superclass's owned state. This is a known footgun inherited from Java's override model. Implementations should warn when a `close()` override does not call `super.close()` on at least one path; programmers writing `close()` overrides should treat `super.close()` as part of the override's contract.

---

## 7. Strings

### STR-01 — `String` is a normal class

`String` is not final. Classes may extend it. The compiler must permit user-defined subclasses such as `class Email extends String`.

### STR-02 — Strings are tracked as owned or borrowed per binding

A `String` binding is either an owned heap allocation or a borrowed view into another `String`'s storage. The compiler tracks this per-binding and applies lifetime rules to borrowed instances.

### STR-03 — Slice methods return borrows

Methods that return a view into the receiver's storage (e.g., `substring`, `trim`) return a borrowed `String` whose lifetime is tied to `this` per LIFE-03.

### STR-04 — Allocating methods return owned strings

Methods that produce new storage (e.g., `toUpperCase`, `concat`) return an owned `String` with no lifetime tie to the receiver.

### STR-05 — User-defined subclasses are owned

Subclasses of `String` declared by user code are owned. They cannot be returned as borrows into other storage. (The conversation noted a possible opt-in for borrowable subclasses but did not specify one.)

---

## 8. Closures

### CLO-01 — Three capture modes

Closures are classified by how they use captured bindings:

- **Read** — captured bindings are immutably borrowed; closure may be invoked any number of times, including from multiple threads simultaneously (subject to STD-Send rules).
- **Mutate** — captured bindings include a mutable borrow; closure may be invoked any number of times sequentially but not concurrently.
- **Consume** — captured bindings include a moved value; closure may be invoked exactly once.

### CLO-02 — Capture mode is inferred

The compiler infers a closure's capture mode from the body. The user does not declare it.

### CLO-03 — Three closure interfaces

The standard library provides three interfaces a method may take to declare what kind of closure it accepts. A method's parameter type fixes the strongest guarantee it requires.

(Concrete interface names are not normatively fixed by this specification — see open questions.)

### CLO-04 — Capture lifetimes propagate

A closure's type carries the lifetimes of every binding it captures by borrow. The closure cannot outlive any captured borrow.

---

## 9. Exceptions

### EXC-01 — Existing Java exception syntax is preserved

This specification does not redefine Java's exception syntax. Functions declare `throws`, callers use `try`/`catch`/`finally`, exceptions propagate through the call stack.

### EXC-02 — Cleanup runs on exception unwind

When an exception propagates out of a scope, all `close()` calls required by DROP-01 through DROP-04 must execute as part of the unwind, before the exception reaches the next handler.

### EXC-03 — Drop flags participate in unwind

DROP-04's drop flags must be consulted during exception unwind, not only on normal exit.

### EXC-04 — Lazy stack-trace resolution

When an exception is thrown, the runtime must capture the current call stack as raw return addresses. Symbol resolution (mapping addresses to source locations) must be deferred until the trace is inspected. The captured trace is owned by the exception object and freed with it.

---

## 10. Unsafe

### UNS-01 — `unsafe` is a private method modifier

Unsafe operations are permitted only inside methods declared `private unsafe`. There is no `unsafe` modifier on classes and no `unsafe { }` block form. Public APIs are always safe; safety contracts are upheld inside private unsafe methods.

```laterita
public class Shared<T> {
    Heap<ControlBlock<T>> ctrl;

    public Shared<T> share() {
        bumpRefcount();
        return makeHandle();
    }

    private unsafe void bumpRefcount() { /* ... */ }
    private unsafe Shared<T> makeHandle() { /* ... */ }
}
```

### UNS-02 — Fixed list of unsafe operations

Only the following operations require `unsafe` context:

1. Constructing or dereferencing `Heap<T>`.
2. Constructing `Cell<T>` or mutating its contents through a non-`mut` binding.
3. Cross-thread move of a non-`Send` type.
4. Lifetime extension or transmute.
5. Foreign function calls (FFI / native).
6. Unchecked array indexing.

This list is closed. No other operation is gated by `unsafe`.

### UNS-03 — Unsafe-typed fields force private + unsafe

A class field whose declared type is an unsafe primitive (e.g., `Heap<T>`, `Cell<T>`) must be private. Any constructor or method that reads or writes such a field must be marked `unsafe`.

### UNS-04 — Standard checks still apply inside `unsafe`

`unsafe` only unlocks the operations in UNS-02. Type checking, ownership tracking, lifetime inference, and mutability rules continue to apply in unsafe methods.

---

## 11. Standard Library Types (Required)

### STD-01 — `Shared<T>`

A reference-counted shared-ownership smart pointer for single-threaded use. Provides:
- `new Shared<T>(T value)` — takes ownership of `value`, refcount 1.
- `T-borrow read access` — returns a shared borrow of the contained value.
- `Shared<T> share()` — produces a new handle to the same allocation, bumping the refcount.
- `close()` — decrements the refcount; drops the value at zero.

A bare assignment of `Shared<T>` is a borrow per MOVE-01; a `^` move transfers the handle without bumping; `share()` is the only operation that bumps.

A cycle of `Shared<T>` handles whose strong references form a closed loop is not reclaimed: no handle's refcount can reach zero, and the cycle leaks. Programs that may form cycles must use `Weak<T>` (STD-03) for the back-edge to break the cycle. This matches the behavior of Rust's `Rc<T>`/`Weak<T>`; Laterita does not provide a cycle collector.

### STD-02 — `Atomic<T>`

The cross-thread analog of `Shared<T>`. Reference count operations are atomic. `Atomic<T>` is `Send` (see STD-Send).

### STD-03 — `Weak<T>`

A non-owning back-reference. Provides:
- `Weak<T>` produced from `Shared<T>::downgrade()`.
- `Shared<T>? upgrade()` — returns a strong handle if the value is still alive, otherwise `null`. Implementation must be race-free with respect to concurrent strong-count decrement (compare-and-swap upgrade per STD-04).

### STD-04 — Race-safe `Atomic<T>` upgrade

`Weak<T>::upgrade()` on an `Atomic`-flavored weak handle must use compare-and-swap to atomically check the strong count is non-zero and bump it. A simple read-then-bump is unsound.

### STD-05 — `Cell<T>`

Interior-mutability primitive. Permits mutation of contents through a non-`mut` binding. Construction and content mutation require `unsafe` context per UNS-02. Used as a building block for `Atomic<T>`, `Mutex<T>`, lazy initializers, etc.

### STD-06 — `Heap<T>`

Raw heap-allocation primitive. Provides allocation, dereference, and free. All operations require `unsafe` context per UNS-02.

### STD-07 — Send marker

Types that are safe to move between threads carry the `Send` property. Types that are not (such as a single-threaded `Shared<T>`) are not `Send`. The compiler must reject moves of non-`Send` values across thread boundaries except inside `unsafe` methods. (Syntax for declaring `Send` is not fixed by this specification — see open questions.)

---

## 12. Compilation Model

### COMP-01 — Native compilation, no GC

Laterita is intended to be compiled ahead-of-time to native code. There is no garbage collector at runtime. Memory management is determined by static ownership, borrow tracking, and `close()` insertion at scope exits. Reference-counted types (`Shared<T>`, `Atomic<T>`) introduce dynamic refcount-based reclamation; cycles among such handles leak per STD-01. No tracing collector is provided.

### COMP-02 — Generic monomorphization

Generic types and methods are monomorphized: each instantiation produces a specialized implementation at compile time. Field offsets and method dispatch are resolved per-instantiation.

### COMP-03 — Compiler-inserted cleanup

The compiler must emit `close()` calls at every scope-exit point per DROP-03 and unwind table entries per EXC-02. These insertions happen after all user-level analysis and are not visible in source.

### COMP-04 — Drop flags as compile-time state

Per-field move state (DROP-04) is compiler-internal bookkeeping. Implementations should optimize away flags whose values are statically determined.

---

## 13. Reserved Names

The following names are introduced by this specification and must be provided by the standard library: `Shared`, `Atomic`, `Weak`, `Cell`, `Heap`. Three closure interfaces required by CLO-03 must also be provided; their names are not fixed by this specification.

The following keywords are introduced or repurposed by this specification: `let` (immutable type-inferred binding), `mut` (mutability marker for local bindings, fields, methods, and parameters), `from` (lifetime annotation), `unsafe` (private method modifier). Java's `var` keyword for local-variable type inference is not used in Laterita; `let` and `mut` cover the type-inferred forms.

The `^` character is reserved as the move-prefix operator at use sites. The `?` suffix denotes nullable types per NULL-02; `?.` is the safe-call operator (NULL-04); `?:` is the Elvis operator (NULL-05); `!!` is the null-assertion operator (NULL-07).

Java's existing keywords and their meanings are preserved unless explicitly modified by this specification.
