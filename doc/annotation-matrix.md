# Laterita annotation placement matrix

This is a non-normative overview of where the variable and method modifiers may appear and which pairs may combine.
It is derived from `laterita-spec.md` and the `RESV` annotation table.
Every cell cites the governing rule, so the spec remains the source of truth.

The eight columns and rows are `bare`, `final`, `@mut`, `@mutating`, `@take`, `@own`, `@borrow`, `@bound`, in that order.
`bare` means no modifier, so a `bare × X` cell equals `X` on its own and repeats the `X` diagonal.
Each matrix is symmetric, and a diagonal cell `X × X` is the modifier `X` used alone at that position.

## Legend

| Cue | Meaning |
|---|---|
| ⬜ default | The unannotated state, which is the default for this position. |
| 🟢 CODE | Allowed, with the governing rule or rules (at most two). |
| 🔴 ~~CODE~~ | Forbidden by the named rule. |
| ⬛ ~~RESV~~ | The modifier does not target this position, or the pair is unlisted, so it does not compile (RESV). |
| 🟧 ↔ | Forbidden because the two modifiers are opposite intents. |

---

## 1. Class or interface declaration

```java
class User { }              // value class (bare default)
final class Connection { }  // final
@mut class Counter { }      // mutable surface
```

| · | bare | final | @mut | @mutating | @take | @own | @borrow | @bound |
|---|---|---|---|---|---|---|---|---|
| **bare** | ⬜ default | 🟢 DROP-09 | 🟢 MUT-05 | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **final** | 🟢 DROP-09 | 🟢 DROP-09 | 🟢 MUT-05 | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@mut** | 🟢 MUT-05 | 🟢 MUT-05 | 🟢 MUT-05 | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@mutating** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@take** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@own** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@borrow** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@bound** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |

Notes.
A class declaration also carries `@borrowCapped` (LIFE-04) and `@local` or `@local(false)` (STD-07), which are outside this eight-modifier set.
`final` is Java's no-subclass lock and a precondition for an `onDrop()` implementation (DROP-09).
A `final @mut` class is a final mutable class, which is allowed.
The remaining five modifiers never target a type declaration.

---

## 2. Method (receiver and return)

```java
String upperCase(String s);   // bare receiver, owned return (default)
@mutating void inc();         // mutating receiver
@bound Entry get(String key); // return bound to this
@mut @bound E first();        // mutable-borrow return bound to this
```

| · | bare | final | @mut | @mutating | @take | @own | @borrow | @bound |
|---|---|---|---|---|---|---|---|---|
| **bare** | ⬜ default | 🟢 HIER-05 (a) | 🟢 MUT-01 | 🟢 MUT-08 | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🟢 OWN-18 |
| **final** | 🟢 HIER-05 (a) | 🟢 HIER-05 (a) | 🟢 MUT-01 | 🟢 MUT-08 | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🟢 OWN-18 |
| **@mut** | 🟢 MUT-01 | 🟢 MUT-01 | 🟢 MUT-01 | 🟢 MUT-08 MUT-01 | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🟢 OWN-18 MUT-01 |
| **@mutating** | 🟢 MUT-08 | 🟢 MUT-08 | 🟢 MUT-08 MUT-01 | 🟢 MUT-08 | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🟢 MUT-08 OWN-18 |
| **@take** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@own** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@borrow** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@bound** | 🟢 OWN-18 | 🟢 OWN-18 | 🟢 OWN-18 MUT-01 | 🟢 MUT-08 OWN-18 | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🟢 OWN-18 |

Notes.
(a) `final` on a method is Java's override lock and interacts with override variance (HIER-05). It is orthogonal to every receiver and return mode.
`@mut`, `@mutating`, and `@bound` combine: a receiver-mutating method may return a mutable borrow bound to `this`.
`@bound` on a method binds the return to `this` (OWN-18) and is rejected on a `static` method, where the parameter form `@bound` (OWN-17) is used instead.
The other receiver mode `@consuming` (OWN-15) is outside this eight-modifier set and composes with `@mutating`.
`@take`, `@own`, and `@borrow` never target a method.

---

## 3. Field

```java
@mut class User {
    final String id;          // final, set once
    String name;              // owned (bare default)
    @mut List<Session> sess;  // mutate-through
    @borrow Carrier carrier;  // borrow slot
}
```

| · | bare | final | @mut | @mutating | @take | @own | @borrow | @bound |
|---|---|---|---|---|---|---|---|---|
| **bare** | ⬜ default | 🟢 OWN-11 | 🟢 MUT-07a | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | 🟢 OWN-09 | ⬛ ~~RESV~~ (a) |
| **final** | 🟢 OWN-11 | 🟢 OWN-11 | 🟢 MUT-07b | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | 🟢 OWN-09 | ⬛ ~~RESV~~ (a) |
| **@mut** | 🟢 MUT-07a | 🟢 MUT-07b | 🟢 MUT-07a | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | 🟢 OWN-09 MUT-07a (b) | ⬛ ~~RESV~~ (a) |
| **@mutating** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@take** | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ |
| **@own** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@borrow** | 🟢 OWN-09 | 🟢 OWN-09 | 🟢 OWN-09 MUT-07a (b) | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | 🟢 OWN-09 | ⬛ ~~RESV~~ (a) |
| **@bound** | ⬛ ~~RESV~~ (a) | ⬛ ~~RESV~~ (a) | ⬛ ~~RESV~~ (a) | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ (a) | ⬛ ~~RESV~~ (a) |

Notes.
(a) `@bound` does not target a field.
A field that holds a borrow is declared `@borrow` (OWN-09), and its enclosing instance becomes `@bound` automatically.
(b) `final`, `@mut`, and `@borrow` combine into one field, a set-once mutate-through borrow slot such as `@mut @borrow Sink sink` (DROP-11).
`@take` is rejected on every field by OWN-10.

---

## 4. Local variable

```java
var c = makeList();           // follows the initializer (bare default)
final var pi = 3.14159;       // locked slot
@mut var sb = new StringBuilder();  // mutate-through
@bound var v = cache.get(k);  // borrowed value (usually inferred)
```

| · | bare | final | @mut | @mutating | @take | @own | @borrow | @bound |
|---|---|---|---|---|---|---|---|---|
| **bare** | ⬜ default | 🟢 MUT-03 | 🟢 MUT-02 | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ (a) | 🟢 OWN-09 (b) |
| **final** | 🟢 MUT-03 | 🟢 MUT-03 | 🟢 MUT-02 | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ (a) | 🟢 OWN-09 (b) |
| **@mut** | 🟢 MUT-02 | 🟢 MUT-02 | 🟢 MUT-02 | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ (a) | 🟢 OWN-09 MUT-02 (b) |
| **@mutating** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@take** | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ | 🔴 ~~OWN-10~~ |
| **@own** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@borrow** | ⬛ ~~RESV~~ (a) | ⬛ ~~RESV~~ (a) | ⬛ ~~RESV~~ (a) | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ (a) | ⬛ ~~RESV~~ (a) |
| **@bound** | 🟢 OWN-09 (b) | 🟢 OWN-09 (b) | 🟢 OWN-09 MUT-02 (b) | ⬛ ~~RESV~~ | 🔴 ~~OWN-10~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ (a) | 🟢 OWN-09 (b) |

Notes.
(a) A local's borrowed or owned status comes from its initializer (OWN-02), not from a `@borrow` annotation, so `@borrow` does not target a local declaration.
(b) `@bound` marks that the local holds a borrowed value, but it is normally inferred from the right-hand side (OWN-02, TARG-01) and is rarely written.
`@take` is rejected on every local by OWN-10.

---

## 5. Parameter

```java
void inspect(String s);            // shared borrow (bare default)
void store(@take String s);        // ownership transfer
void edit(@mut Buffer b);          // mutable borrow
String first(@bound String s);     // return bound to s
void attach(@take @borrow Sink s); // retained borrow, caps this
```

| · | bare | final | @mut | @mutating | @take | @own | @borrow | @bound |
|---|---|---|---|---|---|---|---|---|
| **bare** | ⬜ default | 🟢 MUT-04 (a) | 🟢 MUT-04 | ⬛ ~~RESV~~ | 🟢 OWN-13 | ⬛ ~~RESV~~ | 🟢 OWN-21 (b) | 🟢 OWN-17 |
| **final** | 🟢 MUT-04 (a) | 🟢 MUT-04 (a) | 🟢 MUT-04 (a) | ⬛ ~~RESV~~ | 🟢 OWN-13 (a) | ⬛ ~~RESV~~ | 🟢 OWN-21 (a)(b) | 🟢 OWN-17 (a) |
| **@mut** | 🟢 MUT-04 | 🟢 MUT-04 (a) | 🟢 MUT-04 | ⬛ ~~RESV~~ | 🟢 MUT-04 | ⬛ ~~RESV~~ | 🟢 MUT-04 OWN-21 (b) | 🟢 OWN-17 MUT-04 |
| **@mutating** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@take** | 🟢 OWN-13 | 🟢 OWN-13 (a) | 🟢 MUT-04 | ⬛ ~~RESV~~ | 🟢 OWN-13 | ⬛ ~~RESV~~ | 🟢 OWN-21 (c) | 🟧 ↔ (d) |
| **@own** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@borrow** | 🟢 OWN-21 (b) | 🟢 OWN-21 (a)(b) | 🟢 MUT-04 OWN-21 (b) | ⬛ ~~RESV~~ | 🟢 OWN-21 (c) | ⬛ ~~RESV~~ | 🟢 OWN-21 (b) | 🟢 OWN-17 (b) |
| **@bound** | 🟢 OWN-17 | 🟢 OWN-17 (a) | 🟢 OWN-17 MUT-04 | ⬛ ~~RESV~~ | 🟧 ↔ (d) | ⬛ ~~RESV~~ | 🟢 OWN-17 (b) | 🟢 OWN-17 |

Notes.
(a) Every parameter slot is already final (MUT-04), so an explicit `final` is accepted but adds nothing.
(b) `@borrow` on a parameter retains nothing without `@take` and then equals a plain borrow (OWN-21), so it is meaningful only as `@take @borrow`.
(c) `@take @mut @borrow` is the move-an-exclusive-borrow parameter (TARG-05), while `@take @borrow` copies a shared borrow in.
(d) `@take` consumes the parameter while `@bound` makes it the source of a returned borrow. A borrow of a consumed value would dangle, so the two are opposite and cannot combine.

---

## 6. Generic type-parameter declaration

```java
record Pair<L, R>(L left, R right) { }  // unconstrained (bare default)
@mut class Mutex<@own T> { }            // rejects a borrowed argument
```

| · | bare | final | @mut | @mutating | @take | @own | @borrow | @bound |
|---|---|---|---|---|---|---|---|---|
| **bare** | ⬜ default | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🟢 TARG-06 (a) | ⬛ ~~RESV~~ (a) | ⬛ ~~RESV~~ |
| **final** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@mut** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@mutating** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@take** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@own** | 🟢 TARG-06 (a) | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🟢 TARG-06 | ⬛ ~~RESV~~ (a) | ⬛ ~~RESV~~ |
| **@borrow** | ⬛ ~~RESV~~ (a) | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ (a) | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@bound** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |

Notes.
(a) `@own` is the only modifier that targets a type-parameter declaration. It is the dual of the type-argument `@borrow` (TARG-01, TARG-06): `@own` forbids a borrowed argument where `@borrow` supplies one. `@borrow` is written on the argument, never on the parameter, so the two never meet in one cell here.

---

## 7. Generic type argument (type use)

```java
List<Foo>            a;  // owned element (bare default)
List<@borrow Foo>    b;  // borrow element
@mut List<@mut Foo>  c;  // mutable element, container is @mut
```

| · | bare | final | @mut | @mutating | @take | @own | @borrow | @bound |
|---|---|---|---|---|---|---|---|---|
| **bare** | ⬜ default | ⬛ ~~RESV~~ | 🟢 TARG-03 (a) | ⬛ ~~RESV~~ | 🔴 ~~TARG-02~~ | ⬛ ~~RESV~~ (b) | 🟢 TARG-01 | ⬛ ~~RESV~~ (c) |
| **final** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🔴 ~~TARG-02~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@mut** | 🟢 TARG-03 (a) | ⬛ ~~RESV~~ | 🟢 TARG-03 (a) | ⬛ ~~RESV~~ | 🔴 ~~TARG-02~~ | ⬛ ~~RESV~~ | 🟢 TARG-01 TARG-03 (a) | ⬛ ~~RESV~~ (c) |
| **@mutating** | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🔴 ~~TARG-02~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ |
| **@take** | 🔴 ~~TARG-02~~ | 🔴 ~~TARG-02~~ | 🔴 ~~TARG-02~~ | 🔴 ~~TARG-02~~ | 🔴 ~~TARG-02~~ | 🔴 ~~TARG-02~~ | 🔴 ~~TARG-02~~ | 🔴 ~~TARG-02~~ |
| **@own** | ⬛ ~~RESV~~ (b) | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ | 🔴 ~~TARG-02~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ (b) | ⬛ ~~RESV~~ |
| **@borrow** | 🟢 TARG-01 | ⬛ ~~RESV~~ | 🟢 TARG-01 TARG-03 (a) | ⬛ ~~RESV~~ | 🔴 ~~TARG-02~~ | ⬛ ~~RESV~~ (b) | 🟢 TARG-01 | ⬛ ~~RESV~~ (c) |
| **@bound** | ⬛ ~~RESV~~ (c) | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ (c) | ⬛ ~~RESV~~ | 🔴 ~~TARG-02~~ | ⬛ ~~RESV~~ | ⬛ ~~RESV~~ (c) | ⬛ ~~RESV~~ (c) |

Notes.
(a) `@mut` on an element is admitted only where the enclosing generic is itself `@mut` at that use (TARG-03). On a shared container it is rejected.
(b) `@own` constrains the type-parameter declaration, not the argument (Section 6). It does not appear on a use.
(c) `@bound` is not written on an argument, but a `@bound` return can substitute through a `@borrow` argument and collapse to a single borrow (TARG-04).
`@take` is rejected in every type argument by TARG-02.

---

## Cross-cutting observations

The matrices expose a few structural facts worth stating directly.

`@take` is the most position-restricted modifier.
It is legal only on a parameter (OWN-13) and is actively rejected on fields and locals (OWN-10) and in type arguments (TARG-02).

`@own` and `@borrow` are duals that never share a cell.
`@own` lives on the type-parameter declaration (TARG-06) and `@borrow` on the argument (TARG-01), so their opposition is structural rather than a forbidden pairing.

The only intra-position opposite is `@take` against `@bound` on a parameter.
Consuming a parameter and lending it back through the return are contradictory, so the pair is excluded (OWN-17, OWN-21).

`@bound` changes meaning by position.
On a parameter it names a return source (OWN-17), on a method or return it binds the return to `this` (OWN-18), and on a local it marks a borrowed value (OWN-09).

`@bound` is not a field annotation.
A field that holds a borrow is declared `@borrow` (OWN-09), and the enclosing instance becomes `@bound` automatically, so there is no standalone `@bound` field.
