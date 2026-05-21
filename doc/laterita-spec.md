# Laterita — Language Specification

This document specifies the normative requirements that a Laterita compiler and standard library must satisfy. Each requirement carries a mnemonic code for cross-reference.

Sections 1 through 18 specify the Java-compatible surface: every rule there is expressible as annotated `.java` source that `javac` parses (COMP-06). Section 19 specifies the `.lat` surface forms — syntactic sugar that desugars to the Java-compatible surface and adds no semantics of its own (LAT-00).

Codes are grouped by area: `BIND` (bindings), `NULL` (optionality), `MOVE` (move/borrow), `MUT` (mutability), `LIFE` (lifetimes), `DROP` (cleanup), `OBJ` (object copying), `UNR` (unreachability), `STR` (strings), `ARR` (arrays), `FN` (functional interfaces), `CLO` (closures), `EXC` (exceptions), `UNS` (unsafe), `STD` (standard library types), `THR` (threads), `COMP` (compilation model), `LAT` (`.lat` surface forms).

---

## 1. Bindings

### BIND-01 — Four binding forms

The language provides exactly four local binding forms:

| Form | Meaning |
|---|---|
| `Type name = expr` | immutable, type explicit |
| `var name = expr` | immutable, type inferred |
| `@mut Type name = expr` | mutable, type explicit |
| `@mut var name = expr` | mutable, type inferred |

Java's `var` is reused for type inference (immutable by default in laterita mode per §18). Whether the binding holds an owned value or a borrow is determined by the RHS expression per MOVE-01 and MOVE-02.

```laterita
String greeting = "hello";        // borrowed; static lifetime per STR-06
var count = items.size();
@mut var sb = new StringBuilder();
@mut int retries = 0;
```

### BIND-02 — `@mut` is the unified mutability marker

The annotation `@mut` denotes mutability in every position it appears: local bindings, fields, methods, and parameters. No other surface form for mutability exists; Java's `final` on local bindings is accepted but redundant since immutability is the default.

### BIND-03 — Field declarations follow binding rules

Fields follow the same rules as locals. A field without `@mut` cannot be reassigned and cannot be mutated through. A field with `@mut` permits both reassignment and mutation through the binding.

A field is owned by default: a bare `T x;` declares storage that owns its value and is dropped with the enclosing instance (DROP-05). `@bound` on a field declares a borrow slot; an instance with any `@bound` field — including via `@bound`-substituted generic arguments (BIND-08) — can only be produced as a `@bound` value, with lifetime per LIFE-03. `@take` on a non-FI field is rejected as redundant; on an FI-typed field `@take` is a slot-mode annotation (CLO-03), not an ownership marker.

```laterita
class User {
    String name;                    // immutable, owned field
    @mut int loginCount;            // mutable, owned field
}

record EntryView<K, V>(@bound K key, @bound V value) {}   // borrow-view; instances must be @bound
```

### BIND-04 — Constructors initialize immutable fields

Every field of a class must be assigned exactly once in every constructor before any method on `this` is invoked. Immutable fields can only be assigned in constructors. Mutable fields can be assigned in constructors and reassigned in `@mut` methods.

### BIND-05 — Methods declare mutation of `this` with `@mut` on an explicit `this`

A method with `@mut` on its explicit `this` parameter may mutate `this` (i.e., reassign or mutate-through `@mut` fields, and call other receiver-mutating methods on `this`). A method without it cannot.

`@mut` on the `this` slot is the parallel of `@mut T name` on an ordinary parameter (MOVE-03) — a mutable borrow of the receiver — exactly as BIND-07 declares receiver consumption with `@take` on the same slot. It is a visibility-like predicate rather than a behavioral one: by BIND-06, a receiver-mutating method is only callable on receivers whose binding is itself `@mut`, so the marker narrows the method's visible API surface to mutable receivers. Declaring the marker on `this` rather than on the return type keeps the return-type position free for the functional-interface slot mode (CLO-03): a `@mut` immediately before a return type is unambiguously the slot mode, and `@mut` before a non-functional-interface return type is rejected as redundant.

```laterita
class Counter {
    @mut int n;
    public int read()                          { return n; }     // bare receiver
    public void inc(@mut Counter this)         { n = n + 1; }     // mutating receiver
    public final void reset(@mut Counter this) { n = 0; }
}
```

### BIND-06 — Mutability transitivity

Mutation requires `@mut` at every level of access. To call a `@mut` method, the receiver binding must be `@mut`. To mutate a field, the field must be `@mut` and the binding holding the containing object must be `@mut` (or the mutation must occur in a `@mut` method of the same object).

```laterita
var counter = new Counter();
counter.inc();              // ERROR: counter is not @mut
@mut var c2 = new Counter();
c2.inc();                   // OK
```

### BIND-07 — Methods declare consumption of `this` with `@take` on an explicit `this`

A method with `@take` on its explicit `this` parameter consumes its receiver. The body owns `this`, may move out of `this`'s fields (MOVE-07), and may hand `this` itself to a `@take` parameter or to another receiver-consuming method. After the call returns, the binding that held the receiver is consumed (MOVE-02); subsequent uses are rejected.

Java's grammar permits an explicit `this` as the first parameter slot with type-use annotations attached. Laterita reuses that slot for every receiver mode: `@mut Self this` declares receiver mutation (BIND-05), `@take Self this` declares receiver consumption, and `@take @mut Self this` combines them — parallel to `@take @mut T` for ordinary parameters (MOVE-03), the receiver is consumed and `this` is reassignable. Calling a receiver-consuming method requires the receiver binding to own its value; the call site needs no `give(...)` wrapper — the signature already declares the transfer.

```laterita
class Connection {
    Heap<DbConn> conn;

    public void close(@take Connection this) {                        // consumes this
        this.conn.flush();
        // `this` is dropped at function end; underlying connection released
    }
}

class StringBuilder {
    @mut String contents;

    public StringBuilder append(@take @mut StringBuilder this, String s) {  // consumes, mutates, returns new
        this.contents = this.contents + s;
        return this;
    }

    public String build(@take StringBuilder this) {                   // consumes, yields String
        return this.contents;
    }
}

var conn = openConnection();
conn.close();        // OK: conn was owned; consumed by close()
conn.use();          // ERROR: conn was consumed
```

### BIND-08 — `@mut` and `@take` banned inside generic type arguments; `@bound` permitted

`@bound`, `@mut`, and `@take` are binding-position annotations. They may appear on parameters, return types, fields, and FI parameter/return slots. Inside a generic type argument (the `T` slot of `Foo<…T…>`), only `@bound` is admitted; `@mut` and `@take` are rejected. On a local binding, only `@mut` is admitted; `@take` and `@bound` are documentary at best (the local's owned-vs-borrow mode follows its RHS per MOVE-02, and any borrow-substituted return is already `@bound` at the producer side) and are rejected to keep one canonical form.

`List<@mut Foo>` and `Pair<@take K, @take V>` are compile errors. `Pair<@bound K, @bound V>` is well-formed and denotes a pair of borrows; the correct form for a mutable list is `@mut List<Foo>`, not `List<@mut Foo>`.

`@mut` is banned in argument position because substitution would silently create aliased mutable slots — two `@bound List<@mut Foo>` borrows would each receive a `@mut Foo` to the same element. Cases that genuinely need shared-container-with-mutable-elements use `Cell<T>` (STD-05) explicitly. `@take` is banned because it is a parameter mode, not a value attribute, and has no meaning as a type argument. `@bound` is permitted because it composes cleanly: a class instance whose generic arguments include any `@bound`-substituted parameter can only be produced as a `@bound` value, with lifetime per LIFE-03 (and the idempotence rule of LIFE-06 when `@bound` stacks). No struct-level lifetime parameters are introduced — the `@bound` binding on the instance carries the lifetime.

```laterita
record Pair<L, R>(L left, R right) {}

Pair<String, Int>                   p1 = new Pair("hello".clone(), 42);   // owned: constructor moves
Pair<@bound String, @bound Int> view = new Pair(name, count);              // bound: bare args borrow; view's mode follows the RHS
```

---

## 2. Mutability Rules (Cross-cutting)

### MUT-01 — Immutability is transitive through borrows

A shared (immutable) borrow grants no mutation rights regardless of any `@mut` markers on fields reached through it. Mutation through a borrow requires the borrow itself to be mutable.

### MUT-02 — Interior mutability requires `Cell<T>`

A type that needs to mutate its contents through a bare receiver must hold those contents inside `Cell<T>`. This is the only mechanism that bypasses MUT-01, and `Cell<T>` is an unsafe primitive (see UNS-02).

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

### MOVE-02 — `give(...)` marks a move at the use site

`Intrinsics.give(x)` (declared in `laterita.lang.Intrinsics` and normally statically imported as `give`) consumes the binding `x`'s ownership and yields its value at the call site. After the call, the source binding is no longer usable. The static method is the syntactic carrier for a move expression; the laterita compiler treats unqualified calls of this method specially.

```laterita
var a = makeString();
var b = give(a);            // a is consumed
// print(a);                // ERROR: use after move
print(b);                   // OK
```

A local's mode follows the RHS: a producer expression (call, constructor, literal, `give(x)`) yields an owned binding; a bare-binding RHS yields a shared borrow per MOVE-01. The annotation positions permitted on locals are listed in BIND-08.

### MOVE-03 — Parameter ownership is declared in the signature

A parameter declares whether it receives a borrow or takes ownership of its argument:

| Form | Meaning |
|---|---|
| `T name` | the parameter receives a shared borrow |
| `@mut T name` | the parameter receives a mutable borrow |
| `@take T name` | the parameter receives ownership (moved in) |
| `@take @mut T name` | the parameter receives ownership and the slot is reassignable |

The parameter declaration drives the call site. A bare argument that is a binding implicitly transfers ownership when the parameter is `@take`; an explicit `give(arg)` is the same operation written for clarity. A temporary expression (call result, constructor, literal) is owned and fills either parameter form — moved into a `@take` parameter, borrowed for the duration of the call by a bare parameter. A bare argument passed to a `@mut` parameter produces a mutable borrow, which requires the source binding to be `@mut` (owned or mutably borrowed); a temporary fills a `@mut` parameter directly.

The illegal cases:
- A `give(arg)` to a bare parameter — the caller is asking to transfer; the function will not accept ownership.
- A bare argument that is a borrow (e.g., the binding itself only holds a borrow) to a `@take` parameter — there is no ownership to give.
- A bare-bound (immutable) binding to a `@mut` parameter — there is no mutable access to lend.

```laterita
void inspect(String s);                                  // borrows s
void store(@take String s, @mut List<String> into);      // takes ownership of s

var name = makeName();
inspect(name);              // OK: borrow
inspect(makeName());        // OK: borrow of a temporary
store(name, list);          // OK: implicit give per @take; name no longer usable
store(makeName(), list);    // OK: temporary moved in
inspect(give(name));        // ERROR: inspect only borrows; do not transfer
```

### MOVE-04 — Borrow exclusivity

For any binding's lifetime, either:
- any number of immutable (shared) borrows may coexist, or
- exactly one mutable borrow may exist, with no other borrows.

The compiler must reject programs that violate this.

### MOVE-05 — Disjoint field borrows

Two simultaneous borrows of statically distinct fields of the same struct are non-aliasing and must be permitted, including when both are mutable. The compiler must perform this disjointness analysis.

```laterita
class Pair { @mut int left; @mut int right; }
@mut Pair p = new Pair();
@mut int l = p.left;
@mut int r = p.right;       // OK: disjoint fields
```

### MOVE-06 — Disjoint array slice borrows

Two simultaneous borrows of array slices with provably disjoint index ranges must be permitted. The compiler proves disjointness for constant ranges and for ranges related by simple arithmetic. For arbitrary computed ranges, the disjointness witness is supplied by ARR-01, which reduces to ordinary slice expressions this rule already covers.

```laterita
int[] data = new int[100];
@mut int[] left  = data.slice(0, 50);
@mut int[] right = data.slice(50, 100);  // OK: provably disjoint
```

### MOVE-07 — Partial moves are tracked per field

Moving out of a field of a value leaves that field in the moved-out state while leaving other fields valid. The compiler must track per-field move state through the function and use it both for use-after-move checking and for cleanup emission (see DROP-04). A field that the value's `onDrop()` body reads cannot be moved out (DROP-08).

### MOVE-08 — `give(...)` to void

A `give(x)` call appearing as a statement (its result discarded) consumes the binding `x` and invokes its `onDrop()` immediately. Equivalent in effect to passing `x` to a function whose only act is to receive ownership and let the parameter go out of scope. After `give(x);`, the binding `x` is consumed; subsequent uses are rejected per MOVE-02.

```laterita
var worker = Thread.ofVirtual().start(() -> task());
if (changedMyMind()) { give(worker); }  // run Thread.onDrop() now; binding consumed
```

Applies to any owning binding. For `Thread`, it is the standard mechanism for early termination per THR-06.

### MOVE-09 — `@take` participates in overload resolution

Two methods in the same scope may have signatures that differ only in the `@take` annotation on one or more parameters; they are distinct overloads. The `@mut` annotation is **not** part of the overload signature: two methods that differ only in `@mut` on a parameter are the same method, and declaring both in the same scope is a compile error.

Java's standard overload resolution applies first: candidates are filtered by applicability, type specificity, boxing, and varargs as today. The ownership axis enters only as a tie-breaker, and only when Java's procedure leaves more than one applicable overload.

The tie-breaker rule: among overloads remaining after Java's procedure, prefer the overload that uses borrow on every parameter position where another candidate uses `@take` (agreeing on all other positions). Equivalently, the overload that demands the least from the caller on the ownership axis wins. If no uniquely most-permissive overload exists — for example, two overloads each borrow one parameter and consume a different one — the call is ambiguous and the caller must disambiguate with `give(...)` on the parameter(s) intended for consumption.

The caller opts in to a `@take` overload by wrapping the argument in `give(...)` at the call site. A `give(...)` argument is applicable only to a `@take` parameter (per MOVE-03), so it removes the borrow form from the candidate set on that argument position.

```laterita
void put(@take K key, @take V value);    // (a) consumes both
void put(K key, @take V value);          // (b) borrows the key, consumes the value

var k = makeKey();
var v = makeValue();
put(k, v);          // (a) and (b) tie on Java specificity → tie-breaker → (b) wins
put(give(k), v);    // give(...) removes (b) → (a) wins
```

The implicit transfer described in MOVE-03 ("a bare argument that is a binding implicitly transfers ownership when the parameter is `@take`") applies whenever the resolved overload is a `@take` form. Whether resolution lands there depends on the overloads in scope and on Java's specificity rules: when type specificity is decisive — e.g., `f(Animal)` borrow vs. `f(@take Dog)` consume on a `Dog` argument — the ownership tie-breaker does not run, the more-specific overload wins as in standard Java, and a bare `Dog` argument is consumed by `f(@take Dog)`.

Two consequences for interface evolution:
- Adding a same-type borrow overload alongside an existing `@take` shifts bare call sites from consume to borrow on the tie-breaker.
- Adding a more-specific `@take` overload alongside an existing less-specific borrow shifts bare call sites at the more-specific type from borrow to consume on Java specificity.

For arguments where the drop point is observable (large buffers, files, threads), authors should wrap the argument in `give(...)` explicitly at sites that need to be pinned to the consuming form, even when only one overload exists today.

### MOVE-10 — Override variance for `@take` and `@mut`

For overrides of inherited methods (subclass override, interface implementation):

- **`@take` matches invariantly.** An override's parameter must carry `@take` if and only if the inherited declaration does. This follows from MOVE-09: a parameter list differing on `@take` is a different overload, not an override of the same method.

- **`@mut` matches contravariantly.** An override may drop `@mut` from a parameter that the inherited declaration marks `@mut`, but may not add `@mut` to a parameter the inherited declaration leaves bare. Dropping `@mut` is sound — the override demands less of the caller than the inherited contract promises. Adding `@mut` is unsound — callers holding only an immutable borrow could no longer satisfy the override through the inherited type.

```laterita
interface Visitor {
    void visit(@mut Node n);
}

class CountingVisitor implements Visitor {
    @Override void visit(Node n) { ... }       // OK: drops @mut; requires less
}

class RewritingVisitor implements Visitor {
    @Override void visit(@mut Node n) { ... }  // OK: matches exactly
}

interface Reader {
    void read(Node n);
}

class BadReader implements Reader {
    @Override void read(@mut Node n) { ... }   // ERROR: cannot strengthen @mut
}
```

The variance rule mirrors Java's existing treatment of `throws`: an override may declare fewer or narrower checked exceptions than the inherited signature, never more. The principle is the same — an override may relax its demands on callers, never tighten them.

---

## 4. Lifetimes

### LIFE-01 — No borrow may outlive its referent

The compiler must reject any program in which a borrow is used after the binding it borrows from has been dropped or moved.

### LIFE-02 — Returns are owned by default

A bare return type means the function gives the caller an owned value. Per MOVE-03 on the return side, a bare `return x;` of an owned binding moves it; `return give(x);` is accepted as explicit form.

To declare a borrowed return instead, the contributing source is marked with `@bound`:

- **Parameter source**: annotate the parameter type with `@bound`. The return is bound to that parameter.
- **Receiver source**: annotate the return type with `@bound`. The return is bound to `this`.

`@bound` is meaningful only when there is something to bind. It requires a non-`void` return for the parameter-source form, and an instance method (not `static`) for the receiver-source form. Misuses are rejected. Concretely: `@bound` on a `void` return is rejected (no value to bind), and `@bound` on the return of a `static` method is rejected (no receiver to bind to) — for a static method that returns a borrow of one of its parameters, place `@bound` on that parameter and leave the return type bare, as in `String firstWord(@bound String s)` above.

```laterita
String upperCase(String s);                          // owned return

String firstWord(@bound String s) {                  // returned borrow bound to s
    return s.substring(0, s.indexOf(' '));
}

class Cache {
    Map<String, Entry> entries;
    @bound Entry get(String key) {                   // returned borrow bound to this
        return entries.get(key);
    }
}
```

### LIFE-03 — Multiple `@bound` sources intersect

When more than one source is marked `@bound` (any combination of parameters and the receiver), the returned borrow's lifetime is the intersection (i.e., bounded by the shortest-lived marked source).

```laterita
@bound String chooseLabel(@bound String fallback) {
    return prefer ? this.label : fallback;           // bound to min(this, fallback)
}
```

### LIFE-04 — Unmarked sources do not contribute

Inputs that are not marked `@bound` cannot contribute to the returned borrow. A method body that returns a borrow tied to an unmarked source is a compile error; the diagnostic suggests adding `@bound` to the relevant source.

```laterita
String prefixOf(@bound String text, String pattern) {
    return text.substring(0, pattern.length());      // bound to text only; pattern unmarked
}
```

### LIFE-05 — Owned/borrowed mismatches are diagnostics

The compiler must report an error when:
- the body returns a borrow but the signature declares no `@bound` source, or
- the body returns an owned value but the signature declares a `@bound` source.

The diagnostic identifies the contributing source the body actually uses, so the user can either add the appropriate `@bound` marker or change the body to match the declared owned form.

### LIFE-06 — `@bound` is idempotent

`@bound` is a binding-mode marker, not a type constructor; it carries no "layer" to stack. When `@bound` appears in stacked position — typically through generic substitution, as in `@bound E` returned from a method on `Container<@bound T>`, which substitutes to `@bound @bound T` — the resulting form denotes the same shape as a single `@bound T`. Each `@bound` position contributes its source to LIFE-03's intersection.

```laterita
class ArrayList<E> {
    @bound E get(int index);                         // outer @bound: bound to `this`
}

var list = new ArrayList<@bound String>();           // inner @bound: bound to whatever was added
var got = list.get(0);                               // shape is @bound String;
                                                     // lifetime = min(list, source-of-element)
```

The rule is what lets `Container<@bound T>` compose through any method whose return is `@bound E`: the doubly-marked form arising from substitution does not introduce a "borrow of a borrow" indirection — it accumulates lifetime constraints on a single borrow.

---

## 5. Scope-Exit Cleanup

### DROP-01 — Universal `onDrop()`

Every binding triggers the drop of its value when the binding leaves scope; the drop sequence is specified by DROP-05. The cleanup hook is `onDrop()`, an `@internal` method (DROP-06) a `final` class may implement (DROP-09). A class with no implementation contributes no body to its drop sequence. No syntactic opt-in is required at the call site.

```laterita
{
    Rc<File> f = openFile();
    f.read();
}   // f's drop sequence runs here (compiler-emitted)
```

### DROP-02 — Reverse declaration order

Within a scope, bindings are dropped in the reverse of their declaration order.

### DROP-03 — Cleanup on all exit paths

`onDrop()` must be invoked on every exit path from a scope: normal completion, return, break, continue, and exceptional unwind.

### DROP-04 — Drop flags for partial moves

When MOVE-07 has resulted in partially-moved values, the compiler must emit code that consults per-field move state and invokes `onDrop()` only on the parts still owned at the exit point. Implementations may optimize away drop flags when static analysis proves they are constant. The enclosing value's own `onDrop()` may not observe the moved-out parts (DROP-08).

### DROP-05 — Drop sequence

Dropping a value runs cleanup in the reverse of construction order. For an instance of dynamic class `C` with superclass chain `C → B → … → Object`, the compiler emits, in order:

1. `C.onDrop()` body, if implemented — only `final` classes may, per DROP-09.
2. `C`'s fields, in reverse declaration order; array elements in reverse index order.
3. Step 2 repeated for `B`, then for each superclass up to `Object`.
4. If the instance is heap-allocated, its storage is released.

Fields that are moved-out (DROP-04), `null` (NULL-09), or `@bound` (BIND-03) are skipped in steps 2 and 3; each surviving owned field is dropped recursively by this same procedure. The step-1 body runs before any field teardown of that class, so it may read its class's non-moved-out fields (subject to DROP-08).

```laterita
final class TimerScope {                  // final: required to implement onDrop (DROP-09)
    Rc<Metrics> metrics;
    long startNanos;

    @internal void onDrop() {
        metrics.record(System.nanoTime() - startNanos);   // both fields still live here
    }
    // drop sequence: onDrop() body → startNanos → metrics dropped (Rc decrement) → free
}
```

### DROP-06 — `@internal` forbids user invocation

The annotation `@internal` declares that a method may be invoked only by compiler-emitted call sites. User code cannot invoke an `@internal` method directly (`x.onDrop()`); doing so is a compile error.

`onDrop()` is the only `@internal` method introduced by this specification. The compiler emits its invocations at scope exits (DROP-01), on partial-move paths (DROP-04), on exception unwind (EXC-02), and as part of the drop sequence (DROP-05).

`@internal` is reserved for future compiler-orchestrated hooks. It is not a general-purpose access-control level; ordinary visibility scoping continues to use `public`, `protected`, `private`, and package-default.

### DROP-07 — Exceptions from `onDrop()` terminate the body, not the drop sequence

An exception propagating out of an `onDrop()` body terminates that body, but the rest of the value's drop sequence — its remaining fields and superclass fields (DROP-05 steps 2–3) and the storage release (step 4) — still runs. The exception then leaves the compiler-emitted call site through the same path a Java `finally`-block exception leaves, joining the normal exception flow at the binding's scope exit.

If multiple invocations along a drop path throw — sibling bindings (DROP-02), nested field drops, the body and a field of the same value, or any of these during an exception unwind (EXC-02) — the first thrown exception is the propagating one; later throws are attached to it via `Throwable.addSuppressed`.

`onDrop()` implementations that perform fallible operations (network flushes, file syncs) may either catch internally or allow exceptions to propagate. DROP-10 guarantees that no external reference to the value can survive into step 4, so the drop sequence is safe to complete even after the body throws.

### DROP-08 — `onDrop()` may not observe moved-out fields

A field that may be moved out (MOVE-07) on any path to a drop site may not be read by that class's `onDrop()` body, nor by any method it transitively invokes on `this`. The compiler diagnoses the violation at the move: a `give` of a field is rejected when the containing class has an `onDrop()` that reads it. The diagnostic identifies the field, the move, and the read.

The restriction is per field — an `onDrop()` reading only some fields pins only those, and partial cleanup of the rest follows DROP-04. A class whose `onDrop()` reads no field — every record, every plain data carrier, every class without an `onDrop()` implementation — imposes no restriction at all.

```laterita
record Pair(Resource left, Resource right) {}        // no onDrop implementation

var p = new Pair(openA(), openB());
useLeft(give(p.left));         // OK: Pair has no onDrop(); left is now moved-out
useRight(give(p.right));       // OK: right still owned; nothing of p remains to drop

final class Logged {           // final: required to implement onDrop (DROP-09)
    Handle h;
    @internal void onDrop() { log("closing " + h.id()); }  // reads field h
}

var x = new Logged(openHandle());
useHandle(give(x.h));          // ERROR: Logged.onDrop() reads h; h cannot be moved out of x
```

### DROP-09 — `onDrop()` implementations only on `final` classes

A class may implement `onDrop()` only if it is declared `final`. An `onDrop()` implementation on a non-`final` class is a compile error; `onDrop()` may not be declared `abstract`, and an interface may neither declare it nor supply it as a `default`. A class without an implementation contributes no body to its drop sequence (DROP-05 step 1). At most one user-written `onDrop()` body therefore runs per instance, on the instance's (necessarily `final`) dynamic class.

A class that needs cleanup beyond what its fields' own `onDrop()`s provide must be `final`. Extensible types compose `final` handle fields (`Rc<T>`, `Arc<T>`, `Thread`, …) whose `onDrop()`s perform the release during the owner's drop sequence (DROP-05, step 2).

```laterita
final class Connection { … }              // OK: final, may implement onDrop

class Service {                           // OK: no onDrop implementation; ordinary extensible class
    Connection conn;                      // resource held by composition; conn dropped in Service's drop sequence
}

abstract class Resource {
    @internal void onDrop() { … }          // ERROR: onDrop implementation on a non-final class
}
```

### DROP-10 — `this` does not escape `onDrop()`

Within an `onDrop()` body, the receiver `this` has a lifetime bounded by the call. It may not be given (`give(this)`) to another function, returned, stored in a field or global, or otherwise made reachable after the body returns. This is the rule that makes the once-per-instance guarantee on `onDrop()` (DROP-09) and the storage release in DROP-05 step 4 sound — no external reference to the value can survive into field teardown or beyond, so the drop sequence is safe to complete even when the body throws (DROP-07).

---

## 6. Unreachability

### UNR-01 — `broken()` declares a path unreachable

`Intrinsics.broken()` (declared in `laterita.lang.Intrinsics` and normally statically imported as `broken`) declares that the enclosing path must not be reachable. The optional overload `Intrinsics.broken(String reason)` attaches an explanatory message. The compiler must reject any program in which the call can be reached on a path it cannot prove dead.

The call has return type `Nothing` (the bottom type): it is a divergence point, code following it in the same block is unreachable, and the enclosing function need not produce a value of its declared return type when control flow ends in `broken()`.

```laterita
class File {
    Heap<FileHandle> handle;
    @Override File clone() {
        broken("files cannot be copied");
    }
}

<T> List<T> deepCopy(List<T> source) {
    var result = new List<T>();
    for (T item : source) {
        result.add(item.clone());
    }
    return result;
}

deepCopy(users);   // OK: User.clone() is the synthesized form
deepCopy(files);   // ERROR: File.clone() reaches broken()
```

Diagnostics must identify the reachable path that leads to `broken()` and report the reason string when one was provided.

A conditional form is expressible as an `if` guarding `broken()`; the compiler's standard dead-code analysis determines whether the path is reachable:

```laterita
if (n < 0) broken("n must be non-negative");
```

---

## 7. Copying

### OBJ-01 — Auto-generated copy constructor

Every class has a `protected ClassName(ClassName source)` copy constructor. The compiler synthesizes one when none is provided. The synthesized form chains `super(source)` and copies each field: primitives bitwise; owned object fields via the field's `clone()` method (`source.field.clone()`). A user-provided copy constructor with the same signature suppresses synthesis.

If a field's `clone()` reaches `broken()` (UNR-01), the enclosing class's auto-generated copy constructor reaches `broken()` transitively and is rejected at compile time.

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
    // Class-level opt-out via broken() clone (OBJ-02).
    @Override SecretKey clone() {
        broken("secret keys must not be copied");
    }
}
```

### OBJ-02 — Auto-generated `clone()` method

Every class has a public `Self clone()` method, synthesized as `return new Self(this);` when not provided by the user. `clone()` is the standard duplication API for code that does not statically know the concrete class — generic code over a type parameter, and polymorphic code holding a value at a supertype or interface — because the call dispatches virtually to the actual class's `clone()`.

```laterita
<T> List<T> deepCopy(List<T> source) {
    var result = new List<T>();
    for (T item : source) {
        result.add(item.clone());
    }
    return result;
}

deepCopy(users);       // OK
deepCopy(secretKeys);  // ERROR: SecretKey.clone() reaches broken()
```

A class opts out of copying by overriding `clone()` with a body that reaches `broken()`, as in `SecretKey` above.

---

## 8. Optionality

Nullability is a property of types in both source surfaces. The `.lat` spelling `T?` and the operators `?.`, `?:`, and `!!` are syntactic sugar specified in §19; the rules below define nullability semantics independent of spelling.

### NULL-01 — Types are non-nullable by default

A bare type `T` excludes the null state. A binding of type `T` always holds a valid value after initialization, and methods on `T` may be invoked without a null check.

```laterita
String name = "Alice";
print(name.length());       // always safe
```

### NULL-02 — Nullable types

A nullable type admits either a value of `T` or the special value `null`. Its canonical form is `@Nullable T` (`@Nullable` declared in `laterita.lang.annotation`); `.lat` sources may use the suffix spelling `T?` (LAT-01). The two spellings denote the same type. `T` and `@Nullable T` are distinct types: `T` widens to `@Nullable T` implicitly; `@Nullable T` does not narrow to `T` without a check (NULL-06) or an assertion (LAT-04).

`T` must be a reference type. Nullable primitive types are rejected at compile time; code that requires null-bearing integer or boolean semantics must use the boxed reference type (`@Nullable Integer`, `@Nullable Boolean`, …). The compiler does not auto-box at the type level.

```laterita
String? maybeName = lookup(id);   // .lat spelling of @Nullable String
print(maybeName.length());        // ERROR: requires null check
```

### NULL-03 — `null` literal

The literal `null` has type `Nothing?` and is assignable to any `T?`. `null` is not assignable to a non-nullable type.

*NULL-04, NULL-05, NULL-07 — Relocated.* The safe-call (`?.`), elvis (`?:`), and null-assertion (`!!`) operators are `.lat` surface forms. Their definitions and `.java`-surface desugarings are LAT-02, LAT-03, and LAT-04 (§19).

### NULL-06 — Smart narrowing on null check

After a control-flow narrowing (e.g., `if (x != null) { ... }`, `if (x == null) return;`), the binding's type within the proven-non-null region is `T`, not `T?`. Calls that require `T` are permitted without further annotation.

```laterita
if (maybeName != null) {
    print(maybeName.length());   // OK: narrowed to String
}
```

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

`give(expr)` where `expr` has type `T?` transfers either the contained `T` (leaving the source as `null`) or transfers `null`. Borrow rules apply identically to `T?` and `T`. A borrow of a `T?` is itself a `T?`-borrow; null narrowing (NULL-06) on a borrowed binding narrows to a `T`-borrow.

---

## 9. Exceptions

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

## 10. Functional Interfaces

Laterita extends Java's *functional interface* concept (an interface with one abstract method) to admit an **anonymous, structural form**: the SAM signature can be written directly inline as a type expression, without declaring a named interface.

### FN-01 — Anonymous functional interface syntax

An anonymous functional interface is written

```
(P1, P2, …, Pn) -> R
```

where each `Pi` is a parameter declaration following MOVE-03 form (bare `T`, `@mut T`, `@take T`, with optional `@bound` per LIFE-02), and `R` is the return type. The form is anonymous and structural: no named interface need be declared.

The single abstract method of an anonymous functional interface is named `apply`. A value `f` of such a type is invoked through it — `f.apply(a1, …, an)`. Laterita has no call-on-binding syntax: a functional-interface value is an object, invoked through its SAM exactly as in Java.

An anonymous functional interface type expression may be written only in two positions: as a parameter type or as a return type. It may not be written as the declared type of a field, the declared type of a local binding, or a generic type argument — a function value held in any of those positions uses a nominal functional interface. The restriction governs the written type expression, not value flow: a `var` local may still hold an anonymous functional-interface value whose type is inferred, such as the result of a closure-returning call.

```laterita
(int, int) -> int                 // owned-int args, owned-int return
(@take String, @mut StringBuilder) -> String
(@bound Record, RecordKey) -> Field
() -> void
```

A nominal functional interface — a regular interface declared with one abstract method — remains available unchanged from Java; the anonymous form is an addition, not a replacement, and is accepted only in `.lat` sources (LAT-05).

### FN-02 — Identity

Two anonymous functional interfaces are identical iff their arity, each parameter's mode and underlying type, the return type, and any `@bound` relationships match. Distinct expressions denote distinct types; one is not implicitly convertible to another. A nominal functional interface and an anonymous one are never equal — even when their SAMs match — because the nominal one carries an interface identity the anonymous one lacks.

### FN-03 — Anonymous synthesis per construction

Each value-construction of an anonymous functional interface yields an anonymous class implementing the SAM dictated by the type expression. The synthesized class is not addressable from source code. Function-shaped contracts that need a name, documentation, or related methods are expressed with a nominal functional interface; the anonymous form covers the "callback parameter" case.

---

## 11. Closures

### CLO-01 — Three capture modes

Closures are classified by how they use captured bindings:

- **Read** — captured bindings are immutably borrowed; closure may be invoked any number of times, including from multiple threads simultaneously (subject to the `@local` rules of STD-07).
- **Mutate** — captured bindings include a mutable borrow; closure may be invoked any number of times sequentially but not concurrently.
- **Consume** — captured bindings include a moved value; closure may be invoked exactly once.

### CLO-02 — Capture mode is inferred

The compiler infers a closure's capture mode from the body. The user does not declare it.

### CLO-03 — Slot mode controls invocation

A binding of functional-interface type follows the standard parameter-modifier rules. The binding's mode controls which receiver-mode method on the held value may be invoked through it (BIND-06, BIND-07):

| Slot mode | SAM receiver modes the slot can invoke   | Why (BIND-06 / BIND-07) |
|---|---|---|
| bare      | bare-receiver only                       | a bare binding cannot call `@mut` or receiver-consuming methods |
| `@mut`    | bare- or mut-receiver                    | a `@mut` binding can call bare and `@mut` methods, but cannot consume the receiver |
| `@take`   | any (bare-, mut-, or take-receiver)      | a `@take` binding owns the value and may consume it |

```laterita
void demo((int, int) -> int adder,         // bare slot — bare-receiver SAMs only
          @mut (int, int) -> int counter,  // mut slot — bare- or mut-receiver SAMs
          @take () -> void onClose) {      // take slot — any SAM, including take-receiver
    adder.apply(1, 2);                     // OK: bare binding invokes the bare-receiver SAM
    adder.apply(3, 4);                     // OK: a bare slot may be invoked any number of times
    var worker = Thread.ofVirtual().start(() -> adder.apply(5, 6));  // OK: a bare slot holds a
    worker.join();                         //     read closure (CLO-01), safe to invoke off-thread

    counter.apply(1, 2);                   // OK: @mut binding invokes a bare- or @mut-receiver SAM
    counter.apply(3, 4);                   // OK: a @mut slot may be invoked repeatedly, but sequentially
    Thread.ofVirtual().start(() -> counter.apply(7, 8));   // ERROR: a @mut slot may hold a mutate
                                           //        closure, which CLO-01 forbids invoking off-thread

    onClose.apply();                       // OK: invokes the held SAM; a take-receiver SAM consumes it
    onClose.apply();                       // ERROR: the @take slot was consumed by the first invocation
}
```

A caller supplies each slot with a lambda whose capture mode (CLO-04) fits it:

```laterita
@mut int hits = 0;
var log = openLog();
demo(
    (a, b) -> a + b,                               // read closure    — fits the bare slot
    (a, b) -> { hits = hits + 1; return a + b; },  // mutate closure  — fits the @mut slot
    () -> { give(log); }                           // consume closure — fits the @take slot
);
```

The slot mode bounds invocation; whether a *particular* construction (a lambda per CLO-04, or a method reference) yields a SAM whose receiver mode fits a given slot is governed by the fit relation in CLO-04.

A binding of functional-interface type combines two layers of modifiers. The parameter `fn` in

```laterita
<T, R> void process(@mut (@take T) -> R fn) { /* … */ }
```

carries `@mut` as the **slot mode** on the binding, and `@take` as the **SAM-parameter mode** inside the type expression. The type expression is the same shape it would have if a nominal interface were declared and then used as the slot's type:

```laterita
interface F<T, R> { R apply(@take T); }

<T, R> void process(@mut F<T, R> fn) { /* … */ }
```

The two layers carry different modifiers:

| Layer | Modifiers | Governed by |
|---|---|---|
| Inside the type — SAM parameters and return | `@take`, `@mut`, `@bound` per the usual rules | MOVE-03, LIFE-02 |
| Outside the type — the binding holding the FI value | `@mut` or `@take` (slot mode), plus an orthogonal optional `@bound` (return-binding annotation) | CLO-03, LIFE-02 |

Inside the type, modifiers describe the SAM's parameters and return:

```laterita
<T, R>    void run((@take T) -> R consumer);                           // SAM consumes its T argument
<A, B, R> R each(A source, B key, (@bound A, B) -> R fn);              // SAM parameter A is borrowed
```

Outside the type, the slot mode is one of bare, `@mut`, or `@take`:

```laterita
<T> void inspect(@mut (T) -> void fn);                                 // mut slot
        void onClose(@take () -> void hook);                           // take slot: caller transfers ownership of the FI value
        @mut (int) -> int makeCounter();                               // mut slot on a returned FI
```

A functional-interface return type carries a slot mode the same way a parameter does. Because receiver mutation is declared on the explicit `this` slot (BIND-05), a `@mut` or `@take` written immediately before a return type is unambiguously the slot mode of the returned functional-interface value — never a receiver-mode marker on the enclosing method.

The outer `@bound` is **not** a slot mode — it is an orthogonal annotation declaring that the enclosing function's return is bound to this parameter (LIFE-02). It applies on top of a non-`@take` slot, and arises in practice when the function returns a value derived from a borrow of the FI parameter, most commonly a closure that captures it:

```laterita
// Partial application: the returned closure borrows `fn` (and `first`),
// so its lifetime is bounded by the intersection of both (LIFE-03).
<A, B, R> @bound (B) -> R partial(@bound (A, B) -> R fn, @bound A first) {
    return (b) -> fn.apply(first, b);
}
```

A `@take` slot consumes the FI value on the call, so the return cannot reference the slot's lifetime; outer `@bound` does not combine with `@take`.

### CLO-04 — Lambdas are values of functional interfaces

A lambda literal `(p1, p2, …) -> body` is a value whose type is a functional interface — anonymous (FN-01) or nominal — selected by:

- The target slot's expected type (target typing), when the lambda appears in a position with a known functional-interface expectation; or
- Inference from the body together with any explicit parameter annotations otherwise.

The lambda's capture mode (CLO-01) determines the receiver mode of the synthesized SAM (FN-03), and the slot's mode (CLO-03) must admit that receiver mode for the assignment to type-check:

| Closure capture mode | SAM receiver  | Bare slot | `@mut` slot | `@take` slot |
|---|---|:---:|:---:|:---:|
| Read    | bare-receiver | accept    | accept      | accept      |
| Mutate  | mut-receiver  | reject    | accept      | accept      |
| Consume | take-receiver | reject    | reject      | accept      |

Note the asymmetry against ordinary parameters: for `@mut Buf b`, the more capable parameter mode demands more from the caller; for an FI slot, the more capable slot mode accepts the *broader* range of closures, because the slot mode is an upper bound on what the body may invoke.

```laterita
// Read-or-mutate lambda: consumes input, @mut-borrows buffer, returns owned R.
<A, B, R> R fold(@take A input, @mut B buffer, @mut (@take A, @mut B) -> R lambda) {
    return lambda.apply(give(input), buffer);
}

// Read lambda whose return is bound to the first input's lifetime.
<A, B, R> R lookup(@bound A source, B key, (@bound A, B) -> R lambda) {
    return lambda.apply(source, key);
}

// One-shot consume callback: `@take` slot admits a take-receiver SAM.
void onClose(@take () -> void action) {
    action.apply();
}
```

Caller-side examples against the three signatures above:

```laterita
// Accepted — read closure fits the @mut slot of fold.
@mut int factor = 2;
var r1 = fold(give(input), buf, (a, b) -> b.append(a.scale(factor)));

// Accepted — mutate closure fits the @mut slot of fold.
@mut int count = 0;
var r2 = fold(give(input), buf, (a, b) -> {
    count = count + 1;
    return b.append(a);
});

// Accepted — consume closure fits the @take slot of onClose.
var log = openLog();
onClose(() -> { give(log); });

// Rejected — consume closure does not fit a @mut slot.
fold(give(other), buf, (a, b) -> {
    give(log);                                   // moves a captured owned value
    return b.append(a);
});
// ERROR: lambda's capture mode is consume (take-receiver SAM);
// fold's slot is `@mut`, which only accommodates read or mutate closures.

// Rejected — mutate closure does not fit a bare slot.
lookup(source, key, (s, k) -> {
    count = count + 1;                           // mutate capture
    return s.find(k);
});
// ERROR: lambda's capture mode is mutate (@mut-receiver SAM);
// lookup's slot is bare, which only admits read closures.
```

### CLO-05 — Overloading and override variance for FI parameters

A functional-interface parameter is an ordinary parameter; MOVE-09 (overload resolution) and MOVE-10 (override variance) apply, with one inversion noted below.

**Overloading.** The slot-side `@take` is part of the overload signature; slot-side `@mut` is not.

```laterita
class Stream<T> {
    <R> Stream<R> map(@mut (@take T) -> R fn);    // (a) @mut slot
    <R> Stream<R> map(@take (@take T) -> R fn);   // (b) @take slot — distinct overload (MOVE-09)
    <R> Stream<R> map((@take T) -> R fn);         // ERROR: differs from (a) only in @mut — same method
}

stream.map(x -> x.length());      // resolves to (a): bare argument; MOVE-09 tie-breaker prefers borrow
stream.map(give(oneShot));        // resolves to (b): give(...) removes the borrow form from candidates
```

**Override variance — inverted for slot modes.** MOVE-10 says `@mut` on an ordinary parameter is contravariant: an override may *drop* `@mut` because the override demands less of the caller. For an FI slot the relationship is reversed — an override may **add** `@mut` to a bare slot, but not remove it. The reason: a `@mut` slot accepts strictly more closures (read and mutate) than a bare slot (read only), and an override must continue to accept every closure the inherited declaration accepted. The `@take` axis remains invariant per MOVE-10 / MOVE-09: a slot-mode change between non-take and `@take` produces a distinct overload, not an override.

```laterita
interface Source<T> {
    void forEach((T) -> void fn);                           // base: bare slot
}

class Tracing<T> implements Source<T> {
    @Override void forEach(@mut (T) -> void fn) { ... }     // OK: bare → @mut accepts strictly more
}

interface MutSource<T> {
    void forEach(@mut (T) -> void fn);                      // base: @mut slot
}

class Bare<T> implements MutSource<T> {
    @Override void forEach((T) -> void fn) { ... }          // ERROR: @mut → bare rejects mutate closures
}
```

The SAM type itself is invariant under override (FN-02): two anonymous FIs whose parameter or return modes differ are distinct types. A nominal SAM declared as a regular interface continues to follow MOVE-10 on its own method signatures unchanged.

### CLO-06 — Capture lifetimes propagate

A closure value carries the lifetimes of every binding it captures by borrow. The closure cannot outlive any captured borrow. Lifetime intersection (LIFE-03) applies when multiple borrows are captured.

---

## 12. Strings

### STR-01 — `String` is a normal class

`String` is not final. Classes may extend it. The compiler must permit user-defined subclasses such as `class Email extends String`.

### STR-02 — Strings are tracked as owned or borrowed per binding

A `String` binding is either an owned heap allocation or a borrowed view into another `String`'s storage. The compiler tracks this per-binding and applies lifetime rules to borrowed instances.

### STR-03 — Slice methods return borrows

Methods that return a view into the receiver's storage (e.g., `substring`, `trim`) declare the borrow with `@bound` on the return type per LIFE-02.

```laterita
class String {
    @bound String substring(int start, int end);
    @bound String trim();
}
```

### STR-04 — Allocating methods return owned strings

Methods that produce new storage (e.g., `toUpperCase`, `concat`) return an owned `String` with no lifetime tie to the receiver.

### STR-05 — User-defined subclasses are owned

Subclasses of `String` declared by user code are owned. They cannot be returned as borrows into other storage.

### STR-06 — String literals are static borrows

A string literal expression has type `@bound String` with a static lifetime. A binding initialized from a literal is borrowed; to obtain owned storage, call `.clone()` (OBJ-02).

```laterita
String greeting = "hello";              // borrowed, static lifetime
String owned = "hello".clone();         // owned heap allocation
var s = give(greeting);                 // ERROR: greeting is borrowed
var u = give("hello");                  // ERROR: literal is borrowed (give(...) on a borrow per MOVE-02)
var t = "hello".clone();                // OK: owned
void inspect(String s);                 // accepts a literal directly (borrow)
void store(@take String s);             // requires `.clone()` on a literal
```

### STR-07 — Standard `String` exposes no `@mut` methods

The standard library `String` declares no methods with a `@mut String this` receiver. A binding or field may still be declared `@mut String` per BIND-02 (and reassigned), but no `String` method mutates the value in place. Bulk text construction belongs in `StringBuilder`.

```laterita
@mut String s = readLine();       // declaration permitted
s = readLine();                   // OK: reassigning a @mut binding
// no in-place mutation method exists on String
```

### STR-08 — Default receiver mode of `String` methods is borrow

Methods declared on `String` borrow the receiver unless the signature marks otherwise. Methods that consume the receiver (`@take String this`) are rare and explicitly marked; per STR-07, no `@mut`-receiver methods exist.

```laterita
class String {
    @bound String trim();            // borrow this, return slice
    String toUpperCase();            // borrow this, return owned
    int length();                    // borrow this
}
```

---

## 13. Arrays

### ARR-01 — Methods on `T[]` (`.lat` surface)

The laterita compiler treats `T[]` as a class with the following methods (`.lat`-only; the `.java` mirror on `laterita.lang.Arrays` is ARR-02). Both surfaces compile to the same operations; the `.lat` surface here uses the inline functional-interface spelling of LAT-05, and is sugar over the `.java` mirror per LAT-00.

```laterita
class T[] {
    @bound Pair<@bound @mut T[], @bound @mut T[]> splitAt(@mut T[] this, int mid);

    void forEachChunk(@mut T[] this, int chunkSize,
            @mut (@mut T[]) -> void body);

    void forEachChunkExact(@mut T[] this, int chunkSize,
            @mut (@mut T[]) -> void body);

    Pair<T[], T[]> splitOff(@take T[] this, int mid);
}
```

`splitAt` re-borrows the receiver (BIND-06); the returned record is `@bound` to the receiver's source and the receiver is frozen until both halves expire (LIFE-03). `forEachChunkExact` skips the trailing partial chunk; `forEachChunk` does not. Each chunk passed to `body` is a mut slice of the receiver whose borrow expires at the call's return, so successive chunks are pairwise disjoint by construction. No `@unsafe` is required: each operation reduces to ordinary slice expressions covered by MOVE-06. Fold-style reductions express by capturing a `@mut` local in the body lambda; no dedicated reducer primitive is provided.

`splitOff` consumes the receiver (BIND-07) and returns two owning `T[]` halves spanning `[0, mid)` and `[mid, length)`, sharing the underlying allocation through an internal refcount (freed when the last half drops). Each half is a regular `T[]` supporting the full ARR-01 surface. A different method name from `splitAt` is required because the receiver mode differs and MOVE-09 leaves call sites without a disambiguator.

**Example — long-lived workers.** Each half is pre-extracted by partial move (MOVE-07) before spawning, so each thread captures and consumes its own owning binding.

```laterita
var arr   = readInput();
var split = arr.splitOff(arr.length / 2);
var left  = split.left();
var right = split.right();
var t1 = Thread.ofVirtual().start(() -> heavy(left));
var t2 = Thread.ofVirtual().start(() -> heavy(right));
t1.join();
t2.join();
```

### ARR-02 — `laterita.lang.Arrays` static surface (`.java` mirror)

Static-method mirror of the ARR-01 instance surface for `.java` callers, plus `stream` for read-only parallel processing via the JDK `Stream<T>` API.

```java
package laterita.lang;

public final class Arrays {
    private Arrays() {}

    public static <T> @bound Pair<@bound @mut T[], @bound @mut T[]> splitAt(
            @bound @mut T[] arr, int mid);

    public static <T> void forEachChunk(
            @mut T[] arr, int chunkSize,
            @mut MutableConsumer<T[]> body);

    public static <T> void forEachChunkExact(
            @mut T[] arr, int chunkSize,
            @mut MutableConsumer<T[]> body);

    public static <T> Pair<T[], T[]> splitOff(
            @take T[] arr, int mid);

    public static <T> Stream<T> stream(@bound T[] arr);
}
```

`stream` borrows the source array and exposes its elements through the JDK `Stream<T>` type. The borrow lives on the parameter (`@bound T[] arr`), not on the return — `stream` is static, so the receiver-source `@bound` form on the return type does not apply per LIFE-02. The bare `Stream<T>` return is bound to `arr` by the parameter-source rule, as in `firstWord(@bound String s)` (LIFE-02). Standard terminal operations (including `.parallel().forEach(...)`, `.reduce`, `.collect`) drive multithreading through the stream's underlying `Spliterator`; callers needing a specific executor drive the stream with `ForkJoinPool.submit(...)` per standard JDK practice. Parallel terminal operations require Read-mode closures (CLO-01); a `@mut` capture is rejected at compile time because concurrent invocation would violate the borrow rules. In-place parallel *mutation* of the receiver is not a stream operation — the source array is borrowed, not consumed, and the stream does not write back into it. That use case stays on the `splitOff` path (or the in-thread `forEachChunk` family) per ARR-01.

### ARR-03 — `MutableConsumer<T>`

The written-out form of the anonymous functional type `(@mut T) -> void` used by ARR-01 in the `.lat` surface, for `.java` callers (the inline functional-interface spelling is `.lat`-only per LAT-05).

```java
package laterita.lang;

@FunctionalInterface
public interface MutableConsumer<T> {
    void accept(@mut T data);
}
```

### ARR-04 — `Pair<L, R>`

General-purpose record carrying two values. A single declaration covers owned, borrow, and mixed cases — the mode is driven by what is substituted for `L` and `R` (BIND-08).

```java
package laterita.lang;

public record Pair<L, R>(L left, R right) {}
```

Instantiations encountered in this spec:

- `Pair<T[], T[]>` — owned pair, returned by `splitOff`. Accessors `left()` and `right()` participate in partial-move tracking (MOVE-07), so both fields may be consumed from the same instance.
- `@mut @bound Pair<@bound @mut T[], @bound @mut T[]>` — pair of mutable borrows, returned by `splitAt`. The enclosing binding is `@bound` because the instance contains `@bound`-substituted parameters (BIND-08); its lifetime is the intersection of the field sources (LIFE-03).

The record itself is non-`@local`. Heterogeneous (`L ≠ R`) instantiations are permitted.

---

## 14. Unsafe

### UNS-01 — `@unsafe` is a private-method-only annotation

Unsafe operations are permitted only inside methods declared `private @unsafe`. There is no `@unsafe` annotation on classes and no `unsafe { }` block form. Public APIs are always safe; safety contracts are upheld inside private `@unsafe` methods.

```laterita
public class Rc<T> {
    Heap<ControlBlock<T>> ctrl;

    public Rc<T> share() {
        bumpRefcount();
        return makeHandle();
    }

    private @unsafe void bumpRefcount() { /* ... */ }
    private @unsafe Rc<T> makeHandle() { /* ... */ }
}
```

### UNS-02 — Fixed list of unsafe operations

Only the following operations require `@unsafe` context:

1. Constructing or dereferencing `Heap<T>`.
2. Constructing `Cell<T>` or mutating its contents through a non-`@mut` binding.
3. Cross-thread move of an `@local` type (STD-07).
4. Lifetime extension or transmute.
5. Foreign function calls (FFI / native).
6. Unchecked array indexing.

This list is closed. No other operation is gated by `@unsafe`.

### UNS-03 — Unsafe-typed fields force private + `@unsafe`

A class field whose declared type is an unsafe primitive (e.g., `Heap<T>`, `Cell<T>`) must be private. Any constructor or method that reads or writes such a field must be annotated `@unsafe`.

### UNS-04 — Standard checks still apply inside `@unsafe`

`@unsafe` only unlocks the operations in UNS-02. Type checking, ownership tracking, lifetime inference, and mutability rules continue to apply in `@unsafe` methods.

---

## 15. Standard Library Types (Required)

### STD-01 — `Rc<T>`

A reference-counted shared-ownership smart pointer for single-threaded use. Provides:
- `new Rc<T>(@take T value)` — takes ownership of `value`, refcount 1.
- `new Rc<T>(Rc<T> other)` — copy constructor; the new handle points to the same allocation, bumping the refcount. The contained value is not duplicated.
- `@bound T read()` — returns a shared borrow of the contained value, bound to this handle.
- `Rc<T> share()` — alias for the copy constructor; explicit refcount bump.
- `onDrop()` — decrements the refcount; drops the value at zero. Annotated `@internal` like every `onDrop()` (DROP-06); compiler-emitted at scope exit, never called by user code.

A bare assignment of `Rc<T>` is a borrow per MOVE-01; a `give(...)` move transfers the handle without bumping; `share()` is the only operation that bumps.

A cycle of `Rc<T>` handles whose strong references form a closed loop is not reclaimed: no handle's refcount can reach zero, and the cycle leaks. Programs that may form cycles must use `WeakReference<T>` (STD-03) for the back-edge to break the cycle.

### STD-02 — `Arc<T>`

The cross-thread analog of `Rc<T>`. Reference count operations are atomic. The copy constructor `new Arc<T>(Arc<T> other)` performs the atomic refcount bump. `Arc<T>` is non-`@local` per STD-07 and may be moved or borrowed across thread boundaries.

### STD-03 — `WeakReference<T>`

A non-owning back-reference. The class name and method names follow `java.lang.ref.WeakReference`. Provides:
- `new WeakReference<T>(Rc<T> source)` / `new WeakReference<T>(Arc<T> source)` — constructs a weak handle from a strong one. Mirrors Java's `new WeakReference<T>(referent)` shape but takes the strong handle, because the weak handle is bound to a refcount, not to a GC-tracked referent.
- `Rc<T>? get()` (or `Arc<T>? get()`, matching the source flavor) — returns a strong handle if the value is still alive, otherwise `null`. Implementation must be race-free with respect to concurrent strong-count decrement (compare-and-swap per STD-04).

The return type of `get()` differs from `java.lang.ref.WeakReference.get()`: Java returns the bare referent `T` because the GC keeps it alive across the call site; Laterita returns a fresh strong handle `Rc<T>?` / `Arc<T>?` because liveness during use must be carried by an owning handle, not by a collector. Once the caller drops the returned handle, the value may be reclaimed at the next refcount-zero.

### STD-04 — Race-safe `Arc<T>` upgrade

`WeakReference<T>::get()` on an `Arc`-flavored weak handle must use compare-and-swap to atomically check the strong count is non-zero and bump it. A simple read-then-bump is unsound.

### STD-05 — `Cell<T>`

Interior-mutability primitive. Permits mutation of contents through a non-`@mut` binding. Construction and content mutation require `@unsafe` context per UNS-02. Used as a building block for `Arc<T>`, `Mutex<T>`, lazy initializers, etc.

### STD-06 — `Heap<T>`

Raw heap-allocation primitive. Provides allocation, dereference, and free. All operations require `@unsafe` context per UNS-02. `Heap<T>.clone()` reaches `broken()`: a raw allocation has no defined duplication semantics — duplicating the handle would create two owners of the same memory. Wrapper types built on `Heap<T>` (e.g., `Rc<T>`, `Arc<T>`, owned containers) define their own `clone()` with the appropriate semantics.

### STD-07 — `@local` marker

Cross-thread safety in Laterita is expressed by a single negative marker, `@local`. The language does **not** provide `Send` or `Sync` traits; that vocabulary belongs to Rust and has no analog here. Inter-thread communication uses `Mutex<T>` (STD-09) for shared mutable state and the existing `java.util.concurrent` channel-like classes (e.g., `BlockingQueue`) for hand-off — no auto-trait machinery is involved.

A type carries the `@local` property if its instances cannot safely cross thread boundaries.

The standard library declares `@local`:
- `Rc<T>` (STD-01)
- `Cell<T>` (STD-05)
- `Heap<T>` (STD-06)

A class is `@local` by inference if any field in its transitive field hierarchy is of an `@local` type. A class is **non-local** otherwise. A class may be annotated `@local` to opt in despite having no `@local` fields (used for thread-affine resources whose affinity is not visible to the type system: OS handles, GPU contexts, etc.).

A class may be annotated `@unsafe @nonlocal` to override inferred `@local`-ness despite containing `@local` fields. This declaration asserts that the class internally synchronizes access to those fields per UNS-04. The compiler does not verify the assertion. The stdlib types `Arc<T>` (STD-02), `Mutex<T>`, and `Thread` (THR-01) are annotated `@unsafe @nonlocal`.

The compiler must reject:
- A cross-thread closure capture (CLO-01) of a binding whose type is `@local`.
- A move (MOVE-02) of a `@local` value across a thread boundary outside `@unsafe` (UNS-02 already gates this).

### STD-08 — Borrow-checked mutable iteration

Three operations support in-place modification of collections under the borrow rules:

- **`Collection<T>.removeIf(Predicate<T> p)`** — bulk removal of every element matching `p`. Same name and meaning as `java.util.Collection.removeIf` (Java 8+).
- **`Iterator<T>` and `ListIterator<T>`** — Java's existing iterator types, reused by name and by method set (`hasNext`, `next`, `hasPrevious`, `previous`, `nextIndex`, `previousIndex`, `remove`, `set`, `add`).
- **`next()` and `previous()` return `@bound T`** — a borrow into the underlying collection's storage, bound to the iterator. Any iterator-mutating call (`remove`, `set`, `add`) invalidates the borrow at the type level via MOVE-04.

The one signature deviation from Java: **`Iterator<T>.remove()` and `ListIterator<T>.remove()` return `T`** rather than `void`. The removed element is yielded to the caller as an owned value. Statement-form `it.remove();` (ignoring the return) drops the value via `onDrop` (DROP-01), matching the observable behavior of Java's void-returning `remove`.

Holding a `@mut Iterator<T>` or `@mut ListIterator<T>` is a mutable borrow of the underlying collection per MOVE-04. Concurrent modification through any other path is rejected at compile time; `ConcurrentModificationException` is not part of Laterita's runtime semantics, and `modCount`-style runtime guards are not required.

Implementations of these operations are permitted (and expected) to use `private @unsafe` (UNS-01) for the internal aliasing they require. User code remains safe.

### STD-09 — `Mutex<T>`

A mutual-exclusion primitive wrapping an owned value. Access to the protected value is scoped to a closure call rather than mediated by a separately held guard.

**Constructor.** `new Mutex<T>(@take T value)` — wraps `value`, initially unlocked and unpoisoned.

**Scoped acquisition.** `<R> R with((@bound @mut T) -> R action)` acquires the lock (blocking if held), invokes `action` on the protected value, releases the lock, and returns `action`'s result. `<R> Optional<R> tryWith((@bound @mut T) -> R action)` (including timed variants) is the non-blocking form: it returns an empty `Optional` if the lock cannot be acquired, otherwise runs `action` and returns its result wrapped. The protected `T` is reachable only as the parameter of `action`; there is no `unlock()` method, no externally held guard, and no way to extend the borrow beyond the call.

**Acquisition can throw.** `with` throws `PoisonedException` (THR-10) on a poisoned mutex and `InterruptedException` (THR-04) if the calling thread is interrupted while blocked acquiring the lock. `tryWith` throws `PoisonedException` only.

**Poison on closure throw.** If `action` propagates an exception, `with` / `tryWith` mark the mutex poisoned (THR-10) before releasing the lock and rethrowing. A normal closure return releases the lock without poisoning.

**Drop semantics.** `Mutex<T>.onDrop()` runs `T.onDrop()` on the protected value unconditionally — by LIFE-01 no `with` / `tryWith` call can be in flight when the mutex itself is dropped, so cleanup is independent of lock or poison state.

**Inspection.** `isPoisoned()` reads the poison flag without acquiring the lock.

`Mutex<T>` is annotated `@unsafe @nonlocal` per STD-07. Its internals (a raw OS lock primitive and a `Cell<T>`-backed protected value) require `@unsafe`; the closure-scoped surface above is safe.

---

## 16. Threads

### THR-01 — `Thread` type

`Thread` is the standard `java.lang.Thread` class reused minus the deprecated methods (`stop()`, `suspend()`, `resume()`, `destroy()`, etc.) and with two changes per THR-03 and THR-06.

A `Thread`'s lifetime is bound to the owner of its reference: when the owning binding goes out of scope, `Thread.onDrop()` runs (DROP-03), interrupting the worker and waiting for it to terminate. Long-lived threads (server accept loops, background flushers) must be owned by bindings whose lifetime matches — typically a top-level binding in `main` or a field of an object that is itself owned at top level.

`Thread` is annotated `@unsafe @nonlocal` per STD-07 and may be moved or borrowed across thread boundaries.

### THR-02 — Thread creation

Threads are created using the standard Java `Thread` constructor and `start()` method, or via the fluent factory methods on `Thread.ofVirtual()` and `Thread.ofPlatform()`. No new keyword is introduced.

```laterita
@mut var worker = new Thread(() -> body);
worker.start();

var other = Thread.ofVirtual().start(() -> body);   // factory returns started Thread
```

Captures within the closure body follow the closure capture rules (CLO-01, CLO-06) with the additional restrictions of STD-07: each captured binding's referenced type must be non-`@local`.

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

`Thread.onDrop()` is `@internal` (DROP-06) and is compiler-emitted at scope exit per DROP-03. It performs, in order:

1. Set the interrupt flag (idempotent per THR-03).
2. Wait for the worker to terminate. Termination is bounded by the worker reaching its next interruption point and unwinding via `InterruptedException`; the worker's own `onDrop` chain runs frame-by-frame during the unwind (DROP-03).
3. Reclaim the thread's resources.

To trigger `Thread.onDrop()` before natural scope exit, give the binding to the void per MOVE-08 (`give(worker);`).

`Thread` is `final`: it implements `onDrop()`, so DROP-09 applies. The Java pattern of subclassing `Thread` (`class Worker extends Thread { … }`) is unavailable; pass a `Runnable` or lambda to the constructor instead (THR-01, THR-02), and compose rather than extend when a richer thread wrapper is needed.

### THR-07 — `Thread.interrupt()`

`Thread.interrupt()` sets the interrupt flag on the receiver per THR-03 and returns immediately. It does not wait for the worker to unwind. May be called from any thread holding a reference to the receiver.

### THR-08 — `InterruptedException`

`InterruptedException` is the exception thrown at an interruption point (THR-04) when the running thread's interrupt flag is set. It propagates through the standard exception unwind path (EXC-02). Catching `InterruptedException` does not clear the interrupt flag (THR-03); the next interruption point in the same thread throws it again.

`InterruptedException` is unchecked per EXC-05; methods containing interruption points are not required to declare it.

### THR-09 — `Thread.join()`

`Thread.join()` blocks the calling thread until the receiver terminates. It is an interruption point per THR-04: if the calling thread's interrupt flag is set while it is blocked in `join()`, it throws `InterruptedException`.

`join()` does not interrupt the receiver. To cancel and observe, call `worker.interrupt()` and then `worker.join()`.

### THR-10 — `Mutex<T>` poisoning

A `Mutex<T>` is **poisoned** when the closure passed to its `with` / `tryWith` call (STD-09) propagates an exception — `InterruptedException` or any other — out of the critical section. `with` / `tryWith` set the poison flag inside the `catch` clause that wraps the closure invocation, before releasing the lock and rethrowing. A normal closure return releases the lock without poisoning.

`Mutex<T>.with()` and `tryWith()` throw `PoisonedException` on a poisoned mutex. There is no bypass: a poisoned mutex's contents are no longer reachable through the locking API. Programs that need to recover from poisoning replace the entire `Mutex<T>` (typically the surrounding `Arc<Mutex<T>>`); the replaced instance is dropped along with its protected value through the standard `onDrop` path (STD-09).

Poisoning is per-mutex, sticky, and not cleared by lock release or by inspection. `isPoisoned()` reads the flag without acquiring the lock.

---

## 17. Compilation Model

### COMP-01 — Native compilation, no GC

Laterita is intended to be compiled ahead-of-time to native code. There is no garbage collector at runtime. Memory management is determined by static ownership, borrow tracking, and `onDrop()` insertion at scope exits. Reference-counted types (`Rc<T>`, `Arc<T>`) introduce dynamic refcount-based reclamation; cycles among such handles leak per STD-01. No tracing collector is provided.

### COMP-02 — Generic monomorphization

Generic types and methods are monomorphized: each instantiation produces a specialized implementation at compile time. Field offsets and method dispatch are resolved per-instantiation.

### COMP-03 — Compiler-inserted cleanup

The compiler must emit `onDrop()` calls at every scope-exit point per DROP-03 and unwind table entries per EXC-02. These insertions happen after all user-level analysis and are not visible in source. Each emitted call site must implement the exception handling specified by DROP-07: body termination, drop sequence continuation, and suppressed-exception accumulation.

### COMP-04 — Drop flags as compile-time state

Per-field move state (DROP-04) is compiler-internal bookkeeping. Implementations should optimize away flags whose values are statically determined.

### COMP-05 — No reflection

Laterita does not provide reflection. There is no runtime API for enumerating fields or methods, looking up members by name, instantiating types from a `Class` token, generating dynamic proxies, or loading classes at runtime. The compiler is not required to emit per-type metadata for these purposes, and standard-library APIs equivalent to `java.lang.reflect.*`, `java.lang.Class` member-access methods, `Proxy.newProxyInstance`, or `ServiceLoader`'s runtime classpath scan are not provided.

Use cases traditionally served by reflection are served by compile-time code generation (annotation processors, compiler plugins): serializers, ORM mappers, dependency-injection wiring, validators, mocks, test discovery, and SPI registries are all generated at build time from the types and annotations that exist in source. Stack traces (EXC-04) and exception types remain available; this rule constrains type and member introspection, not error reporting.

### COMP-06 — Source file extensions

A laterita source file uses one of two extensions:

- **`.lat`** — full surface. Additionally admits the `.lat` surface forms specified in §19.
- **`.java`** — Java-compatible subset, parseable by `javac` and Java-aware IDEs. The §19 forms are rejected; equivalent meaning is expressed through their `.java`-surface desugarings.

Both extensions denote the same language: the type system, annotation/intrinsic surface (§18), and emitted artifacts are identical, and cross-unit references work uniformly. Whether a type was declared in `.lat` or `.java` is not part of its identity. Because every `.lat` form is pure syntactic sugar (LAT-00), migration tooling may mechanically translate between the two forms.

### COMP-07 — Compiler invocation

The reference laterita compiler is named `latc`. It accepts both `.lat` and `.java` sources in a single compilation unit, dispatches by file extension per COMP-06, and emits the artifacts required by COMP-01 through COMP-04.

---

## 18. Reserved Names

The following names are introduced by this specification and must be provided by the standard library: `Rc`, `Arc`, `WeakReference`, `Cell`, `Heap`, `Mutex`, `PoisonedException`. The `Thread` type and `InterruptedException` are reused from the Java standard library per THR-01 and THR-08; `java.util.Objects.requireNonNull` is reused as the `.java`-mode null assertion per LAT-04. Anonymous functional interfaces are structural per FN-01 and require no named stdlib interfaces.

The identifier `onDrop` is reserved as the language-orchestrated lifecycle hook (DROP-01).

**Laterita introduces no new keywords.** The ownership, lifetime, mutability, cleanup, and visibility concepts are expressed as annotations and static method calls; the five non-Java syntactic forms (`T?`, `?.`, `?:`, `!!`, `(P1,…,Pn) -> R`) are gated to `.lat` sources per §19. The annotations and stdlib static methods that carry laterita-specific semantics are:

| Concept | Form | Spec rule |
|---|---|---|
| Mutable binding / field / parameter | `@mut` | BIND-02 |
| Method mutates its receiver | `@mut` on the explicit `this` parameter | BIND-05 |
| Owned parameter or LHS prefix | `@take` | MOVE-03 |
| Method consumes its receiver | `@take` on the explicit `this` parameter | BIND-07 |
| Borrow source on parameter or return | `@bound` | LIFE-02 |
| Compiler-only-callable method | `@internal` | DROP-06 |
| Private unsafe method | `@unsafe` | UNS-01 |
| Class is thread-affine | `@local` | STD-07 |
| Class overrides inferred `local`-ness | `@nonlocal` (with `@unsafe`) | STD-07 |
| Nullable type in `.java` mode | `@Nullable T` | NULL-02, COMP-06 |
| Move at a use site (expression or statement) | `Intrinsics.give(x)` | MOVE-02, MOVE-08 |
| Unreachable path | `Intrinsics.broken()` | UNR-01 |

The annotations are declared in `laterita.lang.annotation`. The static methods live on `laterita.lang.Intrinsics` and are normally statically imported so call sites read `give(x)` and `broken()` without a qualifier. To `javac` they are ordinary annotations and ordinary static method calls; the laterita compiler attaches the additional semantics specified in the rules above.

Type inference uses Java's `var` keyword. In laterita mode every binding is immutable unless annotated `@mut`, so `var x = expr` is immutable; `@mut var x = expr` is mutable. Java's `final` is permitted on local bindings but is redundant.

Java's `synchronized` keyword is removed: there is no per-object intrinsic monitor, no `synchronized` method modifier, and no `synchronized(obj) { ... }` block. Mutual exclusion is provided exclusively through `Mutex<T>` (and related stdlib types). The associated `Object.wait()`/`notify()`/`notifyAll()` methods are likewise not provided; condition-variable-style coordination is a stdlib concern.

Java's existing keywords and their meanings are otherwise preserved unless explicitly modified by this specification.

---

## 19. `.lat` Surface Forms

A laterita source file uses one of two extensions (COMP-06): `.java`, the Java-compatible subset that `javac` parses, and `.lat`, which additionally admits the forms specified in this section. Every rule outside this section belongs to the `.java`-compatible surface.

### LAT-00 — The `.lat` surface is pure syntactic sugar

Each form in this section is syntactic sugar: it has an exact `.java`-surface equivalent into which the compiler desugars it before any type, ownership, lifetime, or runtime analysis. No `.lat` form introduces type-system, ownership, or runtime semantics absent from the `.java` surface. Consequently:

- Any `.lat` source can be mechanically rewritten to an equivalent `.java` source and the reverse; this rewrite is total and meaning-preserving.
- A program's meaning never depends on its file extension. Whether a declaration was written in `.lat` or `.java` is not part of its identity (COMP-06).
- A proposed `.lat` form that cannot be expressed as a desugaring to the `.java` surface does not belong in this section. A construct that carries its own semantics belongs in the core spec as a `.java`-surface rule, expressed through the annotation and intrinsic surface of §18.

The forms are listed below with their `.java`-surface desugarings.

### LAT-01 — `T?` nullable-type suffix

`T?` is the `.lat` spelling of the nullable type `@Nullable T` (NULL-02). The two spellings denote the same type; the nullability rules NULL-01 through NULL-10 are stated on the type and apply identically to either spelling.

### LAT-02 — Safe call `?.`

`expr?.method(args)` evaluates to `null` if `expr` is `null`, otherwise invokes `method` on `expr`. The result type is `R?` where `R` is the method's return type.

Desugars to `expr == null ? null : expr.method(args)`, with NULL-06 narrowing applied to the non-null branch.

```laterita
String? upper = maybeName?.toUpperCase();
```

### LAT-03 — Elvis operator `?:`

`a ?: b` evaluates to `a` if `a` is non-null, otherwise to `b`. The result type is the common type of the non-nullable form of `a` and the type of `b`.

Desugars to `a != null ? a : b`, with NULL-06 narrowing on `a`.

```laterita
String shown = maybeName ?: "anonymous";
```

### LAT-04 — Null assertion `!!`

`expr!!` converts `T?` to `T`. If `expr` is `null`, a `NullPointerException` is thrown. This is the only path from `T?` to `T` at the type level without a flow-sensitive narrowing (NULL-06).

Desugars to `java.util.Objects.requireNonNull(expr)`; the laterita compiler attaches the `T? → T` narrowing to a recognized call of `requireNonNull`, so the `.java` form carries the same typing.

### LAT-05 — Inline functional-interface type `(P1, …, Pn) -> R`

The anonymous, structural functional-interface type expression specified by FN-01 is a `.lat`-only spelling. A `.java` source expresses the same SAM signature by declaring a nominal functional interface at the same position. FN-01 through FN-03 specify the type semantics; this rule records that the inline spelling is gated to `.lat`. The desugaring substitutes a nominal interface whose single abstract method — named `apply`, per FN-01 — has the written parameter and return modes.
