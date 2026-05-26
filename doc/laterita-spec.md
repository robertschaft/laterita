# Laterita — Language Specification

This document specifies the normative requirements that a Laterita compiler and standard library must satisfy. Each requirement carries a mnemonic code for cross-reference.

Sections 1 through 18 specify the Java-compatible surface: every rule there is expressible as annotated `.java` source that `javac` parses (COMP-06). Section 19 specifies the `.lat` surface forms — syntactic sugar that desugars to the Java-compatible surface and adds no semantics of its own (LAT-00).

Codes are grouped by area: `BIND` (bindings), `NULL` (optionality), `MOVE` (move/borrow), `MUT` (mutability), `LIFE` (lifetimes), `DROP` (cleanup), `OBJ` (object copying), `UNR` (unreachability), `STR` (strings), `ARR` (arrays), `FN` (functional interfaces), `CLO` (closures), `EXC` (exceptions), `UNS` (unsafe), `STD` (standard library types), `THR` (threads), `COMP` (compilation model), `LAT` (`.lat` surface forms).

---

## 1. Bindings

### BIND-01 — Four binding forms

The language provides four core local binding forms:

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

`@mut` grants two capabilities at once: reassigning the binding and mutating the value through it. Java's `final` may be added to any form to lock reassignment. On a bare (immutable) form it is redundant — immutability is already the default — but on a `@mut` form it produces a third state: the value may still be mutated through the binding, but the binding can no longer be rebound.

```laterita
@mut final Properties config = loadConfig();
config.setProperty("verbose", "true");   // OK — mutation through the binding
config = loadConfig();                   // ERROR — final locks reassignment
```

### BIND-02 — `@mut` is the unified mutability marker

The annotation `@mut` denotes mutability in every binding position it appears: local bindings, fields, parameters, and return types; on a class declaration it marks a mutable surface (MUT-03). A method declares mutation of its receiver with the companion annotation `@mutating` (BIND-05). These two annotations are the only surface forms for mutability. Java's `final` is orthogonal to `@mut` (BIND-01).

### BIND-03 — Field declarations follow binding rules

Fields follow the same rules as locals. A field without `@mut` cannot be reassigned and cannot be mutated through. A field with `@mut` permits reassignment and mutation-through; a `@mut final` field permits mutation-through but not reassignment (BIND-01). A `@mut` field may be declared only in a class declared `@mut` (MUT-04).

A field is owned by default: a bare `T x;` declares storage that owns its value and is dropped with the enclosing instance (DROP-05). `@bound` on a field declares a borrow slot; an instance with any `@bound` field — including via `@bound`-substituted generic arguments (BIND-08) — can only be produced as a `@bound` value, with lifetime per LIFE-03. `@take` on a field is currently not supported: it would be redundant, since every field — functional-interface or not — owns its value by default, and a functional interface's call mode is a property of its type, not of the field (CLO-03).

```laterita
@mut class User {
    String name;                    // immutable, owned field
    @mut int loginCount;            // mutable, owned field
}

record EntryView<K, V>(@bound K key, @bound V value) {}   // borrow-view; instances must be @bound
```

### BIND-04 — Constructors initialize immutable fields

Every field of a class must be assigned exactly once in every constructor before any method on `this` is invoked. Fields that cannot be reassigned — those without `@mut`, and `@mut final` fields — can only be assigned in constructors. A `@mut` field that is not `final` can also be reassigned in `@mutating` methods.

### BIND-05 — Methods declare mutation of `this` with `@mutating`

A method annotated `@mutating` may mutate `this` (i.e., reassign or mutate-through `@mut` fields, and call other `@mutating` methods on `this`). A method without it cannot.

`@mutating` is a declaration annotation on the method, distinct from `@mut`. Keeping it a separate token avoids a collision: `@mut` already denotes binding mutability wherever it appears, including immediately before a return type (BIND-02), so reusing it in modifier position for receiver mutation would make `@mut R foo()` ambiguous between the two. `@mutating` is a visibility-like predicate rather than a behavioral one: by BIND-06, a `@mutating` method is only callable on receivers whose binding is itself `@mut`, so the marker narrows the method's visible API surface to mutable receivers. A method that both mutates and consumes its receiver carries both `@mutating` and `@consuming` (BIND-07).

```laterita
@mut class Counter {
    @mut int n;
    public int read()                    { return n; }   // bare receiver
    public @mutating void inc()          { n = n + 1; }  // mutating receiver
    public final @mutating void reset()  { n = 0; }
}
```

### BIND-06 — Mutability transitivity

Mutation requires `@mut` at every level of access. To call a `@mutating` method, the receiver binding must be `@mut` (and its static type a `@mut` class, per MUT-06). To mutate a field, the field must be `@mut` and the binding holding the containing object must be `@mut` (or the mutation must occur in a `@mutating` method of the same object).

```laterita
var counter = new Counter();
counter.inc();              // ERROR: counter is not @mut
@mut var c2 = new Counter();
c2.inc();                   // OK
```

### BIND-07 — Methods declare consumption of `this` with `@consuming`

A method annotated `@consuming` consumes its receiver. The body owns `this`, may move out of `this`'s fields (MOVE-07), and may hand `this` itself to a `@take` parameter or to another `@consuming` method. After the call returns, the binding that held the receiver is consumed (MOVE-02); subsequent uses are rejected.

`@consuming` is a declaration annotation on the method, in modifier position alongside `public` / `final` / `@mutating`. It is orthogonal to `@mutating` (BIND-05) and the two compose: a method that both mutates and consumes its receiver carries both. Calling a `@consuming` method requires the receiver binding to own its value; the call site needs no `give(...)` wrapper — the signature already declares the transfer.

```laterita
class Connection {
    Heap<DbConn> conn;

    public @consuming void close() {                                // consumes this
        this.conn.flush();
        // `this` is dropped at function end; underlying connection released
    }
}

@mut class StringBuilder {
    @mut String contents;

    public @mutating @mut @bound StringBuilder append(String s) {   // mutates in place; returns
        this.contents = this.contents + s;                          // a @mut borrow of this for chaining
        return this;
    }

    public @consuming String build() {                              // consumes, yields owned String
        return this.contents;
    }
}

var conn = openConnection();
conn.close();                            // OK: conn was owned; consumed by close()
conn.use();                              // ERROR: conn was consumed

@mut var sb = new StringBuilder();
sb.append("hello").append(", world");    // chain via @mut @bound returns; sb still usable
String greeting = sb.build();            // consumes sb; yields owned String
```

### BIND-08 — `@bound` in generic type arguments

`@bound`, `@mut`, and `@take` are binding-position annotations: they may appear on parameters, return types, fields, and FI parameter/return slots. Inside a generic type argument (the `T` slot of `Foo<…T…>`) their admissibility is governed per annotation — `@bound` by this rule, `@take` by BIND-09, `@mut` by BIND-10.

`@bound` is admitted in a type argument. It composes cleanly: a class instance whose generic arguments include any `@bound`-substituted parameter can only be produced as a `@bound` value, with lifetime per LIFE-03 (and the idempotence rule of LIFE-06 when `@bound` stacks). No struct-level lifetime parameters are introduced — the `@bound` binding on the instance carries the lifetime.

On a local binding, only `@mut` is admitted; `@take` and `@bound` are currently not supported, to keep one canonical form (the local's owned-vs-borrow mode follows its RHS per MOVE-02, and any borrow-substituted return is already `@bound` at the producer side).

```laterita
record Pair<L, R>(L left, R right) {}

Pair<String, Int>                   p1 = new Pair<>("hello".clone(), 42);   // owned: constructor moves
Pair<@bound String, @bound Int> view = new Pair<>(name, count);              // bound: bare args borrow; view's mode follows the RHS
```

### BIND-09 — `@take` is rejected in generic type arguments

`@take` may not appear inside a generic type argument. `@take` is a parameter mode — it describes how a call site transfers ownership into a slot — not an attribute a value carries, and it has no referent as a type argument: `Pair<@take K, @take V>` is a compile error. Ownership of the contents of a generic structure is carried by the structure's own binding (owned vs. `@bound`), not by per-argument `@take` marks.

### BIND-10 — `@mut` in generic type arguments

`@mut` may appear inside a generic type argument only when the enclosing generic type is itself `@mut` at that occurrence — the type of a `@mut` binding, `@mut` parameter, `@mut` field, or an owned/`@mut` return. `@mut List<@mut Foo>` is well-formed; `List<@mut Foo>` — a shared binding with `@mut` elements — is rejected at the declaration.

The restriction is a soundness requirement. An element accessor declared `@bound E get(int i)` returns `@mut @bound Foo` when `E` is substituted with `@mut Foo`; producing a `@mut` borrow of an element re-borrows the whole container mutably (BIND-06), which by MOVE-04 is exclusive — the same receiver-reborrow pattern `splitAt` uses (ARR-01). A *shared* `List<@mut Foo>` would instead let that `@mut` element borrow be drawn from each of several coexisting shared borrows of the container, aliasing the element. Requiring the container to be `@mut` makes every `@mut` element borrow an exclusive re-borrow, so two simultaneous element borrows are a borrow-check error rather than aliasing.

```laterita
@mut List<@mut Foo> a = ...;     // OK: mutable list, mutable elements
@mut List<Foo>      b = ...;     // OK: mutable list, immutable elements
List<Foo>           c = ...;     // OK: immutable list, immutable elements
List<@mut Foo>      d = ...;     // ERROR (BIND-10): a shared list cannot carry @mut elements
```

A genuinely shared container whose elements must mutate through shared borrows still requires `Cell<T>` (STD-05), with the `@unsafe` cost visible at the storage site.

### BIND-11 — Static fields are immutable; initializers are const

A field declared `static` is initialized once at program start and cannot be reassigned. `static final` is accepted for Java compatibility but `final` is redundant; `@mut static` is a compile error.

The initializer must be a *const expression* — a literal, a reference to another const-initialized static, or a call to a constructor or function the compiler can evaluate at compile time. The set of const-eligible operations is defined by the compiler and standard library; at minimum it covers primitive arithmetic, string literals, and the const-eligible constructors of the synchronizing stdlib types (`Mutex<T>` per STD-09, `Arc<T>` per STD-02, and the atomic primitives). Initializers that require genuinely runtime computation go through a once-init wrapper held in the static slot and forced at first access.

```laterita
static Mutex<Map<String, Session>> SESSIONS = new Mutex<>(new HashMap<>());
static Arc<Config>                 BUILTIN  = new Arc<>(Config.DEFAULT);

SESSIONS.with(s -> s.put(id, session));   // STD-09
Config base = BUILTIN.read();             // STD-02
```

### BIND-12 — Static fields must be non-`@local`

The declared type of a static field must be non-`@local` (STD-07). A static slot is reachable from every thread, so a `@local` type stored there would be reachable cross-thread — exactly the case `@local` exists to forbid. `static Rc<T>` is rejected; use `static Arc<T>`. `static Cell<T>` and `static Heap<T>` likewise.

---

## 2. Mutability Rules (Cross-cutting)

### MUT-01 — Immutability is transitive through borrows

A shared (immutable) borrow grants no mutation rights regardless of any `@mut` markers on fields reached through it. Mutation through a borrow requires the borrow itself to be mutable.

### MUT-02 — Interior mutability requires `Cell<T>`

A type that needs to mutate its contents through a bare receiver must hold those contents inside `Cell<T>`. This is the only mechanism that bypasses MUT-01, and `Cell<T>` is an unsafe primitive (see UNS-02).

### MUT-03 — `@mut` class declaration

A class, abstract class, or interface may be declared `@mut` (`@mut class C`, `@mut abstract class C`, `@mut interface I`). The marker declares that the type has a *mutable surface*: `@mut` fields may be declared in it (MUT-04, classes only) and `@mutating` methods may be declared on it (BIND-05). A type not declared `@mut` is a *value type* — its instances expose no callable `@mutating` method and cannot be mutated through any binding; a non-`@mut` interface may declare only methods that do not carry `@mutating`. Both `@mut` and non-`@mut` interfaces may be implemented by either `@mut` classes or value classes.

`@mut` is currently not supported on a `record` or an `enum`: both are value classes by construction.

`Object` is `@mut`. `Number` is a value class; therefore `Integer`, `Long`, `Float`, and the other boxed numeric types are value classes.

### MUT-04 — `@mut` fields require a `@mut` class

A `@mut` field may be *declared* only in a class declared `@mut`. A value class may *inherit* `@mut` fields from a `@mut` ancestor (MUT-05) but may not declare new ones. The declared type of a `@mut` field is unrestricted: a `@mut` field whose type is a value class is permitted and grants reassignment of the field (BIND-03) without granting mutation through it.

### MUT-05 — Mutability and inheritance

A class declared `@mut` may extend only a `@mut` class. A value class may extend a class of either kind. Two consequences follow:

- every superclass of a `@mut` class is itself `@mut`, up to `Object`;
- once a class in a hierarchy is a value class, every subclass of it is a value class.

A value class that extends a `@mut` class inherits its ancestors' `@mut` fields and `@mutating` methods. The inherited `@mutating` methods are not callable on the value class (MUT-06): the value class is a frozen view of the inherited surface. This is the mechanism for deriving an immutable variant of a mutable class — a collection, a configuration holder, a builder — without re-declaring its API.

```laterita
@mut class Counter {
    @mut int n;
    Counter(int start) { this.n = start; }
    @mutating void inc() { n = n + 1; }
    int read()           { return n; }
}

class FrozenCounter extends Counter {           // value class extending a @mut class
    FrozenCounter(int start) { super(start); }
}

var fc = new FrozenCounter(5);
fc.read();      // OK: non-@mutating method
fc.inc();       // ERROR: inc is @mutating; FrozenCounter is a value class (MUT-06)
```

### MUT-06 — Calling `@mutating` methods

A `@mutating` method is callable on a receiver only when both conditions hold, each checked statically:

- the receiver binding is `@mut` (BIND-06), and
- the receiver's static type is a `@mut` class or a `@mut` interface.

When the static type is a `@mut` interface, the `@mut`-binding requirement together with MUT-07 already guarantees the receiver's dynamic class is `@mut`.

A constructor is exempt: within a constructor, `@mutating` methods may be called on `this` and inherited `@mut` fields assigned, whatever the class kind. This is the initialization phase; the value-class freeze takes effect when the constructor returns. A value class therefore establishes its inherited mutable state during construction — typically by chaining `super(...)` — after which that state is permanently unreachable for mutation.

### MUT-07 — `@mut` access is not obtainable by widening

Widening a value-class instance to one of its `@mut` supertypes (class or interface) never produces a `@mut` value. Such a widened value may not initialize, be assigned to, or be passed to a `@mut` binding, parameter, or field; and the cast `(@mut Super) v` is rejected when `v`'s static type is a value class. Widening to a bare (immutable) binding of the supertype remains permitted.

Together with MUT-05 this guarantees that any `@mut` binding whose static type is a `@mut` class or an interface refers to an instance whose dynamic class is `@mut` — which is what makes MUT-06's static check sound. `@mut` access to an instance originates only at construction of a `@mut` class and propagates only through `@mut` bindings, parameters, returns, and fields.

```laterita
Counter view   = new FrozenCounter(5);    // OK: a value-class instance widens to a bare Counter
@mut Counter m = new FrozenCounter(5);    // ERROR (MUT-07): a value-class instance cannot fill a @mut slot

FrozenCounter fc = new FrozenCounter(5);
@mut Counter bad = (@mut Counter) fc;     // ERROR (MUT-07): a cast cannot manufacture @mut access
```

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

The laterita annotations on a parameter (`@take`, `@mut`, `@bound`) are not part of the Java overload signature, and neither are the method-level receiver-mode annotations (`@mutating`, BIND-05; `@consuming`, BIND-07). Two same-name methods that differ only in these annotations are a duplicate declaration and rejected by `javac`. An API that needs both shapes — borrow and consume on the same parameter slot, or shared-receiver and consuming-receiver on the same operation — uses distinct method names; the array surface follows this pattern with `splitAt` and `splitOff` (ARR-01).

### MOVE-04 — Borrow exclusivity

For any binding's lifetime, either:
- any number of immutable (shared) borrows may coexist, or
- exactly one mutable borrow may exist, with no other borrows.

The compiler must reject programs that violate this.

### MOVE-05 — Disjoint field borrows

Two simultaneous borrows of statically distinct fields of the same struct are non-aliasing and must be permitted, including when both are mutable. The compiler must perform this disjointness analysis.

```laterita
@mut class Pair { @mut int left; @mut int right; }
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

### MOVE-10 — Override variance for `@take` and `@mut`

For overrides of inherited methods (subclass override, interface implementation):

- **`@take` matches invariantly.** An override's parameter must carry `@take` if and only if the inherited declaration does. An override that dropped `@take` would refuse a binding the caller transferred through the inherited contract; an override that added `@take` would silently consume a binding the inherited contract said it would only borrow. Both directions break callers using the inherited type.

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

### CLO-03 — Call mode and binding mode

A functional-interface value has two independent properties.

**Call mode** is a property of the *type*. The single abstract method of a functional interface carries a receiver mode, declared exactly as on any method (BIND-05, BIND-07). That receiver mode is the interface's call mode:

| SAM receiver mode | Call mode | Invocation |
|---|---|---|
| bare | **shared-call** | through a shared borrow; repeatedly; concurrently (subject to STD-07) |
| `@mutating` | **mut-call** | through a `@mut` binding; repeatedly but sequentially |
| `@consuming` | **once-call** | once; the call consumes the value |

```laterita
interface MissResolver<T> { T resolve(String key); }                // shared-call
@mut interface HitListener  { @mutating void onHit(String key); }   // mut-call
interface Finalizer         { @consuming void run(); }              // once-call
```

**Binding mode** is a property of the *binding* that holds the value. A functional-interface binding follows the ordinary binding rules with no special case: a field owns its value by default (BIND-03); a parameter receives ownership with `@take` or a borrow otherwise (MOVE-03); `@mut` grants mutability (BIND-01); `@bound` marks a borrowed field or return (BIND-03, LIFE-02); a local follows its RHS (MOVE-02).

Invoking the SAM is an ordinary method call on the functional-interface value and obeys mutability transitivity (BIND-06, BIND-07): invoking a mut-call SAM requires the binding to be `@mut`; invoking a once-call SAM requires the binding to own the value, and the call consumes it (a partial move per MOVE-07 when the binding is a field). Storing, moving, or borrowing a functional-interface value is governed by the binding mode alone, independently of the call mode — a value may be held in a binding from which its SAM cannot be invoked.

```laterita
class C {
    MissResolver<Foo> resolve;   // owned field, shared-call — invocable through a bare receiver
    @mut HitListener  onHit;     // owned field, mut-call — invocable only in a @mutating method
}
```

A functional-interface type used as a parameter or return combines modifiers from three layers, each governed independently:

| Layer | Modifiers | Governed by |
|---|---|---|
| Inside the type — the SAM's parameters and return | `@take`, `@mut`, `@bound` | MOVE-03, LIFE-02 |
| The SAM's receiver — the type's call mode | bare / `@mutating` / `@consuming` | this rule |
| The binding holding the value | `@mut`, `@take`, `@bound`, ownership | BIND-01–08, MOVE-03, LIFE-02 |

```laterita
@mut interface F<T, R> { @mutating R apply(@take T); }   // call mode mut-call; SAM parameter @take T

void process(@mut F<Job, Done> fn) { /* … */ }      // @mut: binding mode of the parameter
```

A function may return a functional-interface value. `@mut` written before a return type is the binding mutability of the returned binding (BIND-02); on an owned return it is redundant, meaningful only together with `@bound`. A `@bound` before a returned functional-interface type is the ordinary return-binding annotation (LIFE-02): it declares that the returned value borrows a `@bound` source, and arises when a function returns a closure that captures one of its parameters:

```laterita
// The returned closure borrows `fn` and `first`,
// so its lifetime is the intersection of both (LIFE-03).
<A, B, R> @bound (B) -> R partial(@bound (A, B) -> R fn, @bound A first) {
    return (b) -> fn.apply(first, b);
}
```

A once-call functional-interface value cannot be a `@bound` source: the call that would produce the return consumes it.

The anonymous form `(P1, …, Pn) -> R` (FN-01) denotes a shared-call functional interface. A spelling for mut-call and once-call anonymous forms is unresolved (OQ-29); until it is resolved, a callback that must be mut-call or once-call uses a nominal functional interface whose SAM carries the receiver mode.

### CLO-04 — Lambdas are values of functional interfaces

A lambda literal `(p1, p2, …) -> body` is a value whose type is a functional interface — anonymous (FN-01) or nominal — selected by:

- the expected type at the position where the lambda appears (target typing); or
- inference from the body together with any explicit parameter annotations otherwise.

The lambda's capture mode (CLO-01) fixes the receiver mode of its synthesized SAM (FN-03), and therefore its call mode (CLO-03): read → shared-call, mutate → mut-call, consume → once-call. A lambda is a value of a functional-interface type of call mode `M` iff the lambda's own call mode is no greater than `M` under the order `shared-call < mut-call < once-call`.

A less-demanding lambda satisfies a more-demanding type — a read lambda is a value of a shared-call, mut-call, or once-call interface; a mutate lambda is a value of a mut-call or once-call interface — never the reverse. This is the `Fn ⊆ FnMut ⊆ FnOnce` containment, expressed through the SAM's receiver mode.

| Lambda capture mode | Lambda call mode | shared-call type | mut-call type | once-call type |
|---|---|:---:|:---:|:---:|
| Read    | shared-call | accept | accept | accept |
| Mutate  | mut-call    | reject | accept | accept |
| Consume | once-call   | reject | reject | accept |

Assignability concerns the value only. Whether the binding that receives the value can invoke its SAM is the separate question settled by CLO-03 (binding mode versus call mode).

```laterita
@mut interface Doubler { @mutating int apply(int x); }   // mut-call

@mut int calls = 0;
Doubler counting = (x) -> { calls = calls + 1; return x * 2; };  // mutate lambda → mut-call type: OK
Doubler pure     = (x) -> x * 2;                                 // read lambda → shared-call ≤ mut-call: OK

// Doubler bad   = (x) -> { give(resource); return x; };          // ERROR: a consume lambda (once-call)
//                                                               //        is not a value of a mut-call type
```

### CLO-05 — Override variance for FI parameters

A functional-interface parameter is an ordinary parameter and MOVE-10 (override variance) applies, with one inversion noted below.

**Override variance — inverted for slot modes.** MOVE-10 says `@mut` on an ordinary parameter is contravariant: an override may *drop* `@mut` because the override demands less of the caller. For an FI slot the relationship is reversed — an override may **add** `@mut` to a bare slot, but not remove it. The reason: a `@mut` slot accepts strictly more closures (read and mutate) than a bare slot (read only), and an override must continue to accept every closure the inherited declaration accepted. The `@take` axis remains invariant per MOVE-10: an override that flipped `@take` on the slot would either refuse closures the inherited contract accepted or accept closures the inherited contract rejected.

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

### STR-07 — `String` is a value class

`String` is a value class (MUT-03): it declares no `@mut` fields and no `@mutating` methods, and none can be introduced by extension (MUT-05). A binding or field may still be declared `@mut String` — `@mut` then grants reassignment per BIND-03 — but no `String` method mutates the value in place. Bulk text construction belongs in `StringBuilder`, which is `@mut`.

```laterita
@mut String s = readLine();       // declaration permitted: @mut grants reassignment
s = readLine();                   // OK: reassigning a @mut binding
// String is a value class — no in-place mutation method can exist
```

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

### STR-08 — Default receiver mode of `String` methods is borrow

Methods declared on `String` borrow the receiver unless the signature marks otherwise. Methods that consume the receiver (`@consuming`) are rare and explicitly marked; per STR-07, no `@mutating` methods exist.

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
@mut class T[] {
    @mutating @bound Pair<@bound @mut T[], @bound @mut T[]> splitAt(int mid);

    @mutating void forEachChunk(int chunkSize,
            @mut (@mut T[]) -> void body);

    @mutating void forEachChunkExact(int chunkSize,
            @mut (@mut T[]) -> void body);

    @consuming Pair<T[], T[]> splitOff(int mid);
}
```

`splitAt` re-borrows the receiver (BIND-06); the returned record is `@bound` to the receiver's source and the receiver is frozen until both halves expire (LIFE-03). `forEachChunkExact` skips the trailing partial chunk; `forEachChunk` does not. Each chunk passed to `body` is a mut slice of the receiver whose borrow expires at the call's return, so successive chunks are pairwise disjoint by construction. No `@unsafe` is required: each operation reduces to ordinary slice expressions covered by MOVE-06. Fold-style reductions express by capturing a `@mut` local in the body lambda; no dedicated reducer primitive is provided.

`splitOff` consumes the receiver (BIND-07) and returns two owning `T[]` halves spanning `[0, mid)` and `[mid, length)`, sharing the underlying allocation through an internal refcount (freed when the last half drops). Each half is a regular `T[]` supporting the full ARR-01 surface. A different method name from `splitAt` is used because the receiver mode differs; two same-name methods that differed only in receiver-mode annotations would be a duplicate declaration (the annotations are not part of the overload signature per MOVE-03).

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

    public static <T> @mut @bound Pair<@bound @mut T[], @bound @mut T[]> splitAt(
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
- `@mut @bound Pair<@bound @mut T[], @bound @mut T[]>` — pair of mutable borrows, returned by `splitAt`. The enclosing binding is `@bound` because the instance contains `@bound`-substituted parameters (BIND-08), and the `@mut` element marks are admitted because the `Pair` is itself `@mut` (BIND-10); its lifetime is the intersection of the field sources (LIFE-03).

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

### STD-10 — `ReentrantLock`

A reentrant mutual-exclusion primitive without a protected value: the lock alone. Unlike `Mutex<T>` (STD-09), `ReentrantLock` owns no data, hands out no borrow of protected state, and may be re-entered by the same thread; the data it guards lives in fields of the surrounding object and is reached through ordinary `@mut` access (BIND-06). Acquisition returns a `LockGuard` (STD-11) whose `onDrop` releases the lock — forgetting to unlock is structurally impossible (DROP-01). Method names and shapes mirror `java.util.concurrent.locks.ReentrantLock`.

**Constructor.** `new ReentrantLock()` — creates an unlocked, unfair lock. Fairness is not configurable on this surface.

**Acquisition.**
- `@bound LockGuard lock() throws InterruptedException` — blocks until the lock is held, returns a guard bound to this lock. Reentrant: the same thread acquiring twice receives two guards; the lock is released only after both are dropped. Is an interruption point (THR-04).
- `@bound LockGuard? tryLock() throws InterruptedException` — non-blocking; returns the guard or `null` if another thread holds the lock.
- `@bound LockGuard? tryLock(long timeout, TimeUnit unit) throws InterruptedException` — timed variant.

**Condition variables.** `@bound Condition newCondition()` — returns a fresh `Condition` (STD-12) bound to this lock. May be called any number of times; one lock can pair with multiple conditions (the classic bounded-buffer "not full" / "not empty" pattern).

`ReentrantLock` is `@nonlocal` per STD-07.

### STD-11 — `LockGuard`

A value witnessing that the calling thread holds a `ReentrantLock` (STD-10). Returned by `ReentrantLock.lock` / `tryLock`; not user-constructible. `@bound` to its source `ReentrantLock`. A `LockGuard` is `@local`; it cannot be borrowed across threads.

`LockGuard.onDrop()` releases one acquisition of the bound lock — at full release (no outstanding guards on the same thread), the lock becomes available to other threads.

`LockGuard` exposes nothing beyond its existence and its `@internal` `onDrop` (DROP-06). Its only role is to make scope exit equivalent to lock release.

### STD-12 — `Condition`

A condition variable bound to a `ReentrantLock` (STD-10), created by `ReentrantLock.newCondition()`. Class and method names match `java.util.concurrent.locks.Condition`; semantics match Java. The "caller must hold the bound lock" precondition remains a runtime check (`IllegalMonitorStateException`); `await` is an interruption point per THR-04.

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

The following names are introduced by this specification and must be provided by the standard library: `Rc`, `Arc`, `WeakReference`, `Cell`, `Heap`, `Mutex`, `ReentrantLock`, `LockGuard`, `Condition`, `PoisonedException`. The `Thread` type and `InterruptedException` are reused from the Java standard library per THR-01 and THR-08; `java.util.Objects.requireNonNull` is reused as the `.java`-mode null assertion per LAT-04. Anonymous functional interfaces are structural per FN-01 and require no named stdlib interfaces.

The identifier `onDrop` is reserved as the language-orchestrated lifecycle hook (DROP-01).

**Laterita requires no new keywords or constructs.** The ownership, lifetime, mutability, cleanup, and visibility concepts are expressed as annotations and static method calls; some non-Java syntactic forms (`T?`, `?.`, `?:`, `!!`, `(P1,…,Pn) -> R`) and class extensions are gated to `.lat` sources per §19.
Below is a list of laterita annotations. Combinations not listed are currently not supported and won't compile.

| Annotation | `@Target` | Additional condition | Meaning | Spec rule |
|---|---|---|---|---|
| `@mut` | `TYPE` | Not supported on enum and record | Class or interface has a mutable surface | MUT-03 |
| `@mut` | `LOCAL_VARIABLE` | - | Local binding is mutable (reassignable; mutation-through when the type is `@mut`) | BIND-01 |
| `@mut` | `FIELD` | only in a `@mut` class | Field is mutable | BIND-03, MUT-04 |
| `@mut` | `PARAMETER` | on `@mut` types, or combined with `@take` | Mutable parameter (mutable borrow, or reassignable owned slot with `@take`) | MOVE-03 |
| `@mut` | `METHOD` | on `@mut` types, or combined with `@bound` | Return is a `@mut` binding | BIND-02, LIFE-02 |
| `@mut` | `TYPE_USE` | only when enclosing generic type is `@mut` | Generic type argument carries `@mut` elements | BIND-10 |
| `@mutating` | `METHOD` | - | Method mutates its receiver | BIND-05 |
| `@consuming` | `METHOD` | - | Method consumes its receiver | BIND-07 |
| `@take` | `PARAMETER` | - | Parameter receives ownership | MOVE-03 |
| `@bound` | `PARAMETER` | - | Borrow source | LIFE-02, BIND-03, BIND-08 |
| `@bound` | `FIELD` | - | Field is only borrowed (default: owned) | LIFE-02, BIND-03, BIND-08 |
| `@bound` | `METHOD` | non `void`, non `static` | return is borrowed from `this` | LIFE-02, BIND-03, BIND-08 |
| `@bound` | `TYPE_USE` | in type arguments | Instances of this type argument are only borrowed to the declared instance | LIFE-02, BIND-03, BIND-08 |
| `@internal` | `METHOD` | - | Callable only by compiler-emitted call sites | DROP-06 |
| `@unsafe` | `METHOD` | - | Private method permitted to use the ops in UNS-02 | UNS-01 |
| `@local` | `TYPE` | - | Class instances are thread-affine | STD-07 |
| `@nonlocal` | `TYPE` | with `@unsafe` | Overrides inferred `@local` on a class | STD-07 |
| `@Nullable` | `TYPE_USE` | - | Type admits `null` (`.lat` spelling: `T?`) | NULL-02 |

The annotations are declared in `laterita.lang.annotation`. Stdlib static methods that carry laterita-specific semantics live on `laterita.lang.Intrinsics` and are normally statically imported so call sites read `give(x)` and `broken()` without a qualifier:

| Intrinsic | Meaning | Spec rule |
|---|---|---|
| `Intrinsics.give(x)` | Explicitly removes ownership from `x` | MOVE-02, MOVE-08 |
| `Intrinsics.broken(reason?)` | Compilation fails if an execution path would lead to this statement | UNR-01 |

To `javac` the annotations are ordinary annotations and the intrinsics ordinary static method calls; the laterita compiler attaches the additional semantics specified in the rules above.

Type inference uses Java's `var` keyword. In laterita mode every binding is immutable unless annotated `@mut`, so `var x = expr` is immutable; `@mut var x = expr` is mutable. Java's `final` locks reassignment on a `@mut` binding (BIND-01); it is otherwise redundant.

Java's `synchronized` keyword is not supported: there is no per-object intrinsic monitor, no `synchronized` method modifier, and no `synchronized(obj) { ... }` block. Mutual exclusion is provided exclusively through `Mutex<T>` (STD-09) for data-bound locking and `ReentrantLock` + `Condition` (STD-10, STD-12) for the data-less / multi-condition cases. The associated `Object.wait()`/`notify()`/`notifyAll()` methods are likewise not provided. Condition-variable-style coordination uses `Condition` (STD-12) bound to a `ReentrantLock`.

Java's existing keywords and their meanings are otherwise preserved unless explicitly modified by this specification.

---

## 19. `.lat` Surface Forms

A laterita source file uses one of two extensions (COMP-06): `.java`, the Java-compatible subset that `javac` parses, and `.lat`, which additionally admits the forms specified in this section. Every rule outside this section belongs to the `.java`-compatible surface.

### LAT-00 — The `.lat` surface is pure syntactic sugar

Forms LAT-01 through LAT-05 are syntactic sugar: each has an exact `.java`-surface equivalent into which the compiler desugars it before any type, ownership, lifetime, or runtime analysis. Consequently:

- Any `.lat` source built from LAT-01–LAT-05 can be mechanically rewritten to an equivalent `.java` source and the reverse; this rewrite is total and meaning-preserving.
- A program's meaning over the LAT-01–LAT-05 forms never depends on its file extension. Whether a declaration was written in `.lat` or `.java` is not part of its identity (COMP-06).
- A proposed sugar form that cannot be expressed as a desugaring to the `.java` surface does not belong in this section. A construct that carries its own semantics belongs in the core spec as a `.java`-surface rule, expressed through the annotation and intrinsic surface of §18.

A `.lat` source may additionally use the structural extensions listed at the end of this section (currently STR-01 only) — rules whose meaning the core spec already defines but whose surface `javac` cannot parse or compile and which therefore cannot appear in `.java`. Such extensions are explicitly enumerated; the default assumption for new `.lat` forms remains "pure sugar".

The sugar forms are listed below with their `.java`-surface desugarings.

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

### Structural extensions

Rules below appear in `.lat` because `javac` cannot parse or compile their source form. They have no desugaring to the `.java` surface; the `.java` analog is "this declaration is not expressible". The laterita compiler accepts them only in `.lat` units.

### STR-01 — `String` is a normal class

In `.lat`, `String` is not `final` and classes may extend it: `class Email extends String`. The platform's `java.lang.String` is declared `final`, so `javac` rejects this construct and it cannot appear in `.java`. Subclasses participate in the per-binding owned-vs-borrowed tracking (STR-02), inherit the value-class restrictions of STR-07 (no `@mutating` methods can be introduced), and are constrained by all other rules in §12.
