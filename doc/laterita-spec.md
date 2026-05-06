# Laterita — Language Specification

This document specifies the normative requirements that a Laterita compiler and standard library must satisfy. Each requirement carries a mnemonic code for cross-reference.

Codes are grouped by area: `BIND` (bindings), `NULL` (optionality), `MOVE` (move/borrow), `MUT` (mutability), `LIFE` (lifetimes), `DROP` (cleanup), `OBJ` (object copying), `UNR` (unreachability), `STR` (strings), `CLO` (closures), `EXC` (exceptions), `UNS` (unsafe), `STD` (standard library types), `THR` (threads), `COMP` (compilation model).

---

## 1. Bindings

### BIND-01 — Four binding forms

The language provides exactly four local binding forms:

| Form | Meaning |
|---|---|
| `Type name = expr` | immutable, type explicit |
| `let name = expr` | immutable, type inferred |
| `mut Type name = expr` | mutable, type explicit |
| `mut name = expr` | mutable, type inferred |

Whether the binding holds an owned value or a borrow is determined by the RHS expression per MOVE-01 and MOVE-02.

```laterita
String greeting = "hello";
let count = items.size();
mut sb = new StringBuilder();
mut int retries = 0;
```

### BIND-02 — `mut` is the unified mutability marker

The keyword `mut` denotes mutability in every position it appears: local bindings, fields, methods, and parameters.

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

A method marked `mut` may mutate `this` (i.e., reassign or mutate-through `mut` fields, and call other `mut` methods on `this`). A method without `mut` cannot.

In a method declaration, `mut` occupies the slot immediately after the visibility modifiers (`public`, `protected`, `private`, `internal`) and before every other modifier. It is a visibility-like predicate rather than a behavioral one: by BIND-06, a `mut` method is only callable on receivers whose binding is itself `mut`, so the marker narrows the method's visible API surface to mutable receivers. Other modifiers (`static`, `final`, `override`, `unsafe`, and Java's `native`/`strictfp`) follow `mut` in their conventional Java positions.

```laterita
class Counter {
    mut int n;
    public int read()              { return n; }       // immutable receiver
    public mut void inc()          { n = n + 1; }      // mutable receiver
    public mut final void reset()  { n = 0; }          // mut precedes final
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

### BIND-07 — Methods declare consumption of `this` with `give`

A method marked `give` consumes its receiver. The body owns `this`, may move out of `this`'s fields (MOVE-07), and may hand `this` itself to a `take` parameter or to another `give` method. After the call returns, the binding that held the receiver is consumed (MOVE-04, MOVE-08); subsequent uses are rejected.

In a method declaration, `give` occupies the slot immediately after the visibility modifiers (`public`, `protected`, `private`, `internal`), preceding `mut` and every other modifier. The combination `give mut` parallels the parameter form `take mut T` (MOVE-03): the receiver is consumed and the implicit `this` slot is reassignable. Other modifiers (`static`, `final`, `override`, `unsafe`, and Java's `native`/`strictfp`) follow in their conventional Java positions.

Calling a `give` method requires the receiver binding to own its value; a borrowed binding cannot satisfy a `give` call. The receiver consumption is implicit at the call site — no `give` keyword is written there, since the method's signature already declares the transfer.

```laterita
class Connection {
    Heap<DbConn> conn;

    public give void close() {                       // consumes this
        this.conn.flush();
        // `this` is dropped at function end; underlying connection released
    }
}

class StringBuilder {
    mut String contents;

    public give StringBuilder append(String s) {     // consumes this, returns new
        this.contents = this.contents + s;
        return give this;
    }

    public give String build() {                     // consumes this, yields String
        return give this.contents;
    }
}

let conn = openConnection();
conn.close();        // OK: conn was owned; consumed by close()
conn.use();          // ERROR: conn was consumed
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

`T` must be a reference type. Nullable primitive types (`int?`, `long?`, `boolean?`, etc.) are rejected at compile time; code that requires null-bearing integer or boolean semantics must use the boxed reference type (`Integer?`, `Boolean?`, …). The compiler does not auto-box at the type-suffix level.

```laterita
String? maybeName = lookup(id);
print(maybeName.length());   // ERROR: requires null check
```

### NULL-03 — `null` literal

The literal `null` has type `Nothing?` and is assignable to any `T?`. `null` is not assignable to a non-nullable type.

### NULL-04 — Safe call `?.`

`expr?.method(args)` evaluates to `null` if `expr` is `null`, otherwise invokes `method` on `expr`. The result type is `R?` where `R` is the method's return type.

```laterita
String? upper = maybeName?.toUpperCase();
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

### MOVE-08 — `give` to void

A `give` expression with no destination, written as the statement `give x;`, consumes the binding `x` and invokes its `onDrop()` immediately. Equivalent in effect to passing `x` to a function whose only act is to receive ownership and let the parameter go out of scope. After `give x;`, the binding `x` is consumed; subsequent uses are rejected per MOVE-04.

```laterita
let worker = Thread.ofVirtual().start(() -> task());
if (changedMyMind()) { give worker; }   // run Thread.onDrop() now; binding consumed
```

Applies to any owning binding. For `Thread`, it is the standard mechanism for early termination per THR-06.

### MOVE-09 — `take` participates in overload resolution

Two methods in the same scope may have signatures that differ only in the `take` marker on one or more parameters; they are distinct overloads. The `mut` marker is **not** part of the overload signature: two methods that differ only in `mut` on a parameter are the same method, and declaring both in the same scope is a compile error.

Java's standard overload resolution applies first: candidates are filtered by applicability, type specificity, boxing, and varargs as today. The ownership axis enters only as a tie-breaker, and only when Java's procedure leaves more than one applicable overload.

The tie-breaker rule: among overloads remaining after Java's procedure, prefer the overload that uses borrow on every parameter position where another candidate uses `take` (agreeing on all other positions). Equivalently, the overload that demands the least from the caller on the ownership axis wins. If no uniquely most-permissive overload exists — for example, two overloads each borrow one parameter and consume a different one — the call is ambiguous and the caller must disambiguate with `give` on the parameter(s) intended for consumption.

The caller opts in to a `take` overload by writing `give arg` at the call site. A `give` argument is applicable only to a `take` parameter (per MOVE-03), so writing `give` removes the borrow form from the candidate set on that argument position.

```laterita
void put(take K key, take V value);    // (a) consumes both
void put(K key, take V value);         // (b) borrows the key, consumes the value

let k = makeKey();
let v = makeValue();
put(k, v);          // (a) and (b) tie on Java specificity → tie-breaker → (b) wins
put(give k, v);     // give removes (b) → (a) wins
```

The implicit transfer described in MOVE-03 ("a bare argument that is a binding implicitly transfers ownership when the parameter is `take`") applies whenever the resolved overload is a `take` form. Whether resolution lands there depends on the overloads in scope and on Java's specificity rules: when type specificity is decisive — e.g., `f(Animal)` borrow vs. `f(take Dog)` consume on a `Dog` argument — the ownership tie-breaker does not run, the more-specific overload wins as in standard Java, and a bare `Dog` argument is consumed by `f(take Dog)`.

Two consequences for interface evolution:
- Adding a same-type borrow overload alongside an existing `take` shifts bare call sites from consume to borrow on the tie-breaker.
- Adding a more-specific `take` overload alongside an existing less-specific borrow shifts bare call sites at the more-specific type from borrow to consume on Java specificity.

For arguments where the drop point is observable (large buffers, lock guards, files), authors should write `give` explicitly at sites that need to be pinned to the consuming form, even when only one overload exists today.

### MOVE-10 — Override variance for `take` and `mut`

For overrides of inherited methods (subclass override, interface implementation):

- **`take` matches invariantly.** An override's parameter must carry `take` if and only if the inherited declaration does. This follows from MOVE-09: a parameter list differing on `take` is a different overload, not an override of the same method.

- **`mut` matches contravariantly.** An override may drop `mut` from a parameter that the inherited declaration marks `mut`, but may not add `mut` to a parameter the inherited declaration leaves bare. Dropping `mut` is sound — the override demands less of the caller than the inherited contract promises. Adding `mut` is unsound — callers holding only an immutable borrow could no longer satisfy the override through the inherited type.

```laterita
interface Visitor {
    void visit(mut Node n);
}

class CountingVisitor implements Visitor {
    override void visit(Node n) { ... }       // OK: drops mut; requires less
}

class RewritingVisitor implements Visitor {
    override void visit(mut Node n) { ... }   // OK: matches exactly
}

interface Reader {
    void read(Node n);
}

class BadReader implements Reader {
    override void read(mut Node n) { ... }    // ERROR: cannot strengthen mut
}
```

The variance rule mirrors Java's existing treatment of `throws`: an override may declare fewer or narrower checked exceptions than the inherited signature, never more. The principle is the same — an override may relax its demands on callers, never tighten them.

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

A bare return type means the function gives the caller an owned value. To declare a borrowed return instead, the contributing source is marked with `bound`:

- **Parameter source**: prefix the parameter type with `bound`. The return is bound to that parameter.
- **Receiver source**: prefix the return type with `bound`. The return is bound to `this`.

```laterita
String upperCase(String s);                    // owned return

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
    Rc<File> f = openFile();
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

### DROP-06 — `internal` visibility forbids user invocation

The visibility modifier `internal` declares that a method may be invoked only by compiler-emitted call sites. User code cannot invoke an `internal` method directly (`x.onDrop()`) nor via super-chain (`super.onDrop()`); both are compile errors. Subclasses may `override` an `internal` method; the override inherits the modifier and does not need to repeat it.

`onDrop()` is the only `internal` method introduced by this specification. The compiler emits all of its invocations: at scope exits (DROP-01), on partial-move paths (DROP-04), on exception unwind (EXC-02), and at the end of override bodies (DROP-05).

The `internal` modifier is reserved for future compiler-orchestrated hooks. It is not a general-purpose access-control level; ordinary visibility scoping continues to use `public`, `protected`, `private`, and package-default.

### DROP-07 — `onDrop()` is no-throw; uncaught exceptions abort

An `onDrop()` invocation must not propagate an exception to its compiler-emitted call site. Any exception that escapes the override body — directly thrown, or propagated from a transitively called method, or from the auto-chained `super.onDrop()` — causes the program to terminate immediately.

Implementations must insert a runtime guard at each compiler-emitted `onDrop()` call site that catches any exception and triggers termination with diagnostic output identifying the throwing class's `onDrop()` and the originating exception. `onDrop()` overrides that perform fallible operations (network flushes, file syncs) must catch and handle exceptions internally.

---

## 7. Copying

### OBJ-01 — Auto-generated copy constructor

Every class has a `protected ClassName(ClassName source)` copy constructor. The compiler synthesizes one when none is provided. The synthesized form chains `super(source)` and copies each field: primitives bitwise; owned object fields via the field's `clone()` method (`source.field.clone()`). A user-provided copy constructor with the same signature suppresses synthesis.

If a field's `clone()` is `broken` (UNR-01), the enclosing class's auto-generated copy constructor reaches `broken` transitively and is rejected at compile time.

```laterita
class User {
    String name;
    int age;
    Rc<Address> address;
    // synthesized:
    //   super(source);
    //   name = source.name.clone();
    //   age = source.age;                    // bitwise
    //   address = source.address.clone();    // share-bump, via Rc's clone()
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
    override SecretKey clone() {
        broken "secret keys must not be copied";
    }
}
```

### OBJ-02 — Auto-generated `clone()` method

Every class has a public `Self clone()` method, synthesized as `return new Self(this);` when not provided by the user. `clone()` is the standard duplication API for code that does not statically know the concrete class — generic code over a type parameter, and polymorphic code holding a value at a supertype or interface — because the call dispatches virtually to the actual class's `clone()`.

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

A class opts out of copying by overriding `clone()` with a `broken` body, as in `SecretKey` above.

---

## 8. Unreachability

### UNR-01 — `broken` declares a path unreachable

The statement `broken;` (or `broken "<reason>";`) declares that the enclosing path must not be reachable. The compiler must reject any program in which the statement can be reached on a path it cannot prove dead.

`broken` is a divergence point: code following it in the same block is unreachable, and the enclosing function need not produce a value of its declared return type when control flow ends in `broken`.

```laterita
class File {
    Heap<FileHandle> handle;
    override File clone() {
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

A conditional form is expressible as an `if` guarding `broken`; the compiler's standard dead-code analysis determines whether the path is reachable:

```laterita
if (n < 0) broken "n must be non-negative";
```

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

- **Read** — captured bindings are immutably borrowed; closure may be invoked any number of times, including from multiple threads simultaneously (subject to the `local` rules of STD-07).
- **Mutate** — captured bindings include a mutable borrow; closure may be invoked any number of times sequentially but not concurrently.
- **Consume** — captured bindings include a moved value; closure may be invoked exactly once.

### CLO-02 — Capture mode is inferred

The compiler infers a closure's capture mode from the body. The user does not declare it.

### CLO-03 — Closures have structural function types

A closure's type is a structural function type written

```
(P1, P2, …, Pn) -> R
```

where each `Pi` is a parameter declaration following MOVE-03 form (bare `T`, `mut T`, `take T`, with optional `bound` per LIFE-02) and `R` is the return type. Two function types are identical iff their arity, each parameter's mode and underlying type, the return type, and any `bound` relationships match. Function types may appear wherever another type may appear: parameter, return, field, local binding, generic argument.

A parameter of function type follows the standard parameter-modifier rules. The slot's mode controls which closures may be passed to it by determining which receiver-mode SAM may be invoked through it (BIND-06, BIND-07):

- **Bare slot** — invocations need a bare-receiver SAM. Read closures (CLO-01) fit.
- **`mut` slot** — invocations need a bare- or mut-receiver SAM. Read or mutate closures fit.
- **`take` slot** — invocations need any receiver-mode SAM, including `give`. Read, mutate, or consume closures all fit.

There is no normatively-named closure interface in the standard library. For each lambda the compiler synthesizes one anonymous class implementing the SAM dictated by the inferred function type and the captures' mode (CLO-01, CLO-02). The synthesized class is not addressable from source code.

```laterita
// Read-or-mutate lambda: consumes input, mut-borrows buffer, returns owned R.
<A, B, R> R fold(take A input, mut B buffer, mut (take A, mut B) -> R lambda) {
    return lambda(give input, buffer);
}

// Read lambda whose return is bound to the first input's lifetime.
<A, B, R> R lookup(bound A source, B key, (bound A, B) -> R lambda) {
    return lambda(source, key);
}

// One-shot consume callback: `take` slot admits a give-receiver SAM.
void onClose(take () -> void action) {
    action();
}
```

### CLO-04 — Capture lifetimes propagate

A closure value carries the lifetimes of every binding it captures by borrow. The closure cannot outlive any captured borrow. Lifetime intersection (LIFE-03) applies when multiple borrows are captured.

---

## 11. Exceptions

### EXC-01 — Existing Java exception syntax is preserved

This specification does not redefine Java's exception syntax. Methods may declare `throws`, callers use `try`/`catch`/`finally`, exceptions propagate through the call stack, and the `Throwable` hierarchy is reused. The checked/unchecked distinction is removed per EXC-05; the `throws` clause becomes documentary.

### EXC-02 — Cleanup runs on exception unwind

When an exception propagates out of a scope, all `onDrop()` calls required by DROP-01 through DROP-04 must execute as part of the unwind, before the exception reaches the next handler. If an `onDrop()` invocation throws, DROP-07 applies.

### EXC-03 — Drop flags participate in unwind

DROP-04's drop flags must be consulted during exception unwind, not only on normal exit.

### EXC-04 — Lazy stack-trace resolution

When an exception is thrown, the runtime must capture the current call stack as raw return addresses. Symbol resolution (mapping addresses to source locations) must be deferred until the trace is inspected. The captured trace is owned by the exception object and freed with it.

### EXC-05 — All exceptions are unchecked

The compiler performs no checked-exception analysis. Any throwable type may be thrown from any method without a corresponding declaration, and callers are never required to catch a particular exception type or re-declare it on their own signatures. Java's distinction between `Exception` and `RuntimeException` carries no language-level significance in Laterita; the entire `Throwable` hierarchy is uniformly unchecked.

The `throws` clause is permitted as documentation. A method may list the exception types it expects to propagate, and tooling (IDEs, generated documentation) may surface that list. The list is not enforced: declaring `throws X` does not commit the method to throwing only `X`, and omitting the clause does not prevent any exception from propagating.

---

## 12. Unsafe

### UNS-01 — `unsafe` is a private method modifier

Unsafe operations are permitted only inside methods declared `private unsafe`. There is no `unsafe` modifier on classes and no `unsafe { }` block form. Public APIs are always safe; safety contracts are upheld inside private unsafe methods.

```laterita
public class Rc<T> {
    Heap<ControlBlock<T>> ctrl;

    public Rc<T> share() {
        bumpRefcount();
        return makeHandle();
    }

    private unsafe void bumpRefcount() { /* ... */ }
    private unsafe Rc<T> makeHandle() { /* ... */ }
}
```

### UNS-02 — Fixed list of unsafe operations

Only the following operations require `unsafe` context:

1. Constructing or dereferencing `Heap<T>`.
2. Constructing `Cell<T>` or mutating its contents through a non-`mut` binding.
3. Cross-thread move of a `local` type (STD-07).
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

### STD-01 — `Rc<T>`

A reference-counted shared-ownership smart pointer for single-threaded use. Provides:
- `new Rc<T>(take T value)` — takes ownership of `value`, refcount 1.
- `new Rc<T>(Rc<T> other)` — copy constructor; the new handle points to the same allocation, bumping the refcount. The contained value is not duplicated.
- `bound T read()` — returns a shared borrow of the contained value, bound to this handle.
- `Rc<T> share()` — alias for the copy constructor; explicit refcount bump.
- `onDrop()` — decrements the refcount; drops the value at zero. Declared `internal` like every `onDrop()` (DROP-06); compiler-emitted at scope exit, never called by user code.

A bare assignment of `Rc<T>` is a borrow per MOVE-01; a `give` move transfers the handle without bumping; `share()` is the only operation that bumps.

A cycle of `Rc<T>` handles whose strong references form a closed loop is not reclaimed: no handle's refcount can reach zero, and the cycle leaks. Programs that may form cycles must use `WeakReference<T>` (STD-03) for the back-edge to break the cycle.

### STD-02 — `Arc<T>`

The cross-thread analog of `Rc<T>`. Reference count operations are atomic. The copy constructor `new Arc<T>(Arc<T> other)` performs the atomic refcount bump. `Arc<T>` is non-`local` per STD-07 and may be moved or borrowed across thread boundaries.

### STD-03 — `WeakReference<T>`

A non-owning back-reference. The class name and method names follow `java.lang.ref.WeakReference`. Provides:
- `new WeakReference<T>(Rc<T> source)` / `new WeakReference<T>(Arc<T> source)` — constructs a weak handle from a strong one. Mirrors Java's `new WeakReference<T>(referent)` shape but takes the strong handle, because the weak handle is bound to a refcount, not to a GC-tracked referent.
- `Rc<T>? get()` (or `Arc<T>? get()`, matching the source flavor) — returns a strong handle if the value is still alive, otherwise `null`. Implementation must be race-free with respect to concurrent strong-count decrement (compare-and-swap per STD-04).

The return type of `get()` differs from `java.lang.ref.WeakReference.get()`: Java returns the bare referent `T` because the GC keeps it alive across the call site; Laterita returns a fresh strong handle `Rc<T>?` / `Arc<T>?` because liveness during use must be carried by an owning handle, not by a collector. Once the caller drops the returned handle, the value may be reclaimed at the next refcount-zero.

### STD-04 — Race-safe `Arc<T>` upgrade

`WeakReference<T>::get()` on an `Arc`-flavored weak handle must use compare-and-swap to atomically check the strong count is non-zero and bump it. A simple read-then-bump is unsound.

### STD-05 — `Cell<T>`

Interior-mutability primitive. Permits mutation of contents through a non-`mut` binding. Construction and content mutation require `unsafe` context per UNS-02. Used as a building block for `Arc<T>`, `Mutex<T>`, lazy initializers, etc.

### STD-06 — `Heap<T>`

Raw heap-allocation primitive. Provides allocation, dereference, and free. All operations require `unsafe` context per UNS-02. `Heap<T>.clone()` is `broken`: a raw allocation has no defined duplication semantics — duplicating the handle would create two owners of the same memory. Wrapper types built on `Heap<T>` (e.g., `Rc<T>`, `Arc<T>`, owned containers) define their own `clone()` with the appropriate semantics.

### STD-07 — `local` marker

Cross-thread safety in Laterita is expressed by a single negative marker, `local`. The language does **not** provide `Send` or `Sync` traits; that vocabulary belongs to Rust and has no analog here. Inter-thread communication uses `Mutex<T>` (STD-09) for shared mutable state and the existing `java.util.concurrent` channel-like classes (e.g., `BlockingQueue`) for hand-off — no auto-trait machinery is involved.

A type carries the `local` property if its instances cannot safely cross thread boundaries.

The standard library declares `local`:
- `Rc<T>` (STD-01)
- `Cell<T>` (STD-05)
- `Heap<T>` (STD-06)

A class is `local` by inference if any field in its transitive field hierarchy is of a `local` type. A class is **non-local** otherwise. A class may be declared `local` to opt in despite having no `local` fields (used for thread-affine resources whose affinity is not visible to the type system: OS handles, GPU contexts, etc.).

A class may be declared `unsafe nonlocal` to override inferred `local`-ness despite containing `local` fields. This declaration asserts that the class internally synchronizes access to those fields per UNS-04. The compiler does not verify the assertion. The stdlib types `Arc<T>` (STD-02), `Mutex<T>`, and `Thread` (THR-01) are declared `unsafe nonlocal`.

The compiler must reject:
- A cross-thread closure capture (CLO-01) of a binding whose type is `local`.
- A move (MOVE-02) of a `local` value across a thread boundary outside `unsafe` (UNS-02 already gates this).

### STD-08 — Borrow-checked mutable iteration

Three operations support in-place modification of collections under the borrow rules:

- **`Collection<T>.removeIf(Predicate<T> p)`** — bulk removal of every element matching `p`. Same name and meaning as `java.util.Collection.removeIf` (Java 8+).
- **`Iterator<T>` and `ListIterator<T>`** — Java's existing iterator types, reused by name and by method set (`hasNext`, `next`, `hasPrevious`, `previous`, `nextIndex`, `previousIndex`, `remove`, `set`, `add`).
- **`next()` and `previous()` return `bound T`** — a borrow into the underlying collection's storage, bound to the iterator. Any iterator-mutating call (`remove`, `set`, `add`) invalidates the borrow at the type level via MOVE-04.

The one signature deviation from Java: **`Iterator<T>.remove()` and `ListIterator<T>.remove()` return `T`** rather than `void`. The removed element is yielded to the caller as an owned value. Statement-form `it.remove();` (ignoring the return) drops the value via `onDrop` (DROP-01), matching the observable behavior of Java's void-returning `remove`.

Holding a `mut Iterator<T>` or `mut ListIterator<T>` is a mutable borrow of the underlying collection per MOVE-04. Concurrent modification through any other path is rejected at compile time; `ConcurrentModificationException` is not part of Laterita's runtime semantics, and `modCount`-style runtime guards are not required.

Implementations of these operations are permitted (and expected) to use `private unsafe` (UNS-01) for the internal aliasing they require. User code remains safe.

### STD-09 — `Mutex<T>`

A mutual-exclusion primitive wrapping an owned value. The API mirrors `java.util.concurrent.locks.Lock` for blocking, fairness, and timed acquisition; what follows specifies the differences.

**Constructor.** `new Mutex<T>(take T value)` — wraps `value`, initially unlocked and unpoisoned.

**Acquisition returns a guard.** `lock()` and `tryLock()` (including timed variants) return `bound mut MutexGuard<T>` rather than `void`/`boolean`. The guard *is* the critical section: its `onDrop` releases the lock, and there is no `unlock()` method. `MutexGuard<T>.value()` returns a `bound mut T` borrow of the protected value.

**Acquisition can throw.** `lock()` throws `PoisonedException` (THR-10) on a poisoned mutex and `InterruptedException` (THR-04) if the calling thread is interrupted while blocked. `tryLock()` throws `PoisonedException` only.

**Drop semantics.** `MutexGuard<T>.onDrop()` releases the lock; if the guard is being dropped on an exception unwind (EXC-02), the mutex is marked poisoned (THR-10) before release. `Mutex<T>.onDrop()` runs `T.onDrop()` on the protected value unconditionally — by LIFE-01 no guard can be outstanding when the mutex itself is dropped, so cleanup is independent of lock or poison state.

**Inspection.** `isPoisoned()` reads the poison flag without acquiring the lock.

`Mutex<T>` is declared `unsafe nonlocal` per STD-07.

---

## 14. Threads

### THR-01 — `Thread` type

`Thread` is the standard `java.lang.Thread` class reused minus the deprecated methods (`stop()`, `suspend()`, `resume()`, `destroy()`, etc.) and with two changes per THR-03 and THR-06.

A `Thread`'s lifetime is bound to the owner of its reference: when the owning binding goes out of scope, `Thread.onDrop()` runs (DROP-03), interrupting the worker and waiting for it to terminate. Long-lived threads (server accept loops, background flushers) must be owned by bindings whose lifetime matches — typically a top-level binding in `main` or a field of an object that is itself owned at top level.

`Thread` is declared `unsafe nonlocal` per STD-07 and may be moved or borrowed across thread boundaries.

### THR-02 — Thread creation

Threads are created using the standard Java `Thread` constructor and `start()` method, or via the fluent factory methods on `Thread.ofVirtual()` and `Thread.ofPlatform()`. No new keyword is introduced.

```laterita
mut worker = new Thread(() -> body);
worker.start();

let other = Thread.ofVirtual().start(() -> body);   // factory returns started Thread
```

Captures within the closure body follow the closure capture rules (CLO-01, CLO-04) with the additional restrictions of STD-07: each captured binding's referenced type must be non-`local`.

### THR-03 — Interrupt flag

Each `Thread` carries an interrupt flag observable via `Thread.isInterrupted()`. The flag is initially clear. `Thread.interrupt()` sets it; no operation clears it. The flag is **sticky and idempotent**: subsequent `interrupt()` calls are no-ops, and no exception, control-flow construct, or scope exit clears the flag once set.

The static `Thread.interrupted()` is synonymous with `Thread.currentThread().isInterrupted()` and does **not** clear the flag. The Java semantics in which `Thread.interrupted()` clears the flag are not provided.

Any interruption point reached after the flag is set throws `InterruptedException` (THR-08).

### THR-04 — Interruption points

An **interruption point** is a program location at which the running thread reacts to its own interrupt flag. The standard reaction is to throw `InterruptedException` from a stdlib blocking operation (`Thread.join`, `Thread.sleep`, `Object.wait`, `BlockingQueue.take`, IO read/write, and others marked as such in their stdlib definitions).

User code may also create an interruption point by polling `Thread.currentThread().isInterrupted()` or the static `Thread.interrupted()` (THR-03) and using the result to alter control flow — for example, exiting an otherwise non-terminating loop.

Reading another thread's flag via `otherThread.isInterrupted()` is **not** an interruption point: neither thread is reacting to its own state. Reading the running thread's flag without using the result for control flow (e.g. logging it) is likewise not an interruption point.

CPU-bound code that does not reach a stdlib blocking primitive and does not poll its own flag is uncancellable.

### THR-05 — `onDrop()` must not block

A user-defined or stdlib `onDrop()` body (DROP-01) must not contain an interruption point (THR-04). The compiler must reject any `onDrop()` definition whose body transitively reaches a stdlib blocking operation.

To enforce the user-code half of THR-04 conservatively, the compiler must additionally reject `Thread.currentThread().isInterrupted()` and the static `Thread.interrupted()` calls inside an `onDrop()` body. Calls of the form `otherThread.isInterrupted()` (reading another thread's flag) remain permitted, since they are observations and cannot react to the running thread's own state.

`Thread.onDrop()` (THR-06) is exempt: it is the cancellation orchestrator and runs in the parent's stack, not in a body subject to interruption. The rule applies to every other `onDrop`.

Resources whose cleanup needs to block (flush-on-close for buffered IO, drain on channel teardown) belong in an explicit `close()` method, not in `onDrop()`.

### THR-06 — `Thread.onDrop()`

`Thread.onDrop()` is `internal` (DROP-06) and is compiler-emitted at scope exit per DROP-03. It performs, in order:

1. Set the interrupt flag (idempotent per THR-03).
2. Wait for the worker to terminate. Termination is bounded by the worker reaching its next interruption point and unwinding via `InterruptedException`; the worker's own `onDrop` chain runs frame-by-frame during the unwind (DROP-03).
3. Reclaim the thread's resources.

To trigger `Thread.onDrop()` before natural scope exit, give the binding to the void per MOVE-08 (`give worker;`).

### THR-07 — `Thread.interrupt()`

`Thread.interrupt()` sets the interrupt flag on the receiver per THR-03 and returns immediately. It does not wait for the worker to unwind. May be called from any thread holding a reference to the receiver.

### THR-08 — `InterruptedException`

`InterruptedException` is the exception thrown at an interruption point (THR-04) when the running thread's interrupt flag is set. It propagates through the standard exception unwind path (EXC-02). Catching `InterruptedException` does not clear the interrupt flag (THR-03); the next interruption point in the same thread throws it again.

`InterruptedException` is unchecked per EXC-05; methods containing interruption points are not required to declare it.

### THR-09 — `Thread.join()`

`Thread.join()` blocks the calling thread until the receiver terminates. It is an interruption point per THR-04: if the calling thread's interrupt flag is set while it is blocked in `join()`, it throws `InterruptedException`.

`join()` does not interrupt the receiver. To cancel and observe, call `worker.interrupt()` and then `worker.join()`.

### THR-10 — `Mutex<T>` poisoning

A `Mutex<T>` whose holder unwinds (via `InterruptedException` or any other thrown exception) before exiting its critical section is **poisoned**. The outstanding `MutexGuard`'s `onDrop` on the unwind path (EXC-02) sets the mutex's poison flag before releasing the lock.

`Mutex<T>.lock()` and `tryLock()` throw `PoisonedException` on a poisoned mutex. There is no bypass: a poisoned mutex's contents are no longer reachable through the locking API. Programs that need to recover from poisoning replace the entire `Mutex<T>` (typically the surrounding `Arc<Mutex<T>>`); the replaced instance is dropped along with its protected value through the standard `onDrop` path (STD-09).

Poisoning is per-mutex, sticky, and not cleared by lock release or by inspection. `isPoisoned()` reads the flag without acquiring the lock.

---

## 15. Compilation Model

### COMP-01 — Native compilation, no GC

Laterita is intended to be compiled ahead-of-time to native code. There is no garbage collector at runtime. Memory management is determined by static ownership, borrow tracking, and `onDrop()` insertion at scope exits. Reference-counted types (`Rc<T>`, `Arc<T>`) introduce dynamic refcount-based reclamation; cycles among such handles leak per STD-01. No tracing collector is provided.

### COMP-02 — Generic monomorphization

Generic types and methods are monomorphized: each instantiation produces a specialized implementation at compile time. Field offsets and method dispatch are resolved per-instantiation.

### COMP-03 — Compiler-inserted cleanup

The compiler must emit `onDrop()` calls at every scope-exit point per DROP-03 and unwind table entries per EXC-02. These insertions happen after all user-level analysis and are not visible in source. Each emitted call site must include the runtime guard required by DROP-07.

### COMP-04 — Drop flags as compile-time state

Per-field move state (DROP-04) is compiler-internal bookkeeping. Implementations should optimize away flags whose values are statically determined.

### COMP-05 — No reflection

Laterita does not provide reflection. There is no runtime API for enumerating fields or methods, looking up members by name, instantiating types from a `Class` token, generating dynamic proxies, or loading classes at runtime. The compiler is not required to emit per-type metadata for these purposes, and standard-library APIs equivalent to `java.lang.reflect.*`, `java.lang.Class` member-access methods, `Proxy.newProxyInstance`, or `ServiceLoader`'s runtime classpath scan are not provided.

Use cases traditionally served by reflection are served by compile-time code generation (annotation processors, compiler plugins): serializers, ORM mappers, dependency-injection wiring, validators, mocks, test discovery, and SPI registries are all generated at build time from the types and annotations that exist in source. Stack traces (EXC-04) and exception types remain available; this rule constrains type and member introspection, not error reporting.

---

## 16. Reserved Names

The following names are introduced by this specification and must be provided by the standard library: `Rc`, `Arc`, `WeakReference`, `Cell`, `Heap`, `Mutex`, `MutexGuard`, `PoisonedException`. The `Thread` type and `InterruptedException` are reused from the Java standard library per THR-01 and THR-08. Closure types are structural per CLO-03 and require no named stdlib interfaces.

The identifier `onDrop` is reserved as the language-orchestrated lifecycle hook (DROP-01). The keyword `internal` is introduced as a visibility modifier marking a method as compiler-only-callable (DROP-06); it is not a general-purpose access level and is currently used only by `Object.onDrop()`.

The following keywords are introduced or repurposed by this specification: `let` (immutable type-inferred binding), `mut` (mutability marker for local bindings, fields, methods, and parameters), `give` (use-site move marker per MOVE-02; bare-statement form `give x;` per MOVE-08; method-level receiver-consume modifier per BIND-07), `take` (parameter-type prefix declaring an owned parameter per MOVE-03; also an optional declarative LHS prefix on binding declarations), `bound` (borrow-source marker on parameter types and return types per LIFE-02), `broken` (statement declaring a path unreachable per UNR-01), `unsafe` (private method modifier), `internal` (visibility modifier for compiler-only-callable methods per DROP-06), `local` (class-body declaration marking the class as `local` per STD-07), `nonlocal` (class-body declaration paired with `unsafe` overriding inferred `local`-ness per STD-07). Java's `var` keyword for local-variable type inference is not used in Laterita; `let` and `mut` cover the type-inferred forms.

The `?` suffix denotes nullable types per NULL-02; `?.` is the safe-call operator (NULL-04); `?:` is the Elvis operator (NULL-05); `!!` is the null-assertion operator (NULL-07). The form `(P1, …, Pn) -> R` denotes a structural function type per CLO-03.

Java's `synchronized` keyword is removed: there is no per-object intrinsic monitor, no `synchronized` method modifier, and no `synchronized(obj) { ... }` block. Mutual exclusion is provided exclusively through `Mutex<T>` (and related stdlib types). The associated `Object.wait()`/`notify()`/`notifyAll()` methods are likewise not provided; condition-variable-style coordination is a stdlib concern.

Java's existing keywords and their meanings are otherwise preserved unless explicitly modified by this specification.
