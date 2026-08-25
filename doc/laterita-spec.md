# Laterita Language Specification

This document specifies the normative requirements that a Laterita compiler and standard library must satisfy.
Each requirement carries a mnemonic code for cross-reference.

Every topic except `LAT` specifies the Java-compatible surface.
Every rule there is expressible as annotated `.java` source that `javac` parses (COMP-06).
The `LAT` topic specifies the `.lat` surface forms.
Those are syntactic sugar that desugars to the Java-compatible surface and adds no semantics of its own (LAT-00).

---

## OWN Ownership

This section specifies how values are owned and borrowed, and how ownership transfers across local variables, parameters, returns, and fields.

### OWN-00 A class declaration is its complete ownership contract

All rules adhere to one basic concept: every mutability, ownership, and borrow fact needed to interact with a class is fully carried by its declaration.
To check that a class is used correctly, the compiler never needs the class's actual implementation.

### OWN-01 Owned and borrowed values

Each value has one **owner**: the variable that drops it (DROP-01) at scope exit.
Other variables holding the same value are **borrows**, bounded by the owner's lifetime (LIFE-01).

### OWN-02 A local variable follows its initializer

A local variable owns or borrows its value depending on its initializer.

- A **producer expression** (call, constructor, literal) yields an owner.
- A **naming initializer** (the name of an existing variable) yields a borrow of that source, shared or mutable per MUT-60.

```java
String a = makeString();    // owner: the initializer is a producer
String b = a;               // borrow: the initializer names a
print(a);                   // OK
print(b);                   // OK
```

Reassigning a non-`final` local variable (MUT-20) re-applies this rule to the new right-hand side.
The variable's owned-or-borrowed status, and for a borrow its source, are taken from the most recent assignment and checked flow-sensitively (LIFE-01).

### OWN-03 Borrow exclusivity

A value's borrow state at any point is one of:

- **no borrows**: the owner has unobstructed access (subject to MUT-01).
- **shared borrows**: any number of readers may coexist (including the owner).
No mutation is allowed, not even by the owner.
- **one mutable borrow**: that borrow has exclusive access.
The owner is frozen until the borrow ends.

A mutable borrow may write through the value.
It requires the source to be mutable (MUT-01), or the borrow to occur in a mutating method of the same object.
A borrow of a value also borrows the variable that holds it, so reassigning `x` while any borrow of `x` is live is excluded.
This exclusivity is subject to the disjoint-borrow exceptions of OWN-04 and OWN-05.
It is a compile-time error to violate this.

### OWN-04 Disjoint field borrows are permitted

Two simultaneous borrows of statically distinct fields of the same value are non-aliasing.
They are permitted, including when both are mutable.
The compiler performs this disjointness analysis.

```java
class Tree { Node left; Node right; }
Tree t = makeTree();
Node l = t.left;
Node r = t.right;
l.rename("l");               // OK: disjoint fields, both mutable borrows (MUT-60)
r.rename("r");
```

### OWN-05 Disjoint slice borrows are permitted

Two simultaneous borrows of array slices with provably disjoint index ranges must be permitted.
The compiler proves disjointness for constant ranges and for ranges related by simple arithmetic.
For arbitrary computed ranges, ARR-01 supplies the disjointness witness.
That reduces to ordinary slice expressions this rule covers.

```java
int[] data = new int[100];
int[] left  = data.slice(0, 50);
int[] right = data.slice(50, 100);  // OK: provably disjoint
left[0]  = 1;                       // a write, so left is a mutable borrow (MUT-60)
right[0] = 2;
```

### OWN-06 Destruction transfers an owned object's fields to its scope

Only an owned object with no borrow of it outstanding can be destructed (OWN-03, LIFE-01).
Destruction requires ownership but not mutability.
At its first destructing operation (DES) each of its fields transfers to the scope where the destruction was initiated.
A formerly owned field becomes an independent value owned by that scope.
A `@borrow` field transfers as a `@bound` value still bound to its original source (OWN-09, LIFE-03).

### OWN-07 An unowned value drops at end of statement

An owned value lives only as long as some owner holds it: a local variable, a field, a return, or a `@take` parameter (OWN-13).
A value with no owner (a function result the caller doesn't store, for example) drops at the end of the enclosing statement (DROP-01).

`give` is the ordinary standard-library helper

```java
public static <T> T give(@take T t) { return t; }   // laterita.lang.Intrinsics
```

normally statically imported.
`give(x)` consumes `x` via `@take` and returns its owned value (OWN-16).
A stored result (`var b = give(a);`) lives on in the new owner.
A statement-form result (`give(x);`) drops at the semicolon, running its `onDrop()` immediately.

```java
var worker = Thread.ofVirtual().start(() -> task());
if (changedMyMind()) { give(worker); }   // worker consumed; returned value drops here
```

### OWN-08 Fields are owned by default

A bare field declaration `T x;` declares storage that owns its value.
The field is dropped with the enclosing instance (DROP-05).

### OWN-09 A `@borrow` field holds a borrow, and its instance is `@bound`

`@borrow` on a field or record component declares that the field holds a borrow rather than an owned value.
An instance of a class with any `@borrow` field can only be produced as a `@bound` value.
`@bound` constrains a value's lifetime.
The producer fixes the initial sources.
See OWN-17 and OWN-18 for returns, and LIFE-02 for intersection across multiple sources.

### OWN-10 `@take` is rejected on fields and local variables

`@take` is rejected on a field, on a record component, and on a local variable declaration (OWN-02, OWN-08, OWN-09).

### OWN-11 Constructor initializes every field exactly once

Every field of a class must be assigned exactly once on every path through every constructor, before any method on `this` is invoked.
`final` fields, with or without `@fixed`, can be assigned only in constructors.
Non-`final` fields may be reassigned later per MUT-22.

### OWN-12 Record components follow field rules

A record component is a field for the purposes of OWN-08 through OWN-10.

### OWN-13 Parameter ownership modes

A parameter declares whether it receives a borrow or takes ownership.

| Form | Meaning |
|---|---|
| `T name` | parameter receives a borrow, mutable or shared per MUT-41 |
| `@take T name` | parameter receives ownership (moved in) |

### OWN-14 Call-site argument forms

An argument that is the name of a variable fills a bare parameter with a borrow for the duration of the call, mutable or shared per MUT-41.
It fills a `@take` parameter with an implicit transfer of ownership, and `give(arg)` states that transfer explicitly.
An argument that is a temporary expression, a method invocation, a class instance creation, or a literal, is owned at the call site and fills either form of parameter.

Illegal cases:

- `give(arg)` passed to a bare parameter.
- A variable holding only a borrow passed to a `@take` parameter whose type is owned.
A `@take @borrow` parameter instead expects the borrow itself (OWN-21, TARG-05).

```java
var name = makeName();
inspect(name);              // OK: borrow
inspect(makeName());        // OK: borrow of a temporary
store(name);                // OK: implicit transfer, name no longer usable
store(makeName());          // OK: temporary moved in
inspect(give(name));        // ERROR: inspect borrows, do not transfer
```

Laterita annotations are not part of the Java overload signature.
Two same-name methods differing only in `@take`, `@fixed`, `@bound`, `@borrow`, `@readonly`, or `@consuming` are a duplicate declaration.
APIs needing both borrow and consume shapes use distinct names (e.g. `splitAt` and `splitOff`, ARR-01).

### OWN-15 `@consuming` consumes the receiver

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

`@consuming` is a method modifier and may be combined with `@readonly` (MUT-13).
The combination is callable on a `@fixed` receiver, which an unmarked `@consuming` method is not.
`@consuming` calls require an owned receiver and no `give(...)` wrapper.

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

### OWN-16 An un-`@bound` return is owned

A return type without `@bound` means the function returns an owned value.
`return x;` of an owner moves it.
`return give(x);` is accepted as the explicit form.

### OWN-17 `@bound` on a parameter binds the return to that parameter

`@bound` on a parameter declares that the function returns a borrow whose source is that parameter.
Valid only on a non-`void` return.

```java
String firstWord(@bound String s) {              // returned borrow bound to s
    return s.substring(0, s.indexOf(' '));
}
```

### OWN-18 `@bound` on a return binds the return to `this`

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

### OWN-19 Only a `@bound` source may contribute to a returned borrow

A body that returns a borrow tied to a source not marked `@bound` is a compile-time error.
The diagnostic identifies the source and suggests adding `@bound`.

```java
String prefixOf(@bound String text, String pattern) {
    return text.substring(0, pattern.length());  // bound to text only, pattern unmarked
}
```

### OWN-20 A `@bound` source cannot accompany an owned return

A body that returns an owned value from a signature declaring a `@bound` source is a compile-time error.
The diagnostic identifies the declared source and suggests removing `@bound`.

### OWN-21 A `@take @borrow` parameter caps `this` at its source

A `@take @borrow` parameter receives a borrow and retains it.
The cap is part of the signature: from the call onward the parameter's source is a source of `this` (LIFE-02, LIFE-03), whether or not the body actually stores the borrow.
The form is meaningful only on instance methods: a retained borrow may be stored only into a `@borrow` field of `this`, and on a `static` method the form is rejected.
Storing a borrow into a `@borrow` field of `this` requires this parameter form, and the assignment itself additionally requires a mutable receiver (MUT-22), but declaring the parameter does not: a non-mutating method may declare `@take @borrow` and merely narrow the caller's `this`.
A `@borrow` parameter without `@take` retains nothing and is an ordinary borrow parameter (OWN-13).
A constructor needs no `@take @borrow` parameter: the borrows it stores are the initial sources of OWN-09.

```java
class Cursor {
    @borrow Buffer buf;
    void retarget(@take @borrow Buffer b) { this.buf = b; }   // stores the borrow into this
}

Cursor cur = openOn(mainBuf);
Buffer scratch = makeBuffer();
cur.retarget(scratch);     // from here cur may not outlive scratch (LIFE-01, LIFE-02)
```

---

## LIFE Lifetimes

### LIFE-01 A borrow may not outlive its source

It is a compile-time error to use a borrow after its source has been dropped or moved.

### LIFE-02 Multiple `@bound` sources intersect

When more than one source is marked `@bound` (any combination of parameters and the receiver), the returned borrow's lifetime is the intersection.
It is bounded by the shortest-lived marked source.

```java
@bound String chooseLabel(@bound String fallback) {
    return prefer ? this.label : fallback;       // bound to min(this, fallback)
}
```

### LIFE-03 A `@bound` instance intersects its `@borrow` field sources

A `@bound` instance produced from `@borrow` fields takes each field's source into LIFE-02's intersection.
The instance is usable only while every field's source remains live.

```java
record EntryView<K, V>(@borrow K key, @borrow V value) {}

EntryView<String, Integer> view = new EntryView<>(name, count);
// view's lifetime = min(name, count)
```

### LIFE-05 A primitive has no lifetime

`@bound`, `@borrow`, and `@take` have no effect on a primitive (MUT-18), and a `@borrow` field of primitive type does not make its instance `@bound` (OWN-09).

### LIFE-04 `@borrowCapped` caps an instance's lifetime within its borrow sources

`@borrowCapped` is a class-level annotation, permitted on any class declaration.
It declares that every `@borrow` field's source must remain live until the instance goes out of scope, not only until the instance's last explicit use (LIFE-03).

The obligation is a property of the value, like the `@bound` mode it refines: it is fixed by the instance's class at construction and preserved across assignment and upcast.

`@borrowCapped` is inherited.
A subclass of a `@borrowCapped` class is `@borrowCapped` and cannot remove the marker.
A subclass may add `@borrowCapped` that its superclass lacks.

It is a compile-time error if a `@borrowCapped` instance's scope exit is reached after a source of one of its `@borrow` fields has been dropped or moved.

---
## MUT Mutability

### MUT-01 Mutable and immutable variables

A variable is *mutable* if the object it refers to may be modified through it.
To modify an object is to call a mutating method on it (MUT-13) or to assign one of its fields.

A variable is mutable unless it is annotated `@fixed`, or its declared type is immutable (MUT-31).
Whether a variable is mutable is independent of whether it may be assigned (MUT-20).

`@fixed` is admitted in these positions:

| Position | Bare | `@fixed` |
|---|---|---|
| local variable (MUT-40) | takes the mutability of its initializer's type | immutable |
| field (MUT-21) | the object may be modified through the field | may not |
| parameter (MUT-41) | receives a mutable borrow | receives a shared borrow |
| return type | mutable | immutable |
| type argument, use of a type parameter (TARG-03) | takes the mutability of the type argument | immutable |
| type-parameter declaration (TARG-03) | each use follows the type argument | every use is `@fixed` |
| class or interface declaration (MUT-10) | mutable class | immutable class |

### MUT-10 Mutable and immutable classes

A class, abstract class, or interface is *mutable* or *immutable*.
A class or interface annotated `@fixed` (`@fixed class C`, `@fixed abstract class C`, `@fixed interface I`) is immutable, and one that is not is mutable.

A mutable class may declare methods that are not `@readonly` (MUT-13), and fields that may be assigned or modified through (MUT-21, MUT-22).
Every method of an immutable class or interface is `@readonly`, including an inherited one (HIER-03).

### MUT-11 `record` and `enum` are immutable

Every `record` and every `enum` is immutable.

### MUT-18 Primitive types are immutable

Every primitive type (e.g. `boolean`, `int`, `double`) is immutable.

### MUT-12 A borrow of an immutable instance may be a copy

Where the lifetime constraints are the same, the compiler may replace a borrow with a copy of the instance, or a copy with a borrow, when the declared type is a `final` immutable class, a record, an enum, or a primitive.

### MUT-13 `@readonly` methods

A method may modify its receiver: assign the receiver's non-`final` fields, modify the objects the receiver's fields refer to, and call other mutating methods on `this`.
A method annotated `@readonly` may do none of these.

`@readonly` is a method modifier.
Its element `value` has type `InheritFrom` and defaults to `InheritFrom.NONE`, the form specified here.
On an immutable class `@readonly` has no effect (MUT-10).

HIER-05 specifies override variance.

### MUT-17 `@readonly(InheritFrom.RECEIVER)` methods

A method annotated `@readonly(InheritFrom.RECEIVER)` (MUT-13) requires only the mutability its caller supplies.
Called on a mutable receiver it behaves as a mutating method and takes an exclusive borrow of the receiver (MUT-15).
Called on an immutable receiver or a shared borrow it behaves as `@readonly`.

A `@bound` return of such a method has the mutability of the receiver.
On a mutable receiver the returned borrow is mutable, on an immutable receiver it is `@fixed`.
Where the return is a container or a cursor, the elements it lends have the same mutability.

The compiler generates one implementation per receiver mutability, as for any generic (COMP-02).

```java
class Box<T> {
    T value;
    @readonly(InheritFrom.RECEIVER) @bound T get() { return value; }   // one declaration, both forms
}

Box<Foo> a = makeBox();
var x = a.get();                 // @bound Foo: a is mutable, x.mutate() is permitted
x.mutate();
@fixed Box<Foo> b = makeBox();
var y = b.get();                 // @fixed @bound Foo: b is @fixed
```

### MUT-14 Immutability is transitive

An immutable variable may not be used to modify any object reachable through it, whatever the fields on the path declare.
An object may be modified through a borrow only if that borrow is mutable.
A borrow of a variable whose declared type is an immutable class is always shared (OWN-03).

### MUT-15 Calling a mutating method

It is a compile-time error to call a method that is not `@readonly` unless both of the following hold:

- the receiver variable is mutable, and
- the static type of the receiver is a mutable class or a mutable interface.

Where the static type is a mutable interface, the first condition and HIER-04 together ensure that the run-time class is mutable.
A `@readonly` method may be called on any receiver.

A constructor is exempt.
In a constructor, mutating methods may be called on `this` and inherited non-`final` fields may be assigned, whatever the kind of the class.
The class becomes immutable when the constructor returns.

An `onDrop()` body (DROP-05) is exempt.
Its receiver is mutable whatever the kind of the class.

### MUT-16 Interior mutability requires `Cell<T>`

A class that must modify its contents through an immutable receiver holds those contents in a `Cell<T>`, the only exception to MUT-14.
`Cell<T>` is an unsafe primitive (UNS-02).

### MUT-20 `final` variables

`final` does not affect whether a variable is mutable (MUT-01).

A parameter is always `final` and may not be assigned in the body (OWN-13).
A `@take` parameter may be moved with `give` (OWN-07), which consumes the value rather than assigning the parameter.

Assigning a variable that owns its value drops the previous value first (DROP-01).

### MUT-21 `@fixed` fields

Modifying an object through a field requires a mutable receiver (MUT-15).
The declared type of the field is not restricted.

### MUT-22 Assigning a field

A field may be assigned only through a mutable variable (MUT-13, MUT-15).

Every field of an immutable class is `final` and `@fixed`, including an inherited one (HIER-03).

```java
class User {
    final String id;                 // assigned in the constructor, never again
    String name;                     // assignable in a mutating method
    int loginCount;                  // assignable
    final List<Session> sessions;    // sessions.add() OK, sessions = ... is an error
    final @fixed List<Role> roles;   // roles.add() is an error too
}
```

### MUT-30 The interface `@fixed C`

For every class or interface `C`, `@fixed C` is an interface containing only its `@readonly` methods (MUT-13).
`@fixed C` implements the `@fixed` counterparts of all interfaces that `C` implements or extends.
Every mutable class `C` implements `@fixed C`, and `@fixed Object` is the top type.

A value of type `C` may be assigned to a variable of type `@fixed C`.
It is a compile-time error to assign a value of type `@fixed C` to a variable of type `C`.

`@fixed C` may appear in an `implements` clause, restricting the class to `C`'s `@readonly` methods, and as a type-parameter bound (TARG-03).

### MUT-31 Assignment between mutable and immutable

A value is immutable if its class is immutable (MUT-10, MUT-11, MUT-18), or if the variable it is read from is immutable (MUT-01) or is a shared borrow.
Otherwise it is mutable.

It is a compile-time error to assign an immutable value to a mutable variable.
An immutable variable may be assigned a value of either kind, a mutable one as `@fixed C` (MUT-30).

`@fixed` on a variable whose declared type is immutable has no effect.
A `@fixed` annotation that has no effect is permitted wherever `@fixed` is applicable.

### MUT-40 Local variables

For a `var` declaration the declared type, including `@fixed`, is the type of the initializer (MUT-01).
A later assignment does not change it.

```java
var sb = new StringBuilder();   // the initializer is a StringBuilder, a mutable class
sb.append("x");                 // OK

@fixed var frozen = sb;         // frozen is annotated @fixed
frozen.append("y");             // ERROR: frozen is immutable (MUT-15)
```

### MUT-41 Parameters

With `@take` a parameter receives ownership (OWN-13), and `@fixed` makes that ownership immutable.

A mutable borrow is exclusive (OWN-03).
It is a compile-time error for one variable to fill two mutable-borrow parameters of the same call, or to fill one while it is borrowed elsewhere.

### MUT-42 The `fixed` method

`fixed(x)` applies the MUT-31 conversion to an expression.

```java
public static <T> @fixed T fixed(@bound T in) { return in; }   // laterita.lang.Intrinsics
```

It returns a `@fixed @bound` borrow of `in` (OWN-17).

### MUT-50 A non-static inner class borrows its enclosing instance

A non-static inner class holds an implicit borrow of the instance that created it.
That borrow is a synthetic `final @borrow` field naming the enclosing instance, and it is mutable (OWN-09).
An inner class annotated `@readonly` holds `final @fixed @borrow` instead, a shared borrow of the enclosing instance.
Such a class may be declared inside a mutable class only (MUT-10).
`@fixed` on the same declaration is independent of `@readonly` and makes the inner class itself immutable (MUT-10).

A field of an outer level may be assigned only if no inner class between the assignment and that level is `@readonly`.
The first `@readonly` level holds the level above it as a shared borrow, and it is a compile-time error to assign through that borrow (MUT-14).

```java
class Document {
    int revision;

    @readonly class Appendix {
        int page;

        class Footnote {
            void renumber() {
                page = 2;       // OK: Footnote holds Appendix as a mutable borrow
                // revision = 1; // ERROR: Appendix is @readonly (MUT-14)
            }
        }
    }
}
```

### MUT-51 `@readonly(InheritFrom.RECEIVER)` inner classes

An inner class annotated `@readonly(InheritFrom.RECEIVER)` takes the mutability of its enclosing borrow (MUT-50) from the `this` that constructs the instance.
One such class serves as a mutable cursor when constructed from a mutable enclosing instance, and as a read cursor when constructed from a shared one.

### MUT-60 Effectively fixed local variables

A mutable local variable (MUT-40) is *effectively fixed* if none of its uses is a mutating use.
A *mutating use* is a call to a mutating method on the variable, an assignment through it, passing it to a mutable parameter, or returning it through a mutable return type (MUT-31).
A call to a `@readonly(InheritFrom.RECEIVER)` method (MUT-17) is a mutating use only if the borrow it returns has one.

An effectively fixed local variable borrows its source as a shared borrow.
A local variable with a mutating use borrows its source as a mutable borrow (OWN-02, OWN-03).
The classification applies to the whole variable.

```java
Node l = t.left;                // a borrow of one field (OWN-04)
l.rename("root");               // a mutating use, so l borrows t.left mutably

Node r = t.right;               // no mutating use: r borrows t.right as a shared borrow
report(r.name());               // a second shared borrow of t.right is permitted
```

### MUT-61 Effectively final local variables

A local variable that is not declared `final` and is never assigned after its initializer is *effectively final*.
Borrow analysis treats it as `final` (OWN-02, OWN-03).
Only an effectively final local variable may be captured by a closure (CLO-01).

### MUT-70 A parameter that does not need to be mutable is reported

The compiler reports a mutable parameter (MUT-31) that the method body has no mutating use of (MUT-60), and names `@fixed` as the correction.
The report is a warning.

Where the declared type of the parameter is a type parameter, the bound is used (TARG-03).
The rule does not apply to a parameter whose declared type is an immutable class (MUT-31), nor to an override (HIER-05).

```java
void render(Scene s) { s.draw(); }        // warning: s may be @fixed
void update(Scene s) { s.setDpi(300); }   // no warning: a mutating use
void label(String s) { }                  // no warning: String is immutable
```

### MUT-71 A method that does not modify its receiver is reported

The compiler reports a method that is not `@readonly` and whose body does not modify `this`, and names `@readonly` as the correction.
The report is a warning, on the terms of MUT-70.
The rule does not apply to a method of an immutable class, to an override, or to an abstract or interface method, which has no body.

```java
class Counter {
    int n;
    int read()   { return n; }      // warning: read may be @readonly
    void inc()   { n = n + 1; }     // no warning: it modifies the receiver
}
```

---

## HIER Class Hierarchy and Override

### HIER-02 `Object`

`Object` is mutable.
Its `equals`, `hashCode`, and `toString` are annotated `@readonly`, and its `equals` parameter is `@fixed Object` (MUT-30).

### HIER-03 Immutable subclass of a mutable ancestor is a frozen view

An immutable class extending a mutable class inherits its ancestors' fields and mutating methods.
The inherited mutating methods are not callable on the immutable class (MUT-15).

```java
class Counter {
    int n;
    Counter(int start) { this.n = start; }
    void inc() { n = n + 1; }
    @readonly int read() { return n; }
}

@fixed class FrozenCounter extends Counter {   // @fixed: immutable subclass of the mutable Counter
    FrozenCounter(int start) { super(start); }
}

var fc = new FrozenCounter(5);
fc.read();      // OK
fc.inc();       // ERROR: inc mutates, FrozenCounter is immutable
```

### HIER-04 Mutability is not obtainable by widening

An immutable class implements `@fixed S` for each of its supertypes `S` and is not a subtype of `S` itself (MUT-30).
Widening an immutable instance to a mutable supertype, class or interface, therefore yields the frozen view.
A variable narrowed by a cast or by a pattern is immutable when the variable it is narrowed from is immutable.

An instance is mutable only from the construction of a mutable class.
It stays mutable only through variables, parameters, returns, and fields that are not annotated `@fixed`.

```java
@fixed Counter view = new FrozenCounter(5);   // OK: widens to the frozen view
Counter m           = new FrozenCounter(5);   // ERROR (HIER-04)
FrozenCounter fc    = new FrozenCounter(5);
Counter bad         = (Counter) fc;           // ERROR (HIER-04)
```

### HIER-05 Override variance

An override of an inherited method (subclass override or interface implementation) may **demand less of its caller** and **guarantee more to its caller**, but never the reverse.

- **Parameters** and **the receiver** describe what the method demands.
An override may demand less.
- **The return** describes what the method gives back.
An override may give more.

| Annotation | Position | Override may drop | Override may add |
|---|---|---|---|
| `@take` | parameter | ✗ | ✗ |
| `@fixed` | parameter | ✗ | ✓ |
| `@bound` | parameter | ✓ (jointly with return) | ✗ |
| `@bound` | return | ✓ | ✗ |
| `@fixed` | return | ✓ | ✗ |
| `@readonly` | method | ✗ | ✓ |
| `@consuming` | method | ✓ (to `@readonly` or bare) | ✗ |
| `@fixed` | class | ✗ | ✓ (immutable subclass of a mutable parent, HIER-03) |
| Call mode | functional-interface parameter | ✗ | ✓ (strengthen, CLO-05) |

```java
interface Visitor {
    void visit(Node n);
    @bound String describe(@bound Node n);
}

class CountingVisitor implements Visitor {
    @Override void visit(@fixed Node n) { ... }                // OK: adds @fixed, admits shared callers too
    @Override String describe(Node n) { return "counting"; }   // OK: drops @bound jointly, returns owned
}

interface Reader { void read(@fixed Node n); }

class BadReader implements Reader {
    @Override void read(Node n) { ... }                        // ERROR: drops @fixed, rejects shared callers
}
```

---

## TARG Annotations in Generic Type Arguments

### TARG-01 `@borrow` in a type argument

`@borrow` may appear inside a generic type argument.
It declares that the values substituted for that type parameter are borrows, the same role `@borrow` plays on a field (OWN-09).
When a `@borrow`-substituted argument is stored in a field, that field becomes a `@borrow` field (OWN-09).

```java
// laterita.lang.Pair<L, R> (ARR-04)

Pair<String, Integer>                 p1   = new Pair<>("hello".clone(), 42);
Pair<@borrow String, @borrow Integer> view = new Pair<>(name, count);   // view bound to name, count
```

### TARG-02 `@take` rejected in a type argument

`@take` may not appear inside a generic type argument.
`Pair<@take K, @take V>` is a compile-time error.
Ownership of a generic structure's contents is carried by the structure's own variable (owned vs. `@bound`).

### TARG-03 Type parameters and `@fixed`

The bound determines which type arguments a type parameter accepts, by ordinary subtyping (MUT-30, HIER-04).
The implicit bound is `@fixed Object`, the top type.
A bound that is a mutable class accepts only mutable type arguments.

A use of a type parameter, as a field, parameter, return, local variable, or nested type argument, is a variable whose declared type is the type argument substituted for it.
Whether it is mutable follows from that type argument (MUT-31).
The method body is checked once against the bound: it may call the bound's mutating methods on a use that is not annotated `@fixed`, and a bound with no mutable surface has no such method to call.
Checking a body requires the bound alone, and checking a use site requires the declaration alone (OWN-00).

A type parameter annotated `@fixed` annotates every use of it in the body.
A single use may be annotated instead (`@fixed T field`, `List<@fixed T> xs`, a `@fixed T` parameter, return, or local variable), leaving the others to follow the type argument.
`@fixed` requires nothing of the type argument.

The bound and the annotation are independent, giving six forms.
`B` names a mutable class (MUT-10) in the forms below.

| Declaration | Accepts | Use of `T` |
|---|---|---|
| `<T>` | any type argument | as the type argument |
| `<@fixed T>` | any type argument | `@fixed` |
| `<T extends B>` | subtypes of `B` | as the type argument |
| `<@fixed T extends B>` | subtypes of `B` | `@fixed` |
| `<T extends @fixed B>` | subtypes of `B` and of `@fixed B` | as the type argument |
| `<@fixed T extends @fixed B>` | subtypes of `B` and of `@fixed B` | `@fixed` |

```java
class Counter { int n; void inc() { n = n + 1; } }
@fixed class Role { }

class Bar<T, @fixed S, V extends Counter> {
    T t1;          // mutable when the argument is, with no known mutating method to call
    @fixed T t2;   // frozen use of T
    S s1;          // @fixed, from the declaration
    @fixed S s2;   // redundant (MUT-31)
    V v1;          // mutable: V's bound declares inc()
    @fixed V v2;   // frozen use of a Counter
}

var x = new Bar<Role, Counter, Counter>(/* … */);   // T admits Role, S admits Counter
```

The elements of a container take their mutability from the variable holding the container (MUT-14, MUT-17).
A type argument is annotated `@fixed` to make the elements of a mutable container immutable.

```java
class Registry<T extends Counter> {           // mutable bound: accepts Counter, not Role
    T counter;
    void bump()                                 { counter.inc(); }         // the bound carries inc()
    @readonly(InheritFrom.RECEIVER) @bound T get() { return counter; }     // lends as the receiver does
}

class Box<T> {                                // implicit @fixed Object bound: admits both
    T held;
    @readonly(InheritFrom.RECEIVER) @bound T get() { return held; }
}

var live   = new Registry<Counter>();
live.bump();                                  // OK
var seen   = live.get();                      // mutable borrow: live is mutable
@fixed Registry<Counter> ro = live;
var read   = ro.get();                        // @fixed borrow: ro is @fixed (MUT-17)

var names  = new Box<String>();               // OK: String is immutable, the bound admits it
var counts = new Box<Counter>();
counts.get().inc();                           // OK: the argument is mutable, so the usage is
```

### TARG-04 Stacked `@bound` and `@borrow` collapse to one borrow

`@bound` and `@borrow` are variable-mode markers, not type constructors.
They carry no "layer" to stack.
When they stack through generic substitution, in any combination such as `@bound @borrow T` or `@borrow @borrow T`, the result denotes a single borrow rather than a borrow of a borrow.
Each stacked marker contributes its source to LIFE-02's intersection.
For example, `@bound E` with source `this` (OWN-18), returned from a method on `Container<@borrow T>`, substitutes to `@bound @borrow T`, one borrow bound to both the receiver and the element's source.

```java
class ArrayList<E> {
    void add(@take E e);                    // stores a borrow (TARG-05) when E is a @borrow
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

### TARG-05 `@take` transfers a borrowed type argument by value

A generic `@take T` parameter monomorphized with a borrowed type argument becomes `@take @borrow T` for an exclusive element, or `@take @fixed @borrow T` for a shared one.
`@take` transfers the value by value into the parameter.
`@take @borrow` keeps the reference itself, not the value it points at, which stays owned where it was.
A shared borrow is copied, and an exclusive borrow is moved.
The transferred borrow keeps its original source (LIFE-01).
A method that stores its argument declares `@take` for every element mode.
Written directly on a non-generic parameter, `@take @borrow` is the retained-borrow form of OWN-21, applying the same caller-side cap.

```java
class List<T> { void add(@take T e); }

List<Foo> a;                  // add(@take Foo e): move owned in
List<@fixed @borrow Foo> b;   // add(@take @fixed @borrow Foo e): copy a shared borrow in
List<@borrow Foo> c;          // add(@take @borrow Foo e): move an exclusive borrow in
```

### TARG-06 `@own` requires an owned type argument

A type parameter declared `@own` rejects a borrowed type argument.
`@own` is the dual of `@borrow`: `@borrow` admits a borrow in a type argument, `@own` forbids one at the type parameter.
It marks a type that must own its contents, the role a `'static` bound plays in Rust.
`Arc` (STD-02) and `Mutex` (STD-09) declare their parameter `@own`.

```java
class Mutex<@own T> { /* … */ }

Mutex<Config>         ok  = new Mutex<>(loadConfig());   // owned argument
Mutex<@borrow Config> bad = /* … */;                     // ERROR (TARG-06): borrowed argument
```

### TARG-07 A bare `T` return monomorphized to a borrow binds to its container

A method declared with a bare, and therefore owned, `T` return, monomorphized with a borrowed type argument, returns a `@bound` value instead of an owned one.
For an owned type argument the return is owned (OWN-16).
For a borrowed one the return is the borrow, bound to the receiver (OWN-18).

```java
class List<T> { T remove(int i); }

List<Foo> a;          // remove(int): returns owned Foo (moved out)
List<@borrow Foo> b;  // remove(int): returns @bound Foo, bound to the list
```

---

## STAT Static Storage

### STAT-01 Static fields are immutable

A field declared `static` is initialized once at program start and cannot be reassigned.
Every static field is `final` and `@fixed` whatever its declaration writes.
`static final` and `@fixed static` are accepted and are redundant (MUT-31).

### STAT-02 Const initializer or once-init wrapper

A static field's initializer must be a *const expression*.
A const expression is a literal, a reference to another const-initialized static, or a call to a constructor or function the compiler can evaluate at compile time.
The set of const-eligible operations is defined by the compiler and standard library.
At minimum it covers primitive arithmetic, string literals, and the const-eligible constructors of the synchronizing standard-library types (`Mutex<T>` per STD-09, `Arc<T>` per STD-02, and the atomic primitives).
Initializers that require runtime computation go through a once-init wrapper held in the static field and forced at first access.

```java
static Mutex<Map<String, Session>> SESSIONS = new Mutex<>(new HashMap<>());
static Arc<Config>                 BUILTIN  = new Arc<>(Config.DEFAULT);
```

### STAT-03 Static field type must be non-`@local`

The declared type of a static field must be non-`@local` (STD-07).
`static Rc<T>`, `static Cell<T>`, and `static Heap<T>` are rejected.
Use `static Arc<T>`.

---

## DROP Scope-Exit Cleanup

### DROP-01 Universal `onDrop()`

Every variable triggers the drop of its value when the variable leaves scope.
The drop sequence is specified by DROP-05.
The cleanup hook is `onDrop()`, an `@internal` method (DROP-06) a `final` class may implement (DROP-09).
A class with no implementation contributes no body to its drop sequence.

```java
{
    Rc<File> f = openFile();
    f.read();
}   // f's drop sequence runs here (compiler-emitted)
```

### DROP-02 Reverse declaration order

Within a scope, variables are dropped in the reverse of their declaration order.

### DROP-03 Cleanup on all exit paths

`onDrop()` must be invoked on every exit path from a scope: normal completion, return, break, continue, and exceptional unwind.

### DROP-04 A destructed object's fields drop independently

Per OWN-06 and DES-02, a destructed object is never dropped as a whole.
Each of its formerly owned fields drops at scope exit like any other owned variable (DROP-01, DROP-02), unless it has since been moved away.
The compiler records per field whether it is still owned at each exit point, a drop flag, and emits the drop only for the fields still owned there.
Implementations may optimize away drop flags when static analysis proves them constant.

### DROP-05 Drop sequence

Dropping a value runs cleanup in the reverse of construction order.
For an instance of dynamic class `C` with superclass chain `C → B → … → Object`, the compiler emits, in order:

1. `C.onDrop()` body, if implemented: only `final` classes may, per DROP-09.
2. `C`'s fields, in reverse declaration order, array elements in reverse index order.
3. Step 2 repeated for `B`, then for each superclass up to `Object`.
4. If the instance is heap-allocated, its storage is released.

Fields that are `null` (NULL-09) or `@borrow` (OWN-09) are skipped in steps 2 and 3.
Each surviving owned field is dropped recursively by this same procedure.
The step-1 body runs before any field teardown of that class.
It may read every owned field visible to it, and mutates under MUT-15.
A value reaches this sequence only as a whole: moving a field out is destruction (OWN-06, DROP-04).

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

### DROP-06 `@internal` forbids user invocation

The annotation `@internal` declares that a method may be invoked only by compiler-emitted call sites.
It is a compile-time error for user code to invoke one directly (`x.onDrop()`).

`onDrop()` is the only `@internal` method introduced by this specification.
The compiler emits its invocations at scope exits (DROP-01), on destruction paths (DROP-04), on exception unwind (EXC-02), and as part of the drop sequence (DROP-05).

`@internal` is reserved for compiler-orchestrated hooks.

### DROP-07 Exceptions from `onDrop()` terminate the body, not the drop sequence

An exception propagating out of an `onDrop()` body terminates that body, and the rest of the drop sequence still runs (DROP-05).
The exception then leaves the compiler-emitted call site through the same path a Java `finally`-block exception leaves, joining the normal exception flow at the variable's scope exit.

If multiple invocations along a drop path throw (sibling variables (DROP-02), nested field drops, the body and a field of the same value, or any of these during an exception unwind (EXC-02)) the first thrown exception is the propagating one.
Later throws are attached to it via `Throwable.addSuppressed`.

An `onDrop()` implementation may either catch internally or allow exceptions to propagate.

### DROP-08 A class with `onDrop()` cannot be destructed

No field may be moved out of a value whose class implements `onDrop()`, whether or not the `onDrop()` body reads that field.
The compiler diagnoses the violation at the destruction site: a `give` of such a field is rejected.
The diagnostic identifies the field, the destruction, and the `onDrop()` declaration that locks it.

### DROP-09 `onDrop()` implementations only on `final` classes

A class may implement `onDrop()` only if it is declared `final`.
An `onDrop()` implementation on a non-`final` class is a compile-time error, `onDrop()` may not be declared `abstract`, and an interface may neither declare it nor supply it as a `default`.
At most one user-written `onDrop()` body therefore runs per instance, on the instance's (necessarily `final`) dynamic class.

Extensible types compose `final` handle fields (`Rc<T>`, `Arc<T>`, `Thread`, …) whose `onDrop()`s perform the release during the owner's drop sequence (DROP-05, step 2).

```java
final class Connection { … }              // OK: final, may implement onDrop

class Service {                           // OK: no onDrop implementation; ordinary extensible class
    Connection conn;                      // resource held by composition; conn dropped in Service's drop sequence
}

abstract class Resource {
    @internal void onDrop() { … }          // ERROR: onDrop implementation on a non-final class
}
```

### DROP-10 `this` does not escape `onDrop()`

Within an `onDrop()` body, the receiver `this` has a lifetime bounded by the call.
It may not be given (`give(this)`) to another function, returned, stored in a field or global, or otherwise made reachable after the body returns.

### DROP-11 `onDrop()` access to `@borrow` fields requires `@borrowCapped`

An `onDrop()` body may access a `@borrow` field (OWN-09), its own or an inherited one, only if the class is `@borrowCapped`, declared or inherited (LIFE-04).
Accessing a `@borrow` field otherwise is a compile-time error.
The diagnostic names the field and points to `@borrowCapped`.

A field whose static type is a type parameter counts as a `@borrow` field for this rule unless the parameter is `@own` (TARG-06).

```java
final class Logger {
    @borrow Sink sink;
    @internal void onDrop() {
        sink.flush();              // ERROR: accesses a @borrow field without @borrowCapped
    }
}

@borrowCapped final class Logger {
    @borrow Sink sink;
    @internal void onDrop() {
        sink.flush();              // OK: @borrowCapped, sink live at scope exit (LIFE-04)
    }
}
```

---

## UNR Unreachability

### UNR-01 `broken()` declares a path unreachable

`Intrinsics.broken()` (declared in `laterita.lang.Intrinsics` and normally statically imported as `broken`) declares that the enclosing path must not be reachable.
The optional overload `Intrinsics.broken(String reason)` attaches an explanatory message.
It is a compile-time error if the call can be reached on a path the compiler cannot prove dead.

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

A conditional form is expressible as an `if` guarding `broken()`.
The compiler's standard dead-code analysis determines whether the path is reachable:

```java
if (n < 0) broken("n must be non-negative");
```

---

## DES Destruction

When destruction is permitted, and what it does, are OWN-06 and DROP-08.

### DES-01 Destruct by `give`-ing a directly accessible field

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

### DES-02 Restrictions for destructed instances

Once any field has been moved out, the object's lifetime has ended.
It may only be taken further apart, one remaining field at a time:

1. no method may be invoked on it.
2. its fields may not be assigned.
3. it cannot be returned, stored, or passed whole.

```java
var s = makeSplit();
var h = give(s.head);                          // s is now destructed, tail still owned

s.flush();                                     // ERROR: no method may be called on a destructed object
s.tail = makeBuffer();                         // ERROR: it can't be mutated anymore
return s;                                      // ERROR: it cannot be returned whole
var t = give(s.tail);                          // OK: a remaining field may still be moved out
```

---

## OBJ Copying

### OBJ-01 Auto-generated copy constructor

Every class has a `protected ClassName(ClassName source)` copy constructor.
The compiler synthesizes one when none is provided.
The synthesized form chains `super(source)` and copies each field: primitives bitwise, owned object fields via the field's `clone()` method (`source.field.clone()`).
A user-provided copy constructor with the same signature suppresses synthesis.

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

### OBJ-02 Auto-generated `clone()` method

Every class has a public `Self clone()` method, synthesized as `return new Self(this);` when not provided by the user.
The call dispatches virtually to the actual class's `clone()`.

A class opts out of copying by overriding `clone()` with a body that reaches `broken()`, as in `SecretKey` above.

---

## NULL Optionality

Nullability is a property of types in both source surfaces.
The `.lat` spelling `T?` and the operators `?.`, `?:`, and `!!` are syntactic sugar specified in the `LAT` topic.
The rules below define nullability semantics independent of spelling.

### NULL-01 Types are non-nullable by default

A type `T` that is not annotated `@Nullable` excludes the null state.

### NULL-02 Nullable types

A nullable type admits either a value of `T` or the special value `null`.
Its canonical form is `@Nullable T` (`@Nullable` declared in `laterita.lang.annotation`).
`.lat` sources may use the suffix spelling `T?` (LAT-01).
The two spellings denote the same type.
`T` and `@Nullable T` are distinct types: `T` widens to `@Nullable T` implicitly.
`@Nullable T` does not narrow to `T` without a check (NULL-06) or an assertion (LAT-04).

`T` must be a reference type.
Nullable primitive types are rejected at compile time.
Code that requires null-bearing integer or boolean semantics must use the boxed reference type (`@Nullable Integer`, `@Nullable Boolean`, …).
The compiler does not auto-box at the type level.

```java
String? maybeName = lookup(id);   // .lat spelling of @Nullable String
print(maybeName.length());        // ERROR: requires null check
```

### NULL-03 `null` literal

The literal `null` has type `Nothing?` and is assignable to any `T?`.
`null` is not assignable to a non-nullable type.

*NULL-04, NULL-05, NULL-07: Relocated.* The safe-call (`?.`), elvis (`?:`), and null-assertion (`!!`) operators are `.lat` surface forms.
Their definitions and `.java`-surface desugarings are LAT-02, LAT-03, and LAT-04 in the `LAT` topic.

### NULL-06 Smart narrowing on null check

After a control-flow narrowing (e.g., `if (x != null) { ... }`, `if (x == null) return;`), the variable's type within the proven-non-null region is `T`, not `T?`.
Calls that require `T` are permitted without further annotation.

### NULL-08 Field default is non-nullable

A field declared `T` is non-nullable (NULL-01), and OWN-11 guarantees no observable `null`.
A nullable field is declared `T?`.

### NULL-09 `onDrop()` skips null

When a variable of type `T?` leaves scope, the compiler-inserted `onDrop()` call is conditional.
If the value is `null` no call is made, otherwise `onDrop()` is invoked on the contained value.

### NULL-10 Move and borrow on `T?`

`give(expr)` where `expr` has type `T?` transfers either the contained `T` (leaving the source as `null`) or transfers `null`.
Borrow rules apply identically to `T?` and `T`.
A borrow of a `T?` is itself a `T?`-borrow.
Null narrowing (NULL-06) on a borrowed variable narrows to a `T`-borrow.

---

## EXC Exceptions

### EXC-01 Existing Java exception syntax is preserved

Java's exception syntax is preserved unchanged: `throws`, `try`/`catch`/`finally`, and the `Throwable` hierarchy.
The checked/unchecked distinction is removed per EXC-05.

### EXC-02 Cleanup runs on exception unwind

When an exception propagates out of a scope, all `onDrop()` calls required by DROP-01 through DROP-04 must execute as part of the unwind, before the exception reaches the next handler.
If an `onDrop()` invocation throws, DROP-07 applies.

### EXC-03 Drop flags participate in unwind

DROP-04's drop flags must be consulted during exception unwind, not only on normal exit.

### EXC-04 Lazy stack-trace resolution

When an exception is thrown, the runtime must capture the current call stack as raw return addresses.
Symbol resolution (mapping addresses to source locations) must be deferred until the trace is inspected.
The captured trace is owned by the exception object and freed with it.

### EXC-05 All exceptions are unchecked

The compiler performs no checked-exception analysis.
Any throwable type may be thrown from any method without a corresponding declaration, and callers are never required to catch a particular exception type or re-declare it on their own signatures.
Java's distinction between `Exception` and `RuntimeException` carries no language-level significance in Laterita.
The entire `Throwable` hierarchy is uniformly unchecked.

The `throws` clause is permitted as documentation.
A method may list the exception types it expects to propagate, and tooling (IDEs, generated documentation) may surface that list.
The list is not enforced: declaring `throws X` does not commit the method to throwing only `X`, and omitting the clause does not prevent any exception from propagating.

---

## FN Functional Interfaces

Laterita extends Java's functional interfaces with an **anonymous, structural form**: the SAM signature is written inline as a type expression, with no interface declared.

### FN-01 Anonymous functional interface syntax

An anonymous functional interface is written

```
[ @readonly | @consuming ] (P1, P2, …, Pn) -> R
```

where each `Pi` follows OWN-13 / MUT-41 parameter form (bare `T`, `@fixed T`, or `@take T`, with an optional `@bound` per OWN-17 or OWN-18), `R` is the return type, and the optional prefix declares the SAM's call mode (CLO-03).
The two prefixes are mutually exclusive: a SAM that is both `@readonly` and `@consuming` must use a nominal interface.
The single abstract method is named `apply` and invoked as `f.apply(a1, …, an)`, and there is no call-on-variable syntax.

Examples: each comment describes what a lambda assigned to that parameter type may do:

```java
void fold(int seed, @readonly (int, int) -> int reducer) { … }
// shared-call: invocable any number of times, concurrently; lambda may only read captures

void buildAll((StringBuilder) -> void appender) { … }
// mut-call: invoked sequentially; lambda may mutate captures
// (the bare variable is mutable, which is what lets buildAll invoke a mut-call SAM, per CLO-03)

void submit(@take @consuming (@take Result) -> void onComplete) { … }
// once-call: invoked at most once; lambda may consume captures and the Result argument
// (@take on the variable is what lets submit invoke a once-call SAM, per CLO-03)

<F extends Field> @bound F lookup(@bound Record rec, RecordKey key, (@bound Record, RecordKey) -> @bound F selector) { … }
// @bound on the SAM parameter pairs with @bound on its return (OWN-18 / OWN-20): lambda must
// project from rec (e.g. rec -> rec.name), not allocate a fresh Field
```

Mapping to Rust: `@readonly` is `Fn`, bare is `FnMut`, and `@consuming` is `FnOnce`.
CLO-03 carries the call-mode ordering.
The anonymous form is an addition to the nominal one, accepted only in `.lat` sources (LAT-05).

### FN-02 Assignability

Two anonymous functional-interface types are *identical* (the same compile-time type) only when their call mode, arity, parameter modes, underlying types, return type, and `@bound` relationships all match exactly.
Distinct expressions denote distinct types.
A nominal functional interface and an anonymous one are never identical, even when their SAMs match: the nominal one carries an interface identity the anonymous one lacks.

*Assignability* governs when a value of functional-interface type `A` may be assigned to a variable of functional-interface type `B`.
It is HIER-05's override variance applied to the SAM, reading `B` as the base declaration and `A` as the override.
`A` is assignable to `B` exactly when `A`'s SAM could legally override `B`'s, its call mode is `≤` `B`'s (CLO-03), and the underlying parameter and return types agree.

```java
(Job)         -> String        // type α, the target
(@fixed Job)  -> String        // type β, the value
(Job)         -> @bound String // type γ, the target

// β flows into α:  parameter adds @fixed (contravariant) ✓
// β flows into γ:  return owned satisfies @bound (covariant) ✓
// γ does NOT flow into β: a @bound return cannot satisfy an owned target
```

A lambda literal is checked against the expected functional-interface type by CLO-04.

### FN-03 Anonymous synthesis per construction

Each value-construction of an anonymous functional interface yields a synthesized interface whose single abstract method, named `apply`, carries the parameter and return modes written in the type expression.
The interface and its implementing class are not addressable from source code.
Function-shaped contracts that need a name, documentation, or related methods use a nominal functional interface instead.

Minimal, `(int) -> int` synthesizes:

```java
interface $Anon { int apply(int p0); }
```

Maximal, `@consuming (@take String, List<T>) -> @bound String` synthesizes:

```java
interface $Anon<T> {
    @consuming @bound String apply(@take String p0, List<T> p1);
}
```

The synthesized interface is mutable (HIER-02).

### FN-04 Allowed positions

An anonymous functional-interface type expression (FN-01) may be written as:

- a parameter type
- a return type
- a generic bound: e.g. `<F extends (T) -> R>`
- a generic type argument: e.g. `Stream<(T) -> R>`

It may not be written as:

- the declared type of a field (FN-03)
- the declared type of a local variable

The restrictions govern the written type expression, not value flow: a `var` local variable may hold an anonymous functional-interface value whose type is inferred, such as the result of a closure-returning call.

---

## CLO Closures

A closure value is a lambda together with the variables it captures from the enclosing scope: a synthesized object whose fields are the captured variables and whose single method is the lambda body, passed to (or returned from) a function and invoked through that method.
The mode in which each variable is captured (shared borrow, mutable borrow, or moved owned) determines what the closure may do and how often it may be invoked.
CLO-01 classifies these modes.
CLO-03 connects them to the functional-interface type that holds the closure.

### CLO-01 Three capture modes

Closures are classified by how they use captured variables:

- **Read**: captured variables are immutably borrowed.
Closure may be invoked any number of times, including from multiple threads simultaneously (subject to the `@local` rules of STD-07).
- **Mutate**: captured variables include a mutable borrow.
Closure may be invoked any number of times sequentially but not concurrently.
- **Consume**: captured variables include a moved value.
Closure may be invoked exactly once.

A captured local variable must be effectively final (MUT-61).
A closure that modifies captured state captures a mutable local variable and modifies the object through it.
Such a closure is a mut-call value (CLO-03).

### CLO-02 Capture mode is inferred

The compiler infers a closure's capture mode from the body.

### CLO-03 Call mode and variable mode

A functional-interface value has two independent properties: the **call mode** of its type, and the **variable mode** of the variable that holds it.

Call mode is the receiver mode of the single abstract method, declared exactly as on any method (MUT-13, OWN-15), and is ordered `shared-call < mut-call < once-call`.
A lambda fits a parameter whose call mode is at least its own (CLO-04), and invoking the SAM requires the variable holding the value to support the SAM's receiver mode.

| Call mode | SAM receiver | Lambda that fits | Invocable through | Guarantee to the holder |
|---|---|---|---|---|
| shared-call | `@readonly` | read | any variable | never mutates captures, invocable repeatedly and concurrently (STD-07) |
| mut-call | bare | read, mutate | a mutable variable | may mutate captures, invoked sequentially |
| once-call | `@consuming` | read, mutate, consume | a variable owning the value, which the call consumes | may consume captures, invoked at most once |

```java
interface MissResolver<T> { @readonly T resolve(String key); }      // shared-call
interface HitListener       { void onHit(String key); }             // mut-call
interface Finalizer         { @consuming void run(); }              // once-call
```

Variable mode follows the ordinary variable rules with no special case (OWN-02, OWN-08, OWN-09, OWN-13, OWN-17, OWN-18, MUT-01), and the SAM's own parameters and return follow OWN-13, OWN-17, and OWN-18.
Storing, moving, or borrowing a functional-interface value is governed by the variable mode alone, so a value may be held in a variable from which its SAM cannot be invoked.
Consuming a once-call value held in a field is a destruction (OWN-06).

A once-call functional-interface value cannot be a `@bound` source: the call that would produce the return consumes it.

```java
// The returned closure borrows `fn` and `first`,
// so its lifetime is the intersection of both (LIFE-02).
<A, B, R> @bound (B) -> R partial(@bound (A, B) -> R fn, @bound A first) {
    return (b) -> fn.apply(first, b);
}

void process((Event) -> void handler) {          // mut-call parameter, mutable variable
    handler.apply(e);                                       // OK
}

void fireOnce(@take @consuming (Event) -> void handler) {  // once-call parameter, owned variable
    handler.apply(e);                                       // OK
}
```

### CLO-04 Lambdas are values of functional interfaces

A lambda literal `(p1, p2, …) -> body` is a value whose type is a functional interface (anonymous (FN-01) or nominal) selected by:

- the expected type at the position where the lambda appears (target typing), or
- inference from the body together with any explicit parameter annotations otherwise.

The lambda's capture mode (CLO-01) fixes the receiver mode of its synthesized SAM (FN-03), and therefore its call mode (CLO-03): read → shared-call, mutate → mut-call, consume → once-call.
A lambda is a value of a functional-interface type of call mode `M` if and only if its own call mode is `≤ M` (CLO-03).

Assignability concerns the value only.

```java
interface Doubler { int apply(int x); }   // mut-call

List<Integer> seen = new ArrayList<>();                    // owned local variable, mutable: List is a mutable class (MUT-40)
Doubler counting = (x) -> { seen.add(x); return x * 2; };  // mutates through seen → mutate lambda → mut-call: OK
Doubler pure     = (x) -> x * 2;                           // read lambda → shared-call ≤ mut-call: OK

// Doubler bad   = (x) -> { give(resource); return x; };    // ERROR: a consume lambda (once-call)
//                                                         //        is not a value of a mut-call type
```

### CLO-05 Override variance for functional-interface parameters

A functional-interface parameter has two annotation axes, the *call-mode prefix* on the functional-interface type (FN-01: `@readonly`, bare, or `@consuming`) and the *variable-mode* annotations on the parameter (`@take`, `@fixed`, `@bound`).
Both follow HIER-05's unified override-variance table.

On the call-mode axis an override may *strengthen* the parameter's call mode, from `@readonly` to bare to `@consuming` (CLO-03).

The variable-mode annotations on such a parameter (`@take`, `@fixed`, `@bound`) follow HIER-05 directly: they govern how the override's variable holds the functional-interface value, not which closures fit the parameter.

```java
interface Source<T> {
    void forEach(@readonly (T) -> void fn);                       // base: shared-call parameter
}

class Tracing<T> implements Source<T> {
    @Override void forEach((T) -> void fn) { ... }                // OK: shared-call to mut-call accepts strictly more
}

interface MutSource<T> {
    void forEach((T) -> void fn);                                 // base: mut-call parameter
}

class Narrowing<T> implements MutSource<T> {
    @Override void forEach(@readonly (T) -> void fn) { ... }      // ERROR: mut-call to shared-call rejects mutate closures
}
```

The SAM's underlying parameter and return *types* must agree (FN-02): annotation variance applies, type substitution does not.

### CLO-06 Capture lifetimes propagate

A by-borrow capture is a `@borrow` field of the synthesized closure (FN-03), and a by-move capture is an owned field.
A closure with any `@borrow` capture is therefore a `@bound` value (OWN-09, LIFE-03), bound to the intersection of its captured sources (LIFE-02) and unable to outlive any of them (LIFE-01).
A closure that captures only by move is owned.
When the closure escapes through a return, its captured parameters are the `@bound` sources of the return (OWN-17).

---

## STR Strings

### STR-07 `String` is immutable

`String` is an immutable class (MUT-10).
Bulk text construction belongs in `StringBuilder`.

### STR-02 Strings are tracked as owned or borrowed per variable

A `String` variable is either an owned heap allocation or a borrowed view into another `String`'s storage.
The compiler tracks this per-variable and applies lifetime rules to borrowed instances.

### STR-03 Slice methods return borrows

Methods that return a view into the receiver's storage (e.g., `substring`, `trim`) declare the borrow with `@bound` on the return type per OWN-18.

```java
class String {
    @bound String substring(int start, int end);
    @bound String trim();
}
```

### STR-04 Allocating methods return owned strings

Methods that produce new storage (e.g., `toUpperCase`, `concat`) return an owned `String` with no lifetime tie to the receiver.

### STR-06 String literals are static borrows

A string literal expression has type `@bound String` with a static lifetime.
A variable initialized from a literal is borrowed, to obtain owned storage, call `.clone()` (OBJ-02).

```java
String greeting = "hello";              // borrowed, static lifetime
String owned = "hello".clone();         // owned heap allocation
var s = give(greeting);                 // ERROR: greeting is borrowed
var u = give("hello");                  // ERROR: literal is borrowed (give(...) on a borrow per OWN-07)
var t = "hello".clone();                // OK: owned
void inspect(String s);                 // accepts a literal directly (borrow)
void store(@take String s);             // requires `.clone()` on a literal
```

### STR-08 Default receiver mode of `String` methods is borrow

Methods declared on `String` borrow the receiver unless the signature declares otherwise.
Methods that consume the receiver are marked `@consuming`.

---

## ARR Arrays

### ARR-01 Methods on `T[]` (`.lat` surface)

The Laterita compiler treats `T[]` as a class with the following methods (`.lat`-only, the `.java` mirror on `laterita.lang.Arrays` is ARR-02).
Both surfaces compile to the same operations.
The `.lat` surface here uses the inline functional-interface spelling of LAT-05, and is sugar over the `.java` mirror per LAT-00.

```java
class T[] {
    @readonly(InheritFrom.RECEIVER) @bound Pair<@borrow T[], @borrow T[]> splitAt(int mid);

    void forEachChunk(int chunkSize,
            (T[]) -> void body);

    void forEachChunkExact(int chunkSize,
            (T[]) -> void body);

    @consuming Pair<T[], T[]> splitOff(int mid);
}
```

`splitAt` re-borrows the receiver (MUT-15), and the returned pair is `@bound` to the receiver's source (LIFE-02).
Over a mutable receiver the halves lend mutably, and over a `@fixed` or shared receiver they lend read-only (MUT-17).
`forEachChunkExact` skips the trailing partial chunk while `forEachChunk` keeps it.
Each chunk passed to `body` is a mutable slice of the receiver whose borrow expires when the call returns, so successive chunks are pairwise disjoint by construction.
Fold-style reductions express by capturing a mutable accumulator in the body lambda (CLO-01), and no dedicated reducer primitive is provided.

`splitOff` consumes the receiver (OWN-15) and returns two owning `T[]` halves spanning `[0, mid)` and `[mid, length)`, sharing the underlying allocation through an internal reference count (freed when the last half drops).
Each half is a regular `T[]` supporting the full ARR-01 surface.

**Example: long-lived workers.** Each half is extracted by destruction (OWN-06) before the threads are spawned.

```java
var arr   = readInput();
var split = arr.splitOff(arr.length / 2);
var left  = give(split.left);       // left field moved out of the pair (OWN-06)
var right = give(split.right);      // right field moved out, split now fully destructed
var t1 = Thread.ofVirtual().start(() -> heavy(left));
var t2 = Thread.ofVirtual().start(() -> heavy(right));
t1.join();
t2.join();
```

### ARR-02 `laterita.lang.Arrays` static surface (`.java` mirror)

Static-method mirror of the ARR-01 instance surface for `.java` callers, plus `stream` for read-only parallel processing via the JDK `Stream<T>` API.

```java
package laterita.lang;

public final class Arrays {
    private Arrays() {}

    public static <T> @fixed @bound Pair<@borrow @fixed T[], @borrow @fixed T[]> splitAt(
            @bound @fixed T[] arr, int mid);

    public static <T> @bound Pair<@borrow T[], @borrow T[]> splitMutableAt(
            @bound T[] arr, int mid);

    public static <T> void forEachChunk(
            T[] arr, int chunkSize,
            MutableConsumer<T[]> body);

    public static <T> void forEachChunkExact(
            T[] arr, int chunkSize,
            MutableConsumer<T[]> body);

    public static <T> Pair<T[], T[]> splitOff(
            @take T[] arr, int mid);

    public static <T> Stream<T> stream(@bound T[] arr);
}
```

`@readonly(InheritFrom.RECEIVER)` may not be written on a static method, which has no receiver (MUT-17).
The split appears under two names.
Both bind their return to the `@bound` parameter rather than to a receiver (OWN-17).
Distinct names rather than an overloaded pair are required by OWN-14.

ARR-01's single `splitAt` is sugar over this pair (LAT-00): it desugars to `splitMutableAt` on a mutable receiver and to `splitAt` on a `@fixed` or shared one, which are the two monomorphizations MUT-17 produces.

`stream` exposes the elements of the borrowed source array through the JDK `Stream<T>` type, with the return bound to the `@bound` parameter (OWN-17).
Standard terminal operations (including `.parallel().forEach(...)`, `.reduce`, `.collect`) drive multithreading through the stream's underlying `Spliterator`, and callers needing a specific executor drive the stream with `ForkJoinPool.submit(...)`.
Parallel terminal operations require a read closure (CLO-01).
In-place parallel *mutation* of the receiver is not a stream operation and stays on the `splitOff` path or the in-thread `forEachChunk` family (ARR-01).

### ARR-03 `MutableConsumer<T>`

The written-out form of the anonymous functional type `(T) -> void` used by ARR-01, for `.java` callers (LAT-05).
Mutable per FN-03.

```java
package laterita.lang;

@FunctionalInterface
public interface MutableConsumer<T> {
    void accept(T data);
}
```

### ARR-04 `Pair<L, R>`

General-purpose class carrying two values.
A single declaration covers owned, borrow, and mixed cases: the mode is driven by what is substituted for `L` and `R` (TARG-01).
It is mutable (HIER-02).

```java
package laterita.lang;

public class Pair<L, R> {
    public final L left;
    public final R right;

    public Pair(@take L left, @take R right);
}
```

The components are `public final` fields rather than record components, so the pair destructs by direct field access on both surfaces (OWN-06).

Instantiations encountered in this spec:

- `Pair<T[], T[]>`: owned pair, returned by `splitOff`.
The owning halves are obtained by destructing the pair, `give(p.left)` and `give(p.right)` (OWN-06).
- `@bound Pair<@borrow T[], @borrow T[]>`: pair of borrowed halves, returned by `splitAt` (TARG-01, LIFE-02).
Whether those halves lend mutably follows the receiver `splitAt` was called on (MUT-17).

The class itself is non-`@local`.
Heterogeneous (`L ≠ R`) instantiations are permitted.

### ARR-05 Array indexing is always bounds-checked

Every array index expression `a[i]`, read or write, is bounds-checked, and an out-of-range index throws `ArrayIndexOutOfBoundsException`.
There is no unchecked-indexing form and no annotation that suppresses the check (UNS-02, UNS-04).
A compiler may elide a check it proves redundant.

---

## UNS Unsafe

### UNS-01 `@unsafe` is a private-method-only annotation

Unsafe operations are permitted only inside methods declared `private @unsafe`.
There is no `@unsafe` annotation on classes and no `unsafe { }` block form.
Public APIs are always safe.
Safety contracts are upheld inside private `@unsafe` methods.

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

### UNS-02 Fixed list of unsafe operations

Only the following operations require `@unsafe` context:

1. Constructing or dereferencing `Heap<T>`.
2. Constructing `Cell<T>` or mutating its contents through a `@fixed` variable.
3. Cross-thread move of an `@local` type (STD-07).
4. Lifetime extension or transmute.
5. Foreign function calls (FFI / native).

This list is closed.

### UNS-03 Unsafe-typed fields force private + `@unsafe`

A class field whose declared type is an unsafe primitive (e.g., `Heap<T>`, `Cell<T>`) must be private.
Any constructor or method that reads or writes such a field must be annotated `@unsafe`.

### UNS-04 Standard checks still apply inside `@unsafe`

`@unsafe` only unlocks the operations in UNS-02.
Type checking, ownership tracking, lifetime inference, and mutability rules continue to apply in `@unsafe` methods.

---

## STD Standard Library Types (Required)

### STD-01 `Rc<T>`

A reference-counted shared-ownership smart pointer for single-threaded use.
Provides:
- `new Rc<T>(@take T value)`: takes ownership of `value`, with a reference count of 1.
- `new Rc<T>(Rc<T> other)`: copy constructor, where the new handle refers to the same allocation and bumps the reference count.
The contained value is not duplicated.
- `@bound T read()`: returns a shared borrow of the contained value, bound to this handle.
- `Rc<T> share()`: the copy constructor under another name, bumping the reference count explicitly.
- `onDrop()`: decrements the reference count.
Drops the value at zero.

Assigning one `Rc<T>` variable from another is a borrow per OWN-02.
A `give(...)` move transfers the handle without bumping.
`share()` is the only operation that bumps.

A cycle of `Rc<T>` handles whose strong references form a closed loop is not reclaimed, since no handle's reference count can reach zero.
Programs that may form cycles must use `WeakReference<T>` (STD-03) for the back-edge to break the cycle.

### STD-02 `Arc<T>`

The cross-thread analog of `Rc<T>`, with atomic reference-count operations.
The copy constructor `new Arc<T>(Arc<T> other)` bumps the reference count atomically.
`Arc<T>` may be moved or borrowed across thread boundaries (STD-07).
The type parameter is `@own` (TARG-06).

### STD-03 `WeakReference<T>`

A non-owning back-reference.
The class name and method names follow `java.lang.ref.WeakReference`.
Provides:
- `new WeakReference<T>(Rc<T> source)` / `new WeakReference<T>(Arc<T> source)`: constructs a weak handle from the strong one.
- `Rc<T>? get()` (or `Arc<T>? get()`, matching the source flavor): returns a strong handle if the value is still alive, otherwise `null`.
Implementation must be race-free with respect to concurrent strong-count decrement (compare-and-swap per STD-04).

`get()` returns a fresh strong handle rather than the value itself, which `java.lang.ref.WeakReference.get()` returns.
Once the caller drops the returned handle, the value may be reclaimed when the reference count next reaches zero.

### STD-04 Race-safe `Arc<T>` upgrade

`WeakReference<T>::get()` on an `Arc`-flavored weak handle must use compare-and-swap to atomically check the strong count is non-zero and bump it.
A simple read-then-bump is unsound.

### STD-05 `Cell<T>`

Interior-mutability primitive.
Permits mutation of contents through a `@fixed` variable (UNS-02).
Used as a building block for `Arc<T>`, `Mutex<T>`, lazy initializers, etc.

### STD-06 `Heap<T>`

Raw heap-allocation primitive.
Provides allocation, dereference, and free (UNS-02).
`Heap<T>.clone()` reaches `broken()` (UNR-01).
Wrapper types built on `Heap<T>` (e.g. `Rc<T>`, `Arc<T>`, owned containers) define their own `clone()`.

### STD-07 `@local` marker

Cross-thread safety is expressed by a single negative marker, `@local`.
There are no `Send` or `Sync` traits.
Inter-thread communication uses `Mutex<T>` (STD-09) for shared mutable state and the `java.util.concurrent` channel-like classes such as `BlockingQueue` for hand-off.

A type carries the `@local` property if its instances cannot safely cross thread boundaries.

Standard-library types declaring `@local` include:
- `Rc<T>` (STD-01)
- `Cell<T>` (STD-05)
- `Heap<T>` (STD-06)

A class with any transitively `@local` field must carry an explicit `@local` annotation, either `@local` (inherit thread-affinity) or `@local(false)` (assert encapsulation).
Failure to declare one is a compile-time error.
A class with no `@local` fields is non-`@local` by default.
It may be annotated `@local` to opt in for thread-affine resources whose affinity isn't visible to the type system (OS handles, GPU contexts, etc.).

`@local(false)` asserts that the class encapsulates its `@local` fields, and the compiler does not verify the assertion.
The internal access to those fields uses `@unsafe` methods (UNS-01) for the operations in UNS-02 that the compiler cannot verify, notably cross-thread move of `@local`.
`@local(false)` lives on the class and `@unsafe` on individual methods, independently.
Standard-library types declaring `@local(false)` include `Arc<T>` (STD-02), `Mutex<T>` (STD-09), and `Thread` (THR-01).

Each of the following is a compile-time error:
- A cross-thread closure capture (CLO-01) of a variable whose type is `@local`.
- A move (OWN-07) of a `@local` value across a thread boundary outside an `@unsafe` method (UNS-02).

### STD-08 Borrow-checked iteration

Iteration reuses Java's `Iterator<T>` and `ListIterator<T>` by name.
`Iterable<T>.iterator()` is `@readonly(InheritFrom.RECEIVER)` (MUT-17).
There is one cursor type and one factory: the read and update forms are the two monomorphizations of the same `iterator()`.

The enhanced-for consumes exactly this.
`for (var x : source)` desugars to `var it = source.iterator(); while (it.hasNext()) { var x = it.next(); ... }` with no cursor selection, and the loop variable inherits its mutability from `next()` (MUT-40).
A loop body with no mutating use of the loop variable leaves the receiver effectively fixed (MUT-60, MUT-17).

```java
for (var x : fixed(list)) {   // shared borrow, so a nested read of `list` still compiles
    ...
}
```

Structural modification (`remove`, `set`, `add`) lives on `ListIterator<T>`, obtained from `listIterator()`, which always holds an exclusive borrow rather than an inherited one.
`ListIterator<T>.remove()` returns the removed element owned rather than `void` (OWN-07).
`Collection<T>.removeIf(Predicate<T> p)` is unchanged from `java.util.Collection.removeIf`.
`Iterator<T>.remove()` is `broken()` by default (UNR-01), and `ListIterator<T>` overrides it with the working form.

Holding a cursor borrows the collection per OWN-03: an inherited-mutable cursor or a `ListIterator` is an exclusive borrow, a `@fixed` cursor a shared one.
Concurrent modification through any other path is rejected at compile time, so `ConcurrentModificationException` is not part of Laterita's runtime semantics and `modCount`-style guards are not required.
Implementations are permitted to use `private @unsafe` (UNS-01) for the internal aliasing they require.

### STD-09 `Mutex<T>`

A mutual-exclusion primitive wrapping an owned value.
Access to the protected value is scoped to a closure call rather than mediated by a separately held guard.
The type parameter is `@own` (TARG-06).

**Constructor.** `new Mutex<T>(@take T value)`: wraps `value`, initially unlocked and unpoisoned.

**Scoped acquisition.** `<R> R with((T) -> R action)` acquires the lock (blocking if held), invokes `action` on the protected value, releases the lock, and returns `action`'s result.
`<R> Optional<R> tryWith((T) -> R action)` (including timed variants) is the non-blocking form: it returns an empty `Optional` if the lock cannot be acquired, otherwise runs `action` and returns its result wrapped.
The action parameter is mut-call (FN-01, with no prefix), so the closure may capture state by mutable borrow, and a read closure fits as well (CLO-03).
The protected `T` is reachable only as the parameter of `action`.
There is no `unlock()` method, no externally held guard, and no way to extend the borrow beyond the call.

**Acquisition can throw.** `with` throws `PoisonedException` (THR-10) on a poisoned mutex and `InterruptedException` (THR-04) if the calling thread is interrupted while blocked acquiring the lock.
`tryWith` throws `PoisonedException` only.

**Drop semantics.** `Mutex<T>.onDrop()` runs `T.onDrop()` on the protected value unconditionally, whatever the lock or poison state.

**Inspection.** `isPoisoned()` reads the poison flag without acquiring the lock.

Its internals (a raw OS lock primitive and a `Cell<T>`-backed protected value) are accessed through `@unsafe` methods.
The closure-scoped surface above is safe.

### STD-10 `ReentrantLock`

A reentrant mutual-exclusion primitive without a protected value: the lock alone.
Unlike `Mutex<T>` (STD-09), `ReentrantLock` owns no data, hands out no borrow of protected state, and may be re-entered by the same thread.
The data it guards lives in fields of the surrounding object and is reached through ordinary mutable access (MUT-15).
Acquisition returns a `LockGuard` (STD-11) whose `onDrop` releases the lock: forgetting to unlock is structurally impossible (DROP-01).
Method names and shapes mirror `java.util.concurrent.locks.ReentrantLock`.

**Constructor.** `new ReentrantLock()`, creates an unlocked, unfair lock.
Fairness is not configurable on this surface.

**Acquisition.** Names and signatures match `java.util.concurrent.locks.ReentrantLock`.
Each method returns a `@bound LockGuard` that the Java caller may ignore.
- `@bound LockGuard lock()`: blocks until the lock is held, ignores interrupt.
Reentrant: the same thread acquiring twice receives two guards.
The lock is released only after both are dropped.
- `@bound LockGuard lockInterruptibly() throws InterruptedException`: as `lock()` but is an interruption point (THR-04).
- `@bound LockGuard? tryLock()`: non-blocking.
Returns the guard or `null` if another thread holds the lock.
- `@bound LockGuard? tryLock(long timeout, TimeUnit unit) throws InterruptedException`: timed variant, interruption point.

**Condition variables.** `@bound Condition newCondition()`: returns a fresh `Condition` (STD-12) bound to this lock.
May be called any number of times.
One lock can pair with multiple conditions (the classic bounded-buffer "not full" / "not empty" pattern).

### STD-11 `LockGuard`

A value witnessing that the calling thread holds a `ReentrantLock` (STD-10).
Returned by `ReentrantLock.lock` / `lockInterruptibly` / `tryLock`, and not user-constructible.
`@bound` to its source `ReentrantLock`.
A `LockGuard` cannot be borrowed across threads (STD-07).

`LockGuard.onDrop()` releases one acquisition of the bound lock: at full release (no outstanding guards on the same thread), the lock becomes available to other threads.

`LockGuard` exposes nothing beyond its existence and its `@internal` `onDrop` (DROP-06).
Its only role is to make scope exit equivalent to lock release.

### STD-12 `Condition`

As `java.util.concurrent.locks.Condition`, created by `ReentrantLock.newCondition()`.

---

## THR Threads

### THR-01 `Thread` type

`Thread` is the standard `java.lang.Thread` class reused minus the deprecated methods (`stop()`, `suspend()`, `resume()`, `destroy()`, etc.) and with two changes per THR-03 and THR-06.

A `Thread`'s lifetime is bound to its owner: when the owning variable goes out of scope, `Thread.onDrop()` runs (DROP-03, THR-06).
Long-lived threads (server accept loops, background flushers) must be owned by variables whose lifetime matches, typically a top-level variable in `main` or a field of an object that is itself owned at top level.

`Thread` may be moved or borrowed across thread boundaries (STD-07).

### THR-02 Thread creation

Threads are created using the standard Java `Thread` constructor and `start()` method, or via the fluent factory methods on `Thread.ofVirtual()` and `Thread.ofPlatform()`.
No new keyword is introduced.

```java
var worker = new Thread(() -> body);   // worker is mutable: Thread is a mutable class (MUT-40)
worker.start();

var other = Thread.ofVirtual().start(() -> body);   // factory returns started Thread
```

Captures within the closure body follow the closure capture rules (CLO-01, CLO-06) with the additional restrictions of STD-07: each captured variable's referenced type must be non-`@local`.

### THR-03 Interrupt flag

Each `Thread` carries an interrupt flag observable via `Thread.isInterrupted()`.
The flag is initially clear.
`Thread.interrupt()` sets it, no operation clears it.
The flag is **sticky and idempotent**: subsequent `interrupt()` calls are no-ops, and no exception, control-flow construct, or scope exit clears the flag once set.

The static `Thread.interrupted()` is synonymous with `Thread.currentThread().isInterrupted()` and does **not** clear the flag.
The Java semantics in which `Thread.interrupted()` clears the flag are not provided.

Any interruption point reached after the flag is set throws `InterruptedException` (THR-08).

### THR-04 Interruption points

An **interruption point** is a program location at which the running thread reacts to its own interrupt flag.
The standard reaction is to throw `InterruptedException` from a standard-library blocking operation (`Thread.join`, `Thread.sleep`, `Object.wait`, `BlockingQueue.take`, IO read/write, and others identified as such in their standard-library definitions).

User code may also create an interruption point by polling `Thread.currentThread().isInterrupted()` or the static `Thread.interrupted()` (THR-03) and using the result to alter control flow, for example, exiting an otherwise non-terminating loop.

Reading another thread's flag via `otherThread.isInterrupted()` is **not** an interruption point: neither thread is reacting to its own state.
Reading the running thread's flag without using the result for control flow (e.g. logging it) is likewise not an interruption point.

CPU-bound code that does not reach a standard-library blocking primitive and does not poll its own flag is uncancellable.

### THR-05 `onDrop()` must not block

A user-defined or standard-library `onDrop()` body (DROP-01) must not contain an interruption point (THR-04).
It is a compile-time error to declare an `onDrop()` whose body transitively reaches a standard-library blocking operation.

It is likewise a compile-time error to call `Thread.currentThread().isInterrupted()` or the static `Thread.interrupted()` inside an `onDrop()` body.
Calls of the form `otherThread.isInterrupted()` remain permitted (THR-04).

`Thread.onDrop()` (THR-06) is exempt.
The rule applies to every other `onDrop`.

Resources whose cleanup needs to block (flush-on-close for buffered IO, drain on channel teardown) belong in an explicit `close()` method, not in `onDrop()`.

### THR-06 `Thread.onDrop()`

`Thread.onDrop()` is `@internal` (DROP-06) and is compiler-emitted at scope exit per DROP-03.
It performs, in order:

1. Set the interrupt flag (idempotent per THR-03).
2. Wait for the worker to terminate.
Termination is bounded by the worker reaching its next interruption point and unwinding via `InterruptedException`.
The worker's own `onDrop` chain runs frame-by-frame during the unwind (DROP-03).
3. Reclaim the thread's resources.

To trigger `Thread.onDrop()` before natural scope exit, use `give(worker);` as a statement (OWN-07).

`Thread` is `final` (DROP-09).
The Java pattern of subclassing `Thread` (`class Worker extends Thread { … }`) is unavailable, and a `Runnable` or lambda is passed to the constructor instead (THR-01, THR-02).

### THR-07 `Thread.interrupt()`

`Thread.interrupt()` sets the interrupt flag on the receiver per THR-03 and returns immediately.
It does not wait for the worker to unwind.
May be called from any thread holding a reference to the receiver.

### THR-08 `InterruptedException`

`InterruptedException` is the exception thrown at an interruption point (THR-04) when the running thread's interrupt flag is set.
It propagates through the standard exception unwind path (EXC-02).
Catching it does not clear the interrupt flag (THR-03).

`InterruptedException` is unchecked per EXC-05.

### THR-09 `Thread.join()`

`Thread.join()` blocks the calling thread until the receiver terminates.
It is an interruption point per THR-04: if the calling thread's interrupt flag is set while it is blocked in `join()`, it throws `InterruptedException`.

`join()` does not interrupt the receiver.
To cancel and observe, call `worker.interrupt()` and then `worker.join()`.

### THR-10 `Mutex<T>` poisoning

A `Mutex<T>` is **poisoned** when the closure passed to its `with` / `tryWith` call (STD-09) propagates an exception (`InterruptedException` or any other) out of the critical section.
`with` / `tryWith` set the poison flag inside the `catch` clause that wraps the closure invocation, before releasing the lock and rethrowing.
A normal closure return releases the lock without poisoning.

There is no bypass: a poisoned mutex's contents are unreachable through the locking API (STD-09).
Programs that need to recover from poisoning replace the entire `Mutex<T>`, typically the surrounding `Arc<Mutex<T>>`.

Poisoning is per-mutex, sticky, and not cleared by lock release or by inspection.

---

## COMP Compilation Model

### COMP-01 Native compilation, no GC

Laterita is intended to be compiled ahead-of-time to native code.
There is no garbage collector at runtime.
Memory management is determined by static ownership, borrow tracking, and `onDrop()` insertion at scope exits.
Reference-counted types (`Rc<T>`, `Arc<T>`) introduce dynamic reference-count-based reclamation (STD-01).
No tracing collector is provided.

### COMP-02 Generic monomorphization

Generic types and methods are monomorphized: each instantiation produces a specialized implementation at compile time.
Field offsets and method dispatch are resolved per-instantiation.

### COMP-03 Compiler-inserted cleanup

The compiler must emit `onDrop()` calls at every scope-exit point per DROP-03 and unwind table entries per EXC-02.
These insertions happen after all user-level analysis and are not visible in source.
Each emitted call site must implement the exception handling specified by DROP-07: body termination, drop sequence continuation, and suppressed-exception accumulation.

### COMP-04 Drop flags as compile-time state

Per-field move state (DROP-04) is compiler-internal bookkeeping.
Implementations should optimize away flags whose values are statically determined.

### COMP-05 No reflection

Laterita does not provide reflection.
There is no runtime API for enumerating fields or methods, looking up members by name, instantiating types from a `Class` token, generating dynamic proxies, or loading classes at runtime.
The compiler is not required to emit per-type metadata for these purposes, and standard-library APIs equivalent to `java.lang.reflect.*`, `java.lang.Class` member-access methods, `Proxy.newProxyInstance`, or `ServiceLoader`'s runtime classpath scan are not provided.

Use cases traditionally served by reflection are served by compile-time code generation (annotation processors, compiler plugins): serializers, ORM mappers, dependency-injection wiring, validators, mocks, test discovery, and SPI registries are all generated at build time from the types and annotations that exist in source.
Stack traces (EXC-04) and exception types remain available.

### COMP-06 Source file extensions

A Laterita source file uses one of two extensions:

- **`.lat`**: full surface.
Additionally admits the `.lat` surface forms specified in the `LAT` topic.
- **`.java`**: Java-compatible subset, parseable by `javac` and Java-aware IDEs.
The `.lat` forms are rejected.
Equivalent meaning is expressed through their `.java`-surface desugarings.

Both extensions denote the same language: the type system, the annotation and intrinsic surface (RESV), and emitted artifacts are identical, and cross-unit references work uniformly.
Whether a type was declared in `.lat` or `.java` is not part of its identity.

### COMP-07 Compiler invocation

The reference Laterita compiler is named `latc`.
It accepts both `.lat` and `.java` sources in a single compilation unit, dispatches by file extension per COMP-06, and emits the artifacts required by COMP-01 through COMP-04.

### COMP-08 Inlining permission

The compiler is permitted and encouraged to inline any function whose body is small enough that call overhead dominates.
No annotation is required.
Generated forwarding methods (GEN) and accessor methods on records and immutable classes are primary candidates.
The compiler may apply any semantics-preserving combination of inlining, constant folding, and dead-code elimination.

---

## RESV Reserved Names

The following names are introduced by this specification and must be provided by the standard library: `Rc`, `Arc`, `WeakReference`, `Cell`, `Heap`, `Mutex`, `ReentrantLock`, `LockGuard`, `Condition`, `PoisonedException`.
The `Thread` type and `InterruptedException` are reused from the Java standard library per THR-01 and THR-08.
`java.util.Objects.requireNonNull` is reused as the `.java`-mode null assertion per LAT-04.
Anonymous functional interfaces are structural per FN-01 and require no named standard-library interfaces.

The identifier `onDrop` is reserved as the language-orchestrated lifecycle hook (DROP-01).

**Laterita requires no new keywords or constructs.** The ownership, lifetime, mutability, cleanup, and visibility concepts are expressed as annotations and static method calls.
Some non-Java syntactic forms (`T?`, `?.`, `?:`, `!!`, `(P1,…,Pn) -> R`) and class extensions are gated to `.lat` sources per the `LAT` topic.
Below is a list of Laterita annotations.
Combinations not listed are currently not supported and won't compile.

| Annotation | `@Target` | Additional condition | Meaning | Spec rule |
|---|---|---|---|---|
| `@fixed` | `TYPE` | redundant on enum and record | Class or interface is immutable | MUT-10 |
| `@fixed` | `LOCAL_VARIABLE` | redundant when the declared type is an immutable class | The local variable may not be used to modify the object (assignment is the separate `final` axis) | MUT-40 |
| `@fixed` | `FIELD` | redundant in an immutable class and on a field of immutable type | The field may not be used to modify the object (assignment is MUT-22) | MUT-21 |
| `@fixed` | `PARAMETER` | redundant when the type is an immutable class | Parameter receives a shared borrow instead of a mutable one, and its absence is reported when the body never mutates through it | MUT-41, MUT-70 |
| `@fixed` | `METHOD` | redundant when the type is an immutable class | Return is a `@fixed` variable | MUT-01 |
| `@fixed` | `TYPE_USE` | - | Generic type-argument usage is `@fixed`, and requires nothing of the container | TARG-03 |
| `@fixed` | `TYPE_PARAMETER` | - | `<@fixed T>` writes `@fixed` at every usage of `T`, leaving the bound unchanged | TARG-03 |
| `@readonly` | `METHOD` | default `InheritFrom` | Method does not mutate its receiver, and as an anonymous functional-interface prefix applies to the synthesized `apply` (FN-01) | MUT-13, FN-01 |
| `@readonly(InheritFrom.RECEIVER)` | `METHOD` | - | Method inherits the receiver's mutability | MUT-13, MUT-17 |
| `@readonly` | `TYPE` | non-static inner class | Inner class holds a shared borrow of its enclosing instance | MUT-50 |
| `@readonly(InheritFrom.RECEIVER)` | `TYPE` | only inside a mutable class | Non-static inner class inherits the mutability of its enclosing instance | MUT-50, MUT-51 |
| `@consuming` | `METHOD` | - | Method consumes its receiver, and as an anonymous functional-interface prefix applies to the synthesized `apply` (FN-01) | OWN-15, FN-01 |
| `@take` | `PARAMETER` | - | Parameter receives ownership | OWN-13 |
| `@borrow` | `FIELD` | - | Field holds a borrow rather than an owned value, and the enclosing instance must be `@bound` | OWN-09, LIFE-03 |
| `@borrow` | `PARAMETER` | meaningful with `@take` | Retained-borrow parameter, capping `this` at the parameter's source (without `@take` it is an ordinary borrow) | OWN-21 |
| `@bound` | `PARAMETER` | - | Return is bound to this parameter | OWN-17 |
| `@bound` | `METHOD` | non `void`, non `static` | Return is bound to `this` | OWN-18 |
| `@borrow` | `TYPE_USE` | in type arguments | Type argument is a borrow, and the enclosing instance must be `@bound` | TARG-01 |
| `@own` | `TYPE_PARAMETER` | - | Type parameter rejects a borrowed type argument (dual of `@borrow`) | TARG-06 |
| `@bound` | `LOCAL_VARIABLE`, `PARAMETER`, `METHOD` (return) | - | Variable holds a borrowed value (on an instance with a `@borrow` field or a `@borrow`-substituted type argument, OWN-09, TARG-01) | OWN-09 |
| `@borrowCapped` | `TYPE` | inherited by subclasses | Every `@borrow` source the instance holds must stay live until its scope exit | LIFE-04, DROP-11 |
| `@internal` | `METHOD` | - | Callable only by compiler-emitted call sites | DROP-06 |
| `@unsafe` | `METHOD` | - | Private method permitted to use the ops in UNS-02 | UNS-01 |
| `@local` | `TYPE` | - | Class instances are thread-affine | STD-07 |
| `@local(false)` | `TYPE` | class contains `@local` fields | Asserts the class encapsulates its `@local` fields | STD-07 |
| `@Nullable` | `TYPE_USE` | - | Type admits `null` (`.lat` spelling: `T?`) | NULL-02 |
| `@Operator(op)` | `METHOD` | instance method, arity matches `op` (1 param for `PLUS`/`MINUS`/`TIMES`/`DIVIDE`, 0 for `NEGATE`) | Method provides the arithmetic operator `op` (`.lat` sugar) | LAT-07 |
| `@Delegate` | `FIELD` | non-`@Nullable` field or record component | Forwards the field type's public methods onto the owner | GEN-01 |
| `@Getter` `@Setter` | `TYPE`, `FIELD` | needs a mutable class | Generate bean accessors | GEN-02 |
| `@NoArgsConstructor` `@RequiredArgsConstructor` `@AllArgsConstructor` | `TYPE` | - | Generate constructors | GEN-03 |
| `@ToString` | `TYPE`, `FIELD` | - | Generate `toString()` | GEN-04 |
| `@EqualsAndHashCode` | `TYPE`, `FIELD` | - | Generate `equals` and `hashCode` | GEN-05 |
| `@Data` `@Value` | `TYPE` | - | Bundle accessors, constructor, `toString`, `equals`/`hashCode` (`@Data` needs a mutable class, `@Value` makes the class `@fixed`) | GEN-06 |
| `@Builder` | `TYPE`, `METHOD`, `CONSTRUCTOR` | - | Generate a fluent `Builder` | GEN-07 |
| `@With` | `TYPE`, `FIELD` | - | Generate copy-with methods | GEN-08 |
| `@NonNull` | `PARAMETER`, `FIELD` | redundant with the non-null default | Assert non-null | GEN-09 |
| `@SneakyThrows` | `METHOD`, `CONSTRUCTOR` | - | Body may throw undeclared (no-op under EXC-05) | GEN-10 |
| `@Synchronized` | `METHOD` | - | Wrap the body in a generated `ReentrantLock` | GEN-11 |
| `@Cleanup` | `LOCAL_VARIABLE` | - | Deterministic scope-exit cleanup | GEN-12 |
| `@Log` (and `@Slf4j`, `@Log4j2`, …) | `TYPE` | - | Generate a static logger field | GEN-13 |
| `@StandardException` | `TYPE` | `Throwable` subclass | Generate the four standard exception constructors | GEN-15 |

An anonymous functional-interface type expression (FN-01, `.lat`-only) encodes a complete SAM signature, so it carries both method-target annotations (`@readonly` / `@consuming`, applied to the synthesized `apply`) and type-use-target annotations (`@fixed` / `@take` / `@bound`, on the SAM's parameter and return positions).
These are the same annotations the table lists, and the form needs no separate `TYPE_USE` registration.

The annotations are declared in `laterita.lang.annotation`.
Standard-library static methods that carry Laterita-specific semantics live on `laterita.lang.Intrinsics` and are normally statically imported so call sites read `give(x)` and `broken()` without a qualifier:

| Intrinsic | Meaning | Spec rule |
|---|---|---|
| `Intrinsics.give(x)` | Explicitly removes ownership from `x` | OWN-07 |
| `Intrinsics.broken(reason?)` | Compilation fails if an execution path would lead to this statement | UNR-01 |
| `Intrinsics.fixed(x)` | Returns a `@fixed` borrow of `x` | MUT-42 |

To `javac` the annotations are ordinary annotations and the intrinsics ordinary static method calls, the Laterita compiler attaches the additional semantics specified in the rules above.

Java's `synchronized` keyword is not supported: there is no per-object intrinsic monitor, no `synchronized` method modifier, and no `synchronized(obj) { ... }` block.
Mutual exclusion is provided exclusively through `Mutex<T>` (STD-09) for data-bound locking and `ReentrantLock` + `Condition` (STD-10, STD-12) for the data-less / multi-condition cases.
The associated `Object.wait()`/`notify()`/`notifyAll()` methods are likewise not provided.
Condition-variable-style coordination uses `Condition` (STD-12) bound to a `ReentrantLock`.

Java's existing keywords and their meanings are otherwise preserved unless explicitly modified by this specification.

---

## LAT `.lat` Surface Forms

This section specifies the forms a `.lat` source additionally admits (COMP-06).

### LAT-00 The `.lat` surface is pure syntactic sugar

Forms LAT-01 through LAT-07 are syntactic sugar: each has an exact `.java`-surface equivalent into which the compiler desugars it.
Consequently:

- Any `.lat` source built from LAT-01–LAT-07 can be mechanically rewritten to an equivalent `.java` source and the reverse.
This rewrite is total and meaning-preserving.
- A program's meaning over the LAT-01–LAT-07 forms never depends on its file extension (COMP-06).
LAT-08 is no exception.
- A proposed sugar form that cannot be expressed as a desugaring to the `.java` surface does not belong in this section.
A construct that carries its own semantics belongs in the core spec as a `.java`-surface rule, expressed through the annotation and intrinsic surface of the `RESV` topic.

Most of these forms desugar before any type analysis.
The operator sugar LAT-07 is resolved with operand types, as Java resolves its own built-in operators, and still rewrites to a `.java`-surface method call or built-in operator.

### LAT-01 `T?` nullable-type suffix

`T?` is the `.lat` spelling of the nullable type `@Nullable T` (NULL-02).
The nullability rules NULL-01 through NULL-10 are stated on the type and apply identically to either spelling.

### LAT-02 Safe call `?.`

`expr?.method(args)` evaluates to `null` if `expr` is `null`, otherwise invokes `method` on `expr`.
The result type is `R?` where `R` is the method's return type.

Desugars to `expr == null ? null : expr.method(args)`, with NULL-06 narrowing applied to the non-null branch.

```java
String? upper = maybeName?.toUpperCase();
```

### LAT-03 Elvis operator `?:`

`a ?: b` evaluates to `a` if `a` is non-null, otherwise to `b`.
The result type is the common type of the non-nullable form of `a` and the type of `b`.

Desugars to `a != null ? a : b`, with NULL-06 narrowing on `a`.

```java
String shown = maybeName ?: "anonymous";
```

### LAT-04 Null assertion `!!`

`expr!!` converts `T?` to `T`.
If `expr` is `null`, a `NullPointerException` is thrown.
This is the only path from `T?` to `T` at the type level without a flow-sensitive narrowing (NULL-06).

Desugars to `java.util.Objects.requireNonNull(expr)`.
The Laterita compiler attaches the `T? → T` narrowing to a recognized call of `requireNonNull`, so the `.java` form carries the same typing.

### LAT-05 Inline functional-interface type `(P1, …, Pn) -> R`

The anonymous structural functional-interface expression of FN-01 is a `.lat`-only spelling, and FN-01 through FN-04 specify its type semantics and its permitted positions.
A `.java` source expresses the same SAM by declaring a nominal functional interface in the corresponding position: the synthesized shape is given by FN-03.
For the generic-bound and generic-type-argument positions admitted by FN-04, the desugaring substitutes that nominal interface in the corresponding generic position: e.g. `<F extends (T) -> R>` becomes `<F extends $Anon<T, R>>`, and `Stream<(T) -> R>` becomes `Stream<$Anon<T, R>>`.

### LAT-06 Diamond `<>` is optional on constructor calls

In `.lat` sources the diamond `<>` may be omitted from a parameterized constructor call: `new Pair("hello", 42)` denotes `new Pair<>("hello", 42)`, with type arguments inferred from context exactly as Java's diamond inference would produce.
Raw types are not part of the `.lat` surface.

The `.java` mirror writes the diamond explicitly: a diamond-less `new Pair("hello", 42)` in `.java` is the raw-type constructor and is not equivalent to the diamond-bearing form.
Migration tooling rewriting `.lat` to `.java` inserts `<>` on every parameterized-class constructor call that omits it.

```java
// laterita.lang.Pair<L, R> (ARR-04)

Pair<String, Int> p = new Pair("hello".clone(), 42);     // .lat: diamond implicit
Pair<String, Int> q = new Pair<>("hello".clone(), 42);   // also accepted in .lat
```

### LAT-07 Operator sugar

In `.lat`, the arithmetic operators `+ - * /` and unary `-` and the comparison operators `< <= > >=` are sugar for method calls.
Other operators have no method-call sugar.

Arithmetic desugars to an **instance** method annotated `@Operator(op)` (RESV).
Comparison desugars through `java.lang.Comparable`:

| Form | Desugars to | Eligibility on the left operand's type |
|---|---|---|
| `a + b` | `a.add(b)` | `@Operator(PLUS)`, one parameter |
| `a - b` | `a.subtract(b)` | `@Operator(MINUS)`, one parameter |
| `a * b` | `a.multiply(b)` | `@Operator(TIMES)`, one parameter |
| `a / b` | `a.divide(b)` | `@Operator(DIVIDE)`, one parameter |
| `-a` | `a.negate()` | `@Operator(NEGATE)`, no parameters |
| `a < b` (and `<=`, `>`, `>=`) | `a.compareTo(b) < 0` (resp. `<= > >=`) | implements `Comparable<S>`, `b` assignable to `S` |

The method name is unconstrained, so `BigDecimal.add`, `Instant.plus` / `minus`, and `Duration.negated` qualify unchanged.
`@Operator` is rejected on a `static` method or where arity does not match.
An operator parameter should be a `@fixed` borrow (`@take` discouraged).
Implementing `Comparable` is the opt-in for comparison, which carries no annotation.

`a OP b` is resolved by the static type of the left operand (or for unary `-a`, by `a`).
If that type supplies the operator applicable to the right operand, the form is the call.
Otherwise, if both operands are primitive-numeric (including GEN-01 `@Delegate` records whose generated forwarder widens to a numeric base), the built-in operator applies.
Otherwise it is a type error.
Resolution never dispatches on the right operand and never inserts implicit conversion.

Desugaring preserves Java operator precedence.
So `a + b * c` is `a.add(b.multiply(c))` and `a + b < c` is `a.add(b).compareTo(c) < 0`.
The desugared call then obeys the Java-compatible surface unchanged.

### LAT-08 Record components are public in `.lat`

In a `.lat` source the components of a `record` are `public` fields.
A record may therefore be destructed (OWN-06) through direct component access:

```java
record Span(Buffer head, Buffer tail) {}   // .lat

var s = makeSpan();
var h = give(s.head);     // head is a public component field, moved out
var t = give(s.tail);     // tail moved out; s fully destructed
```

A record declared in a `.java` source keeps private components (DES-01).

The `give`-of-a-component spelling is pure sugar (LAT-00).
It desugars through a companion POJO and a `@consuming` method the compiler generates beside the record.
For a record `Record(T left, S right)` destructed by a `.lat` source the generated members are:

```java
@AllArgsConstructor public final class Record$AsClass { public T left; public S right; }

// on Record:
@consuming Record$AsClass intoClass() { return new Record$AsClass(give(this.left), give(this.right)); }
```

`intoClass()` is `@consuming` (OWN-15) and runs inside the record's own body, where the components are accessible (OWN-06).
The companion's fields are `public`, so it destructs on the plain `.java` surface (DES-01).
A destruction site rewrites accordingly:

```java
record Span(Buffer head, Buffer tail) {}   // .lat

var s = makeSpan();        // returns an owned instance
// .lat surface:              desugared .java:
                           // Span$AsClass s$class = s.intoClass();
var h = give(s.head);      // var h = give(s$class.head);
var t = give(s.tail);      // var t = give(s$class.tail);
```

The record keeps its `record` identity in the `.java` mirror.
`intoClass()` and the companion are generated members like any in the `GEN` topic.

---

## NABI Native ABI Guarantees

### NABI-01 Single-field aggregate layout and calling convention

A `final` immutable class (MUT-10) or record with exactly one field or component has the same size, alignment, and calling-convention treatment as that field or component: no wrapper, object header, or padding, passed and returned in the same register or registers as a value of the field's type.

---

## GEN Code Generation Annotations

Laterita supports the stable [Project Lombok](https://projectlombok.org/) annotations natively.
A `.java` or `.lat` source using them compiles unchanged and produces the same observable result a Lombok build produces on the JVM.
The compiler generates the members at compile time, and generated members are visible to the type checker and overload resolution.

A generator supplies the Laterita annotation a generated member implies (e.g. `setX(@take X x)` when x is owned).
It also deduces the Laterita class-level annotations: a class annotated with `@Value` is automatically also `@fixed` (MUT-10).

An explicitly declared member with the same name and erased parameter types shadows the generated one.
Annotations and attributes not listed in this section pass through to downstream annotation processors unchanged.

GEN-01, GEN-02, GEN-03, and GEN-08 carry generation rules of their own and are stated individually.
The remaining generators are tabulated at the end of this topic.

### GEN-01 `@Delegate`

`@Delegate` on a field or record component generates, for each `public` instance method of the field's declared type, a forwarding method on the owner that calls the same method on the field.
`Object` methods (`equals`, `hashCode`, `toString`) and `static` methods are not forwarded.
Forwarder return types are the source method's own (they *decay*), and ownership annotations are propagated: a `@consuming` source yields a `@consuming` forwarder, a `@readonly` source yields a `@readonly` forwarder.

Per the shadowing rule, declaring the methods you want to change and letting `@Delegate` fill in the rest is the supported way to adapt a forwarded surface.

`@Delegate` on a `@Nullable` field is a compile-time error.
When two `@Delegate` fields would generate the same signature, that signature is a compile-time error until an explicit declaration resolves it.
Cyclic delegation, where the delegated type transitively forwards back to the owner, is a compile-time error.

Two optional attributes mirror Lombok: `types` restricts forwarding to the methods of the listed types instead of the field's whole declared surface, and `excludes` removes the methods of the listed types.
Both attributes accept generic types directly, and a generic method forwards with its concrete instantiated signature (COMP-02).
The generics limitations Lombok documents for `@Delegate` do not apply.

A single-component record carrying `@Delegate` is the *newtype idiom*, a distinct nominal type exposing the wrapped surface.
NABI-01 gives it the component's layout, and COMP-08 admits its forwarders for inlining.
The component accessor is the only path back to the wrapped value, and no implicit widening exists.

### GEN-02 `@Getter` and `@Setter`

`@Getter` on a field, or on the class for all fields, generates a `public @readonly(InheritFrom.RECEIVER)` bean accessor: `getFieldName()` (`isFieldName()` for a `boolean`) returning `@bound T` (OWN-18), a borrow of the field with the mutability of the receiver (MUT-17).
`@Getter(lazy = true)` on a final field generates a memoized accessor that computes the value once on first call.

`@Setter` requires a mutable class (MUT-10) and generates a setter for each non-`final` non-`static` field.
`@Setter` on a field carries the same requirement.
The setter annotation depends on the field variable:

```java
@Setter T owned;
public void setOwned(@take T value);
@Setter @borrow S borrowed;
public void setBorrowed(@take @borrow S value);   // stores the borrow into this, caps the instance (OWN-21)
```

### GEN-03 Constructor generators

`@AllArgsConstructor` generates a constructor containing every field.
`@NoArgsConstructor` generates one with no parameters.
`@RequiredArgsConstructor` generates a constructor with a parameter, in declaration order, for every field that carries no initializer (OWN-11).
In all three, an owned field's parameter is marked `@take` and a `@borrow` field's parameter is bare, and therefore borrows (OWN-13).
`@RequiredArgsConstructor` and `@NoArgsConstructor` treat `@Nullable` fields as initialized and set them to `null`, satisfying OWN-11 without an explicit initializer.
`@NoArgsConstructor` therefore requires every non-`@Nullable` field to carry an initializer.

```java
@AllArgsConstructor class Shipment {
    String trackingId;        // owned field
    @borrow Carrier carrier;  // borrowed field
}
// generated: Shipment(@take String trackingId, Carrier carrier)
```

The lifetime of a `Shipment` instance is bound to the `Carrier` it borrows (LIFE-03).

### GEN-08 `@With`

`@With` generates, for each field, a `public @readonly [@bound] X withFieldName([@take|@bound] [@fixed] T value)` returning a new instance with that field set to `value`.
The `@bound` return annotation is generated when any other field of `X` is `@borrow`: the result's lifetime is bound to `this`.
The parameter annotations are generated conditionally:

- `@take` when the field is owned,
- bare when the field is `@borrow`: the result's lifetime is also bound to `value`,
- `@fixed` when the field is `@fixed`.

Internally, other owned fields are `clone()`d from `this` (OBJ-02).
`@With` needs a constructor covering all fields, as in Lombok.

### GEN-04 through GEN-07, GEN-09 through GEN-15

Each of these generates what Lombok documents, with the members and the Laterita annotations below.

| Code | Annotation | Generated | Laterita specifics |
|---|---|---|---|
| GEN-04 | `@ToString` | `public @readonly String toString()`, the class name followed by the field values in declaration order, comma-separated in parentheses | `.Exclude` omits a field, `.Include` adds a method result |
| GEN-05 | `@EqualsAndHashCode` | `public @readonly boolean equals(@fixed Object)` and `public @readonly int hashCode()` over all instance fields in declaration order | `.Exclude` omits a field |
| GEN-06 | `@Data`, `@Value` | `@Data` bundles `@Getter`, `@Setter`, `@ToString`, `@EqualsAndHashCode`, `@RequiredArgsConstructor`. `@Value` bundles `@Getter`, `@ToString`, `@EqualsAndHashCode`, `@AllArgsConstructor`, with all fields and the class `final` | `@Data` requires a mutable class (GEN-02), and `@Value` makes the class `@fixed` (MUT-10) |
| GEN-07 | `@Builder` | a nested mutable `class Builder`, a static `builder()`, a fluent method per field returning the builder, and `build()` | owned fields are taken `@take` through the builder |
| GEN-09 | `@NonNull` | nothing | accepted and adds nothing (NULL-01), and is a compile-time error together with `@Nullable` |
| GEN-10 | `@SneakyThrows` | nothing | accepted and has no effect (EXC-05) |
| GEN-11 | `@Synchronized` | `private final ReentrantLock $lock`, or `$LOCK` on a static method, acquired through a `LockGuard` (STD-11) | `@Synchronized("name")` uses the named lock field |
| GEN-12 | `@Cleanup` | a call to `close()` at the end of the local variable's block | `@Cleanup("method")` selects a different method |
| GEN-13 | `@Log` and its variants `@Slf4j`, `@Log4j`, `@Log4j2`, `@CommonsLog`, `@JBossLog`, `@Flogger`, `@CustomLog`, `@XSlf4j` | a `private static final` logger field named `log`, initialized for the annotated type | none |
| GEN-14 | `val`, `var` | nothing | `val` is unsupported and spelled `final var` (MUT-40, MUT-20), and `var` is unchanged (OQ-34) |
| GEN-15 | `@StandardException` | on a `Throwable` subclass, the four standard constructors (no-arg, `(String)`, `(Throwable)`, `(String, Throwable)`), each chaining to `super` | none |
