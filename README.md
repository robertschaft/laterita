# Laterita

**Java's syntax.
Rust's memory model.
No garbage collector.**

Laterita is a language that reads like Java and compiles like Rust.
You write classes, methods, generics, and exceptions exactly as you always have, and the compiler hands you back the guarantees Rust earned: no use after free, no data races, no unobserved nulls, and deterministic cleanup at scope exit.
There is no garbage collector to pause you, no `Send` and `Sync` vocabulary to learn, and no new keywords to memorize.

If you can read Java, you can read Laterita on day one.
The ownership rules ride on a handful of annotations, so a Laterita program is still ordinary Java source that `javac` parses.
Only the borrow checker got smarter.

The name comes from *laterite*, the rust-red, iron-rich tropical soil that volcanic islands grow coffee in.

## Why Laterita

Java has a mature ecosystem, a huge developer base, and a syntax those developers have internalized over decades.
Rust has the memory model that has made systems software safer than any garbage-collected language can.
Laterita gives Java developers Rust's guarantees without asking them to learn a new surface language.

## Highlights

- **Ownership and borrowing instead of a collector.**
Every value has a single owner, every other reference to it is a tracked borrow, and cleanup is deterministic.
When a variable goes out of scope the compiler runs its `onDrop()`.
There is no tracing collector, no finalizer, and no pause.

- **No new keywords.**
Mutability, ownership, lifetimes, and cleanup are expressed entirely through annotations (`@fixed`, `@readonly`, `@take`, `@bound`, `@local`, and a few more) and through the static methods of `laterita.lang.Intrinsics` (`give(x)`, `fixed(x)`) and of `laterita.lang.Broken` (`broken()`).
The core language is annotated Java that `javac` parses unchanged.

- **Immutability is explicit and transitive.**
`@fixed` declares a variable or a type immutable.
Mutable is the default, as in Java, and `@fixed` is written where the guarantee is wanted.
A method annotated `@readonly` may only read its receiver, and only such methods appear in the frozen view of a class.

- **Non-nullable by default.**
A type that is not annotated `@Nullable` excludes null and needs no null check, while a nullable type admits it.
The compiler proves the rest and narrows the type after an `if (x != null)` check.

- **Moves are visible.**
An ordinary assignment borrows, while `give(x)` transfers ownership and ends the source variable.
A parameter declares in its signature whether it borrows or takes ownership (`@take`), so every transfer is readable.

- **No data races by construction.**
A thread-affine type is annotated `@local` and cannot cross a thread boundary.
Shared mutable state goes through `Mutex<T>`, which owns the data it protects and hands it out only inside a closure, so there is no separate guard to leak or forget.

- **Exceptions, simplified.**
Java's `try`, `catch`, `finally`, and the `Throwable` hierarchy stay.
The checked and unchecked distinction is gone, every exception is unchecked, and `throws` becomes documentation.
A stack trace resolves lazily, so throwing stays cheap.

- **Ahead of time, monomorphized, no reflection.**
Laterita compiles natively, monomorphizes generics, and emits no runtime metadata.
Reflection is removed, and serializers, ORM mappers, and dependency-injection wiring are generated at build time by annotation processors instead.

- **Two source surfaces, one language.**
A `.java` file is the Java-compatible surface, and a `.lat` file adds the syntactic sugar described below.
The two are mechanically inter-convertible, so the file extension never changes a program's meaning.
The reference compiler is `latc`.

## The `.lat` surface

A `.java` file keeps Laterita within what `javac` can parse, expressing every ownership concept through annotations.
A `.lat` file lifts that restriction with five forms that are sugar and nothing else.
Each desugars exactly to the `.java` surface before any analysis runs and adds no semantics, so `.lat` is only about writing the same program with lighter syntax.

### Nullable types and operators

A type already excludes null unless it is annotated, and `.lat` adds a nullable suffix and three operators for working with it.
The `T?` suffix mirrors [JEP draft 8303099](https://openjdk.org/jeps/8303099) (Null-Restricted and Nullable Types), except that Laterita makes non-null the *default* rather than requiring an explicit `T!` marker.
The three operators are Kotlin-style and have no JEP counterpart.

- **`T?`**: the nullable type, the `.lat` spelling of `@Nullable T`.
- **`expr?.m()`**: the safe call, which yields `null` instead of invoking `m` on a null receiver.
- **`a ?: b`**: the elvis operator, which evaluates to `a` when it is non-null and to `b` otherwise.
- **`expr!!`**: the null assertion, which converts `T?` to `T` and throws `NullPointerException` if the value is null.

```java
String? maybeName = lookup(id);
String  shown     = maybeName?.toUpperCase() ?: "ANONYMOUS".clone();
String  forced    = maybeName!!;   // NullPointerException if maybeName is null
```

### Inline functional-interface types

Write a single-abstract-method signature directly as a parameter type, with its ownership modes, instead of declaring a named interface for the callback.

```java
<R> Stream<R> map((@take T) -> R fn);
@bound Field  select((@bound Record) -> @bound Field selector);
```

In a `.java` file the same callback is expressed by declaring a nominal functional interface and using it in the same position.

## Documents

| File | Purpose |
|---|---|
| [`doc/laterita-spec.md`](doc/laterita-spec.md) | The normative specification. Every topic except `LAT` is the Java-compatible surface, and each rule there is expressible as annotated `.java` that `javac` parses. The `LAT` topic specifies the `.lat` sugar. Each rule carries a mnemonic code (`OWN-01`, `DES-02`, and so on) grouped by topic: ownership, lifetimes, mutability, class hierarchy, static storage, cleanup, destruction, copying, optionality, exceptions, functional interfaces, closures, strings, arrays, unsafe code, standard library, threads, and the compilation model. |
| [`doc/laterita-reasoning.md`](doc/laterita-reasoning.md) | The design rationale. It explains *why* each rule reads the way it does: the alternatives considered, the trade-offs taken, and the Java, Rust, and Kotlin precedents followed or rejected. |
| [`doc/laterita-open-questions.md`](doc/laterita-open-questions.md) | Language-design questions raised but not yet resolved (`OQ-NN`). Each entry references the spec codes it touches. |
| [`doc/resolved-questions.md`](doc/resolved-questions.md) | The registry of closed decisions: rejected alternatives and resolved-question tombstones, so a settled choice is not raised again. |
| [`doc/terminology.md`](doc/terminology.md) | The terms the specification uses that Java does not define. |

## Reading order

- New to the project: skim the [specification](doc/laterita-spec.md) topics in the order they appear, and look up unfamiliar terms in the [terminology](doc/terminology.md).
- Evaluating a design choice: open the reasoning document at the relevant section, follow the spec codes it cites, and check `resolved-questions.md` for decisions already closed.
- Looking for what is still undecided: start with the open-questions document.

## License

Laterita is distributed under **GPL v2 with the Classpath Exception**, the same terms as OpenJDK.
The exception means that a user program may link against Laterita's libraries without itself becoming subject to the GPL.

The license is dictated by the project's upstream sources.

- Standard-library code is forked and modified from **OpenJDK** (GPLv2 with the Classpath Exception).
A derivative work must remain under those terms.
- Borrow-checker logic and interface code is adapted from the **Rust project**, which is dual-licensed MIT or Apache-2.0.
Laterita takes it under the **MIT** half, which is compatible with GPLv2, and not under the Apache-2.0 option.

See [`LICENSE`](LICENSE), [`ASSEMBLY_EXCEPTION`](ASSEMBLY_EXCEPTION), and [`NOTICE`](NOTICE) for the full terms, the upstream attribution, and the rules for adding third-party code.
