---
name: Rust vs Java Vocabulary
description: Key Rust concepts appearing in the Laterita spec and how they map to Java
type: reference
---

## Core Ownership Concepts (Not in Standard Java)

These Rust concepts are central to Laterita and have no direct Java equivalent:

### Ownership
One binding owns a value and may move it. No GC; cleanup happens at scope exit when owner drops. Closest Java analog: final ownership of an object (rare), but Java's GC handles cleanup implicitly.

### Move / Give
Transferring ownership from one binding to another. The source becomes invalid. Expressed with `give` keyword at call sites and `take` parameters. No Java equivalent; Java references are always borrows.

### Borrow / Reference
A non-owning view into an owned value. Must not outlive the owner. Two flavors: shared (immutable) and mutable (exclusive). Java references are all implicit borrows (from GC's perspective).

### Lifetime
How long a borrow is valid. Bounded by the owner's lifetime. Must be explicitly annotated in signatures via `bound` for borrowed returns. Java has no lifetime concept; GC tracks liveness implicitly.

### Drop / Cleanup
Calling `onDrop()` when a binding leaves scope (normal exit, return, exception, etc.). Deterministic, guaranteed on all paths. Similar to C++ destructors or Java's try-with-resources (close) and finalizers (unreliable).

---

## Mutability Differences

| Concept | Java | Laterita | Difference |
|---------|------|----------|-----------|
| Field immutability | `final` | non-`mut` by default | Laterita makes immutable the default |
| Transitive immutability | No | Yes (via `mut` transitivity) | Java allows mutating fields through immutable refs |
| Method mutation | No marker | `mut` method marker | Java has no signature-level mutation declaration |
| Interior mutability | `volatile`, `AtomicX`, locks | `Cell<T>` (unsafe primitive) | Java uses various mechanisms; Laterita unifies as one primitive |
| Parameter mutability | `final` parameters (convention) | `mut` parameter | Laterita tracks in signature; Java does not |

---

## Smart Pointers (Shared Ownership)

These are Rust's answer to multiple owners; Java doesn't need them (GC owns everything):

### Rc<T> (Reference Counting)
Like having N references to the same object, tracked by a counter. Each `new Rc` or `.share()` bumps the count; drop decrements. When count reaches zero, value is freed. Single-threaded only. No direct Java equivalent; Java's GC is implicit reference counting.

### Arc<T> (Atomic Reference Counting)
Like `Rc<T>` but thread-safe (atomic operations). Allows moving an `Arc` across threads. Java has no need (GC is global), but `Arc<Mutex<T>>` is a common Rust pattern for multi-threaded shared state.

### WeakReference<T>
A reference that doesn't count toward refcount. Used to break cycles in `Rc` graphs. Similar concept to Java's `java.lang.ref.WeakReference`, but Laterita's weak returns a strong `Rc?` on `get()` (you must own the refcount bump), not the bare referent.

---

## Compiler Concepts

| Term | Rust | Laterita | Context |
|------|------|----------|---------|
| Monomorphization | Yes | Yes (per `COMP-02`) | Each generic instantiation produces separate code |
| Borrow checker | Core | Core (per `MOVE-04`, `LIFE-01`) | Compile-time verification of lifetime safety |
| Drop flags | Used internally | Used (per `DROP-04`, `EXC-03`) | Track per-field destruction state |
| Variance | In generics & override context | In override context (per `MOVE-10`) | How strict/loose parameter modes can be in overrides |

---

## Terminology Recurring Across Sections

**Ownership axis terminology** appears in:
- Parameters (`MOVE-03`: bare, `mut`, `take`, `take mut`)
- Method receivers (`BIND-05`, `BIND-07`: bare, `mut`, `give`)
- Binding declarations (`MOVE-02`: `give` to move)
- Override rules (`MOVE-10`: `take` invariant, `mut` contravariant)

**Lifetime terminology** appears in:
- Return types (`LIFE-02`: `bound` for borrowed returns)
- Parameters (`LIFE-02`: `bound` for borrowed parameters)
- Closures (`CLO-06`: capture lifetimes propagate)
- Functional interfaces (`FN-01`: `bound` in SAM parameters)

**Cleanup terminology** appears in:
- Scope exit (`DROP-01`, `DROP-02`, `DROP-03`)
- Exception unwind (`EXC-02`, `EXC-03`)
- Destruction (`DROP-04`, `DES` topic)
- Iterators (`STD-08`: invalidating a borrow via iterator mutation)
- Threads (`THR-06`: `Thread.onDrop()` interruption)

---

## Concepts Rust Has That Laterita Dropped

- **Traits & trait objects**: Laterita uses nominal (named) interfaces, not traits. No impl blocks.
- **Pattern matching**: Not part of Laterita; control flow is Java's `if`, `switch`, loops.
- **Macros**: Not provided; code generation happens at compile time via annotation processors.
- **Unsafe blocks**: Laterita restricts `unsafe` to private methods only (per `UNS-01`), not arbitrary blocks.
- **Modules**: Laterita reuses Java's package system.
- **Send / Sync traits**: Laterita uses the single `local` marker (per `STD-07`) instead of Rust's auto-trait machinery.

---

## Concepts Java Has That Laterita Removes or Changes

- **Garbage collection**: Replaced by ownership and refcounting.
- **`synchronized` keyword**: Replaced by `Mutex<T>` (the `STD` / `THR` topics).
- **Checked exceptions**: All exceptions are unchecked (per `EXC-05`); `throws` is documentation.
- **Reflection**: Removed entirely (per `COMP-05`); replaced by compile-time code generation.
- **`var` keyword**: Replaced by `let` and `mut` (per `BIND-01`).
- **Implicit null**: Replaced by non-nullable by default, `T?` for nullable (per `NULL-01`).
