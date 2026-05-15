# Laterita

**A modern Java that adopts Rust's memory model.**

Laterita keeps Java's syntax, type system, and developer experience, and replaces tracing garbage collection with Rust's ownership-and-borrow discipline. The goal is a language a Java developer can read on day one, with the safety properties (no use-after-free, no data races, no unobserved nulls, deterministic cleanup) that Rust earned by taking those problems seriously.

The name comes from *laterite* — the rust-red, iron-rich tropical soil that volcanic islands grow coffee in.

## Intent

Java has a mature ecosystem, a huge developer base, and a syntax those developers have internalized over decades. Rust has the memory model that has made systems software safer than any garbage-collected language can. Laterita is the attempt to give Java's developers Rust's guarantees without asking them to learn a new surface language.

Concretely:

- **Ownership and borrowing instead of GC.** Values have a single owner; references are tracked borrows; cleanup is deterministic at scope exit.
- **Non-nullable by default.** `T` excludes null; `T?` admits it. The compiler proves the rest.
- **Mutability is explicit and transitive.** A single `mut` keyword marks every mutation point — bindings, fields, methods, parameters — and propagates through access paths.
- **No data races by construction.** Thread-affine types are marked `local` and cannot cross thread boundaries; shared mutable state goes through `Mutex<T>`, which owns the data it protects.
- **Java's syntactic vocabulary.** Types come first (`String name`), classes and methods look like Java, exceptions still exist where they earn their keep.

## Documents

| File | Purpose |
|---|---|
| [`doc/laterita-spec.md`](doc/laterita-spec.md) | The normative specification. Each requirement carries a mnemonic code (`BIND-01`, `MOVE-03`, …) grouped by area: bindings, optionality, move/borrow, mutability, lifetimes, cleanup, copying, unreachability, strings, closures, exceptions, unsafe, standard library, threads, compilation. This is what a compiler must implement. |
| [`doc/laterita-reasoning.md`](doc/laterita-reasoning.md) | The design rationale. Section by section, it explains *why* each spec rule reads the way it does — the alternatives considered, the trade-offs taken, and the Java/Rust/Kotlin precedents being followed or rejected. Read this when the spec leaves you wondering "but why?". |
| [`doc/laterita-open-questions.md`](doc/laterita-open-questions.md) | Issues raised during design that were *not* resolved. Each entry references the spec codes it touches and explains what would need to be decided to close it. Resolved questions are reduced to a one-line reminder at the bottom of the file. |

## Reading order

- New to the project: skim §1–§3 of the spec (bindings, optionality, move/borrow), then read the matching sections of the reasoning document.
- Evaluating a design choice: open the reasoning document at the relevant section; cross-reference the spec codes it cites.
- Looking for what's still undecided: start with the open-questions document.

## License

Laterita is distributed under **GPL v2 with the Classpath Exception** — the same terms as OpenJDK. The exception means user programs may link against Laterita's libraries without themselves becoming subject to the GPL.

The license is dictated by the project's upstream sources:

- Standard-library code is forked and modified from **OpenJDK** (GPLv2 + Classpath Exception). Derivative works must remain under those terms.
- Borrow-checker logic and interface code is adapted from the **Rust project** (dual-licensed MIT OR Apache-2.0). Laterita takes it under the **MIT** half, which is GPLv2-compatible; the Apache-2.0 option is not.

See [`LICENSE`](LICENSE), [`ASSEMBLY_EXCEPTION`](ASSEMBLY_EXCEPTION), and [`NOTICE`](NOTICE) for the full terms, upstream attribution, and the rules for adding third-party code.
