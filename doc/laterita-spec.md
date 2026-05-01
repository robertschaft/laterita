# Laterita — Language Specification

This document specifies the normative requirements that a Laterita compiler and standard library must satisfy. Each requirement carries a mnemonic code for cross-reference.

Codes are grouped by area: `BIND` (bindings), `NULL` (optionality), `MOVE` (move/borrow), `MUT` (mutability), `LIFE` (lifetimes), `DROP` (cleanup), `OBJ` (object copying), `UNR` (unreachability), `STR` (strings), `CLO` (closures), `EXC` (exceptions), `UNS` (unsafe), `STD` (standard library types), `COMP` (compilation model).

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

### NULL-09 — `onDrop()` skips null

When a binding of type `T?` leaves scope, the compiler-inserted `onDrop()` call is conditional: if the value is `null`, no call is made; otherwise `onDrop()` is invoked on the contained value. This composes with DROP-04's drop-flag treatment — the compiler already tracks per-binding "still live?" state.

### NULL-10 — Move and borrow on `T?`

`give expr` where `expr` has type `T?` transfers either the contained `T` (leaving the source as `null`) or transfers `null`. Borrow rules apply identically to `T?` and `T`. A borrow of a `T?` is itself a `T?`-borrow; null narrowing (NULL-06) on a borrowed binding narrows to a `T`-borrow.

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

### MOVE-02 — `give` marks a move at the use site

A `give` prefix on a binding name in any expression position consumes the binding's ownership and transfers it to the destination. After a move, the source binding is no longer usable.

```laterita
let a = makeString();
let b = give a;             // a is consumed
// print(a);                // ERROR: use after move
print(b);                   // OK
```

A binding declaration may use `take` as a declarative prefix on the type, asserting that the LHS receives ownership. `take` is documentary: it does not by itself trigger a move. The RHS must independently produce ownership (a `give` expression, or a producer expression such as a call, constructor, or literal). A `take` LHS paired with a bare-binding RHS is a compile error per MOVE-01 (the bare RHS is a borrow, which `take` rejects).

```laterita
let c = makeString();
take String d = give c;     // both sides explicit; same operation as `let d = give c`
take String e = c;          // ERROR: bare RHS is a borrow per MOVE-01; take demands ownership
```

### MOVE-03 — Parameter ownership is declared in the signature

A parameter declares whether it receives a borrow or takes ownership of its argument:

| Form | Meaning |
|---|---|
| `T name` | the parameter receives a shared borrow |
| `mut T name` | the parameter receives a mutable borrow |
| `take T name` | the parameter receives ownership (moved in) |
| `take mut T name` | the parameter receives ownership and the slot is reassignable |

The parameter declaration drives the call site. A bare argument that is a binding implicitly transfers ownership when the parameter is `take`; an explicit `give` is the same operation written for clarity. A temporary expression (call result, constructor, literal) is owned and fills either parameter form — moved into a `take` parameter, borrowed for the duration of the call by a bare parameter.

The illegal cases:
- A `give arg` to a bare parameter — the caller is asking to transfer; the function will not accept ownership.
- A bare argument that is a borrow (e.g., the binding itself only holds a borrow) to a `take` parameter — there is no ownership to give.

```laterita
void inspect(String s);                              // borrows s
void store(take String s, mut List<String> into);    // takes ownership of s

let name = makeName();
inspect(name);              // OK: borrow
inspect(makeName());        // OK: borrow of a temporary
store(name, list);          // OK: implicit give; name no longer usable
store(give name, list);     // OK: explicit give; same operation
store(makeName(), list);    // OK: temporary moved in
inspect(give name);         // ERROR: inspect only borrows; do not transfer
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

### LIFE-02 — Returns are owned by default

A bare return type means the function gives the caller an owned value. The keyword `give` may be used as an optional declarative prefix on the return type, equivalent to the bare form; tooling and documentation may surface it for emphasis. To declare a borrowed return instead, the contributing source is marked with `bound`:

- **Parameter source**: prefix the parameter type with `bound`. The return is bound to that parameter.
- **Receiver source**: prefix the return type with `bound`. The return is bound to `this`.

```laterita
String upperCase(String s);                    // owned return (bare)
give String upperCase(String s);        // owned return (explicit; equivalent)

String firstWord(bound String s) {             // returned borrow bound to s
    return s.substring(0, s.indexOf(' '));
}

class Cache {
    Map<String, Entry> entries;
    bound Entry get(String key) {              // returned borrow bound to this
        return entries.get(key);
    }
}
```

### LIFE-03 — Multiple `bound` sources intersect

When more than one source is marked `bound` (any combination of parameters and the receiver), the returned borrow's lifetime is the intersection (i.e., bounded by the shortest-lived marked source).

```laterita
bound String chooseLabel(bound String fallback) {
    return prefer ? this.label : fallback;     // bound to min(this, fallback)
}
```

### LIFE-04 — Unmarked sources do not contribute

Inputs that are not marked `bound` cannot contribute to the returned borrow. A method body that returns a borrow tied to an unmarked source is a compile error; the diagnostic suggests adding `bound` to the relevant source.

```laterita
String prefixOf(bound String text, String pattern) {
    return text.substring(0, pattern.length());   // bound to text only; pattern unmarked
}
```

### LIFE-05 — Owned/borrowed mismatches are diagnostics

The compiler must report an error when:
- the body returns a borrow but the signature declares no `bound` source, or
- the body returns an owned value but the signature declares a `bound` source.

The diagnostic identifies the contributing source the body actually uses, so the user can either add the appropriate `bound` marker or change the body to match the declared owned form.

---

## 6. Scope-Exit Cleanup

### DROP-01 — Universal `onDrop()`

Every binding triggers an invocation of its value's `onDrop()` method when the binding leaves scope. `Object.onDrop()` is declared `internal void onDrop()` (see DROP-06 for the `internal` modifier) and is a no-op by default; classes override it to perform cleanup. No syntactic opt-in is required at the call site.

```laterita
{
    Shared<File> f = openFile();
    f.read();
}   // f.onDrop() runs here (compiler-emitted)
```

### DROP-02 — Reverse declaration order

Within a scope, bindings are dropped in the reverse of their declaration order.

### DROP-03 — Cleanup on all exit paths

`onDrop()` must be invoked on every exit path from a scope: normal completion, return, break, continue, and exceptional unwind.

### DROP-04 — Drop flags for partial moves

When MOVE-07 has resulted in partially-moved values, the compiler must emit code that consults per-field move state and invokes `onDrop()` only on the parts still owned at the exit point. Implementations may optimize away drop flags when static analysis proves they are constant.

### DROP-05 — Auto-chained `super.onDrop()`

When a class overrides `onDrop()`, the compiler auto-inserts a call to `super.onDrop()` as the last statement of the override body. Users do not write the super-chain themselves; the compiler-emitted call guarantees the entire inheritance chain participates in cleanup, in conventional reverse-construction order (subclass first, then superclass).

```laterita
class CachedFile extends File {
    Cache cache;

    override void onDrop() {
        cache.flush();
        // compiler appends: super.onDrop();
    }
}
```

This is a deliberate departure from Java's general override semantics, justified by `onDrop`'s position as a compiler-orchestrated lifecycle hook rather than a normal method (analogous to how the synthesized copy constructor auto-chains `super(source)` in OBJ-01). The user's role is to specify *what* to clean up; the recursive backbone is the language's responsibility.

### DROP-06 — `internal` visibility forbids user invocation

The visibility modifier `internal` declares that a method may be invoked only by compiler-emitted call sites. User code cannot invoke an `internal` method directly (`x.onDrop()`) nor via super-chain (`super.onDrop()`); both are compile errors. Subclasses may `override` an `internal` method; the override inherits the modifier and does not need to repeat it.

`onDrop()` is the only `internal` method introduced by this specification. The compiler emits all of its invocations: at scope exits (DROP-01), on partial-move paths (DROP-04), on exception unwind (EXC-02), and at the end of override bodies (DROP-05). Lifetime control is exclusively scope-based. The single mechanical consequence — no double-drop can be expressed — eliminates a class of double-free-shaped bugs entirely.

The `internal` modifier is reserved for future compiler-orchestrated hooks. It is not a general-purpose access-control level; ordinary visibility scoping continues to use `public`, `protected`, `private`, and package-default.

### DROP-07 — `onDrop()` is no-throw; uncaught exceptions abort

An `onDrop()` invocation must not propagate an exception to its compiler-emitted call site. Any exception that escapes the override body — directly thrown, or propagated from a transitively called method, or from the auto-chained `super.onDrop()` — causes the program to terminate immediately.

This rule eliminates the leak shapes that exception-during-cleanup would otherwise create: skipped sibling-binding drops (DROP-02), skipped super-chain (DROP-05), and skipped enclosing-scope unwind (EXC-02). A throwing `onDrop()` is treated as a fatal program error rather than a recoverable condition, analogous to C++'s `std::terminate` for exceptions during stack unwinding and Rust's abort behavior for panics during drop.

Implementations must insert a runtime guard at each compiler-emitted `onDrop()` call site that catches any exception and triggers termination with diagnostic output identifying the throwing class's `onDrop()` and the originating exception. `onDrop()` overrides that perform fallible operations (network flushes, file syncs) must catch and handle exceptions internally — typically by logging and continuing, since the operation has no caller to report failure to.

---

## 7. Copying

### OBJ-01 — Auto-generated copy constructor

Every class has a `protected ClassName(ClassName source)` copy constructor. The compiler synthesizes one when none is provided. The synthesized form chains `super(source)` and copies each field: primitives bitwise; owned object fields via the field's `clone()` method (`source.field.clone()`). A user-provided copy constructor with the same signature suppresses synthesis.

The synthesized form uses `clone()` rather than the field type's copy constructor because the latter is `protected` and not accessible across class boundaries — only a class's own subclasses can invoke its protected constructor. `clone()` is public (OBJ-02), uniformly callable, and dispatches virtually to handle subtype duplication correctly.

The rule is uniform — there is no special-casing for any field type. Whether a field's `clone()` allocates fresh storage, bumps a refcount, or does something else is that field type's own concern. If a field's `clone()` is `broken` (UNR-01), the enclosing class's auto-generated copy constructor reaches `broken` transitively and is rejected at compile time. The class author can opt out explicitly with their own `broken` clone (OBJ-02), provide a copy constructor that handles the field differently, or accept the inherited brokenness.

```laterita
class User {
    String name;
    int age;
    Shared<Address> address;
    // synthesized:
    //   super(source);
    //   name = source.name.clone();
    //   age = source.age;                    // bitwise
    //   address = source.address.clone();    // share-bump, via Shared's clone()
}

class CachedFile extends File {
    Cache cache;
    LogPolicy policy;

    // User-provided; suppresses auto-gen.
    protected CachedFile(CachedFile source) {
        super(source);
        this.cache = source.cache.clone();
        this.policy = source.policy;          // share, not duplicate
    }
}

class SecretKey {
    byte[] material;
    // Class-level opt-out via broken clone() (OBJ-02).
    override give SecretKey clone() {
        broken "secret keys must not be copied";
    }
}
```

The copy constructor is `protected` so subclasses can chain via `super(source)`; external code uses the public `clone()` method (OBJ-02).

### OBJ-02 — Auto-generated `clone()` method

Every class has a public `give Self clone()` method, synthesized as `return new Self(this);` when not provided by the user. `clone()` is the standard duplication API for code that does not statically know the concrete class — generic code over a type parameter, and polymorphic code holding a value at a supertype or interface — because the call dispatches virtually to the actual class's `clone()`.

```laterita
<T> List<T> deepCopy(List<T> source) {
    let result = new List<T>();
    for (T item : source) {
        result.add(item.clone());
    }
    return give result;
}

deepCopy(users);       // OK
deepCopy(secretKeys);  // ERROR: SecretKey.clone() is broken
```

The standard opt-out is a user-provided `clone()` override whose body is `broken`, as in `SecretKey` above. Marking `clone()` rather than the copy constructor is the natural place: the copy constructor is internal and reached only via `clone()`, while `clone()` is the public contract callers see.

A user override of `clone()` is otherwise rare. The auto-generated form is the canonical implementation; overriding only makes sense for opting out (`broken`) or when polymorphic dispatch needs different semantics from the copy constructor (e.g., factory-method patterns).

---

## 8. Unreachability

### UNR-01 — `broken` declares a path unreachable

The statement `broken;` (or `broken "<reason>";`) declares that the enclosing path must not be reachable. The compiler must reject any program in which the statement can be reached on a path it cannot prove dead.

`broken` is a divergence point: code following it in the same block is unreachable, and the enclosing function need not produce a value of its declared return type when control flow ends in `broken`.

Typical use is opting out of an operation that would otherwise be required by auto-synthesis or generic-context use — a copy constructor for a non-copyable type, a method whose contract cannot be honoured, etc.

```laterita
class File {
    Heap<FileHandle> handle;
    override give File clone() {
        broken "files cannot be copied";
    }
}

<T> List<T> deepCopy(List<T> source) {
    let result = new List<T>();
    for (T item : source) {
        result.add(item.clone());
    }
    return give result;
}

deepCopy(users);   // OK: User.clone() is the synthesized form
deepCopy(files);   // ERROR: File.clone() reaches `broken`
```

Diagnostics must identify the reachable path that leads to `broken` and report the reason string when one was provided.

A conditional form is naturally expressible as an `if` guarding `broken`:

```laterita
if (n < 0) broken "n must be non-negative";
```

The compiler's standard dead-code analysis applies. If the condition is provably constant-false, the `broken` is dead and the program compiles. If not, `broken` is reachable on some path and the program is rejected. No additional rule or analysis machinery is required.

---

## 9. Strings

### STR-01 — `String` is a normal class

`String` is not final. Classes may extend it. The compiler must permit user-defined subclasses such as `class Email extends String`.

### STR-02 — Strings are tracked as owned or borrowed per binding

A `String` binding is either an owned heap allocation or a borrowed view into another `String`'s storage. The compiler tracks this per-binding and applies lifetime rules to borrowed instances.

### STR-03 — Slice methods return borrows

Methods that return a view into the receiver's storage (e.g., `substring`, `trim`) declare the borrow with `bound` on the return type per LIFE-02.

```laterita
class String {
    bound String substring(int start, int end);
    bound String trim();
}
```

### STR-04 — Allocating methods return owned strings

Methods that produce new storage (e.g., `toUpperCase`, `concat`) return an owned `String` with no lifetime tie to the receiver.

### STR-05 — User-defined subclasses are owned

Subclasses of `String` declared by user code are owned. They cannot be returned as borrows into other storage.

---

## 10. Closures

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

## 11. Exceptions

### EXC-01 — Existing Java exception syntax is preserved

This specification does not redefine Java's exception syntax. Functions declare `throws`, callers use `try`/`catch`/`finally`, exceptions propagate through the call stack.

### EXC-02 — Cleanup runs on exception unwind

When an exception propagates out of a scope, all `onDrop()` calls required by DROP-01 through DROP-04 must execute as part of the unwind, before the exception reaches the next handler. If an `onDrop()` invocation throws, DROP-07 applies.

### EXC-03 — Drop flags participate in unwind

DROP-04's drop flags must be consulted during exception unwind, not only on normal exit.

### EXC-04 — Lazy stack-trace resolution

When an exception is thrown, the runtime must capture the current call stack as raw return addresses. Symbol resolution (mapping addresses to source locations) must be deferred until the trace is inspected. The captured trace is owned by the exception object and freed with it.

---

## 12. Unsafe

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

## 13. Standard Library Types (Required)

### STD-01 — `Shared<T>`

A reference-counted shared-ownership smart pointer for single-threaded use. Provides:
- `new Shared<T>(take T value)` — takes ownership of `value`, refcount 1.
- `new Shared<T>(Shared<T> other)` — copy constructor; the new handle points to the same allocation, bumping the refcount. The contained value is not duplicated.
- `bound T read()` — returns a shared borrow of the contained value, bound to this handle.
- `Shared<T> share()` — alias for the copy constructor; explicit refcount bump.
- `onDrop()` — decrements the refcount; drops the value at zero. Declared `internal` like every `onDrop()` (DROP-06); compiler-emitted at scope exit, never called by user code.

A bare assignment of `Shared<T>` is a borrow per MOVE-01; a `give` move transfers the handle without bumping; `share()` is the only operation that bumps.

A cycle of `Shared<T>` handles whose strong references form a closed loop is not reclaimed: no handle's refcount can reach zero, and the cycle leaks. Programs that may form cycles must use `Weak<T>` (STD-03) for the back-edge to break the cycle. This matches the behavior of Rust's `Rc<T>`/`Weak<T>`; Laterita does not provide a cycle collector.

### STD-02 — `Atomic<T>`

The cross-thread analog of `Shared<T>`. Reference count operations are atomic. The copy constructor `new Atomic<T>(Atomic<T> other)` performs the atomic refcount bump. `Atomic<T>` is `Send` (see STD-Send).

### STD-03 — `Weak<T>`

A non-owning back-reference. Provides:
- `Weak<T>` produced from `Shared<T>::downgrade()`.
- `Shared<T>? upgrade()` — returns a strong handle if the value is still alive, otherwise `null`. Implementation must be race-free with respect to concurrent strong-count decrement (compare-and-swap upgrade per STD-04).

### STD-04 — Race-safe `Atomic<T>` upgrade

`Weak<T>::upgrade()` on an `Atomic`-flavored weak handle must use compare-and-swap to atomically check the strong count is non-zero and bump it. A simple read-then-bump is unsound.

### STD-05 — `Cell<T>`

Interior-mutability primitive. Permits mutation of contents through a non-`mut` binding. Construction and content mutation require `unsafe` context per UNS-02. Used as a building block for `Atomic<T>`, `Mutex<T>`, lazy initializers, etc.

### STD-06 — `Heap<T>`

Raw heap-allocation primitive. Provides allocation, dereference, and free. All operations require `unsafe` context per UNS-02. `Heap<T>.clone()` is `broken`: a raw allocation has no defined duplication semantics — duplicating the handle would create two owners of the same memory. Wrapper types built on `Heap<T>` (e.g., `Shared<T>`, owned containers) define their own `clone()` with the appropriate semantics.

### STD-07 — Send marker

Types that are safe to move between threads carry the `Send` property. Types that are not (such as a single-threaded `Shared<T>`) are not `Send`. The compiler must reject moves of non-`Send` values across thread boundaries except inside `unsafe` methods. (Syntax for declaring `Send` is not fixed by this specification — see open questions.)

---

## 14. Compilation Model

### COMP-01 — Native compilation, no GC

Laterita is intended to be compiled ahead-of-time to native code. There is no garbage collector at runtime. Memory management is determined by static ownership, borrow tracking, and `onDrop()` insertion at scope exits. Reference-counted types (`Shared<T>`, `Atomic<T>`) introduce dynamic refcount-based reclamation; cycles among such handles leak per STD-01. No tracing collector is provided.

### COMP-02 — Generic monomorphization

Generic types and methods are monomorphized: each instantiation produces a specialized implementation at compile time. Field offsets and method dispatch are resolved per-instantiation.

### COMP-03 — Compiler-inserted cleanup

The compiler must emit `onDrop()` calls at every scope-exit point per DROP-03 and unwind table entries per EXC-02. These insertions happen after all user-level analysis and are not visible in source. Each emitted call site must include the runtime guard required by DROP-07.

### COMP-04 — Drop flags as compile-time state

Per-field move state (DROP-04) is compiler-internal bookkeeping. Implementations should optimize away flags whose values are statically determined.

---

## 15. Reserved Names

The following names are introduced by this specification and must be provided by the standard library: `Shared`, `Atomic`, `Weak`, `Cell`, `Heap`. Three closure interfaces required by CLO-03 must also be provided; their names are not fixed by this specification.

The identifier `onDrop` is reserved as the language-orchestrated lifecycle hook (DROP-01). The keyword `internal` is introduced as a visibility modifier marking a method as compiler-only-callable (DROP-06); it is not a general-purpose access level and is currently used only by `Object.onDrop()`. User code may declare `override` of an `internal` method but cannot invoke one in any form.

The following keywords are introduced or repurposed by this specification: `let` (immutable type-inferred binding), `mut` (mutability marker for local bindings, fields, methods, and parameters), `give` (use-site move marker per MOVE-02; also an optional declarative prefix on return types per LIFE-02), `take` (parameter-type prefix declaring an owned parameter per MOVE-03; also an optional declarative LHS prefix on binding declarations), `bound` (borrow-source marker on parameter types and return types per LIFE-02), `broken` (statement declaring a path unreachable per UNR-01), `unsafe` (private method modifier), `internal` (visibility modifier for compiler-only-callable methods per DROP-06). Java's `var` keyword for local-variable type inference is not used in Laterita; `let` and `mut` cover the type-inferred forms.

The `?` suffix denotes nullable types per NULL-02; `?.` is the safe-call operator (NULL-04); `?:` is the Elvis operator (NULL-05); `!!` is the null-assertion operator (NULL-07).

Java's existing keywords and their meanings are preserved unless explicitly modified by this specification.
