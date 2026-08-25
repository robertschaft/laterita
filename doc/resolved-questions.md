# Laterita Resolved Questions and Settled Decisions

This document is the registry of closed decisions.
It has two parts.

1. **Rejected alternatives**: design options that were evaluated and discarded, listed under the name a contributor is likely to reach for.
**If a mechanism is listed there, the decision is closed: do not reopen it, re-propose it, or raise it as an open question without new evidence that directly contradicts the recorded reasoning.**
2. **Resolved open questions**: tombstones for the `OQ-NN` entries that have been answered.

The full reasoning lives in `laterita-reasoning.md`.
Unresolved questions live in `laterita-open-questions.md`.

## Rejected alternatives

### `Send` / `Sync` marker interfaces, as separate cross-thread move and share markers

Rejected.
A single negative marker, `@local`, covers both the move and the borrow restriction.
The `Send`-but-not-`Sync` distinction is rare and unusable without the fine-grained borrow reasoning Java developers do not expect.

Where: STD-07, OQ-04, reasoning "Why `@local`, not `Send`"

### Runtime reflection and dynamic class loading

Rejected.
The language and its standard library provide no runtime reflection.
The use cases are served by compile-time annotation processing.

Where: COMP-05, OQ-03, reasoning "Why no reflection"

### `Optional<T>`, or Rust's `Option<T>`, as the optionality type

Rejected.
Optionality is a nullable type (`@Nullable T`, spelled `T?` in `.lat`) with flow narrowing, not a wrapper type.

Where: NULL-01 through NULL-10, reasoning "Why no separate Optional<T>"

### `finalize()` as the cleanup hook

Rejected.
Cleanup is `onDrop()`, invoked from compiler-emitted code.
A finalizer runs at an unpredictable time and only under a garbage collector.

Where: DROP-01, reasoning "Why not `finalize()`"

### `synchronized`, intrinsic object monitors, and `Object.wait` / `notify` / `notifyAll`

Rejected.
Mutual exclusion is provided by `Mutex<T>` where the lock owns the data, and by `ReentrantLock` with `Condition` where it does not.

Where: RESV, STD-09 through STD-12, reasoning "Why `synchronized` is removed and replaced by `ReentrantLock` + `Condition`"

### Rust's lifetime syntax (`'a`), or a `from` keyword

Rejected.
A borrow source is named by the `@bound` annotation.
Named lifetime variables are not part of the surface.

Where: OWN-17, OWN-18, reasoning "Why `@bound` instead of `'a` or `from`"

### A cycle collector or tracing collector for reference cycles

Rejected.
A cycle of `Rc` or `Arc` handles leaks, and `WeakReference<T>` is what breaks the back edge.
Laterita matches Rust's memory model here rather than exceeding it.

Where: STD-01, STD-03, reasoning "Why cycles leak"

### One marker for both giving and taking ownership

Rejected.
Giving and taking are inverse roles.
`give(...)` at the use site and `@take` on the signature keep each end explicit.

Where: OWN-07, OWN-13, reasoning "Why `@take` on parameters and `give` as the move-expression"

### Named closure interfaces, one nominal functional interface per callback shape

Rejected.
The anonymous structural form avoids an explosion of interface names that no naming convention survives once parameter modes enter the type system.

Where: FN-01, OQ-05, reasoning "Why structural rather than nominal"

### `@take` on a local variable declaration

Rejected.
A local variable's ownership is fixed by its initializer.
`@take` is published contract and appears only on a parameter.

Where: OWN-13, reasoning "Why `@take` on parameters and `give` as the move-expression"

### One mutability annotation covering both assignment and modification

Rejected.
The two are independent axes.
`final` already means that a variable may be assigned only once, and modifying the object it refers to is the separate property.
Folding both onto one annotation makes the ordinary `private final List<Item> items` inexpressible, since one half would undo the other.
`final` governs assignment, `@ro` governs modification, and a parameter is always `final`.

Where: MUT-40, MUT-20, MUT-41, MUT-21, MUT-22, reasoning "Why variable and object mutability are separate axes", reasoning "Why fields default to mutable"

### A closure capture that reassigns the captured local variable

Rejected.
`javac` requires every captured local variable to be final or effectively final (JLS 15.27.2), so the form cannot appear in a `.java` source, the same bar that excludes annotation-only overloads.
A `.lat`-only spelling desugaring to a compiler-generated holder is rejected as well, since it would rewrite every later use of the local variable in the enclosing method and stretch LAT-00 for marginal ergonomics.
A captured local variable must be effectively final, and a mutate closure modifies the object through a mutable capture instead, which is the checked form of Java's holder idiom.

Where: CLO-01, CLO-04, MUT-40, COMP-06, OQ-35, reasoning "Why a captured local must be effectively final"

### Receiver mutation declared by the mutability annotation, on the return type or on an explicit `this`

Rejected.
The mutability axis has one negative annotation and no positive one, so there is nothing to reuse: a claim to mutate the receiver has to be stated positively, and `@ro` in modifier position states the opposite.
Nor can the receiver take the mutable default the way a parameter does: every method has a receiver, so that default would demand a mutable receiver at every call and leave an immutable class with no callable method at all.
A marker on an explicit `this` forces every mutating method to spell out a `(Self this)` parameter.
The receiver takes the mutable default like a parameter, and `@readonly` withdraws it.

Where: MUT-41, MUT-13, reasoning "Why a bare parameter lends mutably while a bare receiver does not", reasoning "Why methods declare their receiver effect in the signature"

### One three-valued mode annotation on a functional-interface variable (`@ro`, bare, `@take`)

Rejected.
Combining ownership of the functional-interface value with the right to invoke its single abstract method makes "owns a callback and invokes it many times" inexpressible.
The two are separate axes: call mode belongs to the type, and the variable's mode belongs to the variable.

Where: CLO-03, CLO-04, reasoning "Why call mode and variable mode are separate"

### Unchecked array indexing, whether as Rust's `get_unchecked`, an `@unsafe`-gated raw index, or an annotation suppressing the check

Rejected.
Every `a[i]` is bounds-checked, inside a `private @unsafe` method as everywhere else.
The unlock buys no expressiveness the checked form lacks, its correctness depends on whatever produced the index rather than on a local invariant, and a redundant check is elidable by the compiler.
Raw unchecked memory access stays behind `Heap<T>`.

Where: ARR-05, UNS-02, STD-06, reasoning "Why array indexing is never unchecked"

### `InheritFrom` on a `@bound` parameter (`@bound(InheritFrom.SOURCE)`), so a static method's return inherits its argument's mutability

Rejected.
It adds an inheritance axis to the language to spare one signature in the `.java` array mirror.
The mirror declares the two forms as `splitAt` and `splitMutableAt` instead, which is the distinct-names pattern OWN-13 already requires wherever mutability annotations would otherwise carry an overload.

Where: ARR-02, MUT-17, OWN-13, reasoning "Why the cross-thread story splits in two (ARR-01, ARR-02)"

### A `record` as the general-purpose pair type (`record Pair<L, R>(L left, R right)`)

Rejected.
A record is immutable by construction, so its components are `final` and `@ro` and every half borrowed through it is shared, which is what `splitAt` must not produce.
`Pair` is an ordinary mutable class with `public final` components, and a record stays uniformly immutable.

Where: ARR-04, MUT-11, MUT-17, reasoning "Why the cross-thread story splits in two (ARR-01, ARR-02)"

### Separate `BoundPair` and `OwnedPair` return types for array splitting

Rejected.
The substitution rule of TARG-01 lets the general-purpose `Pair<L, R>` carry variable modifiers on its type arguments, so no dedicated pair type is needed.

Where: ARR-04, TARG-01, OQ-19, OQ-21

### Two string types, one owned and one borrowed (`String` and `&str`)

Rejected.
There is one `String` type, and the compiler tracks per variable whether it is owned or borrowed.
`clone()` is the general escape from any owned-against-borrowed mismatch.

Where: STR-02, OBJ-02, OQ-08, reasoning "Why owned vs. borrowed strings tracked per-variable"

### Unbounded operator overloading, as in C++, where any operator carries arbitrary semantics

Rejected.
The unbounded surface produces unreadable code, `<<` as stream insertion and expression-template libraries among it, and hides cost behind ordinary-looking syntax.
Laterita's operator surface is the closed set of LAT-07.

Where: LAT-07, OQ-24, reasoning "Why operators are bounded sugar"

### Interface-based operator overloading, as in Rust, with `Add` and `Mul` traits, a by-value against by-borrow matrix, and coherence rules

Rejected.
The matrix and its coherence rules are more machinery than a Java-shaped language wants.
The bounded, annotation-gated sugar of LAT-07 gets the same ergonomics through ordinary overload resolution.

Where: LAT-07, OQ-24, reasoning "Why operators are bounded sugar"

### One arithmetic interface (`Arithmetic<Self>`, `Numeric<Self>`) gating `+ - * /` the way `Comparable` gates ordering

Rejected.
It assumes a homogeneous closed operation, `Self op Self` yielding `Self`, but `Money / Money` is a scalar, `Money * Money` is meaningless, `Vec * Vec` is ambiguous, and `Instant - Instant` is a `Duration`.
Expressing those requires per-operator operand and result types, as Rust's `Add<Rhs, Output>` does, and Java cannot implement one generic interface twice with different type arguments.
The per-method `@Operator(op)` annotation carries each operator's own signature and has no such limit.

Where: LAT-07, OQ-24, reasoning "Why operators are bounded sugar"

### A `@newtype` or `@valuewrapper` annotation on a single-component record

Rejected.
The annotation would only restate that `@Delegate` is present, which the compiler already reads from the component list.
The newtype idiom emerges from composing GEN-01, NABI-01, and COMP-08, so no dedicated annotation is introduced.

Where: GEN-01, NABI-01, COMP-08, OQ-26, reasoning "How Laterita supports the newtype idiom"

### A field-less subclass as the newtype mechanism (`class Email extends String`)

Rejected.
A subclass widens to its superclass implicitly, so `String s = email` compiles with no explicit conversion and the distinct type is silently lost at every call boundary.
A record whose component carries `@Delegate` is a distinct nominal type with no widening to the component's type, so the accessor is the only path back.

Where: GEN-01, OQ-26, reasoning "How Laterita supports the newtype idiom"

### An anonymous functional interface as the declared type of a field or a local variable

Rejected.
A stored function value carries obligations a nominal functional interface exists to meet: a name, documentation, and somewhere for an IDE to navigate to.
The anonymous form is written only in the positions a caller reads: a parameter type, a return type, a generic bound, and a generic type argument.

Where: FN-04, FN-03, reasoning "Why anonymous functional interfaces are restricted to positions read at a call site"

### Receiver consumption declared by `@take` on an explicit `this` parameter

Rejected.
It forces a `(@take ClassName this)` parameter onto every consuming method, restating the class name and adding a parameter that carries neither a name nor a use.
It also makes the two annotated receiver modes read in different shapes, `@readonly` in modifier position against `@take` in parameter position.
Receiver consumption is the dedicated `@consuming` method annotation instead, and `@take` keeps its single role on the parameter side.

Where: OWN-15, reasoning "Why methods declare consumption of `this` with `@consuming`"

### Ownership annotations as part of the overload signature

Rejected.
`javac` ignores annotations when it computes the overload signature, so two same-name methods differing only in `@take`, `@ro`, `@bound`, `@readonly`, or `@consuming` are a duplicate declaration, and the rule is unimplementable on the surface it would live in.
There is no caller-side disambiguator for a `@ro` or receiver-mode overload either, and a tie-breaker that silently flipped ownership behavior is exactly the invisibility these annotations exist to eliminate.
An API needing several shapes of one operation declares distinct method names, as `splitAt` and `splitOff` do.

Where: OWN-13, ARR-01, reasoning "Why ownership annotations are not part of overload identity"

### Requiring the diamond `<>` on a parameterized constructor call in `.lat`

Rejected.
Java requires the diamond because the diamond-less form is the raw-type constructor.
Laterita has no raw types, so the diamond carries no information in `.lat` and is optional there.
The `.java` mirror inserts it back.

Where: LAT-06, OQ-31, reasoning "Why `.lat` drops the diamond on constructor calls"

### Restricting the anonymous functional-interface form to shared-call

Rejected.
It forces every mut-call and once-call callback onto a nominal interface, among them the chunk body of ARR-01, the critical-section closure of `Mutex.with`, and any one-shot teardown hook, which reintroduces the pressure on interface names that FN-01 exists to remove.
The anonymous form carries a `@readonly` or `@consuming` prefix for the non-mutating and the consuming case.

Where: FN-01, CLO-03, OQ-29, reasoning "Why the anonymous form spells call mode with `@readonly` / `@consuming` prefix"

### Defaulting the anonymous functional-interface call mode to once-call

Rejected.
It penalizes the dominant case, a read callback invocable many times, forces an annotation onto every `forEach`-shaped API, and breaks a shared-call return that borrows a captured closure and calls it repeatedly.
Defaulting to shared-call matches both what a Java developer expects and Rust's `Fn`.

Where: FN-01, OQ-29, reasoning "Why the anonymous form spells call mode with `@readonly` / `@consuming` prefix"

### Reading the variable's mutability as the call mode of the functional interface it holds

Rejected.
It conflates the variable's mutability with the receiver mode of the single abstract method, which are separate axes, and it makes an owned functional-interface variable invocable many times inexpressible.
Call mode is a prefix on the type expression, and `@ro` keeps its ordinary meaning on the variable.

Where: FN-01, CLO-03, CLO-05, OQ-29

### Locking only the fields the `onDrop()` body reads, leaving the rest movable

Rejected.
The locked set is a function of the `onDrop()` body and of every method it transitively calls on `this`, so whether a move is legal is invisible at the `give` site: adding one field read in cleanup silently breaks a distant `give`.
The analysis costs more than it buys, since a class with an `onDrop()` is a resource-owning leaf rarely worth taking apart in the first place.
Laterita adopts Rust's type-level rule (`E0509`): a class that implements `onDrop()` is moved whole.
The rare case that needs both cleanup and surrender uses an explicit handle-and-extract idiom.

Where: DROP-08, reasoning "Why a class with `onDrop()` cannot be destructed"

### Reading `@borrow` fields in `onDrop()` without a class-level opt-in, whether always permitted or always forbidden

Rejected.
Always permitting it hides from the reader that a held borrow tightens its source's lifetime, and burdens the ordinary owned-handle leaf with a constraint it does not need.
Always forbidding it makes a lock guard, a span timer, and a scope-bound writer inexpressible.
The `@borrowCapped` class annotation gates the read: it is visible on the class, paid only by a class that reads a borrow at drop, and satisfied within a scope by the ordering rule of DROP-02.

Where: LIFE-04, DROP-11, reasoning "Why `@borrowCapped` rather than always capping at the borrows"

### Restricting `@borrowCapped` to `final` classes

Rejected.
`final` governs only where a cleanup body may be declared.
The `@borrowCapped` lifetime contract is a separate axis, applies to any class, and is inherited, so the contract can sit on an extensible base class while the `onDrop()` that reads the borrow sits on a `final` leaf.
Coupling the two would force every such base class to be a leaf.

Where: LIFE-04, DROP-09, reasoning "Why `@borrowCapped` rather than always capping at the borrows"

### Destruction through a method return or a closure capture, as Rust 2021 captures a field path (RFC 2229)

Rejected.
A destruction names one accessible field directly, so that per-field move state stays statically decidable.
A method result is an ordinary owned or borrowed value, a record accessor returns a borrow and cannot be moved, and a closure captures whole variables rather than field paths.
The idiom is to destruct into owned local variables first and capture those, which is the pattern Rust used before 2021.
Capturing a field by borrow is unaffected.

Where: OWN-06, LAT-08, reasoning "Why destruction is restricted to direct field access"

### Keeping a destructed object usable as a whole

Rejected.
Once a field has been moved out, the object can neither satisfy a method that receives the whole receiver nor be mistaken for a live value.
It may only be taken further apart, and it is dropped at the end of its block.
This matches Rust's treatment of a destructed value.

Where: OWN-06, reasoning "Why destruction is restricted to direct field access"

### Mirroring a destructed `.lat` record to a `.java` class with `public final` component fields

Rejected.
It makes a declaration's `.java` identity depend on whether some `.lat` source destructs it, which is the one thing LAT-00 forbids.
The `give`-of-a-component spelling desugars instead to a generated `@consuming intoClass()` method that moves the components into a companion class, which is then taken apart by ordinary destruction.
The record keeps its `record` identity in the mirror, and destruction adds no semantics.

Where: LAT-08, LAT-00, OWN-06, reasoning "Why destruction is restricted to direct field access"

### Making a mutable `onDrop()` receiver opt-in, or keeping the body read-only

Rejected.
Cleanup routinely writes, flushing a buffer, resetting a field, or decrementing a count, which is why Rust's `Drop::drop` takes `&mut self`.
`onDrop()` runs where `this` is uniquely owned and no borrow is live, so its receiver is mutable whatever the kind of the class and with no annotation.
An opt-in would diverge from Rust and burden the most ordinary cleanup bodies.
The immutability of an immutable class still holds through teardown, so its `onDrop()` stays read-only, and the field-level rules still gate every write.

Where: MUT-15, DROP-05, reasoning "Why `onDrop()`'s receiver is mutable"

### Two methods per operation, one per receiver mutability (`iterator` and `iteratorMut`, `get` and `getMut`)

Rejected in favor of receiver-inherited mutation.
One `@readonly(InheritFrom.RECEIVER)` declaration lets the receiver's mutability flow to the `@bound` return, so a single `iterator()` serves both reading and in-place update, and the paired methods and the paired interfaces collapse onto it.
Rust pays the duplication to keep mutation explicit, while Laterita opts in to the polymorphic form, as D's `inout` and C++23's explicit object parameter do, and keeps plain `@readonly` always read-only.

Where: MUT-17, STD-08, reasoning "Why the receiver mode can be inherited"

### Immutable classes by default, with a positive mutability annotation on the declaration

Rejected.
It reverses the meaning of every bare Java class, so ordinary-looking Java source would mean something else in Laterita.
It still needs a negative companion for the contexts where a default grants mutation, leaving two words on an axis that one word covers.
It also puts the annotation on builders, collections, counters, and streams, which are the classes most often written by hand.
Mutable is the default kind, and `@ro` is the only annotation.

Where: MUT-01, MUT-10, HIER-02, reasoning "Why `@ro` is the single mutability marker", reasoning "Why immutability is the marked class kind"

### A positive receiver-mutation annotation on methods (`@mutating`)

Rejected.
It leaves one position in the language where a bare declaration is restricted rather than unrestricted, against `final`, `@ro`, `@readonly`, and `@local`, which all withdraw.
It also separates the receiver from the parameter beside it, since a bare parameter lends mutably while a bare receiver would not.
The receiver takes the mutable default and `@readonly` withdraws it, at the cost of a marker on an ordinary accessor, which MUT-71 reports.

Where: MUT-13, MUT-41, MUT-71, HIER-05, reasoning "Why methods declare their receiver effect in the signature"

### A positive mutability annotation on a variable (`@mut`)

Rejected.
Mutability may be withdrawn freely and never added, so a positive annotation has an error case in every position and a redundant case in most, while a negative one may always be written and never has to fail.
With mutable as the default, no position needs to add mutability, so a positive annotation has no work to do.
`@ro` is the whole axis.

Where: MUT-01, MUT-30, MUT-31, reasoning "Why `@ro` is the single mutability marker"

### `@const`, `@immutable`, `@frozen`, or `@fixed` as the name of the mutability annotation

Rejected.
`const` is a reserved Java keyword (JLS 3.9), so `@const` does not parse, and the C++ and D spelling is unavailable at any length.
`@immutable` and `@frozen` name a state of the value rather than the capability a position withdraws, and neither offers a method modifier a reader could pair with it.
`@fixed` names the assignment axis `final` already owns, and `@fixed class Money` is read as a class that cannot change.
The axis is `@ro` on variables and types and `@readonly` on methods, with `readonly(x)` in expression position.

Where: MUT-01, MUT-13, MUT-42, reasoning "Why the axis is spelled `@ro` and `@readonly`"

### A neutral `Object` belonging to neither class kind

Rejected.
A third kind held by one class adds a case to every rule that reads a supertype's kind, and buys only a different default for a class extending `Object` directly.
`Object` is an ordinary mutable class, and `@ro Object` names its frozen view, which is the top type and the implicit type-parameter bound.

Where: HIER-02, MUT-30, TARG-03, reasoning "Why the kind is declared and the default is mutable"

### Inferring the mutability of a parameter, a field, or a return from its uses

Rejected.
OWN-00 requires every fact needed to use a class to sit on its declaration, and all three are published contract: inferring them would make a caller's obligations depend on a body it cannot see, and would change a published signature whenever an unrelated line of that body changed.
A local variable's borrow mode is classified from its uses precisely because it is not published and the compiler already sees every one of them.

Where: OWN-00, MUT-60, MUT-41, MUT-21, reasoning "Why a local's borrow mode follows its uses", reasoning "Why a bare parameter lends mutably while a bare receiver does not"

### `@ro C` as a supertype of `C`, carrying the immutable kind down to it

Rejected.
It would make every class immutable through its own frozen view.
`@ro C` is an interface that every mutable `C` implements, and a class takes its kind from its own declaration alone.

Where: MUT-30, MUT-10, HIER-04, reasoning "Why `@ro C` is the frozen view rather than a supertype"

### `@ro` on a type-parameter declaration widening the bound

Rejected.
It combines two independent decisions in one annotation: which type arguments the parameter accepts, which is the bound's job, and what a use of the parameter may do, which is the annotation's.
The combined form has no spelling for "accepts only mutable type arguments, uses them frozen", and it makes `class Foo<@ro T>` accept type arguments a reader would not deduce from the bound.
`@ro` annotates each use and leaves the bound alone.

Where: TARG-03, MUT-30, reasoning "Why the bound decides admission and the argument decides mutability"

### `Object` as the implicit type-parameter bound

Rejected.
`Object` is mutable, and a mutable bound admits only mutable type arguments, so an unconstrained `class Box<T>` would reject `Box<String>`.
The implicit bound is the top type `@ro Object`, which admits every type argument, as Java's implicit `Object` bound does.

Where: TARG-03, MUT-30, HIER-02, reasoning "Why the bound decides admission and the argument decides mutability"

### A dedicated rule set placing primitive types outside the ownership and borrow rules

Rejected.
It restates for `int`, `long`, and the rest what the rules for an immutable class already say.
A primitive type carries no mutable surface, so it is immutable, every primitive position is immutable, and the copy substitution of MUT-12 makes every primitive parameter, return, and field a value copy with no borrow to reason about.

Where: MUT-18, MUT-12, MUT-31, OQ-33, reasoning "Why primitives need no rules of their own"

### A positive mutability annotation on a type-parameter bound (`<T extends @mut Counter>`)

Rejected.
A mutable bound already accepts only mutable type arguments, since the frozen view `@ro C` is not a subtype of `C` and fails the bound.
The annotation would restate the bound it is attached to.

Where: TARG-03, MUT-30, HIER-04, reasoning "Why the bound decides admission and the argument decides mutability"

### A third, either-kind type-parameter annotation (`<@eitherKind T extends Counter>`)

Rejected.
A generic body is checked once against its bound, so it cannot also be proved sound against a stronger reading of the same parameter.
The cases it targets are covered already: a body that mutates a `T` declares a mutable bound, a body that does not needs no annotation, because element mutability reaches the caller from the variable holding the container and from `@readonly(InheritFrom.RECEIVER)`, and a body that must freeze its elements annotates the uses and leaves the bound to decide what is accepted.
Mutability polymorphism is a property of a method, carried by MUT-17, not a property of a type parameter.

Where: TARG-03, MUT-17, MUT-14, COMP-02, reasoning "Why the bound decides admission and the argument decides mutability"

## Deferred, neither rejected nor specified

### An early-cleanup statement (`drop x;`)

Not specified.
Restructuring the scope covers the rare cases, and an escape hatch reintroduces the double-drop surface.
It can be added later without breaking existing code.

Where: reasoning "Why explicit `onDrop()` calls are forbidden"

## Renamed code groups

The `BIND` and `MOVE` code groups, and the four-section structure of Variables, Mutability, Move and Borrow, and Lifetimes, are reorganized into six topics: `OWN` for ownership, `LIFE` for lifetime intersection, `MUT` for mutability, `HIER` for the class hierarchy and override, `TARG` for annotations in generic type arguments, and `STAT` for static storage.
The reorganization preserves every rule.
The old codes are obsolete, and every cross-reference in the spec uses the new ones.

A field, a record component, or a generic type argument that holds a borrow is spelled `@borrow`, where those positions once took `@bound`.
`@bound` bounds a value's lifetime only where a source relationship is declared: on a parameter, whose source is that parameter, on a return, whose source is `this`, and on the variable that holds such a value.
Separating the two tokens makes the structural role, that a position holds a borrow whose source is fixed elsewhere, and the relational role, that a value is bound to a named source, legible where each is declared.
A generic type argument names no source, so it is structural and takes `@borrow`.

### `broken()` as a diverging call returning any type

Rejected.
An unreachable path is declared by constructing an `UncompilableException`, written `throw broken(...)` (UNR-01, UNR-02).
A call declared `static <T> T broken()` takes a different spelling in a value-returning method than in a `void` one or a constructor, so the form depends on the return type, and `javac`'s reachability analysis (JLS 14.21) does not treat the call as ending the path, leaving `javac` and Laterita disagreeing about which statements are dead.

Where: UNR-01, UNR-02, reasoning "Why an exception, not a diverging call"

### A bottom type as the type of a divergent expression

Rejected.
Java has no bottom type, so a `.java` source cannot declare one and `javac` cannot infer it (COMP-06).
Divergence is a `throw`, which the JLS already treats as ending a path.

Where: UNR-01, UNR-02, NULL-03, reasoning "Why an exception, not a diverging call"


## Resolved open questions

A tombstone names the question, summarizes its resolution, and points at the spec codes and the reasoning that record the decision.
The original wording of each question is in this file's git history.

### OQ-01 Panic safety and lock poisoning

Resolved by the closure-scoped API of `Mutex<T>` (STD-09) and by THR-10.
A mutex whose critical-section closure throws is poisoned, every later acquirer gets a `PoisonedException`, and there is no bypass.

### OQ-02 Exception ergonomics beyond what ownership forces

Resolved by EXC-01, which preserves Java's `try`, `catch`, `finally`, and `Throwable` hierarchy unchanged, and by EXC-05, which drops the checked and unchecked distinction and leaves `throws` documentary.
The narrower question of restoring checked exceptions is reopened as OQ-22.

### OQ-03 Reflection model

Resolved as none: the language and its standard library provide no runtime reflection.

### OQ-04 Cross-thread safety marker

Resolved as `@local` (STD-07).

### OQ-05 Closure interface names

Dissolved by the anonymous functional interface (FN-01): there are no closure-interface names to fix, because there are no closure interfaces.

### OQ-07 Method-level `mut` syntax

Resolved by MUT-01 and MUT-13: a method mutates its receiver unless it is annotated `@readonly`.
Laterita introduces no keyword for it (RESV).

### OQ-08 Owned and borrowed strings, one type or two

Resolved as one type.
The compiler tracks per variable whether a `String` is owned or borrowed (STR-02), and `clone()` (OBJ-02) resolves any mismatch between the two.
The two-type model is rejected, as recorded above.

### OQ-09 `Iterator.remove` and `ConcurrentModificationException`

Resolved by STD-08: borrow-checked iteration reuses Java's `Iterator` and `ListIterator`.
OWN-03 makes concurrent modification a compile-time error, so `ConcurrentModificationException` and the `modCount` guard leave the language.

### OQ-12 Doubly-linked structures and graph data

Resolved by `Rc<T>` and `Arc<T>` on the forward edges together with `WeakReference<T>` (STD-03) on the back edges.
No dedicated graph type is added.

### OQ-13 User-invoked `close()` and early cleanup

Resolved by DROP-06: `onDrop()` is `@internal` and never invoked by user code, and a user-declared `close()` survives migration as an ordinary method, distinct from `onDrop()`.
No early-cleanup statement is specified, as recorded above.
The number OQ-13 tracked the rule that `onDrop()` must not block before it was reused for this question, and that rule is THR-05 and THR-06.

### OQ-14 Ownership of strings

Resolved by STR-06 for the literal borrow, STR-07 for the absence of mutating `String` methods, and STR-08 for the borrowing receiver.
The remaining question of buffer splitting was tracked under OQ-17.

### OQ-16 Mutable `String`, and which methods belong where

Resolved by STR-07: `String` declares no mutating method at all, and bulk construction stays on `StringBuilder`.

### OQ-17 Buffer splitting for `String`

Resolved by STR-07.
A `@bound String` is read-only, so a substring view is an ordinary shared borrow under OWN-03.
Splitting a mutable array is resolved under OQ-19 by ARR-01.

### OQ-18 `onDrop()` reaching already-dropped subclass state through virtual dispatch

Resolved by DROP-09: an `onDrop()` body may be declared only on a `final` class, so no dispatch into released subclass storage can occur.

### OQ-19 Ownership splitting of mutable arrays

Resolved by ARR-01, ARR-03, and ARR-04: methods on `T[]` and on `laterita.lang.Arrays`, with `MutableConsumer` for the `.java` surface, and the result carried by the general-purpose `Pair<L, R>`.
The substitution rule of TARG-01 makes a dedicated bound-pair type unnecessary.
The cross-thread case is resolved separately under OQ-21.

### OQ-21 Cross-thread ownership of split mutable slices

Resolved by ARR-01, ARR-02, and ARR-04.
`T[].splitOff(int)` consumes the receiver and returns two owning halves, backed by one reference-counted allocation and carried in a `Pair<T[], T[]>` for extraction field by field.
`Arrays.stream(@bound T[])` produces a JDK `Stream<T>` bound to the source array by the parameter form of OWN-17, and its `.parallel()` form covers read-only data-parallel processing through the standard `Spliterator`.
Parallel mutation in place stays on the `splitOff` path.

### OQ-24 Operator overloading for arithmetic value types

Resolved by LAT-07 as bounded operator sugar.
In `.lat`, `+ - * /` and unary `-` desugar to an instance method annotated `@Operator(op)`, whose name is free, so `BigDecimal.add`, `Instant.plus`, `Instant.minus`, and `Duration.negated` are eligible unchanged.
The comparison operators `< <= > >=` desugar through `Comparable.compareTo` when the left operand implements `Comparable` accepting the right.
The set is closed, with no `%`, no `[]`, no `==` or `!=`, and no compound assignment.
Eligibility is opt-in, by `@Operator` for arithmetic and by implementing `Comparable` for comparison.
Arithmetic resolution follows the left operand, falls back to the built-in numeric operators, and inserts no implicit conversion and no reflected form.
The unbounded and the interface-based forms are rejected, as recorded above.

### OQ-26 Newtype wrappers as zero-cost value classes

Resolved by composition of three rules: GEN-01, where `@Delegate` on the sole record component generates the forwarding surface, NABI-01, where a single-field aggregate has the size, alignment, and calling convention of its component with no dependency on Valhalla, and COMP-08, where inlining collapses the forwarders to direct calls.
No dedicated construct is introduced, and the newtype idiom emerges from applying these three rules to an ordinary `record`.
The field-less subclass is rejected, because widening to the superclass silently loses the distinct type, as recorded above.

### OQ-28 A dedicated method annotation for receiver consumption

Resolved by `@consuming` (OWN-15): receiver consumption is declared in modifier position, parallel to `@readonly` (MUT-13), and the two compose.
An explicit `this` parameter is not used for receiver consumption.

### OQ-29 Spelling call mode in the anonymous functional-interface form

Resolved by the optional prefix of FN-01: `@readonly (P) -> R` is shared-call, an unprefixed `(P) -> R` is mut-call, and `@consuming (P) -> R` is once-call.
The prefixes are the same `@readonly` (MUT-13) and `@consuming` (OWN-15) that a method declaration carries, attached to the single abstract method the type expression denotes.
Call mode is part of the identity of an anonymous functional interface (FN-02), and CLO-04 governs the flow of values between types of different call mode.
Override variance lives in HIER-05's table, where call mode is covariant in strength, and the variable-mode annotations on such a parameter follow that table directly.
The mapping to Rust is direct: `Fn`, `FnMut`, and `FnOnce`.

### OQ-31 Optional `<>` on constructor calls in `.lat`

Resolved by LAT-06: the diamond may be omitted from a parameterized constructor call in `.lat`.
Raw types are not a `.lat` surface form, so the diamond carries no information there.
The `.java` mirror inserts the diamond, since `.java` must remain parseable by `javac` (COMP-06) and the diamond-less form there is the raw-type constructor.

### OQ-32 Status of `synchronized` and `Object.wait` / `notify` / `notifyAll`

Resolved as not supported.
Those constructs rest on per-object run-time checks, `IllegalMonitorStateException` and dynamic lock resolution among them, that the compiler cannot lift to a static guarantee, and a `.java` Laterita source is not required to compile every Java construct.
The same coordination patterns are expressible through three standard-library types: `Mutex<T>` (STD-09) where the lock owns the data, `ReentrantLock` with `LockGuard` (STD-10, STD-11) where it does not, and `Condition` (STD-12) paired with a `ReentrantLock`.
`LockGuard.onDrop()` removes the missing-unlock error that motivates the Java keyword in the first place.

### OQ-33 Primitive types in the ownership and mutability rules

Resolved by MUT-18: a primitive type carries no mutable surface, so it is immutable, every primitive position is immutable (MUT-31), and the copy substitution of MUT-12 makes a primitive parameter, return, or field a value copy.
`@ro`, `@bound`, `@borrow`, and `@take` on a primitive are redundant rather than rejected, and a `@borrow` field of primitive type does not make its instance `@bound`.
A bare `int x` parameter is therefore a copy and not an out-parameter.
The dedicated primitive rule set is rejected, as recorded above.

### OQ-35 Capturing a reassigned local variable against Java's effectively-final rule

Resolved by CLO-01: a captured local variable must be effectively final, exactly as `javac` requires of a lambda (JLS 15.27.2), so a closure cannot assign a captured variable on either surface, and a mutate closure modifies the object through a mutable capture instead.
The `.lat`-only generated-holder desugaring is rejected, as recorded above.
With no other holder of such a borrow, OWN-03 keeps one mutable-borrow form, mutation through the value, together with the rule that a live borrow of `x` excludes assigning `x`.

### OQ-38 The surface name of the mutability annotation

Resolved by `@ro` in the variable and type positions (MUT-01), `@readonly` on methods (MUT-13), and `readonly(x)` as the MUT-42 intrinsic.
The annotation names the capability a position withdraws, which is what C# spells `readonly` on a field and on a `readonly struct`, and the two lengths match the two rates at which the axis is read.
One root carries a reader across both rules, and the two spellings keep the rules apart.
The names drawn from the assignment axis and from the `const` family are rejected, as recorded above.
