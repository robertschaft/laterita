# Laterita

**Java's syntax. Rust's memory model. No garbage collector.**

Laterita is a language that reads like Java and compiles like Rust. You write classes, methods, generics, and exceptions exactly as you always have — and the compiler hands you back the guarantees Rust earned: no use-after-free, no data races, no unobserved nulls, and deterministic cleanup at scope exit. There is no garbage collector to pause you, no `Send`/`Sync` vocabulary to learn, and no new keywords to memorize.

If you can read Java, you can read Laterita on day one. The ownership rules ride on a handful of annotations, so a Laterita program is still ordinary, `javac`-parseable Java source — the borrow checker is the only thing that got smarter.

The name comes from *laterite* — the rust-red, iron-rich tropical soil that volcanic islands grow coffee in.

## Why Laterita

Java has a mature ecosystem, a huge developer base, and a syntax those developers have internalized over decades. Rust has the memory model that has made systems software safer than any garbage-collected language can. Laterita is the attempt to give Java's developers Rust's guarantees without asking them to learn a new surface language.

## Highlights

- **Ownership and borrowing instead of GC.** Every value has a single owner; references are tracked borrows; cleanup is deterministic. When a binding leaves scope the compiler runs its `onDrop()` — no tracing collector, no finalizer surprises, no pauses.

- **No new keywords.** Mutability, ownership, lifetimes, and cleanup are expressed entirely through annotations (`@mut`, `@take`, `@bound`, `@local`, …) and two intrinsics (`give(x)`, `broken()`). The core language is annotated Java that `javac` parses unchanged.

- **Mutability is explicit and transitive.** A single `@mut` marker covers bindings, fields, methods, and parameters, and it must be present at *every* level of an access path to mutate. Immutability is the default everywhere.

- **Non-nullable by default.** A bare `T` excludes null and needs no null check; the nullable type admits it. The compiler proves the rest and narrows automatically after an `if (x != null)` check.

- **Moves are visible.** Plain assignment borrows; `give(x)` transfers ownership and ends the source binding. A parameter declares in its signature whether it borrows or consumes (`@take`), so every ownership transfer is readable.

- **No data races by construction.** Thread-affine types are marked `@local` and cannot cross thread boundaries. Shared mutable state goes through `Mutex<T>`, which owns the data it protects and hands it out only inside a scoped closure — there is no separate lock guard to leak or forget.

- **Exceptions, simplified.** Java's `try`/`catch`/`finally` and the `Throwable` hierarchy stay. The checked/unchecked distinction is gone — every exception is unchecked and `throws` becomes documentation. Stack traces resolve lazily, so throwing stays cheap.

- **Ahead-of-time, monomorphized, no reflection.** Laterita compiles natively with generic monomorphization and no runtime metadata. Reflection is removed; serializers, ORM mappers, and DI wiring are generated at build time by annotation processors instead.

- **Two source surfaces, one language.** `.java` files are the Java-compatible surface; `.lat` files add pure syntactic sugar (below). The two are mechanically inter-convertible — the file extension never changes a program's meaning. The reference compiler is `latc`.

## The `.lat` surface

`.java` files keep Laterita strictly within what `javac` can parse, expressing every ownership concept through annotations. `.lat` files lift that restriction with five sugar-only forms. Each one desugars *exactly* to the `.java` surface before any analysis runs and adds no new semantics — `.lat` is purely about writing the same program with lighter syntax.

### Nullable types and operators

A bare `T` already excludes null; `.lat` adds a nullable suffix and three operators for working with it. The `T?` suffix mirrors [JEP draft 8303099](https://openjdk.org/jeps/8303099) (Null-Restricted and Nullable Types), except Laterita makes non-null the *default* rather than requiring an explicit `T!` marker. The three operators are Kotlin-style and have no JEP counterpart:

- **`T?`** — nullable type; the `.lat` spelling of `@Nullable T`.
- **`expr?.m()`** — safe call; yields `null` instead of invoking `m` on a null receiver.
- **`a ?: b`** — elvis; evaluates to `a` when non-null, otherwise `b`.
- **`expr!!`** — null assertion; converts `T?` to `T`, throwing `NullPointerException` if null.

```java
String? maybeName = lookup(id);
String  shown     = maybeName?.toUpperCase() ?: "ANONYMOUS".clone();
String  forced    = maybeName!!;   // NullPointerException if maybeName is null
```

### Inline functional-interface types — `(P1, …, Pn) -> R`

Write a single-abstract-method signature directly as a method-parameter type — with full ownership modes — instead of declaring a named interface for the callback.

```java
<R> Stream<R> map(@mut (@take T) -> R fn);
void          forEach((@bound Record) -> void action);
```

In `.java` files the same callback is expressed by declaring a nominal functional interface and using it at the same position.

## Documents

| File | Purpose |
|---|---|
| [`doc/laterita-spec.md`](doc/laterita-spec.md) | The normative specification. §1–18 are the Java-compatible surface — every rule expressible as annotated `.java` that `javac` parses. §19 (`LAT-*`) specifies the `.lat` sugar. Each requirement carries a mnemonic code (`BIND-01`, `MOVE-03`, …) grouped by area: bindings, optionality, move/borrow, mutability, lifetimes, cleanup, copying, strings, closures, exceptions, unsafe, standard library, threads, compilation. |
| [`doc/laterita-reasoning.md`](doc/laterita-reasoning.md) | The design rationale. Section by section, it explains *why* each spec rule reads the way it does — the alternatives considered, the trade-offs taken, and the Java/Rust/Kotlin precedents followed or rejected. |
| [`doc/laterita-open-questions.md`](doc/laterita-open-questions.md) | Language-design questions raised but not yet resolved (`OQ-NN`). Each entry references the spec codes it touches. |
| [`doc/resolved-questions.md`](doc/resolved-questions.md) | Registry of closed decisions: rejected alternatives and resolved-OQ tombstones, so settled choices are not re-raised. |
| [`doc/terminology.md`](doc/terminology.md) | Defined terms used across the spec and reasoning. |

## Reading order

- New to the project: skim §1–§3 of the spec (bindings, optionality, move/borrow) and §19 (`.lat` forms), then read the matching sections of the reasoning document.
- Evaluating a design choice: open the reasoning document at the relevant section; cross-reference the spec codes it cites, and check `resolved-questions.md` for decisions already closed.
- Looking for what's still undecided: start with the open-questions document.

## License

Laterita is distributed under **GPL v2 with the Classpath Exception** — the same terms as OpenJDK. The exception means user programs may link against Laterita's libraries without themselves becoming subject to the GPL.

The license is dictated by the project's upstream sources:

- Standard-library code is forked and modified from **OpenJDK** (GPLv2 + Classpath Exception). Derivative works must remain under those terms.
- Borrow-checker logic and interface code is adapted from the **Rust project** (dual-licensed MIT OR Apache-2.0). Laterita takes it under the **MIT** half, which is GPLv2-compatible; the Apache-2.0 option is not.

See [`LICENSE`](LICENSE), [`ASSEMBLY_EXCEPTION`](ASSEMBLY_EXCEPTION), and [`NOTICE`](NOTICE) for the full terms, upstream attribution, and the rules for adding third-party code.
