# Laterita - Language Specification

This document specifies the normative requirements that a Laterita compiler and standard library must satisfy.
Each requirement carries a mnemonic code for cross-reference.

Every topic except `LAT` specifies the Java-compatible surface.
Every rule there is expressible as annotated `.java` source that `javac` parses (COMP-06).
The `LAT` topic specifies the `.lat` surface forms.
Those are syntactic sugar that desugars to the Java-compatible surface and adds no semantics of its own (LAT-00).

Codes are grouped by area:
`OWN` (ownership),
`LIFE` (lifetimes),
`MUT` (mutability),
`HIER` (class hierarchy and override),
`TARG` (annotations in generic type arguments),
`STAT` (static storage),
`NULL` (optionality),
`DROP` (cleanup),
`OBJ` (object copying),
`DES` (destruction),
`UNR` (unreachability),
`STR` (strings),
`ARR` (arrays),
`FN` (functional interfaces),
`CLO` (closures),
`EXC` (exceptions),
`UNS` (unsafe),
`STD` (standard library types),
`THR` (threads),
`COMP` (compilation model),
`RESV` (reserved names),
`LAT` (`.lat` surface forms),
`NABI` (native ABI),
`GEN` (code generation annotations).

---

## OWN — Ownership

This section specifies how values are owned and borrowed, and how ownership transfers across local variables, parameters, returns, and fields.

### OWN-01 - Owned and borrowed values

Each value has one **owner**: the variable that drops it (DROP-01) at scope exit.
Other variables holding the same value are **borrows**, bounded by the owner's lifetime (LIFE-01).

### OWN-02 - A local follows its RHS

A local owns or borrows its value depending on its initializer.

- A **producer expression** (call, constructor, literal) yields an owner.
- A **naming RHS** (the name of an existing variable) yields a shared borrow of that source.

```java
String a = makeString();    // owner: RHS is a producer
String b = a;               // borrow: RHS names a
print(a);                   // OK
print(b);                   // OK
```

Reassigning a non-`final` local (MUT-02) re-applies this rule to the new RHS.
The slot's owned-or-borrowed status, and for a borrow its source, are taken from the most recent assignment and checked flow-sensitively (LIFE-01).
This is independent of `@mut`, which governs only mutation through the value, not the slot.

### OWN-03 - Borrow exclusivity

A value's borrow state at any point is one of:

- **no borrows** — the owner has unobstructed access (subject to MUT-01).
- **shared borrows** — any number of readers may coexist (including the owner). No mutation is allowed, not even by the owner.
- **one mutable borrow** — that borrow has exclusive access. The owner is frozen until the borrow ends.

A mutable borrow writes through the value: it requires the source to be `@mut` (MUT-01) or the borrow to sit inside a `@mutating` method of the same object.
A borrow of a value also borrows the slot that holds it, the source variable its RHS names (OWN-02).
While any borrow of `x` is live, reassigning `x` is therefore excluded: it would pull the value out from under the borrow, and where `x` owns the value, drop it (DROP-01).
This exclusivity is subject to the disjoint-borrow exceptions of OWN-04 and OWN-05.
The compiler must reject programs that violate this.

### OWN-04 - Disjoint field borrows are permitted

Two simultaneous borrows of statically distinct fields of the same value are non-aliasing.
They are permitted, including when both are mutable.
The compiler performs this disjointness analysis.

```java
@mut class Tree { @mut Node left; @mut Node right; }
@mut Tree t = makeTree();
@mut Node l = t.left;
@mut Node r = t.right;       // OK: disjoint fields, both mutable borrows
```

### OWN-05 - Disjoint slice borrows are permitted

Two simultaneous borrows of array slices with provably disjoint index ranges must be permitted.
The compiler proves disjointness for constant ranges and for ranges related by simple arithmetic.
For arbitrary computed ranges, ARR-01 supplies the disjointness witness.
That reduces to ordinary slice expressions this rule covers.

```java
@mut int[] data = new int[100];
@mut int[] left  = data.slice(0, 50);
@mut int[] right = data.slice(50, 100);  // OK: provably disjoint
```

### OWN-06 - Destruction transfers an owned object's fields to its scope

Only an owned object with no borrow of it outstanding can be destructed (OWN-03, LIFE-01).
Destruction consumes the object rather than mutating it, so it requires ownership but not `@mut`.
At its first destructing operation (DES) each of its fields transfers to the scope where the destruction was initiated.
A formerly owned field becomes an independent value owned by that scope.
A `@borrow` field transfers as a `@bound` value still bound to its original source (OWN-09, LIFE-03), a borrow carried out unchanged rather than converted to ownership.

### OWN-07 - An unowned value drops at end of statement

An owned value lives only as long as some owner holds it: a local, a field, a return, or a `@take` parameter (OWN-13).
A value with no owner — a function result the caller doesn't store, for example — drops at the end of the enclosing statement (DROP-01).

`give` is the ordinary stdlib helper

```java
public static <T> T give(@take T t) { return t; }   // laterita.lang.Intrinsics
```

normally statically imported.
`give(x)` consumes `x` via `@take` and returns its owned value (OWN-16).
A stored result — `var b = give(a);` — lives on in the new owner.
A statement-form result — `give(x);` — drops at the semicolon, running its `onDrop()` immediately.

```java
var worker = Thread.ofVirtual().start(() -> task());
if (changedMyMind()) { give(worker); }   // worker consumed; returned value drops here
```

### OWN-08 - Fields are owned by default

A bare field declaration `T x;` declares storage that owns its value.
The field is dropped with the enclosing instance (DROP-05).

### OWN-09 - `@borrow` field declares a borrow slot, instance must be `@bound`

`@borrow` on a field or record component declares that the field holds a borrow rather than an owned value.
An instance of a class with any `@borrow` field can only be produced as a `@bound` value.
This includes the case where the `@borrow` arises via a `@borrow`-substituted generic argument (TARG-01).
`@bound` marks the value as a borrow rather than owned.
The producer fixes the initial sources.
A later call receiving a `@take @borrow` parameter adds its source from that call onward (OWN-21, LIFE-03).
See OWN-17 and OWN-18 for returns, and LIFE-02 for intersection across multiple sources.

```java
record EntryView<K, V>(@borrow K key, @borrow V value) {}   // instances must be @bound
```

### OWN-10 - `@take` is rejected on fields and locals

`@take` describes a transfer of ownership into a parameter slot at a call site.
It has no meaning on a field, a record component, or a local declaration.
A field either owns (the default) or `@borrow`s the value (OWN-08, OWN-09).
A local's mode follows its RHS (OWN-02).

### OWN-11 - Constructor initializes every field exactly once

Every field of a class must be assigned exactly once on every path through every constructor, before any method on `this` is invoked.
`final` fields, with or without `@mut`, can be assigned only in constructors.
Non-`final` fields can also be reassigned later through a `@mut` receiver of a `@mut` class (MUT-07b), in practice in a `@mutating` method (MUT-08) or an `onDrop()` body (MUT-10).

### OWN-12 - Record components follow field rules

A record component is a field for the purposes of OWN-08 through OWN-10.
It is owned by default, optionally `@borrow`, never `@take`.

### OWN-13 - Parameter ownership modes

A parameter declares whether it receives a borrow or takes ownership.

| Form | Meaning |
|---|---|
| `T name` | parameter receives a shared borrow |
| `@take T name` | parameter receives ownership (moved in) |

```java
void inspect(String s);          // borrows s
void store(@take String s);      // takes ownership of s
```

### OWN-14 - Call-site argument forms

A **bare argument** (a variable name) fills a bare parameter with a shared borrow for the duration of the call.
It fills a `@take` parameter with an implicit ownership transfer.
Explicit `give(arg)` is the same operation written for clarity.
A **temporary expression** (call result, constructor, literal) is owned at the call site and fills either parameter form.

Illegal cases:

- `give(arg)` to a bare parameter. The caller asks to transfer, but the function will not accept ownership.
- A bare argument holding only a borrow to a `@take` parameter whose type is owned.
There is no ownership to give.
A `@take @borrow` parameter instead expects the borrow itself (TARG-05).

```java
var name = makeName();
inspect(name);              // OK: borrow
inspect(makeName());        // OK: borrow of a temporary
store(name);                // OK: implicit transfer, name no longer usable
store(makeName());          // OK: temporary moved in
inspect(give(name));        // ERROR: inspect borrows, do not transfer
```

Laterita annotations are not part of the Java overload signature.
Two same-name methods differing only in `@take`, `@mut`, `@bound`, `@borrow`, `@mutating`, or `@consuming` are a duplicate declaration.
APIs needing both borrow and consume shapes use distinct names (e.g. `splitAt` and `splitOff`, ARR-01).

### OWN-15 - `@consuming` consumes the receiver

A method annotated `@consuming` consumes its receiver.
The body owns `this`.
It runs therefore exactly one of the special operations that are normally only permitted to the owner:

- return `this` un-`@bound`.
- hand `this` to another method as a `@take` parameter.
- call another `@consuming` method on `this`.
- drop `this` at the end of the method by doing nothing with it.
- destruct `this` (DES topic) and own its fields as independent objects.
  All the above operations can then be performed on those fields independently.

After the call returns, the caller's receiver is consumed.
Subsequent uses are rejected.

`@consuming` sits in modifier position alongside `public`, `final`, `@mutating`.
It composes with `@mutating` (MUT-08).
A method that both mutates and consumes carries both.
`@consuming` calls require an owned receiver.
The call site needs no `give(...)` wrapper.

```java
class Connection {
    Heap<DbConn> conn;
    public @consuming void close() {
        this.conn.flush();
    }
}

var c = openConnection();
c.close();                  // OK: c owned, consumed by close()
c.use();                    // ERROR: c consumed
```

### OWN-16 - An un-`@bound` return is owned

A return type without `@bound` means the function returns an owned value.
`return x;` of an owner moves it.
`return give(x);` is accepted as the explicit form.

```java
String upperCase(String s);     // owned return
```

### OWN-17 - `@bound` on a parameter binds the return to that parameter

`@bound` on a parameter declares that the function returns a borrow whose source is that parameter.
Valid only on a non-`void` return.

```java
String firstWord(@bound String s) {              // returned borrow bound to s
    return s.substring(0, s.indexOf(' '));
}
```

### OWN-18 - `@bound` on a return binds the return to `this`

`@bound` on a return declares that the function returns a borrow whose source is `this`.
Valid only on instance methods (not `static`).

```java
class Cache {
    Map<String, Entry> entries;
    @bound Entry get(String key) {               // returned borrow bound to this
        return entries.get(key);
    }
}
```

### OWN-19 - Unmarked sources cannot contribute to a returned borrow

A body that returns a borrow tied to a source not marked `@bound` is a compile error.
The diagnostic identifies the source and suggests adding `@bound`.

```java
String prefixOf(@bound String text, String pattern) {
    return text.substring(0, pattern.length());  // bound to text only, pattern unmarked
}
```

### OWN-20 - Owned/borrowed mismatch is an error

The compiler reports an error when:

- the body returns a borrow but the signature declares no `@bound` source, or
- the body returns an owned value but the signature declares a `@bound` source.

The diagnostic identifies the contributing source the body actually uses.

### OWN-21 - A `@take @borrow` parameter caps `this` at its source

A `@take @borrow` parameter receives a borrow and retains it.
The cap is part of the signature: from the call onward the parameter's source is a source of `this` (LIFE-02, LIFE-03), so `this` may not outlive it (LIFE-01), whether or not the body actually stores the borrow.
Because the cap names `this`, the form is meaningful only on instance methods: a retained borrow may be stored only into a `@borrow` field of `this`, and on a `static` method the form is rejected.
It is the same caller-side check TARG-05 applies to a generic `@take T` parameter given a borrowed argument, and the directly written spelling of that form.
Storing a borrow into a `@borrow` field of `this` requires this parameter form, and the assignment itself additionally requires a `@mut` receiver (MUT-07b), but declaring the parameter does not: a non-mutating method may declare `@take @borrow` and merely narrow the caller's `this`.
On a `@borrowCapped` class the source must stay live until the instance's scope exit, not only its last use (LIFE-04).
A bare `@borrow` parameter without `@take` retains nothing and equals a plain borrow parameter (OWN-13).
A constructor needs no marked parameter: the borrows it stores into the instance's `@borrow` fields are the initial sources the producer fixes (OWN-09, LIFE-03), the construction-time counterpart of this rule.

```java
@mut class Cursor {
    @borrow Buffer buf;
    @mutating void retarget(@take @borrow Buffer b) { this.buf = b; }   // stores the borrow into this
}

@mut Cursor cur = openOn(mainBuf);
Buffer scratch = makeBuffer();
cur.retarget(scratch);     // from here cur may not outlive scratch (LIFE-01, LIFE-02)
```

---

## LIFE — Lifetimes

### LIFE-01 - A borrow may not outlive its source

The compiler must reject any program in which a borrow is used after its source has been dropped or moved.

### LIFE-02 - Multiple `@bound` sources intersect

When more than one source is marked `@bound` (any combination of parameters and the receiver), the returned borrow's lifetime is the intersection.
It is bounded by the shortest-lived marked source.

```java
@bound String chooseLabel(@bound String fallback) {
    return prefer ? this.label : fallback;       // bound to min(this, fallback)
}
```

### LIFE-03 - A `@bound` instance intersects its `@borrow` field sources

A `@bound` instance produced from `@borrow` fields takes each field's source into LIFE-02's intersection.
The instance is usable only while every field's source remains live.
A borrow received by a `@take @borrow` parameter (OWN-21) joins the same intersection from that call onward, whether or not the body stores it.

```java
record EntryView<K, V>(@borrow K key, @borrow V value) {}

EntryView<String, Integer> view = new EntryView<>(name, count);
// view's lifetime = min(name, count)
```

### LIFE-04 - `@borrowCapped` caps an instance's lifetime within its borrow sources

`@borrowCapped` is a class-level annotation, permitted on any class declaration.
It declares that every `@borrow` field's source must remain live until the instance goes out of scope, not only until the instance's last explicit use (LIFE-03).

The obligation is a property of the value, like the `@bound` mode it refines: it is fixed by the instance's class at construction and preserved across assignment and upcast.

`@borrowCapped` is inherited.
A subclass of a `@borrowCapped` class is `@borrowCapped` and cannot remove the marker.
A subclass may add `@borrowCapped` that its superclass lacks.

The compiler must reject any program in which a `@borrowCapped` instance's scope exit is reached after a source of one of its `@borrow` fields has been dropped or moved.

---

## MUT — Mutability

### MUT-01 - `@mut` is the unified referent-mutability marker

`@mut` denotes referent mutability everywhere it appears: locals (MUT-02), fields (MUT-07a), parameters (MUT-04), and return types.
On a binding it grants *referent mutability*: the right to mutate the value through the binding, calling `@mutating` methods or writing through it.
This is orthogonal to reassignment of the binding itself, the *slot*, which is on by default and locked by `final` (MUT-02, MUT-03).
On a class or interface declaration it marks a mutable surface (MUT-05).
A method declares mutation of its receiver with `@mutating` (MUT-08).
These are the only surface forms for mutability.

### MUT-02 - Local declaration forms

A local has two independent capabilities, each with its own marker.

- Reassignment of the slot, rebinding the name to another value, is granted by default and locked by `final`.
- Mutation through the referent, calling `@mutating` methods or mutating state reached through it, is off by default and granted by `@mut`.

| Form | Reassign slot | Mutate through |
|---|---|---|
| `final T x = e` | no | no |
| `final @mut T x = e` | no | yes |
| `T x = e` | yes | no |
| `@mut T x = e` | yes | yes |

```java
final List<String> a = makeList();              // neither: no reassignment, no a.add()
final @mut List<String> b = new ArrayList<>();  // mutate-through only: b.add() OK, b = ... rejected
List<String> c = makeList();                    // reassign only: c = ... OK, c.add() rejected
@mut List<String> d = new ArrayList<>();        // both: d = ... and d.add() OK
```

A non-`final` local that is never reassigned is *effectively final*: its slot is fixed, so borrow analysis (OWN-02, OWN-03) treats it as locked.
Only an effectively final local may be captured by a closure (CLO-01).
Reassigning a slot that owns its value drops the previous value first (DROP-01).

Java's `var` infers the type and changes neither axis.
`var x = e` is reassignable and not mutate-through.
`@mut var x = e` adds mutate-through.
`final var x = e` locks the slot.

```java
var count = items.size();           // reassignable, not mutate-through
@mut var sb = new StringBuilder();  // mutate-through: sb.append(...) OK
final var pi = 3.14159;             // locked
```

### MUT-03 - `final` locks the slot, orthogonal to `@mut`

`final` is Java's slot lock.
It forbids reassignment and nothing else.
It composes with `@mut` along the orthogonal axis (MUT-02): `final` governs the slot, `@mut` governs the referent.

```java
final @mut Properties config = loadConfig();
config.setProperty("verbose", "true");   // OK: @mut grants mutate-through
config = loadConfig();                   // ERROR: final locks the slot
```

Because the bare slot is reassignable by default, `final` is never redundant on a local: it removes the reassignment the slot would otherwise grant.
The slot axis therefore behaves exactly as Java's `final` does.

### MUT-04 - Parameter mutability modes

Extending OWN-13, a parameter slot is always `final`: the parameter name cannot be reassigned in the body.
A `@take` parameter may still be moved onward with `give` (OWN-07), which consumes the value rather than rebinding the slot.
`@mut` grants referent mutability: a mutable borrow, or, with `@take`, ownership with mutate-through.

| Form | Meaning |
|---|---|
| `@mut T name` | parameter receives a mutable borrow |
| `@take @mut T name` | parameter receives ownership and may mutate through it |

A bare argument passed to a `@mut` parameter produces a mutable borrow.
The source variable must be `@mut` (owned or mutably borrowed).
A temporary fills a `@mut` parameter directly.
A non-`@mut` source passed to a `@mut` parameter is rejected.
There is no mutable access to lend.

### MUT-05 - `@mut` class declaration

A class, abstract class, or interface may be declared `@mut`: `@mut class C`, `@mut abstract class C`, `@mut interface I`.
The marker declares a *mutable surface*.
`@mut` fields may be declared in it (MUT-07a, classes only).
`@mutating` methods may be declared on it (MUT-08).

A type not declared `@mut` is a *value class*.
No `@mutating` method may be declared on it.
In a non-`@mut` class, all fields are treated like `final` (MUT-07b).
A non-`@mut` interface may declare only methods without `@mutating`.
Because no mutation is observable through a value-class variable, a copy of a value-class instance is interchangeable with a borrow under the same lifetime constraints.
The compiler may substitute either.

Value classes are non-`@local` (STD-07) unless they hold a transitively `@local` field (`Rc<T>`, `Cell<T>`).
Those primitives are themselves value classes whose hidden mutation makes them thread-affine.

`Object` is `@mut`.
`Number` is a value class.
Therefore `Integer`, `Long`, `Float`, and the other boxed numeric types are value classes.

### MUT-06 - `@mut` is rejected on `record` and `enum`

A `record` and an `enum` may not carry `@mut`.
Both are value classes by construction.

### MUT-07a - `@mut` field grants mutation through the referent

`@mut` on a field grants *referent mutability*: mutation through the field, by calling `@mutating` methods on its value or writing through it.
Without `@mut` a field cannot be mutated through.
A `@mut` field may be *declared* only in a class declared `@mut`.
A value class may *inherit* a `@mut` field from a `@mut` ancestor (HIER-03) but may not declare one.
The declared type is unrestricted.
On a value-class-typed field `@mut` grants nothing observable, since the referent has no mutating surface, and is redundant.

### MUT-07b - Non-`final` field is reassignable through a `@mut` receiver

Reassigning a field, rebinding its slot, is the slot axis (MUT-02): granted by default and locked by `final`.
Reassigning a field mutates the enclosing instance, so a non-`final` field is reassignable only where the class is `@mut` and the receiver is `@mut` (MUT-08, MUT-10).
For the receiver `this` that means a constructor, a `@mutating` method, or an `onDrop()` body (MUT-10).
Through any other `@mut` variable the write follows ordinary Java member access.
A value class has no `@mut` receiver after construction (MUT-05, MUT-10), so its fields are treated like `final`: set once in a constructor and never reassigned.

The two axes are independent, giving four field forms.

| Field form | Reassign (receiver `@mut`) | Mutate through |
|---|---|---|
| `final T f` | no | no |
| `final @mut T f` | no | yes |
| `T f` | yes | no |
| `@mut T f` | yes | yes |

```java
@mut class User {
    final String id;                    // set once in the constructor, never reassigned
    String name;                        // reassignable in a @mutating method, not mutated through
    int loginCount;                     // reassignable; @mut would be redundant on an int
    final @mut List<Session> sessions;  // sessions.add() OK, sessions = ... rejected
}
```

### MUT-08 - `@mutating` declares receiver mutation

A method annotated `@mutating` may mutate `this`.
It may reassign the instance's non-`final` fields, mutate through its `@mut` fields, and call other `@mutating` methods on `this`.
A method without it cannot.
`@mutating` sits in modifier position.
It is orthogonal to `@consuming` (OWN-15).
A method that both mutates and consumes carries both.

`@mutating` may be declared only on a `@mut` class or `@mut` interface (MUT-05).
Override variance is HIER-05.

```java
@mut class Counter {
    int n;                                            // reassignable; @mut redundant on an int
    public int read()                    { return n; }
    public @mutating void inc()          { n = n + 1; }
    public final @mutating void reset()  { n = 0; }
}
```

### MUT-09 - Immutability is transitive through borrows

A shared (immutable) borrow grants no mutation rights regardless of any `@mut` markers on fields reached through it.
Mutation through a borrow requires the borrow itself to be `@mut`.

### MUT-10 - Calling `@mutating` methods

A `@mutating` method is callable on a receiver only when both conditions hold, each checked statically:

- the receiver variable is `@mut`, and
- the receiver's static type is a `@mut` class or `@mut` interface.

When the static type is a `@mut` interface, the `@mut`-variable requirement together with HIER-04 guarantees the dynamic class is `@mut`.

A constructor is exempt.
Within a constructor, `@mutating` methods may be called on `this` and inherited non-`final` fields assigned regardless of class kind.
This is the initialization phase.
The value-class freeze takes effect when the constructor returns.

An `onDrop()` body (DROP-05) is exempt in the same way.
Its receiver is `@mut` regardless of class kind, so on a `@mut` class it may reassign non-`final` fields, mutate through `@mut` fields, and call `@mutating` methods on `this`.
This is the teardown phase.
The value-class freeze remains in effect, so on a value class every field is immutable and the body is read-only.

```java
var counter = new Counter();
counter.inc();              // ERROR: counter is not @mut
@mut var c2 = new Counter();
c2.inc();                   // OK
```

### MUT-11 - Interior mutability requires `Cell<T>`

A type that needs to mutate its contents through a bare receiver must hold those contents inside `Cell<T>`.
This is the only mechanism that bypasses MUT-09.
`Cell<T>` is an unsafe primitive (UNS-02).

---

## HIER — Class Hierarchy and Override

### HIER-01 - `@mut` class extends only `@mut`

A class declared `@mut` may extend only a `@mut` class.
Every superclass of a `@mut` class is itself `@mut`, up to `Object`.

### HIER-02 - Value class extends either kind, freeze propagates

A value class may extend a class of either kind.
Once a class in a hierarchy is a value class, every subclass of it is a value class.

### HIER-03 - Value subclass of a `@mut` ancestor is a frozen view

A value class extending a `@mut` class inherits its ancestors' `@mut` fields and `@mutating` methods.
The inherited `@mutating` methods are not callable on the value class (MUT-10).
The value class is a frozen view of the inherited surface.
This is the mechanism for deriving an immutable variant of a mutable class.
Examples include a collection, a configuration holder, or a builder, derived without re-declaring its API.

```java
@mut class Counter {
    int n;
    Counter(int start) { this.n = start; }
    @mutating void inc() { n = n + 1; }
    int read()           { return n; }
}

class FrozenCounter extends Counter {
    FrozenCounter(int start) { super(start); }
}

var fc = new FrozenCounter(5);
fc.read();      // OK
fc.inc();       // ERROR: inc is @mutating, FrozenCounter is a value class
```

### HIER-04 - `@mut` access is not obtainable by widening

Widening a value-class instance to one of its `@mut` supertypes (class or interface) never produces a `@mut` value.
The widened value may not initialize, be assigned to, or be passed to a `@mut` variable, parameter, or field.
The cast `(@mut Super) v` is rejected when `v`'s static type is a value class.
Widening to a bare (immutable) variable of the supertype remains permitted.

Together with HIER-01 this guarantees that any `@mut` variable whose static type is a `@mut` class or interface refers to an instance whose dynamic class is `@mut`.
That is what makes MUT-10's static check sound.
`@mut` access originates only at construction of a `@mut` class.
It propagates only through `@mut` variables, parameters, returns, and fields.

```java
Counter view   = new FrozenCounter(5);    // OK: widens to bare Counter
@mut Counter m = new FrozenCounter(5);    // ERROR (HIER-04)
FrozenCounter fc = new FrozenCounter(5);
@mut Counter bad = (@mut Counter) fc;     // ERROR (HIER-04)
```

### HIER-05 - Override variance

An override of an inherited method (subclass override or interface implementation) may **demand less of its caller** and **guarantee more to its caller**, but never the reverse.

- **Parameters** and **the receiver** describe what the method demands. An override may demand less.
- **The return** describes what the method gives back. An override may give more.

| Annotation | Position | Override may drop | Override may add | Reason |
|---|---|---|---|---|
| `@take` | parameter | ✗ | ✗ | Either direction breaks the caller's transfer expectation |
| `@mut` | parameter | ✓ | ✗ | Override needs only shared access. Adding `@mut` rejects shared-borrow callers |
| `@bound` | parameter | ✓ (jointly with return) | ✗ | Source for a return borrow. Dropping requires the return to drop `@bound` too |
| `@bound` | return | ✓ | ✗ | Owned return is a stronger guarantee than receiver-bound |
| `@mutating` | method | ✓ | ✗ | Drops the `@mut` demand on the receiver |
| `@consuming` | method | ✓ (to `@mutating` or bare) | ✗ | Drops the ownership demand on the receiver |
| `@mut` | class | ✓ (value subclass of `@mut` parent) | ✗ | HIER-02 |
| Call mode of an FI slot | parameter (FI type) | ✗ | ✓ (strengthen) | A stronger slot accepts strictly more closures (CLO-05) |

The FI call-mode row inverts surface direction because what is being relaxed is *closure-acceptance*.
A stronger call mode (bare to `@mutating` to `@consuming`) accepts strictly more closures.
Every closure the base accepted remains accepted.

```java
interface Visitor {
    void visit(@mut Node n);
    @bound String describe(@bound Node n);
}

class CountingVisitor implements Visitor {
    @Override void visit(Node n) { ... }                       // OK: drops @mut
    @Override String describe(Node n) { return "counting"; }   // OK: drops @bound jointly, returns owned
}

interface Reader { void read(Node n); }

class BadReader implements Reader {
    @Override void read(@mut Node n) { ... }                   // ERROR: adds @mut, rejects shared callers
}
```

The rule mirrors Java's existing treatment of `throws`.
An override may declare fewer or narrower checked exceptions than the inherited signature, never more.

---

## TARG — Annotations in Generic Type Arguments

### TARG-01 - `@borrow` admitted in a type argument

`@borrow` may appear inside a generic type argument.
It declares that the values substituted for that type parameter are borrows, the same role `@borrow` plays on a field (OWN-09).
When a `@borrow`-substituted argument is stored in a field, that field becomes a `@borrow` field, so the instance can only be produced as a `@bound` value bound to the sources of the borrowed arguments (OWN-09, LIFE-03).
No struct-level lifetime parameters are introduced.
A local holding such an instance follows its initializer (OWN-02) and needs no marker.

```java
record Pair<L, R>(L left, R right) {}

Pair<String, Integer>                 p1   = new Pair<>("hello".clone(), 42);
Pair<@borrow String, @borrow Integer> view = new Pair<>(name, count);   // view bound to name, count
```

### TARG-02 - `@take` rejected in a type argument

`@take` may not appear inside a generic type argument.
It is a parameter mode that describes how a call site transfers ownership into a slot.
It is not an attribute a value carries.
As a type argument it has no referent.
`Pair<@take K, @take V>` is a compile error.
Ownership of a generic structure's contents is carried by the structure's own variable (owned vs. `@bound`).

### TARG-03 - `@mut` in a type argument requires `@mut` container

`@mut` may appear inside a generic type argument only when the enclosing generic type is itself `@mut` at that occurrence.
That is, the type of a `@mut` variable, `@mut` parameter, `@mut` field, or `@mut`/owned return.

```java
@mut List<@mut Foo> a = ...;     // OK
@mut List<Foo>      b = ...;     // OK
List<Foo>           c = ...;     // OK
List<@mut Foo>      d = ...;     // ERROR (TARG-03)
```

The restriction is a soundness requirement.
An element accessor declared `@bound E get(int i)` returns `@mut @bound Foo` when `E` is `@mut Foo`.
Producing a `@mut` element borrow re-borrows the whole container mutably (MUT-09).
By OWN-03 that is exclusive, the same receiver-reborrow pattern `splitAt` uses (ARR-01).
A *shared* `List<@mut Foo>` would let that `@mut` element borrow be drawn from each of several coexisting shared borrows of the container, aliasing the element.
Requiring the container to be `@mut` makes every `@mut` element borrow an exclusive re-borrow.
Two simultaneous element borrows are then a borrow-check error rather than aliasing.

A genuinely shared container whose elements must mutate through shared borrows still requires `Cell<T>` (STD-05).
The `@unsafe` cost is visible at the storage site.

### TARG-04 - Stacked borrow markers collapse to one borrow

`@bound` and `@borrow` are variable-mode markers, not type constructors.
They carry no "layer" to stack.
When they stack through generic substitution, in any combination such as `@bound @borrow T` or `@borrow @borrow T`, the result denotes a single borrow rather than a borrow of a borrow.
Each stacked marker contributes its source to LIFE-02's intersection.
For example, `@bound E` with source `this` (OWN-18), returned from a method on `Container<@borrow T>`, substitutes to `@bound @borrow T`, one borrow bound to both the receiver and the element's source.

```java
class ArrayList<E> {
    @mutating void add(@take E e);                    // stores a borrow (TARG-05) when E is a @borrow
    @bound E get(int index);                          // return bound to `this` (OWN-18)
}

String config = "shared";                             // long-lived owner
var list = new ArrayList<@borrow String>();           // elements are borrows, each with its own source
list.add(config);                                     // element source: `config`

{
    String line = readLine();                         // shorter-lived owner
    list.add(line);                                   // binds list to a second borrow, reduces lifetime of list (LIFE-02)

    var got = list.get(0);                            // inferred @bound @borrow String:
                                                      // one borrow, not a borrow of a borrow,
                                                      // lifetime = min(list, config, line)
}   // `line` drops here: `list` becomes unreadable (LIFE-01), but is not
    // dropped until the outer scope; its drop skips the borrowed elements (DROP-05)
```

The rule is what lets `Container<@borrow T>` compose through any method whose return is `@bound E`.
The substituted form does not introduce a "borrow of a borrow" indirection.
It accumulates lifetime constraints on a single borrow.

### TARG-05 - `@take` transfers a borrowed type argument by value

A generic `@take T` parameter monomorphized with a borrowed type argument becomes `@take @borrow T`, or `@take @mut @borrow T` for an exclusive element.
`@take` transfers the value by value into the slot.
Because a `@borrow` value is a reference, `@take @borrow` keeps the reference itself, not the value it points at, which stays owned where it was.
The cost follows copyability: a shared borrow is copied, so the caller keeps its own, and an exclusive `@mut` borrow is moved, so the caller loses access.
The transferred borrow keeps its original source (LIFE-01), so the slot's enclosing value is `@bound` (OWN-09).
A bare borrow parameter is scoped to the call (OWN-14) and cannot be stored, so a method that stores its argument keeps `@take` for every element mode.
`@take` therefore needs no degradation for borrows.
Written directly on a non-generic parameter, `@take @borrow` is the retained-borrow form of OWN-21, applying the same caller-side cap.

```java
@mut class List<T> { @mutating void add(@take T e); }

List<Foo> a;                    // add(@take Foo e): move owned in
List<@borrow Foo> b;            // add(@take @borrow Foo e): copy a shared borrow in
@mut List<@mut @borrow Foo> c;  // add(@take @mut @borrow Foo): move an exclusive borrow in
```

### TARG-06 - `@own` requires an owned type argument

A type parameter declared `@own` rejects a borrowed type argument.
`@own` is the dual of `@borrow`: `@borrow` admits a borrow in a type argument, `@own` forbids one at the type parameter.
It marks a type that must own its contents, the role a `'static` bound plays in Rust.
`Arc` (STD-02) and `Mutex` (STD-09) declare their parameter `@own`.

```java
@mut class Mutex<@own T> { /* … */ }

Mutex<Config>         ok  = new Mutex<>(loadConfig());   // owned argument
Mutex<@borrow Config> bad = /* … */;                     // ERROR (TARG-06): borrowed argument
```

### TARG-07 - A bare `T` return monomorphized to a borrow binds to its container

A method declared with a bare (owned) `T` return, monomorphized with a borrowed type argument, returns a `@bound` value instead of an owned one.
For an owned type argument the return is owned (OWN-16).
For a borrowed one the return is the borrow, bound to the receiver (OWN-18), whose lifetime already intersects every element source (LIFE-03).
This is the return-side counterpart of TARG-05.
It is the conservative choice: the returned borrow is capped at the container's lifetime rather than the element's own source, which is stricter than necessary but needs no per-element source tracking.

```java
@mut class List<T> { @mutating T remove(int i); }

List<Foo> a;          // remove(int): returns owned Foo (moved out)
List<@borrow Foo> b;  // remove(int): returns @bound Foo, bound to the list
```

---

## STAT — Static Storage

### STAT-01 - Static fields are immutable

A field declared `static` is initialized once at program start and cannot be reassigned.
`static final` is accepted for Java compatibility, but `final` is redundant.
`@mut static` is a compile error.

### STAT-02 - Const initializer or once-init wrapper

A static field's initializer must be a *const expression*.
A const expression is a literal, a reference to another const-initialized static, or a call to a constructor or function the compiler can evaluate at compile time.
The set of const-eligible operations is defined by the compiler and standard library.
At minimum it covers primitive arithmetic, string literals, and the const-eligible constructors of the synchronizing stdlib types (`Mutex<T>` per STD-09, `Arc<T>` per STD-02, and the atomic primitives).
Initializers that require runtime computation go through a once-init wrapper held in the static slot and forced at first access.

```java
static Mutex<Map<String, Session>> SESSIONS = new Mutex<>(new HashMap<>());
static Arc<Config>                 BUILTIN  = new Arc<>(Config.DEFAULT);
```

### STAT-03 - Static field type must be non-`@local`

The declared type of a static field must be non-`@local` (STD-07).
A static slot is reachable from every thread.
A `@local` type stored there would be reachable cross-thread, exactly the case `@local` exists to forbid.
`static Rc<T>`, `static Cell<T>`, and `static Heap<T>` are rejected.
Use `static Arc<T>`.

---

## DROP — Scope-Exit Cleanup

### DROP-01 — Universal `onDrop()`

Every variable triggers the drop of its value when the variable leaves scope; the drop sequence is specified by DROP-05. The cleanup hook is `onDrop()`, an `@internal` method (DROP-06) a `final` class may implement (DROP-09). A class with no implementation contributes no body to its drop sequence. No syntactic opt-in is required at the call site.

```java
{
    Rc<File> f = openFile();
    f.read();
}   // f's drop sequence runs here (compiler-emitted)
```

### DROP-02 — Reverse declaration order

Within a scope, variables are dropped in the reverse of their declaration order.
Equivalently, when several locals leave scope at the same point, the shortest-living is dropped first.

### DROP-03 — Cleanup on all exit paths

`onDrop()` must be invoked on every exit path from a scope: normal completion, return, break, continue, and exceptional unwind.

### DROP-04 — A destructed object's fields drop independently

Destruction (OWN-06) replaces an owned object with its formerly owned fields, each now an independent value owned by the scope, and ends the object's own lifetime (DES-02).
A destructed object is therefore never dropped as a whole.
Each of those fields drops at scope exit like any other owned variable (DROP-01, DROP-02), unless it has since been moved away.
The compiler records per field whether it is still owned at each exit point, a drop flag, and emits the drop only for the fields still owned there.
Implementations may optimize away drop flags when static analysis proves them constant.

### DROP-05 — Drop sequence

Dropping a value runs cleanup in the reverse of construction order. For an instance of dynamic class `C` with superclass chain `C → B → … → Object`, the compiler emits, in order:

1. `C.onDrop()` body, if implemented — only `final` classes may, per DROP-09.
2. `C`'s fields, in reverse declaration order; array elements in reverse index order.
3. Step 2 repeated for `B`, then for each superclass up to `Object`.
4. If the instance is heap-allocated, its storage is released.

Fields that are `null` (NULL-09) or `@borrow` (OWN-09) are skipped in steps 2 and 3.
Each surviving owned field is dropped recursively by this same procedure.
The step-1 body runs before any field teardown of that class.
It may read every owned field visible to it, and on a `@mut` class may reassign its non-`final` fields and mutate through its `@mut` fields (MUT-07b, MUT-10).
A value reaches this sequence only as a whole: moving a field out is destruction (OWN-06), which replaces the object with its independent fields (DROP-04) rather than dropping it as a unit, so no field is moved-out here.

```java
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

`onDrop()` is the only `@internal` method introduced by this specification. The compiler emits its invocations at scope exits (DROP-01), on destruction paths (DROP-04), on exception unwind (EXC-02), and as part of the drop sequence (DROP-05).

`@internal` is reserved for future compiler-orchestrated hooks. It is not a general-purpose access-control level; ordinary visibility scoping continues to use `public`, `protected`, `private`, and package-default.

### DROP-07 — Exceptions from `onDrop()` terminate the body, not the drop sequence

An exception propagating out of an `onDrop()` body terminates that body, but the rest of the value's drop sequence — its remaining fields and superclass fields (DROP-05 steps 2–3) and the storage release (step 4) — still runs. The exception then leaves the compiler-emitted call site through the same path a Java `finally`-block exception leaves, joining the normal exception flow at the variable's scope exit.

If multiple invocations along a drop path throw — sibling variables (DROP-02), nested field drops, the body and a field of the same value, or any of these during an exception unwind (EXC-02) — the first thrown exception is the propagating one; later throws are attached to it via `Throwable.addSuppressed`.

`onDrop()` implementations that perform fallible operations (network flushes, file syncs) may either catch internally or allow exceptions to propagate. DROP-10 guarantees that no external reference to the value can survive into step 4, so the drop sequence is safe to complete even after the body throws.

### DROP-08 — A class with `onDrop()` cannot be destructed

No field may be moved out of a value whose class implements `onDrop()`, whether or not the `onDrop()` body reads that field.
The compiler diagnoses the violation at the destruction site: a `give` of such a field is rejected.
The diagnostic identifies the field, the destruction, and the `onDrop()` declaration that locks it.

### DROP-09 — `onDrop()` implementations only on `final` classes

A class may implement `onDrop()` only if it is declared `final`. An `onDrop()` implementation on a non-`final` class is a compile error; `onDrop()` may not be declared `abstract`, and an interface may neither declare it nor supply it as a `default`. A class without an implementation contributes no body to its drop sequence (DROP-05 step 1). At most one user-written `onDrop()` body therefore runs per instance, on the instance's (necessarily `final`) dynamic class.

A class that needs cleanup beyond what its fields' own `onDrop()`s provide must be `final`. Extensible types compose `final` handle fields (`Rc<T>`, `Arc<T>`, `Thread`, …) whose `onDrop()`s perform the release during the owner's drop sequence (DROP-05, step 2).

```java
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

### DROP-11 — `onDrop()` access to `@borrow` fields requires `@borrowCapped`

An `onDrop()` body may access an owned field freely (DROP-05).
It may access a `@borrow` field (OWN-09), its own or an inherited one, only if the class is `@borrowCapped`, declared or inherited (LIFE-04).
Accessing a `@borrow` field otherwise is a compile error.
The diagnostic names the field and points to `@borrowCapped`.

A field whose static type is a type parameter counts as a `@borrow` field for this rule unless the parameter is `@own` (TARG-06), since an unconstrained `T` may be substituted with a `@borrow` argument (TARG-01).

```java
final class Logger {
    @mut @borrow Sink sink;
    @internal void onDrop() {
        sink.flush();              // ERROR: accesses a @borrow field without @borrowCapped
    }
}

@borrowCapped final class Logger {
    @mut @borrow Sink sink;
    @internal void onDrop() {
        sink.flush();              // OK: @borrowCapped, sink live at scope exit (LIFE-04)
    }
}
```

---

## UNR — Unreachability

### UNR-01 — `broken()` declares a path unreachable

`Intrinsics.broken()` (declared in `laterita.lang.Intrinsics` and normally statically imported as `broken`) declares that the enclosing path must not be reachable. The optional overload `Intrinsics.broken(String reason)` attaches an explanatory message. The compiler must reject any program in which the call can be reached on a path it cannot prove dead.

The call has return type `Nothing` (the bottom type): it is a divergence point, code following it in the same block is unreachable, and the enclosing function need not produce a value of its declared return type when control flow ends in `broken()`.

```java
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

```java
if (n < 0) broken("n must be non-negative");
```

---

## DES — Destruction

This topic covers the details of destruction.
Other topics describe when it is allowed and what it does: only an owner may destruct an owned object, with no borrow of it outstanding (OWN-06) and no `onDrop()` (DROP-08), which transfers its fields into the initiating scope as independent values (OWN-06) and ends the object's lifetime (DES-02).
It is part of the Java-compatible surface, so every form here is expressible as annotated `.java` that `javac` parses.

### DES-01 — Destruct by `give`-ing a directly accessible field

`give(x.y)` destructs `x` by moving its field `y` out.
The moved field is named by a direct field-access path `obj.field`:

```java
class Split { Buffer head; Buffer tail; }     // POJO: no onDrop(), fields directly accessible

var s = makeSplit();
var h = give(s.head);          // moves the head field out of s
var t = give(s.tail);          // moves the tail field out, leaving s fully destructed
```

The field must be directly accessible.
A POJO's fields are directly accessible.
A record's components are directly accessible only in `.lat`, where they are public (LAT-08).
A `.java` record keeps private components and cannot be destructed.
A method result is never a destruction: a `give` must name a field, never a call, and a record's canonical accessor returns a borrow rather than the component (OWN-18).

### DES-02 — Restrictions for destructed instances

Once any field has been moved out, the object's lifetime has ended.
It may only be taken further apart, one remaining field at a time:

1. no method may be invoked on it.
2. its fields may not be assigned.
3. it cannot be returned, stored, or passed whole.

Its remaining fields, including further record components, may still be moved out.

```java
var s = makeSplit();
var h = give(s.head);                          // s is now destructed, tail still owned

s.flush();                                     // ERROR: no method may be called on a destructed object
s.tail = makeBuffer();                         // ERROR: it can't be mutated anymore
return s;                                      // ERROR: it cannot be returned whole
var t = give(s.tail);                          // OK: a remaining field may still be moved out
```

---

## OBJ — Copying

### OBJ-01 — Auto-generated copy constructor

Every class has a `protected ClassName(ClassName source)` copy constructor. The compiler synthesizes one when none is provided. The synthesized form chains `super(source)` and copies each field: primitives bitwise; owned object fields via the field's `clone()` method (`source.field.clone()`). A user-provided copy constructor with the same signature suppresses synthesis.

If a field's `clone()` reaches `broken()` (UNR-01), the enclosing class's auto-generated copy constructor reaches `broken()` transitively and is rejected at compile time.

```java
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

```java
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

## NULL — Optionality

Nullability is a property of types in both source surfaces.
The `.lat` spelling `T?` and the operators `?.`, `?:`, and `!!` are syntactic sugar specified in the `LAT` topic.
The rules below define nullability semantics independent of spelling.

### NULL-01 — Types are non-nullable by default

A bare type `T` excludes the null state. A variable of type `T` always holds a valid value after initialization, and methods on `T` may be invoked without a null check.

```java
String name = "Alice";
print(name.length());       // always safe
```

### NULL-02 — Nullable types

A nullable type admits either a value of `T` or the special value `null`. Its canonical form is `@Nullable T` (`@Nullable` declared in `laterita.lang.annotation`); `.lat` sources may use the suffix spelling `T?` (LAT-01). The two spellings denote the same type. `T` and `@Nullable T` are distinct types: `T` widens to `@Nullable T` implicitly; `@Nullable T` does not narrow to `T` without a check (NULL-06) or an assertion (LAT-04).

`T` must be a reference type. Nullable primitive types are rejected at compile time; code that requires null-bearing integer or boolean semantics must use the boxed reference type (`@Nullable Integer`, `@Nullable Boolean`, …). The compiler does not auto-box at the type level.

```java
String? maybeName = lookup(id);   // .lat spelling of @Nullable String
print(maybeName.length());        // ERROR: requires null check
```

### NULL-03 — `null` literal

The literal `null` has type `Nothing?` and is assignable to any `T?`. `null` is not assignable to a non-nullable type.

*NULL-04, NULL-05, NULL-07 — Relocated.* The safe-call (`?.`), elvis (`?:`), and null-assertion (`!!`) operators are `.lat` surface forms. Their definitions and `.java`-surface desugarings are LAT-02, LAT-03, and LAT-04 in the `LAT` topic.

### NULL-06 — Smart narrowing on null check

After a control-flow narrowing (e.g., `if (x != null) { ... }`, `if (x == null) return;`), the variable's type within the proven-non-null region is `T`, not `T?`. Calls that require `T` are permitted without further annotation.

```java
if (maybeName != null) {
    print(maybeName.length());   // OK: narrowed to String
}
```

### NULL-08 — Field default is non-nullable

Fields obey NULL-01: a field declared `T` is non-nullable, and OWN-11's "assigned exactly once in every constructor" requirement guarantees no observable `null`. A nullable field is declared `T?`.

```java
class User {
    String name;            // non-nullable
    String? nickname;       // nullable
}
```

### NULL-09 — `onDrop()` skips null

When a variable of type `T?` leaves scope, the compiler-inserted `onDrop()` call is conditional: if the value is `null`, no call is made; otherwise `onDrop()` is invoked on the contained value. This composes with DROP-04's drop-flag treatment — the compiler already tracks per-variable "still live?" state.

### NULL-10 — Move and borrow on `T?`

`give(expr)` where `expr` has type `T?` transfers either the contained `T` (leaving the source as `null`) or transfers `null`. Borrow rules apply identically to `T?` and `T`. A borrow of a `T?` is itself a `T?`-borrow; null narrowing (NULL-06) on a borrowed variable narrows to a `T`-borrow.

---

## EXC — Exceptions

### EXC-01 — Existing Java exception syntax is preserved

Java's exception syntax is preserved unchanged: `throws`, `try`/`catch`/`finally`, and the `Throwable` hierarchy. The checked/unchecked distinction is removed per EXC-05; the `throws` clause becomes documentary.

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

## FN — Functional Interfaces

Laterita extends Java's *functional interface* concept (an interface with one abstract method) to admit an **anonymous, structural form**: the SAM signature can be written directly inline as a type expression, without declaring a named interface.

### FN-01 — Anonymous functional interface syntax

An anonymous functional interface is written

```
[ @mutating | @consuming ] (P1, P2, …, Pn) -> R
```

where each `Pi` follows OWN-13 / MUT-04 parameter form (bare `T`, `@mut T`, `@take T`, with optional `@bound` per OWN-17 or OWN-18), `R` is the return type, and the optional prefix declares the SAM's call mode (CLO-03): bare → shared-call, `@mutating` → mut-call, `@consuming` → once-call. The two prefixes are mutually exclusive: a SAM that is both `@mutating` and `@consuming` (a one-shot mutator) must use a nominal interface. The single abstract method is named `apply` and invoked as `f.apply(a1, …, an)` — there is no call-on-variable syntax.

Examples — each comment describes what a lambda assigned to that parameter type may do:

```java
void fold(int seed, (int, int) -> int reducer) { … }
// shared-call: invocable any number of times, concurrently; lambda may only read captures

void buildAll(@mut @mutating (@mut StringBuilder) -> void appender) { … }
// mut-call: invoked sequentially; lambda may mutate captures
// (@mut on the variable is what lets buildAll invoke a mut-call SAM, per CLO-03)

void submit(@take @consuming (@take Result) -> void onComplete) { … }
// once-call: invoked at most once; lambda may consume captures and the Result argument
// (@take on the variable is what lets submit invoke a once-call SAM, per CLO-03)

<F extends Field> @bound F lookup(@bound Record rec, RecordKey key, (@bound Record, RecordKey) -> @bound F selector) { … }
// @bound on the SAM parameter pairs with @bound on its return (OWN-18 / OWN-20): lambda must
// project from rec (e.g. rec -> rec.name), not allocate a fresh Field
```

Mapping to Rust: bare = `Fn`, `@mutating` = `FnMut`, `@consuming` = `FnOnce`; CLO-04 carries the containment ordering. A nominal functional interface — a regular interface declared with one abstract method — remains available unchanged from Java; the anonymous form is an addition, accepted only in `.lat` sources (LAT-05).

### FN-02 — Assignability

Two anonymous FI types are *identical* — the same compile-time type — only when their call mode, arity, parameter modes, underlying types, return type, and `@bound` relationships all match exactly. Distinct expressions denote distinct types. A nominal FI and an anonymous one are never identical, even when their SAMs match — the nominal one carries an interface identity the anonymous one lacks.

For most code, identity is the wrong question: anonymous types can't be reflected on or compared at runtime. What matters is *assignability* — when a value of FI type `A` may flow into a slot of FI type `B`. This is HIER-05's override variance applied to the SAM: read the slot `B` as the base declaration and the value `A` as the override. `A` is assignable to `B` exactly when `A`'s SAM could legally override `B`'s — its call mode is `≤` `B`'s (CLO-04 containment), a `@mut` or `@bound` parameter may be dropped, `@take` is invariant, a `@bound` return may be strengthened to owned, and the underlying parameter and return types agree.

```java
(@mut Job) -> String           // type α — slot
(Job)      -> String           // type β — value
(Job)      -> @bound String    // type γ — slot

// β flows into α:  parameter drops @mut (contravariant) ✓
// β flows into γ:  return owned satisfies @bound (covariant) ✓
// γ does NOT flow into β: @bound return cannot satisfy owned slot
```

A lambda literal is checked against the expected FI type by CLO-04; the same variance applies to it as to an already-typed FI value.

### FN-03 — Anonymous synthesis per construction

Each value-construction of an anonymous functional interface yields a synthesized interface whose single abstract method, named `apply`, carries the parameter and return modes written in the type expression. The interface and its implementing class are not addressable from source code. Function-shaped contracts that need a name, documentation, or related methods use a nominal functional interface instead.

Minimal — `(int) -> int` synthesizes:

```java
interface $Anon { int apply(int p0); }
```

Maximal — `@consuming (@take String, @mut List<T>) -> @bound String` synthesizes:

```java
@mut interface $Anon<T> {
    @consuming @bound String apply(@take String p0, @mut List<T> p1);
}
```

The synthesized interface is declared `@mut` whenever the SAM carries `@mutating` or `@consuming` — required by MUT-05 / MUT-08.

### FN-04 — Allowed positions

An anonymous functional-interface type expression (FN-01) may be written as:

- a parameter type
- a return type
- a generic bound — e.g. `<F extends @mutating (T) -> R>`
- a generic type argument — e.g. `Stream<(T) -> R>`

It may not be written as:

- the declared type of a field — stored function values use a nominal FI, which keeps a stable name for documentation and debugging
- the declared type of a local variable — `var` inference still holds an anonymous FI value when the RHS produces one

The restrictions govern the written type expression, not value flow: a `var` local may hold an anonymous-FI value whose type is inferred, such as the result of a closure-returning call.

---

## CLO — Closures

A closure value is a lambda together with the variables it captures from the enclosing scope — a synthesized object whose fields are the captured variables and whose single method is the lambda body, passed to (or returned from) a function and invoked through that method. The mode in which each variable is captured — shared borrow, mutable borrow, or moved owned — determines what the closure may do and how often it may be invoked. CLO-01 classifies these modes; CLO-03 connects them to the FI type that holds the closure.

### CLO-01 — Three capture modes

Closures are classified by how they use captured variables:

- **Read** — captured variables are immutably borrowed; closure may be invoked any number of times, including from multiple threads simultaneously (subject to the `@local` rules of STD-07).
- **Mutate** — captured variables include a mutable borrow; closure may be invoked any number of times sequentially but not concurrently.
- **Consume** — captured variables include a moved value; closure may be invoked exactly once.

A captured local must be effectively final (MUT-02): neither the closure body nor the enclosing method may reassign it.
This is Java's own lambda-capture rule (JLS 15.27.2), kept so that every closure form remains expressible on the `.java` surface (COMP-06).
Mutation of captured state therefore always goes through the referent axis: the closure captures a `@mut` local and mutates through it, the checked form of Java's holder idiom.
Such a closure is a mut-call value (CLO-04), invocable only through a `@mut` variable (CLO-03).

### CLO-02 — Capture mode is inferred

The compiler infers a closure's capture mode from the body. The user does not declare it.

### CLO-03 — Call mode and variable mode

A functional-interface value has two independent properties.

**Call mode** is a property of the *type*. The single abstract method of a functional interface carries a receiver mode, declared exactly as on any method (MUT-08, OWN-15). That receiver mode is the interface's call mode:

| SAM receiver mode | Call mode | Invocation |
|---|---|---|
| bare | **shared-call** | through a shared borrow; repeatedly; concurrently (subject to STD-07) |
| `@mutating` | **mut-call** | through a `@mut` variable; repeatedly but sequentially |
| `@consuming` | **once-call** | once; the call consumes the value |

```java
interface MissResolver<T> { T resolve(String key); }                // shared-call
@mut interface HitListener  { @mutating void onHit(String key); }   // mut-call
interface Finalizer         { @consuming void run(); }              // once-call
```

**Variable mode** is a property of the *variable* that holds the value. A functional-interface variable follows the ordinary variable rules with no special case: a field owns its value by default (OWN-08); a parameter receives ownership with `@take` or a borrow otherwise (OWN-13); `@mut` grants referent mutability (MUT-02); `@borrow` marks a borrowed field (OWN-09); `@bound` marks a borrowed return (OWN-17, OWN-18); a local follows its RHS (OWN-02).

Invoking the SAM is an ordinary method call on the functional-interface value and obeys mutability transitivity (MUT-10, OWN-15): invoking a mut-call SAM requires the variable to be `@mut`; invoking a once-call SAM requires the variable to own the value, and the call consumes it (a destruction per OWN-06 when the variable is a field). Storing, moving, or borrowing a functional-interface value is governed by the variable mode alone, independently of the call mode — a value may be held in a variable from which its SAM cannot be invoked.

```java
class C {
    MissResolver<Foo> resolve;   // owned field, shared-call — invocable through a bare receiver
    @mut HitListener  onHit;     // owned field, mut-call — invocable only in a @mutating method
}
```

A functional-interface type used as a parameter or return combines modifiers from three layers, each governed independently:

| Layer | Modifiers | Governed by |
|---|---|---|
| Inside the type — the SAM's parameters and return | `@take`, `@mut`, `@bound` | OWN-13, OWN-17, OWN-18 |
| The SAM's receiver — the type's call mode | bare / `@mutating` / `@consuming` | this rule |
| The variable holding the value | `@mut`, `@take`, `@bound`, ownership | MUT-02, MUT-04, MUT-07a, OWN-13, OWN-17, OWN-18 |

```java
@mut interface F<T, R> { @mutating R apply(@take T); }   // call mode mut-call; SAM parameter @take T

void process(@mut F<Job, Done> fn) { /* … */ }      // @mut: variable mode of the parameter
```

FI return-type variable annotations follow MUT-01 / OWN-18 unchanged. A once-call FI value cannot be a `@bound` source — the call that would produce the return consumes it.

```java
// The returned closure borrows `fn` and `first`,
// so its lifetime is the intersection of both (LIFE-02).
<A, B, R> @bound (B) -> R partial(@bound (A, B) -> R fn, @bound A first) {
    return (b) -> fn.apply(first, b);
}

void process(@mut @mutating (Event) -> void handler) {     // mut-call slot, mut variable
    handler.apply(e);                                       // OK
}

void fireOnce(@take @consuming (Event) -> void handler) {  // once-call slot, owned variable
    handler.apply(e);                                       // OK
}
```

### CLO-04 — Lambdas are values of functional interfaces

A lambda literal `(p1, p2, …) -> body` is a value whose type is a functional interface — anonymous (FN-01) or nominal — selected by:

- the expected type at the position where the lambda appears (target typing); or
- inference from the body together with any explicit parameter annotations otherwise.

The lambda's capture mode (CLO-01) fixes the receiver mode of its synthesized SAM (FN-03), and therefore its call mode (CLO-03): read → shared-call, mutate → mut-call, consume → once-call. A lambda is a value of an FI type of call mode `M` iff its own call mode is `≤ M` under `shared-call < mut-call < once-call` — the `Fn ⊆ FnMut ⊆ FnOnce` containment expressed through the SAM's receiver mode.

| Lambda capture mode | Lambda call mode | shared-call type | mut-call type | once-call type |
|---|---|:---:|:---:|:---:|
| Read    | shared-call | accept | accept | accept |
| Mutate  | mut-call    | reject | accept | accept |
| Consume | once-call   | reject | reject | accept |

Inverted — what each slot guarantees to the function holding the closure:

| Slot call mode | Lambda author must ensure | Slot holder is guaranteed |
|---|---|---|
| shared-call | closure works read-only on captures | closure never mutates captures; may be invoked any number of times, concurrently (subject to STD-07) |
| mut-call | mutate captures only if needed; closure remains re-callable | closure may mutate captures; must be invoked sequentially, never concurrently |
| once-call | closure may consume captures | closure may be invoked at most once |

Assignability concerns the value only. Whether the variable that receives the value can invoke its SAM is the separate question settled by CLO-03 (variable mode versus call mode).

```java
@mut interface Doubler { @mutating int apply(int x); }   // mut-call

@mut List<Integer> seen = new ArrayList<>();               // effectively final slot, @mut referent
Doubler counting = (x) -> { seen.add(x); return x * 2; };  // mutates through seen → mutate lambda → mut-call: OK
Doubler pure     = (x) -> x * 2;                           // read lambda → shared-call ≤ mut-call: OK

// Doubler bad   = (x) -> { give(resource); return x; };    // ERROR: a consume lambda (once-call)
//                                                         //        is not a value of a mut-call type
```

### CLO-05 — Override variance for FI parameters

A functional-interface parameter has two annotation axes — the *call-mode prefix* on the FI type (FN-01: bare / `@mutating` / `@consuming`) and the *variable-mode* annotations on the parameter (`@take`, `@mut`, `@bound`). Both follow HIER-05's unified override-variance table.

The call-mode axis is the row in HIER-05 whose surface direction inverts: an override may *strengthen* the slot's call mode (bare → `@mutating` → `@consuming`) because a stronger slot accepts strictly more closures (CLO-04: shared-call accepts read only; mut-call adds mutate; once-call adds consume). The override continues to accept every closure the inherited declaration accepted.

The variable-mode annotations on the FI parameter — `@take`, `@mut`, `@bound` — follow HIER-05 directly: they govern how the override's variable holds the FI value, not which closures fit the slot.

```java
interface Source<T> {
    void forEach((T) -> void fn);                                 // base: shared-call slot
}

class Tracing<T> implements Source<T> {
    @Override void forEach(@mut @mutating (T) -> void fn) { ... } // OK: shared-call → mut-call accepts strictly more
}

interface MutSource<T> {
    void forEach(@mut @mutating (T) -> void fn);                  // base: mut-call slot
}

class Bare<T> implements MutSource<T> {
    @Override void forEach((T) -> void fn) { ... }                // ERROR: mut-call → shared-call rejects mutate closures
}
```

The SAM *type itself* is invariant under override in the sense that the SAM's underlying parameter and return *types* must agree (FN-02): annotation variance applies, type substitution does not. A nominal SAM declared as a regular interface follows HIER-05 on its own method signatures unchanged.

### CLO-06 — Capture lifetimes propagate

A by-borrow capture is a `@borrow` slot of the synthesized closure (FN-03), and a by-move capture is an owned slot.
A closure with any `@borrow` capture is therefore a `@bound` value (OWN-09, LIFE-03), bound to the intersection of its captured sources (LIFE-02) and unable to outlive any of them (LIFE-01).
A closure that captures only by move is owned.
When the closure escapes through a return, its captured parameters are the `@bound` sources of the return (OWN-17).

---

## STR — Strings

### STR-07 — `String` is a value class

`String` is a value class (MUT-05): no `@mutating` method exists or can be added by extension (HIER-01).
A non-`final` `String` field in a `@mut` class is reassignable (MUT-07b), and a non-`final` `String` local is reassignable (MUT-02), but `@mut String` is redundant since no `String` method mutates in place.
Bulk text construction belongs in `StringBuilder`, which is `@mut`.

### STR-02 — Strings are tracked as owned or borrowed per variable

A `String` variable is either an owned heap allocation or a borrowed view into another `String`'s storage. The compiler tracks this per-variable and applies lifetime rules to borrowed instances.

### STR-03 — Slice methods return borrows

Methods that return a view into the receiver's storage (e.g., `substring`, `trim`) declare the borrow with `@bound` on the return type per OWN-18.

```java
class String {
    @bound String substring(int start, int end);
    @bound String trim();
}
```

### STR-04 — Allocating methods return owned strings

Methods that produce new storage (e.g., `toUpperCase`, `concat`) return an owned `String` with no lifetime tie to the receiver.

### STR-06 — String literals are static borrows

A string literal expression has type `@bound String` with a static lifetime. A variable initialized from a literal is borrowed; to obtain owned storage, call `.clone()` (OBJ-02).

```java
String greeting = "hello";              // borrowed, static lifetime
String owned = "hello".clone();         // owned heap allocation
var s = give(greeting);                 // ERROR: greeting is borrowed
var u = give("hello");                  // ERROR: literal is borrowed (give(...) on a borrow per OWN-07)
var t = "hello".clone();                // OK: owned
void inspect(String s);                 // accepts a literal directly (borrow)
void store(@take String s);             // requires `.clone()` on a literal
```

### STR-08 — Default receiver mode of `String` methods is borrow

Methods declared on `String` borrow the receiver unless the signature marks otherwise. Methods that consume the receiver (`@consuming`) are rare and explicitly marked; per STR-07, no `@mutating` methods exist.

---

## ARR — Arrays

### ARR-01 — Methods on `T[]` (`.lat` surface)

The laterita compiler treats `T[]` as a class with the following methods (`.lat`-only; the `.java` mirror on `laterita.lang.Arrays` is ARR-02). Both surfaces compile to the same operations; the `.lat` surface here uses the inline functional-interface spelling of LAT-05, and is sugar over the `.java` mirror per LAT-00.

```java
@mut class T[] {
    @mutating @bound Pair<@borrow @mut T[], @borrow @mut T[]> splitAt(int mid);

    @mutating void forEachChunk(int chunkSize,
            @mut @mutating (@mut T[]) -> void body);

    @mutating void forEachChunkExact(int chunkSize,
            @mut @mutating (@mut T[]) -> void body);

    @consuming Pair<T[], T[]> splitOff(int mid);
}
```

`splitAt` re-borrows the receiver (MUT-10), and the returned record is `@bound` to the receiver's source, so the receiver is frozen until both halves expire (LIFE-02).
`forEachChunkExact` skips the trailing partial chunk while `forEachChunk` keeps it.
Each chunk passed to `body` is a mut slice of the receiver whose borrow expires at the call's return, so successive chunks are pairwise disjoint by construction.
No `@unsafe` is required: each operation reduces to ordinary slice expressions covered by OWN-05.
Fold-style reductions express by capturing a `@mut` accumulator in the body lambda (CLO-01), and no dedicated reducer primitive is provided.

`splitOff` consumes the receiver (OWN-15) and returns two owning `T[]` halves spanning `[0, mid)` and `[mid, length)`, sharing the underlying allocation through an internal refcount (freed when the last half drops). Each half is a regular `T[]` supporting the full ARR-01 surface. The distinct name from `splitAt` follows OWN-13 (annotation-only differences are duplicate declarations).

**Example — long-lived workers.** Each half is pre-extracted by destruction (OWN-06) before spawning, so each thread captures and consumes its own owning variable.

```java
var arr   = readInput();
var split = arr.splitOff(arr.length / 2);
var left  = give(split.left);       // .lat destruction: left component moved out of the pair (OWN-06, LAT-08)
var right = give(split.right);      // right component; split now fully destructed
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

    public static <T> @mut @bound Pair<@borrow @mut T[], @borrow @mut T[]> splitAt(
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

`stream` borrows the source array and exposes its elements through the JDK `Stream<T>` type. The borrow lives on the parameter (`@bound T[] arr`), not on the return — `stream` is static, so the receiver-source `@bound` form on the return type does not apply per OWN-18. The bare `Stream<T>` return is bound to `arr` by the parameter-source rule, as in `firstWord(@bound String s)` (OWN-17). Standard terminal operations (including `.parallel().forEach(...)`, `.reduce`, `.collect`) drive multithreading through the stream's underlying `Spliterator`; callers needing a specific executor drive the stream with `ForkJoinPool.submit(...)` per standard JDK practice. Parallel terminal operations require Read-mode closures (CLO-01); a `@mut` capture is rejected at compile time because concurrent invocation would violate the borrow rules. In-place parallel *mutation* of the receiver is not a stream operation — the source array is borrowed, not consumed, and the stream does not write back into it. That use case stays on the `splitOff` path (or the in-thread `forEachChunk` family) per ARR-01.

### ARR-03 — `MutableConsumer<T>`

The written-out form of the anonymous functional type `@mutating (@mut T) -> void` used by ARR-01, for `.java` callers (LAT-05). Declared `@mut` per FN-03.

```java
package laterita.lang;

@FunctionalInterface
@mut
public interface MutableConsumer<T> {
    @mutating void accept(@mut T data);
}
```

### ARR-04 — `Pair<L, R>`

General-purpose record carrying two values. A single declaration covers owned, borrow, and mixed cases — the mode is driven by what is substituted for `L` and `R` (TARG-01).

```java
package laterita.lang;

public record Pair<L, R>(L left, R right) {}
```

Instantiations encountered in this spec:

- `Pair<T[], T[]>` — owned pair, returned by `splitOff`. The accessors `left()` and `right()` return borrows bound to the pair (OWN-18). To obtain the owning halves the pair is destructed by direct component access — `give(p.left)`, `give(p.right)` — a destruction (OWN-06) available in `.lat`, where record components are public (LAT-08). `Pair` declares no `onDrop()`, so DROP-08 does not apply, and each half becomes an independently owned `T[]`. A `.java` caller of the ARR-02 mirror can only borrow the halves through the accessors.
- `@mut @bound Pair<@borrow @mut T[], @borrow @mut T[]>` — pair of mutable borrows, returned by `splitAt`.
The enclosing variable is `@bound` because the instance contains `@borrow`-substituted parameters (TARG-01), and the `@mut` element marks are admitted because the `Pair` is itself `@mut` (TARG-03).
Its lifetime is the intersection of the field sources (LIFE-02).

The record itself is non-`@local`. Heterogeneous (`L ≠ R`) instantiations are permitted.

---

## UNS — Unsafe

### UNS-01 — `@unsafe` is a private-method-only annotation

Unsafe operations are permitted only inside methods declared `private @unsafe`. There is no `@unsafe` annotation on classes and no `unsafe { }` block form. Public APIs are always safe; safety contracts are upheld inside private `@unsafe` methods.

```java
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
2. Constructing `Cell<T>` or mutating its contents through a non-`@mut` variable.
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

## STD — Standard Library Types (Required)

### STD-01 — `Rc<T>`

A reference-counted shared-ownership smart pointer for single-threaded use. Provides:
- `new Rc<T>(@take T value)` — takes ownership of `value`, refcount 1.
- `new Rc<T>(Rc<T> other)` — copy constructor; the new handle points to the same allocation, bumping the refcount. The contained value is not duplicated.
- `@bound T read()` — returns a shared borrow of the contained value, bound to this handle.
- `Rc<T> share()` — alias for the copy constructor; explicit refcount bump.
- `onDrop()` — decrements the refcount; drops the value at zero. Annotated `@internal` like every `onDrop()` (DROP-06); compiler-emitted at scope exit, never called by user code.

A bare assignment of `Rc<T>` is a borrow per OWN-02; a `give(...)` move transfers the handle without bumping; `share()` is the only operation that bumps.

A cycle of `Rc<T>` handles whose strong references form a closed loop is not reclaimed: no handle's refcount can reach zero, and the cycle leaks. Programs that may form cycles must use `WeakReference<T>` (STD-03) for the back-edge to break the cycle.

### STD-02 — `Arc<T>`

The cross-thread analog of `Rc<T>`, with atomic reference-count operations.
The copy constructor `new Arc<T>(Arc<T> other)` performs the atomic refcount bump.
`Arc<T>` is declared `@local(false)` per STD-07 and may be moved or borrowed across thread boundaries.
The type parameter is `@own` (TARG-06): `Arc<@own T>` owns its contents, so a borrowed type argument is rejected.

### STD-03 — `WeakReference<T>`

A non-owning back-reference. The class name and method names follow `java.lang.ref.WeakReference`. Provides:
- `new WeakReference<T>(Rc<T> source)` / `new WeakReference<T>(Arc<T> source)` — constructs a weak handle from a strong one. Mirrors Java's `new WeakReference<T>(referent)` shape but takes the strong handle, because the weak handle is bound to a refcount, not to a GC-tracked referent.
- `Rc<T>? get()` (or `Arc<T>? get()`, matching the source flavor) — returns a strong handle if the value is still alive, otherwise `null`. Implementation must be race-free with respect to concurrent strong-count decrement (compare-and-swap per STD-04).

The return type of `get()` differs from `java.lang.ref.WeakReference.get()`: Java returns the bare referent `T` because the GC keeps it alive across the call site; Laterita returns a fresh strong handle `Rc<T>?` / `Arc<T>?` because liveness during use must be carried by an owning handle, not by a collector. Once the caller drops the returned handle, the value may be reclaimed at the next refcount-zero.

### STD-04 — Race-safe `Arc<T>` upgrade

`WeakReference<T>::get()` on an `Arc`-flavored weak handle must use compare-and-swap to atomically check the strong count is non-zero and bump it. A simple read-then-bump is unsound.

### STD-05 — `Cell<T>`

Interior-mutability primitive. Permits mutation of contents through a non-`@mut` variable. Construction and content mutation require `@unsafe` context per UNS-02. Used as a building block for `Arc<T>`, `Mutex<T>`, lazy initializers, etc.

### STD-06 — `Heap<T>`

Raw heap-allocation primitive. Provides allocation, dereference, and free. All operations require `@unsafe` context per UNS-02. `Heap<T>.clone()` reaches `broken()`: a raw allocation has no defined duplication semantics — duplicating the handle would create two owners of the same memory. Wrapper types built on `Heap<T>` (e.g., `Rc<T>`, `Arc<T>`, owned containers) define their own `clone()` with the appropriate semantics.

### STD-07 — `@local` marker

Cross-thread safety in Laterita is expressed by a single negative marker, `@local`. The language does **not** provide `Send` or `Sync` traits; that vocabulary belongs to Rust and has no analog here. Inter-thread communication uses `Mutex<T>` (STD-09) for shared mutable state and the existing `java.util.concurrent` channel-like classes (e.g., `BlockingQueue`) for hand-off — no auto-trait machinery is involved.

A type carries the `@local` property if its instances cannot safely cross thread boundaries.

The standard library declares `@local`:
- `Rc<T>` (STD-01)
- `Cell<T>` (STD-05)
- `Heap<T>` (STD-06)

A class with any transitively `@local` field must carry an explicit `@local` annotation — either `@local` (inherit thread-affinity) or `@local(false)` (assert encapsulation). Failure to declare one is a compile error; the choice is the author's, not the compiler's. A class with no `@local` fields is non-`@local` by default; it may be annotated `@local` to opt in for thread-affine resources whose affinity isn't visible to the type system (OS handles, GPU contexts, etc.).

`@local(false)` asserts that the class encapsulates its `@local` fields — the compiler does not verify the assertion. The internal access to those fields uses `@unsafe` methods (UNS-01) for the operations in UNS-02 that the compiler cannot verify (notably cross-thread move of `@local`). `@local(false)` lives on the class, `@unsafe` lives on individual methods — they are independent. The stdlib types `Arc<T>` (STD-02), `Mutex<T>`, and `Thread` (THR-01) are declared `@local(false)`.

The compiler must reject:
- A cross-thread closure capture (CLO-01) of a variable whose type is `@local`.
- A move (OWN-07) of a `@local` value across a thread boundary outside `@unsafe` (UNS-02 already gates this).

### STD-08 — Borrow-checked mutable iteration

Three operations support in-place modification of collections under the borrow rules:

- **`Collection<T>.removeIf(Predicate<T> p)`** — bulk removal of every element matching `p`. Same name and meaning as `java.util.Collection.removeIf` (Java 8+).
- **`Iterator<T>` and `ListIterator<T>`** — Java's existing iterator types, reused by name and by method set (`hasNext`, `next`, `hasPrevious`, `previous`, `nextIndex`, `previousIndex`, `remove`, `set`, `add`).
- **`next()` and `previous()` return `@bound T`** — a borrow into the underlying collection's storage, bound to the iterator. Any iterator-mutating call (`remove`, `set`, `add`) invalidates the borrow at the type level via OWN-03.

The one signature deviation from Java: **`Iterator<T>.remove()` and `ListIterator<T>.remove()` return `T`** rather than `void`. The removed element is yielded to the caller as an owned value. Statement-form `it.remove();` (ignoring the return) drops the value via `onDrop` (DROP-01), matching the observable behavior of Java's void-returning `remove`.

Holding a `@mut Iterator<T>` or `@mut ListIterator<T>` is a mutable borrow of the underlying collection per OWN-03. Concurrent modification through any other path is rejected at compile time; `ConcurrentModificationException` is not part of Laterita's runtime semantics, and `modCount`-style runtime guards are not required.

Implementations of these operations are permitted (and expected) to use `private @unsafe` (UNS-01) for the internal aliasing they require. User code remains safe.

### STD-09 — `Mutex<T>`

A mutual-exclusion primitive wrapping an owned value.
Access to the protected value is scoped to a closure call rather than mediated by a separately held guard.
The type parameter is `@own` (TARG-06): `Mutex<@own T>` owns its protected value, so a borrowed type argument is rejected.

**Constructor.** `new Mutex<T>(@take T value)` — wraps `value`, initially unlocked and unpoisoned.

**Scoped acquisition.** `<R> R with(@mut @mutating (@mut T) -> R action)` acquires the lock (blocking if held), invokes `action` on the protected value, releases the lock, and returns `action`'s result. `<R> Optional<R> tryWith(@mut @mutating (@mut T) -> R action)` (including timed variants) is the non-blocking form: it returns an empty `Optional` if the lock cannot be acquired, otherwise runs `action` and returns its result wrapped. The action slot is mut-call (FN-01 `@mutating` prefix) so the closure may capture state by mutable borrow — the typical critical-section shape; CLO-04's containment also admits read-only closures. The protected `T` is reachable only as the parameter of `action`; there is no `unlock()` method, no externally held guard, and no way to extend the borrow beyond the call.

**Acquisition can throw.** `with` throws `PoisonedException` (THR-10) on a poisoned mutex and `InterruptedException` (THR-04) if the calling thread is interrupted while blocked acquiring the lock. `tryWith` throws `PoisonedException` only.

**Poison on closure throw.** If `action` propagates an exception, `with` / `tryWith` mark the mutex poisoned (THR-10) before releasing the lock and rethrowing. A normal closure return releases the lock without poisoning.

**Drop semantics.** `Mutex<T>.onDrop()` runs `T.onDrop()` on the protected value unconditionally — by LIFE-01 no `with` / `tryWith` call can be in flight when the mutex itself is dropped, so cleanup is independent of lock or poison state.

**Inspection.** `isPoisoned()` reads the poison flag without acquiring the lock.

`Mutex<T>` is declared `@local(false)` per STD-07. Its internals (a raw OS lock primitive and a `Cell<T>`-backed protected value) are accessed through `@unsafe` methods; the closure-scoped surface above is safe.

### STD-10 — `ReentrantLock`

A reentrant mutual-exclusion primitive without a protected value: the lock alone. Unlike `Mutex<T>` (STD-09), `ReentrantLock` owns no data, hands out no borrow of protected state, and may be re-entered by the same thread; the data it guards lives in fields of the surrounding object and is reached through ordinary `@mut` access (MUT-10). Acquisition returns a `LockGuard` (STD-11) whose `onDrop` releases the lock — forgetting to unlock is structurally impossible (DROP-01). Method names and shapes mirror `java.util.concurrent.locks.ReentrantLock`.

**Constructor.** `new ReentrantLock()` — creates an unlocked, unfair lock. Fairness is not configurable on this surface.

**Acquisition.** Names and signatures match `java.util.concurrent.locks.ReentrantLock`; each method returns a `@bound LockGuard` that the Java caller may ignore.
- `@bound LockGuard lock()` — blocks until the lock is held; ignores interrupt. Reentrant: the same thread acquiring twice receives two guards; the lock is released only after both are dropped.
- `@bound LockGuard lockInterruptibly() throws InterruptedException` — as `lock()` but is an interruption point (THR-04).
- `@bound LockGuard? tryLock()` — non-blocking; returns the guard or `null` if another thread holds the lock.
- `@bound LockGuard? tryLock(long timeout, TimeUnit unit) throws InterruptedException` — timed variant; interruption point.

**Condition variables.** `@bound Condition newCondition()` — returns a fresh `Condition` (STD-12) bound to this lock. May be called any number of times; one lock can pair with multiple conditions (the classic bounded-buffer "not full" / "not empty" pattern).

`ReentrantLock` is `@local(false)` per STD-07.

### STD-11 — `LockGuard`

A value witnessing that the calling thread holds a `ReentrantLock` (STD-10). Returned by `ReentrantLock.lock` / `lockInterruptibly` / `tryLock`; not user-constructible. `@bound` to its source `ReentrantLock`. A `LockGuard` is `@local`; it cannot be borrowed across threads.

`LockGuard.onDrop()` releases one acquisition of the bound lock — at full release (no outstanding guards on the same thread), the lock becomes available to other threads.

`LockGuard` exposes nothing beyond its existence and its `@internal` `onDrop` (DROP-06). Its only role is to make scope exit equivalent to lock release.

### STD-12 — `Condition`

As `java.util.concurrent.locks.Condition`, created by `ReentrantLock.newCondition()`.

---

## THR — Threads

### THR-01 — `Thread` type

`Thread` is the standard `java.lang.Thread` class reused minus the deprecated methods (`stop()`, `suspend()`, `resume()`, `destroy()`, etc.) and with two changes per THR-03 and THR-06.

A `Thread`'s lifetime is bound to its owner: when the owning variable goes out of scope, `Thread.onDrop()` runs (DROP-03), interrupting the worker and waiting for it to terminate. Long-lived threads (server accept loops, background flushers) must be owned by variables whose lifetime matches — typically a top-level variable in `main` or a field of an object that is itself owned at top level.

`Thread` is declared `@local(false)` per STD-07 and may be moved or borrowed across thread boundaries.

### THR-02 — Thread creation

Threads are created using the standard Java `Thread` constructor and `start()` method, or via the fluent factory methods on `Thread.ofVirtual()` and `Thread.ofPlatform()`. No new keyword is introduced.

```java
@mut var worker = new Thread(() -> body);
worker.start();

var other = Thread.ofVirtual().start(() -> body);   // factory returns started Thread
```

Captures within the closure body follow the closure capture rules (CLO-01, CLO-06) with the additional restrictions of STD-07: each captured variable's referenced type must be non-`@local`.

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

To trigger `Thread.onDrop()` before natural scope exit, use `give(worker);` as a statement (OWN-07).

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

## COMP — Compilation Model

### COMP-01 — Native compilation, no GC

Laterita is intended to be compiled ahead-of-time to native code. There is no garbage collector at runtime. Memory management is determined by static ownership, borrow tracking, and `onDrop()` insertion at scope exits. reference-counted types (`Rc<T>`, `Arc<T>`) introduce dynamic refcount-based reclamation; cycles among such handles leak per STD-01. No tracing collector is provided.

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

- **`.lat`** — full surface. Additionally admits the `.lat` surface forms specified in the `LAT` topic.
- **`.java`** — Java-compatible subset, parseable by `javac` and Java-aware IDEs. The `.lat` forms are rejected; equivalent meaning is expressed through their `.java`-surface desugarings.

Both extensions denote the same language: the type system, annotation/intrinsic surface (RESV), and emitted artifacts are identical, and cross-unit variables work uniformly. Whether a type was declared in `.lat` or `.java` is not part of its identity. Because every `.lat` form is pure syntactic sugar (LAT-00), migration tooling may mechanically translate between the two forms.

### COMP-07 — Compiler invocation

The reference laterita compiler is named `latc`. It accepts both `.lat` and `.java` sources in a single compilation unit, dispatches by file extension per COMP-06, and emits the artifacts required by COMP-01 through COMP-04.

### COMP-08 — Inlining permission

The compiler is permitted and encouraged to inline any function whose body is small enough that call overhead dominates. No annotation is required. Generated forwarding methods (GEN) and accessor methods on records and value classes are primary candidates. The compiler may apply any semantics-preserving combination of inlining, constant folding, and dead-code elimination.

---

## RESV — Reserved Names

The following names are introduced by this specification and must be provided by the standard library: `Rc`, `Arc`, `WeakReference`, `Cell`, `Heap`, `Mutex`, `ReentrantLock`, `LockGuard`, `Condition`, `PoisonedException`. The `Thread` type and `InterruptedException` are reused from the Java standard library per THR-01 and THR-08; `java.util.Objects.requireNonNull` is reused as the `.java`-mode null assertion per LAT-04. Anonymous functional interfaces are structural per FN-01 and require no named stdlib interfaces.

The identifier `onDrop` is reserved as the language-orchestrated lifecycle hook (DROP-01).

**Laterita requires no new keywords or constructs.** The ownership, lifetime, mutability, cleanup, and visibility concepts are expressed as annotations and static method calls; some non-Java syntactic forms (`T?`, `?.`, `?:`, `!!`, `(P1,…,Pn) -> R`) and class extensions are gated to `.lat` sources per the `LAT` topic.
Below is a list of laterita annotations. Combinations not listed are currently not supported and won't compile.

| Annotation | `@Target` | Additional condition | Meaning | Spec rule |
|---|---|---|---|---|
| `@mut` | `TYPE` | Not supported on enum and record | Class or interface has a mutable surface | MUT-05 |
| `@mut` | `LOCAL_VARIABLE` | - | Grants mutate-through on the local (the slot axis is separate: reassignable by default, locked by `final`) | MUT-02 |
| `@mut` | `FIELD` | only in a `@mut` class | Grants mutate-through on the field (the slot axis is MUT-07b) | MUT-07a |
| `@mut` | `PARAMETER` | inert when the type is a value class (STR-07) | Mutable parameter (mutable borrow) | MUT-04 |
| `@mut` | `METHOD` | inert when the type is a value class (STR-07) | Return is a `@mut` variable | MUT-01 |
| `@mut` | `TYPE_USE` | only when enclosing generic type is `@mut` | Generic type argument carries `@mut` elements | TARG-03 |
| `@mutating` | `METHOD` | - | Method mutates its receiver; in an anonymous FI prefix, applies to the synthesized `apply` (FN-01) | MUT-08, FN-01 |
| `@consuming` | `METHOD` | - | Method consumes its receiver; in an anonymous FI prefix, applies to the synthesized `apply` (FN-01) | OWN-15, FN-01 |
| `@take` | `PARAMETER` | - | Parameter receives ownership | OWN-13 |
| `@borrow` | `FIELD` | - | Field is a borrow slot (default: owned); enclosing instance must be `@bound` | OWN-09, LIFE-03 |
| `@borrow` | `PARAMETER` | meaningful with `@take` | Retained-borrow parameter, capping `this` at the parameter's source (bare equals a plain borrow) | OWN-21 |
| `@bound` | `PARAMETER` | - | Return is bound to this parameter | OWN-17 |
| `@bound` | `METHOD` | non `void`, non `static` | Return is bound to `this` | OWN-18 |
| `@borrow` | `TYPE_USE` | in type arguments | Type argument is a borrow slot; enclosing instance must be `@bound` | TARG-01 |
| `@own` | `TYPE_PARAMETER` | - | Type parameter rejects a borrowed type argument (dual of `@borrow`) | TARG-06 |
| `@bound` | `LOCAL_VARIABLE`, `PARAMETER`, `METHOD` (return) | - | Variable holds a borrowed value (instance-level marker on a `@borrow`-field or `@borrow`-substituted-generic instance, OWN-09, TARG-01) | OWN-09 |
| `@borrowCapped` | `TYPE` | inherited by subclasses | Every `@borrow` source the instance holds must stay live until its scope exit | LIFE-04, DROP-11 |
| `@internal` | `METHOD` | - | Callable only by compiler-emitted call sites | DROP-06 |
| `@unsafe` | `METHOD` | - | Private method permitted to use the ops in UNS-02 | UNS-01 |
| `@local` | `TYPE` | - | Class instances are thread-affine | STD-07 |
| `@local(false)` | `TYPE` | class contains `@local` fields | Asserts the class encapsulates its `@local` fields | STD-07 |
| `@Nullable` | `TYPE_USE` | - | Type admits `null` (`.lat` spelling: `T?`) | NULL-02 |
| `@Operator(op)` | `METHOD` | instance method; arity matches `op` (1 param for `PLUS`/`MINUS`/`TIMES`/`DIVIDE`, 0 for `NEGATE`) | Method provides the arithmetic operator `op` (`.lat` sugar) | LAT-07 |
| `@Delegate` | `FIELD` | non-`@Nullable` field or record component | Forwards the field type's public methods onto the owner | GEN-01 |
| `@Getter` `@Setter` | `TYPE`, `FIELD` | field-level `@Setter` needs a `@mut` class | Generate bean accessors (class-level `@Setter` makes the class `@mut`) | GEN-02 |
| `@NoArgsConstructor` `@RequiredArgsConstructor` `@AllArgsConstructor` | `TYPE` | - | Generate constructors | GEN-03 |
| `@ToString` | `TYPE`, `FIELD` | - | Generate `toString()` | GEN-04 |
| `@EqualsAndHashCode` | `TYPE`, `FIELD` | - | Generate `equals` and `hashCode` | GEN-05 |
| `@Data` `@Value` | `TYPE` | - | Bundle accessors, constructor, `toString`, `equals`/`hashCode` (`@Data` is `@mut`, `@Value` a value class) | GEN-06 |
| `@Builder` | `TYPE`, `METHOD`, `CONSTRUCTOR` | - | Generate a fluent `Builder` | GEN-07 |
| `@With` | `TYPE`, `FIELD` | - | Generate copy-with methods | GEN-08 |
| `@NonNull` | `PARAMETER`, `FIELD` | redundant with the non-null default | Assert non-null | GEN-09 |
| `@SneakyThrows` | `METHOD`, `CONSTRUCTOR` | - | Body may throw undeclared (no-op under EXC-05) | GEN-10 |
| `@Synchronized` | `METHOD` | - | Wrap the body in a generated `ReentrantLock` | GEN-11 |
| `@Cleanup` | `LOCAL_VARIABLE` | - | Deterministic scope-exit cleanup | GEN-12 |
| `@Log` (and `@Slf4j`, `@Log4j2`, …) | `TYPE` | - | Generate a static logger field | GEN-13 |
| `@StandardException` | `TYPE` | `Throwable` subclass | Generate the four standard exception constructors | GEN-15 |

An anonymous functional-interface type expression (FN-01, `.lat`-only) encodes a complete SAM signature, so it carries both method-target annotations — `@mutating` / `@consuming`, applied to the synthesized `apply` — and type-use-target annotations — `@mut` / `@take` / `@bound`, on the SAM's parameter and return slots. These are the same annotations the table lists; the spelling introduces no annotation placement that is not already a `METHOD` or a parameter/return position on the nominal SAM the form desugars to (LAT-05). It needs no separate `TYPE_USE` registration.

The annotations are declared in `laterita.lang.annotation`. Stdlib static methods that carry laterita-specific semantics live on `laterita.lang.Intrinsics` and are normally statically imported so call sites read `give(x)` and `broken()` without a qualifier:

| Intrinsic | Meaning | Spec rule |
|---|---|---|
| `Intrinsics.give(x)` | Explicitly removes ownership from `x` | OWN-07 |
| `Intrinsics.broken(reason?)` | Compilation fails if an execution path would lead to this statement | UNR-01 |

To `javac` the annotations are ordinary annotations and the intrinsics ordinary static method calls; the laterita compiler attaches the additional semantics specified in the rules above.

Type inference uses Java's `var` keyword, which changes neither mutability axis (MUT-02).

Java's `synchronized` keyword is not supported: there is no per-object intrinsic monitor, no `synchronized` method modifier, and no `synchronized(obj) { ... }` block. Mutual exclusion is provided exclusively through `Mutex<T>` (STD-09) for data-bound locking and `ReentrantLock` + `Condition` (STD-10, STD-12) for the data-less / multi-condition cases. The associated `Object.wait()`/`notify()`/`notifyAll()` methods are likewise not provided. Condition-variable-style coordination uses `Condition` (STD-12) bound to a `ReentrantLock`.

Java's existing keywords and their meanings are otherwise preserved unless explicitly modified by this specification.

---

## LAT — `.lat` Surface Forms

A laterita source file uses one of two extensions (COMP-06): `.java`, the Java-compatible subset that `javac` parses, and `.lat`, which additionally admits the forms specified in this section. Every rule outside this section belongs to the `.java`-compatible surface.

### LAT-00 — The `.lat` surface is pure syntactic sugar

Forms LAT-01 through LAT-07 are syntactic sugar: each has an exact `.java`-surface equivalent into which the compiler desugars it. Consequently:

- Any `.lat` source built from LAT-01–LAT-07 can be mechanically rewritten to an equivalent `.java` source and the reverse; this rewrite is total and meaning-preserving.
- A program's meaning over the LAT-01–LAT-07 forms never depends on its file extension. Whether a declaration was written in `.lat` or `.java` is not part of its identity (COMP-06). LAT-08 (record-component visibility) is no exception: a destructed record keeps its `record` identity in the `.java` mirror and the destruction desugars to generated members on the Java-compatible surface.
- A proposed sugar form that cannot be expressed as a desugaring to the `.java` surface does not belong in this section. A construct that carries its own semantics belongs in the core spec as a `.java`-surface rule, expressed through the annotation and intrinsic surface of the `RESV` topic.

Most of these forms desugar before any type analysis; the operator sugar LAT-07 is resolved with operand types, exactly as Java already resolves its own built-in operators, and still rewrites to a `.java`-surface method call or built-in operator.

The sugar forms are listed below with their `.java`-surface desugarings.

### LAT-01 — `T?` nullable-type suffix

`T?` is the `.lat` spelling of the nullable type `@Nullable T` (NULL-02). The two spellings denote the same type; the nullability rules NULL-01 through NULL-10 are stated on the type and apply identically to either spelling.

### LAT-02 — Safe call `?.`

`expr?.method(args)` evaluates to `null` if `expr` is `null`, otherwise invokes `method` on `expr`. The result type is `R?` where `R` is the method's return type.

Desugars to `expr == null ? null : expr.method(args)`, with NULL-06 narrowing applied to the non-null branch.

```java
String? upper = maybeName?.toUpperCase();
```

### LAT-03 — Elvis operator `?:`

`a ?: b` evaluates to `a` if `a` is non-null, otherwise to `b`. The result type is the common type of the non-nullable form of `a` and the type of `b`.

Desugars to `a != null ? a : b`, with NULL-06 narrowing on `a`.

```java
String shown = maybeName ?: "anonymous";
```

### LAT-04 — Null assertion `!!`

`expr!!` converts `T?` to `T`. If `expr` is `null`, a `NullPointerException` is thrown. This is the only path from `T?` to `T` at the type level without a flow-sensitive narrowing (NULL-06).

Desugars to `java.util.Objects.requireNonNull(expr)`; the laterita compiler attaches the `T? → T` narrowing to a recognized call of `requireNonNull`, so the `.java` form carries the same typing.

### LAT-05 — Inline functional-interface type `(P1, …, Pn) -> R`

The anonymous structural FI expression of FN-01 is a `.lat`-only spelling; FN-01 through FN-04 specify the type semantics and allowed positions. A `.java` source expresses the same SAM by declaring a nominal functional interface in the corresponding position — the synthesized shape is given by FN-03. For the generic-bound and generic-type-argument positions admitted by FN-04, the desugaring substitutes that nominal interface in the corresponding generic slot — e.g. `<F extends @mutating (T) -> R>` becomes `<F extends $Anon<T, R>>`, and `Stream<(T) -> R>` becomes `Stream<$Anon<T, R>>`.

### LAT-06 — Diamond `<>` is optional on constructor calls

In `.lat` sources the diamond `<>` may be omitted from a parameterized constructor call: `new Pair("hello", 42)` denotes `new Pair<>("hello", 42)`, with type arguments inferred from context exactly as Java's diamond inference would produce. Raw types are not part of the `.lat` surface.

The `.java` mirror writes the diamond explicitly: a diamond-less `new Pair("hello", 42)` in `.java` is the raw-type constructor and is not equivalent to the diamond-bearing form. Migration tooling rewriting `.lat` to `.java` inserts `<>` on every parameterized-class constructor call that omits it.

```java
record Pair<L, R>(L left, R right) {}

Pair<String, Int> p = new Pair("hello".clone(), 42);     // .lat: diamond implicit
Pair<String, Int> q = new Pair<>("hello".clone(), 42);   // also accepted in .lat
```

### LAT-07 — Operator sugar

In `.lat`, the arithmetic operators `+ - * /` and unary `-` and the comparison operators `< <= > >=` are sugar for method calls. Other operators are currently not supported in this way for various reasons.

Arithmetic desugars to an **instance** method annotated `@Operator(op)` (RESV). Comparison desugars through `java.lang.Comparable`:

| Form | Desugars to | Eligibility on the left operand's type |
|---|---|---|
| `a + b` | `a.add(b)` | `@Operator(PLUS)`, one parameter |
| `a - b` | `a.subtract(b)` | `@Operator(MINUS)`, one parameter |
| `a * b` | `a.multiply(b)` | `@Operator(TIMES)`, one parameter |
| `a / b` | `a.divide(b)` | `@Operator(DIVIDE)`, one parameter |
| `-a` | `a.negate()` | `@Operator(NEGATE)`, no parameters |
| `a < b` (and `<=`, `>`, `>=`) | `a.compareTo(b) < 0` (resp. `<= > >=`) | implements `Comparable<S>`, `b` assignable to `S` |

The method name is unconstrained. `@Operator` names the operator, so `BigDecimal.add`, `Instant.plus` / `minus`, and `Duration.negated` qualify unchanged. `@Operator` is rejected on a `static` method or where arity does not match. An operator parameter should be a plain borrow (`@take` / `@mut` discouraged). Comparison needs no annotation because implementing `Comparable` is the opt-in.

`a OP b` is resolved by the static type of the left operand (or for unary `-a`, by `a`). If that type supplies the operator applicable to the right operand, the form is the call. Otherwise, if both operands are primitive-numeric (including GEN-01 `@Delegate` records whose generated forwarder widens to a numeric base), the built-in operator applies. Otherwise it is a type error. Resolution never dispatches on the right operand and never inserts implicit conversion.

Desugaring preserves Java operator precedence. So `a + b * c` is `a.add(b.multiply(c))` and `a + b < c` is `a.add(b).compareTo(c) < 0`. The desugared call then obeys the Java-compatible surface unchanged. `javac` rejects these operators on such types, so the operator spelling is `.lat`-only.

### LAT-08 — Record components are public in `.lat`

In a `.lat` source the components of a `record` are `public` fields.
A record may therefore be destructed (OWN-06) through direct component access:

```java
record Span(Buffer head, Buffer tail) {}   // .lat

var s = makeSpan();
var h = give(s.head);     // head is a public component field, moved out
var t = give(s.tail);     // tail moved out; s fully destructed
```

A canonical accessor (`s.head()`) returns a borrow bound to the record (OWN-18), so it can never be the subject of a move.
Destruction always reads the component as a field.
A record declared in a `.java` source keeps javac's private components, so the `give(s.head)` spelling is `.lat`-only.

The `give`-of-a-component spelling is pure sugar (LAT-00).
It desugars through a companion POJO and a `@consuming` method the compiler generates beside the record.
For a record `Record(T left, S right)` destructed by a `.lat` source the generated members are:

```java
@AllArgsConstructor public final class Record$AsClass { public T left; public S right; }

// on Record:
@consuming Record$AsClass intoClass() { return new Record$AsClass(give(this.left), give(this.right)); }
```

`intoClass()` is a `@consuming` method (OWN-15) running inside the record's own body, where the components are accessible, so it may move each one out into the companion (OWN-06).
The companion is a POJO whose component fields are `public`, the destructable shape OWN-06 already requires, so it destructs field by field on the plain `.java` surface.
A destruction site rewrites accordingly:

```java
record Span(Buffer head, Buffer tail) {}   // .lat

var s = makeSpan();        // returns an owned instance
// .lat surface:              desugared .java:
                           // Span$AsClass s$class = s.intoClass();
var h = give(s.head);      // var h = give(s$class.head);
var t = give(s.tail);      // var t = give(s$class.tail);
```

The record keeps its `record` identity in the `.java` mirror, and the destruction reduces to a `@consuming` method plus an ordinary POJO destruction, both already in the Java-compatible surface, so record destruction adds no semantics of its own (LAT-00).
`intoClass()` and the companion are generated members like any in the `GEN` topic: an explicit declaration of the same signature shadows them, and the generators deduce the laterita annotations they imply (`@take` on the constructor's owned parameters per GEN-03, `@consuming` on the method).
A record's `.java` identity therefore no longer depends on whether it is destructed.

---

## NABI — Native ABI Guarantees

### NABI-01 — Single-field aggregate layout and calling convention

A value class (MUT-05) or record with exactly one field or component has the same size, alignment, and calling-convention treatment as that field or component: no wrapper, object header, or padding, passed and returned in the same register(s) as a bare value of the field's type.

---

## GEN — Code Generation Annotations

Laterita supports the stable [Project Lombok](https://projectlombok.org/) annotations natively. A `.java` or `.lat` source using them compiles unchanged and produces the same observable result a Lombok build produces on the JVM. The compiler generates the members at compile time, and generated members are visible to the type checker and overload resolution.

A generator supplies the laterita annotation a generated member implies (e.g. `setX(@take X x)` when x is owned). It also deduces the laterita class-level annotations: a class annotated with `@Setter` or `@Data` is automatically also `@mut` (MUT-05).

An explicitly declared member with the same name and erased parameter types shadows the generated one, so a generator never conflicts with hand-written code. Annotations and attributes not listed in this section pass through to downstream annotation processors unchanged. Several generators duplicate what a `record` or value class already provides. They stay supported for source compatibility even where the idiomatic laterita form is a `record`.

### GEN-01 — `@Delegate`

`@Delegate` on a field or record component generates, for each `public` instance method of the field's declared type, a forwarding method on the owner that calls the same method on the field. `Object` methods (`equals`, `hashCode`, `toString`) and `static` methods are not forwarded. Forwarder return types are the source method's own (they *decay*), and ownership annotations are propagated: a `@consuming` source yields a `@consuming` forwarder, a `@mutating` source yields a `@mutating` forwarder.

Per the shadowing rule, declaring the methods you want to change and letting `@Delegate` fill in the rest is the supported way to adapt a forwarded surface.

`@Delegate` on a `@Nullable` field is a compile error. When two `@Delegate` fields would generate the same signature, that signature is a compile error until an explicit declaration resolves it. Cyclic delegation, where the delegated type transitively forwards back to the owner, is a compile error.

Two optional attributes mirror Lombok: `types` restricts forwarding to the methods of the listed types instead of the field's whole declared surface, and `excludes` removes the methods of the listed types. Because laterita monomorphizes generics (COMP-02), both attributes accept generic types directly and a generic method forwards with its concrete instantiated signature. The generics limitations Lombok documents for `@Delegate` do not apply.

A single-component record carrying `@Delegate` is the *newtype idiom*: NABI-01 gives it the component's layout and COMP-08 inlines the forwarders, so it is a distinct nominal type that exposes the wrapped interface at zero runtime overhead. The component accessor is the only path back to the wrapped value, and no implicit widening exists.

### GEN-02 — `@Getter` and `@Setter`

`@Getter` on a field, or on the class for all fields, generates a `public` bean accessor: `getFieldName()` (`isFieldName()` for a `boolean`) returning `@bound T` (OWN-18), a borrow of the field. `@Getter(lazy = true)` on a final field generates a memoized accessor that computes the value once on first call.

`@Setter` on a class makes the class `@mut` (MUT-05) and generates a setter for each non-`final` non-`static` field. `@Setter` on a field requires an already-`@mut` class. The setter annotation depends on the field variable:

```java
@Setter T owned;
public @mutating void setOwned(@take T value);
@Setter @borrow S borrowed;
public @mutating void setBorrowed(@take @borrow S value);   // stores the borrow into this, caps the instance (OWN-21)
```

### GEN-03 — Constructor generators

`@AllArgsConstructor` generates a constructor containing every field; `@NoArgsConstructor` generates one with no parameters. `@RequiredArgsConstructor` generates a constructor with a parameter, in declaration order, for every field that carries no initializer, since OWN-11 leaves no field over for later assignment. In all three, an owned field's parameter is marked `@take`; a `@borrow` field's parameter is unmarked (bare = borrow per OWN-13). To follow the intent of the Lombok annotations for less boilerplate, `@RequiredArgsConstructor` and `@NoArgsConstructor` treat `@Nullable` fields as initialized and set them to `null`, working around NULL-01. `@NoArgsConstructor` therefore requires every non-`@Nullable` field to carry an initializer.

```java
@AllArgsConstructor class Shipment {
    String trackingId;        // owned field
    @borrow Carrier carrier;  // borrowed field
}
// generated: Shipment(@take String trackingId, Carrier carrier)
```

`Shipment` drops at end of life the owned `trackingId` (OWN-08), so the constructor marks it `@take`. The `@borrow` field only borrows, so the field is marked `@borrow` and its constructor parameter is unmarked (bare = borrow). The lifetime of a `Shipment` instance is itself bound to the `Carrier` it borrows (LIFE-03).

### GEN-04 — `@ToString`

`@ToString` generates a `public String toString()` returning the class name followed by the field values in declaration order, comma-separated in parentheses. `@ToString.Exclude` omits a field, `@ToString.Include` adds a method result.

### GEN-05 — `@EqualsAndHashCode`

`@EqualsAndHashCode` generates `public boolean equals(Object)` and `public int hashCode()` over all instance fields in declaration order. `@EqualsAndHashCode.Exclude` omits a field.

### GEN-06 — `@Data` and `@Value`

`@Data` bundles `@Getter`, `@Setter`, `@ToString`, `@EqualsAndHashCode`, and `@RequiredArgsConstructor`. Because it includes `@Setter`, a `@Data` class is `@mut`. `@Value` is the immutable bundle: `@Getter`, `@ToString`, `@EqualsAndHashCode`, `@AllArgsConstructor`, with all fields final and the class final. A `@Value` class is a value class (MUT-05), for which a `record` is the idiomatic equivalent.

### GEN-07 — `@Builder`

`@Builder` on a class, constructor, or static method generates a nested `@mut class Builder`, a static `builder()` returning a fresh `Builder`, a fluent method per field named after the field that sets it and returns the builder, and a `build()` that invokes the target. Owned fields are taken `@take` through the builder.

### GEN-08 — `@With`

`@With` generates, for each field, a `public [@bound] X withFieldName([@take|@bound] [@mut] T value)` returning a new instance with that field set to `value`. The `@bound` return annotation is generated when any other field of `X` is `@borrow` — the result's lifetime is bound to `this`. The parameter annotations are generated conditionally:

- `@take` when the field is owned;
- bare (no annotation) when the field is `@borrow` — the result's lifetime is also bound to `value`;
- `@mut` when the field is `@mut`.

Internally, other owned fields are `clone()`d from `this` (OBJ-02), because the receiver is borrowed not consumed. `@With` needs a constructor covering all fields, as in Lombok.

### GEN-09 — `@NonNull`

`@NonNull` on a parameter or field asserts non-null. Non-nullability is already the default (NULL-01), so the annotation is accepted but adds nothing. `@NonNull` combined with `@Nullable` is a compile error (a contradiction).

### GEN-10 — `@SneakyThrows`

Under EXC-05 a body may already throw any exception without a `throws` clause, so `@SneakyThrows` is accepted and has no effect. If OQ-22 restores checked exceptions, it regains its Lombok role of throwing a checked exception across a boundary that does not declare it.

### GEN-11 — `@Synchronized`

`@Synchronized` wraps the method body in a generated `private final ReentrantLock $lock` (or `private static final ReentrantLock $LOCK` for a static method), acquired through a `LockGuard` (STD-11) so the lock releases on scope exit. `@Synchronized("name")` uses the named lock field instead. This reproduces Lombok's per-instance private-lock semantics through the existing concurrency primitives (STD-10) without the `synchronized` keyword, which laterita does not provide.

### GEN-12 — `@Cleanup`

`@Cleanup` runs a cleanup method (default `close()`) at the end of the local's block. `@Cleanup("method")` selects a different method, which the compiler calls at scope exit.

### GEN-13 — `@Log` family

`@Log` and its framework variants (`@Slf4j`, `@Log4j`, `@Log4j2`, `@CommonsLog`, `@JBossLog`, `@Flogger`, `@CustomLog`, `@XSlf4j`) generate the framework's logger as a `private static final` field named `log`, initialized for the annotated type.

### GEN-14 — `val` and `var`

`val` is unsupported in laterita.
Lombok's `val` is an immutable inferred local, which laterita spells `final var` (MUT-02, MUT-03).
Lombok's `var` maps to laterita's `var` unchanged: both declare a reassignable inferred local (MUT-02).
Mutation through such a local additionally needs `@mut`, a distinction Lombok does not have.
See OQ-34.

### GEN-15 — `@StandardException`

`@StandardException` on a `Throwable` subclass generates the four standard exception constructors (no-arg, `(String message)`, `(Throwable cause)`, and `(String message, Throwable cause)`), each chaining to `super`.
